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
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.opensearch.action.search.SearchRequest;
import org.opensearch.action.search.SearchResponse;
import org.opensearch.action.support.ActionFilters;
import org.opensearch.action.support.HandledTransportAction;
import org.opensearch.common.inject.Inject;
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
 * 1. Fetch AB test metadata (config IDs, UUIDs, enabled flag)
 * 2. Fetch both search configurations in parallel using GroupedActionListener
 * 3. If test is disabled: execute only config A (single search)
 *    If test is enabled: execute both queries in parallel, interleave with TDI
 * 4. Return results with _search_configuration_id per hit for click attribution
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
        abTestDao.getABTest(
            request.getTestId(),
            ActionListener.wrap(abTestResponse -> extractTestMetadataAndFetchConfigs(abTestResponse, request, listener), e -> {
                if (e instanceof org.opensearch.ResourceNotFoundException) {
                    listener.onFailure(new SearchRelevanceException("AB test not found", RestStatus.NOT_FOUND));
                } else {
                    listener.onFailure(new SearchRelevanceException("Failed to read AB test", e, RestStatus.INTERNAL_SERVER_ERROR));
                }
            })
        );
    }

    /**
     * Extracts AB test metadata (config IDs, UUIDs, enabled flag) from the response
     * and fetches both search configurations in parallel using GroupedActionListener.
     */
    private void extractTestMetadataAndFetchConfigs(
        SearchResponse abTestResponse,
        ABTestSearchRequest request,
        ActionListener<ABTestSearchResponse> listener
    ) {
        try {
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
                listener.onFailure(new SearchRelevanceException("AB test document is missing required fields", RestStatus.INTERNAL_SERVER_ERROR));
                return;
            }
            boolean enabled = enabledObj;

            // Fetch both configurations in parallel with dedicated slots to preserve ordering
            AtomicReference<SearchResponse> configARef = new AtomicReference<>();
            AtomicReference<SearchResponse> configBRef = new AtomicReference<>();
            AtomicInteger remaining = new AtomicInteger(enabled ? 2 : 1);
            AtomicBoolean failed = new AtomicBoolean(false);

            Runnable maybeComplete = () -> {
                if (remaining.decrementAndGet() == 0 && !failed.get()) {
                    executeSearchWithConfigs(configARef.get(), configBRef.get(), enabled, testId, configAUuid, configBUuid, request, listener);
                }
            };

            ActionListener<SearchResponse> configAListener = ActionListener.wrap(r -> {
                configARef.set(r);
                maybeComplete.run();
            }, e -> {
                if (failed.compareAndSet(false, true)) {
                    listener.onFailure(new SearchRelevanceException("Failed to fetch search configuration A", e, RestStatus.NOT_FOUND));
                }
            });

            searchConfigurationDao.getSearchConfiguration(configAId, configAListener);

            if (enabled) {
                ActionListener<SearchResponse> configBListener = ActionListener.wrap(r -> {
                    configBRef.set(r);
                    maybeComplete.run();
                }, e -> {
                    if (failed.compareAndSet(false, true)) {
                        listener.onFailure(new SearchRelevanceException("Failed to fetch search configuration B", e, RestStatus.NOT_FOUND));
                    }
                });
                searchConfigurationDao.getSearchConfiguration(configBId, configBListener);
            }
        } catch (Exception e) {
            listener.onFailure(new SearchRelevanceException("ABTestSearch failed", e, RestStatus.INTERNAL_SERVER_ERROR));
        }
    }

    /**
     * Parses both search configurations, builds search requests using SearchRequestBuilder
     * (preserving query structure for pipeline processors), and executes search.
     * If test is disabled, runs only config A. If enabled, runs both in parallel and interleaves.
     */
    private void executeSearchWithConfigs(
        SearchResponse configAResponse,
        SearchResponse configBResponse,
        boolean enabled,
        String testId,
        String configAUuid,
        String configBUuid,
        ABTestSearchRequest request,
        ActionListener<ABTestSearchResponse> listener
    ) {
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

        // If test is disabled, only execute config A
        if (!enabled) {
            SearchRequest searchRequestA = SearchRequestBuilder.buildSearchRequest(targetIndex, queryA, searchText, pipelineA, sizeA);
            executeSingleSearch(searchRequestA, testId, configAUuid, listener);
            return;
        }

        // Parse config B and execute both searches in parallel with TDI
        if (configBResponse.getHits().getHits().length == 0) {
            listener.onFailure(new SearchRelevanceException("Search configuration B not found", RestStatus.NOT_FOUND));
            return;
        }
        Map<String, Object> configBSource = configBResponse.getHits().getHits()[0].getSourceAsMap();
        String targetIndexB = (String) configBSource.get("index");
        if (targetIndexB == null || !targetIndexB.equals(targetIndex)) {
            listener.onFailure(new SearchRelevanceException(
                "Both search configurations must target the same index. Config A targets [" + targetIndex + "], Config B targets [" + targetIndexB + "]",
                RestStatus.BAD_REQUEST
            ));
            return;
        }
        String queryB = (String) configBSource.get("query");
        String pipelineB = (String) configBSource.get("searchPipeline");
        int sizeB = configBSource.containsKey("size") ? ((Number) configBSource.get("size")).intValue() : DEFAULT_SEARCH_SIZE;

        SearchRequest searchRequestA = SearchRequestBuilder.buildSearchRequest(targetIndex, queryA, searchText, pipelineA, sizeA);
        SearchRequest searchRequestB = SearchRequestBuilder.buildSearchRequest(targetIndex, queryB, searchText, pipelineB, sizeB);

        executeParallelSearchesAndInterleave(searchRequestA, searchRequestB, testId, configAUuid, configBUuid, listener);
    }

    /**
     * Executes both search queries in parallel with dedicated listeners to preserve ordering,
     * then applies Team Draft Interleaving to merge the two ranked lists into one.
     */
    private void executeParallelSearchesAndInterleave(
        SearchRequest searchRequestA,
        SearchRequest searchRequestB,
        String testId,
        String configAUuid,
        String configBUuid,
        ActionListener<ABTestSearchResponse> listener
    ) {
        AtomicReference<SearchResponse> responseA = new AtomicReference<>();
        AtomicReference<SearchResponse> responseB = new AtomicReference<>();
        AtomicInteger remaining = new AtomicInteger(2);
        AtomicBoolean failed = new AtomicBoolean(false);

        Runnable maybeInterleave = () -> {
            if (remaining.decrementAndGet() == 0 && !failed.get()) {
                // Apply Team Draft Interleaving to merge both result sets
                List<SearchHit> hitsA = Arrays.asList(responseA.get().getHits().getHits());
                List<SearchHit> hitsB = Arrays.asList(responseB.get().getHits().getHits());
                TeamDraftInterleaver.Result tdiResult = interleaver.interleave(hitsA, hitsB, Math.max(hitsA.size(), hitsB.size()));

                // Map each hit with its team's config UUID for click attribution
                List<Map<String, Object>> responseHits = new ArrayList<>();
                Set<String> teamADocs = tdiResult.getTeamA();
                for (SearchHit hit : tdiResult.getInterleavedHits()) {
                    String uuid = teamADocs.contains(hit.getId()) ? configAUuid : configBUuid;
                    responseHits.add(mapHit(hit, uuid));
                }
                listener.onResponse(new ABTestSearchResponse(testId, true, responseHits));
            }
        };

        // Execute searches with caller's security context (no stashing) to preserve FGAC
        client.search(searchRequestA, ActionListener.wrap(r -> {
            responseA.set(r);
            maybeInterleave.run();
        }, e -> {
            if (failed.compareAndSet(false, true)) {
                listener.onFailure(new SearchRelevanceException("Search execution failed", e, RestStatus.INTERNAL_SERVER_ERROR));
            }
        }));

        client.search(searchRequestB, ActionListener.wrap(r -> {
            responseB.set(r);
            maybeInterleave.run();
        }, e -> {
            if (failed.compareAndSet(false, true)) {
                listener.onFailure(new SearchRelevanceException("Search execution failed", e, RestStatus.INTERNAL_SERVER_ERROR));
            }
        }));
    }

    /**
     * Executes a single search when the AB test is disabled (only config A).
     * Fully async — no thread blocking.
     */
    private void executeSingleSearch(
        SearchRequest searchRequest,
        String testId,
        String configUuid,
        ActionListener<ABTestSearchResponse> listener
    ) {
        // Execute search with caller's security context (no stashing) to preserve FGAC
        client.search(searchRequest, ActionListener.wrap(
            searchResponse -> {
                List<Map<String, Object>> responseHits = new ArrayList<>();
                for (SearchHit hit : searchResponse.getHits().getHits()) {
                    responseHits.add(mapHit(hit, configUuid));
                }
                listener.onResponse(new ABTestSearchResponse(testId, false, responseHits));
            },
            e -> listener.onFailure(new SearchRelevanceException("Search execution failed", e, RestStatus.INTERNAL_SERVER_ERROR))
        ));
    }

    /**
     * Maps a SearchHit to a response map with config UUID for click attribution.
     */
    private Map<String, Object> mapHit(SearchHit hit, String configUuid) {
        Map<String, Object> hitMap = new HashMap<>();
        hitMap.put("_index", hit.getIndex());
        hitMap.put("_id", hit.getId());
        hitMap.put("_score", hit.getScore());
        hitMap.put("_source", hit.getSourceAsMap());
        hitMap.put("_search_configuration_id", configUuid);
        return hitMap;
    }

}
