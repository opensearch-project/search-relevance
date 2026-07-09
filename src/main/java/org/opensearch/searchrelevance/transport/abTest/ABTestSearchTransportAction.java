/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */
package org.opensearch.searchrelevance.transport.abTest;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.opensearch.action.search.MultiSearchRequest;
import org.opensearch.action.search.MultiSearchResponse;
import org.opensearch.action.search.SearchRequest;
import org.opensearch.action.support.ActionFilters;
import org.opensearch.action.support.HandledTransportAction;
import org.opensearch.common.inject.Inject;
import org.opensearch.common.util.concurrent.ThreadContext;
import org.opensearch.core.action.ActionListener;
import org.opensearch.core.rest.RestStatus;
import org.opensearch.search.SearchHit;
import org.opensearch.searchrelevance.algorithm.TeamDraftInterleaver;
import org.opensearch.searchrelevance.dao.ABTestDao;
import org.opensearch.searchrelevance.dao.SearchConfigurationDao;
import org.opensearch.searchrelevance.exception.SearchRelevanceException;
import org.opensearch.searchrelevance.model.ABTest;
import org.opensearch.searchrelevance.model.builder.SearchRequestBuilder;
import org.opensearch.tasks.Task;
import org.opensearch.threadpool.ThreadPool;
import org.opensearch.transport.TransportService;
import org.opensearch.transport.client.Client;

/**
 * Transport action for AB test search with Team Draft Interleaving.
 *
 * Flow:
 * 1. Get AB test metadata (config IDs, UUIDs, enabled flag)
 * 2. Get config(s) based on test status
 * 3. Prepare search requests
 * 4. Call msearch (single round-trip, ordered responses)
 * 5. Interleave/merge results
 */
public class ABTestSearchTransportAction extends HandledTransportAction<ABTestSearchRequest, ABTestSearchResponse> {
    private static final Logger LOGGER = LogManager.getLogger(ABTestSearchTransportAction.class);
    private static final int DEFAULT_SEARCH_SIZE = 10;

    private final ABTestDao abTestDao;
    private final SearchConfigurationDao searchConfigurationDao;
    private final Client client;
    private final ThreadPool threadPool;
    private final TeamDraftInterleaver interleaver;

    @Inject
    public ABTestSearchTransportAction(
        TransportService transportService,
        ActionFilters actionFilters,
        ABTestDao abTestDao,
        SearchConfigurationDao searchConfigurationDao,
        Client client,
        ThreadPool threadPool,
        TeamDraftInterleaver interleaver
    ) {
        super(ABTestSearchAction.NAME, transportService, actionFilters, ABTestSearchRequest::new);
        this.abTestDao = abTestDao;
        this.searchConfigurationDao = searchConfigurationDao;
        this.client = client;
        this.threadPool = threadPool;
        this.interleaver = interleaver;
    }

    @Override
    protected void doExecute(Task task, ABTestSearchRequest request, ActionListener<ABTestSearchResponse> listener) {
        if (request == null || request.getTestId() == null || request.getParams() == null || request.getParams().isEmpty()) {
            listener.onFailure(new SearchRelevanceException("Request, testId, and params cannot be null", RestStatus.BAD_REQUEST));
            return;
        }
        // Capture caller's security context before DAO calls stash it
        final Supplier<ThreadContext.StoredContext> restoreCallerContext = client.threadPool()
            .getThreadContext()
            .newRestorableContext(false);

        // Step 1: Get AB test metadata
        abTestDao.getABTest(request.getTestId(), ActionListener.wrap(abTestResponse -> {
            if (abTestResponse.getHits().getHits().length == 0) {
                listener.onFailure(new SearchRelevanceException("AB test not found", RestStatus.NOT_FOUND));
                return;
            }
            Map<String, Object> source = abTestResponse.getHits().getHits()[0].getSourceAsMap();
            Boolean enabledObj = (Boolean) source.get(ABTest.ENABLED);
            String configAId = (String) source.get(ABTest.SEARCH_CONFIGURATION_A);
            String configBId = (String) source.get(ABTest.SEARCH_CONFIGURATION_B);
            String configAUuid = (String) source.get(ABTest.CONFIG_A_UUID);
            String configBUuid = (String) source.get(ABTest.CONFIG_B_UUID);
            String testId = (String) source.get(ABTest.TEST_ID);

            if (enabledObj == null || configAId == null || configAUuid == null || testId == null) {
                listener.onFailure(
                    new SearchRelevanceException("AB test document is missing required fields", RestStatus.INTERNAL_SERVER_ERROR)
                );
                return;
            }
            boolean enabled = enabledObj;

            // Step 2: Get config A
            searchConfigurationDao.getSearchConfiguration(configAId, ActionListener.wrap(configAResponse -> {
                if (configAResponse.getHits().getHits().length == 0) {
                    listener.onFailure(new SearchRelevanceException("Search configuration A not found", RestStatus.NOT_FOUND));
                    return;
                }
                Map<String, Object> configASource = configAResponse.getHits().getHits()[0].getSourceAsMap();
                String queryA = (String) configASource.get("query");
                String pipelineA = (String) configASource.get("searchPipeline");
                String targetIndex = (String) configASource.get("index");
                int sizeA = configASource.containsKey("size") ? ((Number) configASource.get("size")).intValue() : DEFAULT_SEARCH_SIZE;

                String searchText = request.getParams().get("SearchText");
                if (searchText == null || searchText.isEmpty()) {
                    listener.onFailure(new SearchRelevanceException("SearchText is required in query_params", RestStatus.BAD_REQUEST));
                    return;
                }

                // If test is disabled, single search with config A only
                if (!enabled) {
                    SearchRequest searchRequestA = SearchRequestBuilder.buildSearchRequest(
                        targetIndex,
                        queryA,
                        searchText,
                        pipelineA,
                        sizeA
                    );
                    try (ThreadContext.StoredContext ctx = restoreCallerContext.get()) {
                        client.search(searchRequestA, ActionListener.wrap(searchResponse -> {
                            List<Map<String, Object>> responseHits = new ArrayList<>();
                            for (SearchHit hit : searchResponse.getHits().getHits()) {
                                responseHits.add(mapHit(hit, configAUuid));
                            }
                            listener.onResponse(new ABTestSearchResponse(testId, responseHits));
                        },
                            e -> listener.onFailure(
                                new SearchRelevanceException(
                                    "Search execution failed",
                                    e,
                                    isSecurityException(e) ? RestStatus.FORBIDDEN : RestStatus.INTERNAL_SERVER_ERROR
                                )
                            )
                        ));
                    }
                    return;
                }

                // Step 2b: Get config B (only when enabled)
                searchConfigurationDao.getSearchConfiguration(configBId, ActionListener.wrap(configBResponse -> {
                    if (configBResponse.getHits().getHits().length == 0) {
                        listener.onFailure(new SearchRelevanceException("Search configuration B not found", RestStatus.NOT_FOUND));
                        return;
                    }
                    Map<String, Object> configBSource = configBResponse.getHits().getHits()[0].getSourceAsMap();
                    String targetIndexB = (String) configBSource.get("index");
                    if (targetIndexB == null || !targetIndexB.equals(targetIndex)) {
                        listener.onFailure(
                            new SearchRelevanceException(
                                String.format(
                                    Locale.ROOT,
                                    "Both search configurations must target the same index. Config A targets [%s], Config B targets [%s]",
                                    targetIndex,
                                    targetIndexB
                                ),
                                RestStatus.BAD_REQUEST
                            )
                        );
                        return;
                    }
                    String queryB = (String) configBSource.get("query");
                    String pipelineB = (String) configBSource.get("searchPipeline");
                    int sizeB = configBSource.containsKey("size") ? ((Number) configBSource.get("size")).intValue() : DEFAULT_SEARCH_SIZE;

                    if (sizeA != sizeB) {
                        listener.onFailure(
                            new SearchRelevanceException(
                                String.format(
                                    Locale.ROOT,
                                    "Both search configurations must use the same size for fair comparison. Config A size [%d], Config B size [%d]",
                                    sizeA,
                                    sizeB
                                ),
                                RestStatus.BAD_REQUEST
                            )
                        );
                        return;
                    }

                    // Step 3: Prepare search requests
                    SearchRequest searchRequestA = SearchRequestBuilder.buildSearchRequest(
                        targetIndex,
                        queryA,
                        searchText,
                        pipelineA,
                        sizeA
                    );
                    SearchRequest searchRequestB = SearchRequestBuilder.buildSearchRequest(
                        targetIndex,
                        queryB,
                        searchText,
                        pipelineB,
                        sizeB
                    );

                    // Step 4: Call msearch — single round-trip, ordered responses
                    MultiSearchRequest msearchRequest = new MultiSearchRequest();
                    msearchRequest.add(searchRequestA);
                    msearchRequest.add(searchRequestB);

                    try (ThreadContext.StoredContext ctx = restoreCallerContext.get()) {
                        client.multiSearch(msearchRequest, ActionListener.wrap(msearchResponse -> {
                            MultiSearchResponse.Item[] items = msearchResponse.getResponses();
                            if (items[0].isFailure()) {
                                listener.onFailure(
                                    new SearchRelevanceException(
                                        "Search A failed",
                                        items[0].getFailure(),
                                        isSecurityException(items[0].getFailure()) ? RestStatus.FORBIDDEN : RestStatus.INTERNAL_SERVER_ERROR
                                    )
                                );
                                return;
                            }
                            if (items[1].isFailure()) {
                                listener.onFailure(
                                    new SearchRelevanceException(
                                        "Search B failed",
                                        items[1].getFailure(),
                                        isSecurityException(items[1].getFailure()) ? RestStatus.FORBIDDEN : RestStatus.INTERNAL_SERVER_ERROR
                                    )
                                );
                                return;
                            }

                            // Step 5: Interleave results with TDI
                            List<SearchHit> hitsA = Arrays.asList(items[0].getResponse().getHits().getHits());
                            List<SearchHit> hitsB = Arrays.asList(items[1].getResponse().getHits().getHits());
                            TeamDraftInterleaver.Result tdiResult = interleaver.interleave(
                                hitsA,
                                hitsB,
                                Math.max(hitsA.size(), hitsB.size())
                            );

                            List<Map<String, Object>> responseHits = new ArrayList<>();
                            Set<String> teamADocs = tdiResult.getTeamA();
                            for (SearchHit hit : tdiResult.getInterleavedHits()) {
                                String uuid = teamADocs.contains(hit.getId()) ? configAUuid : configBUuid;
                                responseHits.add(mapHit(hit, uuid));
                            }
                            listener.onResponse(new ABTestSearchResponse(testId, responseHits));

                        },
                            e -> listener.onFailure(
                                new SearchRelevanceException(
                                    "Search execution failed",
                                    e,
                                    isSecurityException(e) ? RestStatus.FORBIDDEN : RestStatus.INTERNAL_SERVER_ERROR
                                )
                            )
                        ));
                    }

                }, e -> listener.onFailure(new SearchRelevanceException("Failed to fetch search configuration B", e, RestStatus.NOT_FOUND)))
                );

            }, e -> listener.onFailure(new SearchRelevanceException("Failed to fetch search configuration A", e, RestStatus.NOT_FOUND))));

        }, e -> {
            if (e instanceof org.opensearch.ResourceNotFoundException) {
                listener.onFailure(new SearchRelevanceException("AB test not found", RestStatus.NOT_FOUND));
            } else {
                listener.onFailure(new SearchRelevanceException("Failed to read AB test", e, RestStatus.INTERNAL_SERVER_ERROR));
            }
        }));
    }

    private Map<String, Object> mapHit(SearchHit hit, String configUuid) {
        Map<String, Object> hitMap = new HashMap<>();
        hitMap.put("_index", hit.getIndex());
        hitMap.put("_id", hit.getId());
        hitMap.put("_score", hit.getScore());
        hitMap.put("_source", hit.getSourceAsMap());
        hitMap.put("_search_configuration_id", configUuid);
        return hitMap;
    }

    private boolean isSecurityException(Exception e) {
        return e.getClass().getSimpleName().contains("Security")
            || (e.getMessage() != null && e.getMessage().contains("no permissions for"));
    }
}
