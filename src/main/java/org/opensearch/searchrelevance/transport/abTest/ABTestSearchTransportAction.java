/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */
package org.opensearch.searchrelevance.transport.abTest;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
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
import org.opensearch.search.builder.SearchSourceBuilder;
import org.opensearch.searchrelevance.algorithm.TeamDraftInterleaver;
import org.opensearch.searchrelevance.dao.ABTestDao;
import org.opensearch.searchrelevance.dao.SearchConfigurationDao;
import org.opensearch.searchrelevance.exception.SearchRelevanceException;
import org.opensearch.searchrelevance.model.ABTest;
import org.opensearch.searchrelevance.shared.StashedThreadContext;
import org.opensearch.tasks.Task;
import org.opensearch.threadpool.ThreadPool;
import org.opensearch.transport.TransportService;
import org.opensearch.transport.client.Client;

public class ABTestSearchTransportAction extends HandledTransportAction<ABTestSearchRequest, ABTestSearchResponse> {
    private static final Logger LOGGER = LogManager.getLogger(ABTestSearchTransportAction.class);
    private static final int SEARCH_TIMEOUT_SECONDS = 30;
    private static final String SEARCH_TEXT_PLACEHOLDER = "%SearchText%";

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
        ThreadPool threadPool
    ) {
        super(ABTestSearchAction.NAME, transportService, actionFilters, ABTestSearchRequest::new);
        this.abTestDao = abTestDao;
        this.searchConfigurationDao = searchConfigurationDao;
        this.client = client;
        this.threadPool = threadPool;
        this.interleaver = new TeamDraftInterleaver();
    }

    @Override
    protected void doExecute(Task task, ABTestSearchRequest request, ActionListener<ABTestSearchResponse> listener) {
        if (request == null || request.getTestId() == null || request.getParams() == null || request.getParams().isEmpty()) {
            listener.onFailure(new SearchRelevanceException("Request, testId, and params cannot be null", RestStatus.BAD_REQUEST));
            return;
        }
        abTestDao.getABTest(
            request.getTestId(),
            ActionListener.wrap(abTestResponse -> handleABTestResponse(abTestResponse, request, listener), e -> {
                if (e instanceof org.opensearch.ResourceNotFoundException) {
                    listener.onFailure(new SearchRelevanceException("AB test not found", RestStatus.NOT_FOUND));
                } else {
                    listener.onFailure(new SearchRelevanceException("Failed to read AB test", e, RestStatus.INTERNAL_SERVER_ERROR));
                }
            })
        );
    }

    private void handleABTestResponse(
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
            boolean enabled = (Boolean) source.get(ABTest.ENABLED);
            String configAId = (String) source.get(ABTest.SEARCH_CONFIGURATION_A);
            String configBId = (String) source.get(ABTest.SEARCH_CONFIGURATION_B);
            String configAUuid = (String) source.get(ABTest.CONFIG_A_UUID);
            String configBUuid = (String) source.get(ABTest.CONFIG_B_UUID);
            String testId = (String) source.get(ABTest.TEST_ID);

            searchConfigurationDao.getSearchConfiguration(
                configAId,
                ActionListener.wrap(
                    configAResponse -> handleConfigAResponse(
                        configAResponse,
                        configBId,
                        enabled,
                        testId,
                        configAUuid,
                        configBUuid,
                        request,
                        listener
                    ),
                    e -> {
                        if (e instanceof org.opensearch.ResourceNotFoundException) {
                            listener.onFailure(new SearchRelevanceException("Search configuration A not found", RestStatus.NOT_FOUND));
                        } else {
                            listener.onFailure(
                                new SearchRelevanceException("Failed to read config A", e, RestStatus.INTERNAL_SERVER_ERROR)
                            );
                        }
                    }
                )
            );
        } catch (Exception e) {
            listener.onFailure(new SearchRelevanceException("ABTestSearch failed", e, RestStatus.INTERNAL_SERVER_ERROR));
        }
    }

    private void handleConfigAResponse(
        SearchResponse configAResponse,
        String configBId,
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
        String queryA;
        try {
            queryA = substituteParams((String) configASource.get("query"), request.getParams());
        } catch (SearchRelevanceException e) {
            listener.onFailure(e);
            return;
        }
        String pipelineA = (String) configASource.get("searchPipeline");
        String targetIndex = (String) configASource.get("index");

        if (!enabled) {
            threadPool.generic().execute(() -> {
                try {
                    executeSingleSearch(targetIndex, queryA, pipelineA, testId, configAUuid, listener);
                } catch (Exception e) {
                    listener.onFailure(new SearchRelevanceException("ABTestSearch failed", e, RestStatus.INTERNAL_SERVER_ERROR));
                }
            });
            return;
        }

        searchConfigurationDao.getSearchConfiguration(
            configBId,
            ActionListener.wrap(
                configBResponse -> handleConfigBResponse(
                    configBResponse,
                    targetIndex,
                    queryA,
                    pipelineA,
                    testId,
                    configAUuid,
                    configBUuid,
                    request,
                    listener
                ),
                e -> {
                    if (e instanceof org.opensearch.ResourceNotFoundException) {
                        listener.onFailure(new SearchRelevanceException("Search configuration B not found", RestStatus.NOT_FOUND));
                    } else {
                        listener.onFailure(new SearchRelevanceException("Failed to read config B", e, RestStatus.INTERNAL_SERVER_ERROR));
                    }
                }
            )
        );
    }

    private void handleConfigBResponse(
        SearchResponse configBResponse,
        String targetIndex,
        String queryA,
        String pipelineA,
        String testId,
        String configAUuid,
        String configBUuid,
        ABTestSearchRequest request,
        ActionListener<ABTestSearchResponse> listener
    ) {
        if (configBResponse.getHits().getHits().length == 0) {
            listener.onFailure(new SearchRelevanceException("Search configuration B not found", RestStatus.NOT_FOUND));
            return;
        }
        Map<String, Object> configBSource = configBResponse.getHits().getHits()[0].getSourceAsMap();
        String queryB;
        try {
            queryB = substituteParams((String) configBSource.get("query"), request.getParams());
        } catch (SearchRelevanceException e) {
            listener.onFailure(e);
            return;
        }
        String pipelineB = (String) configBSource.get("searchPipeline");

        threadPool.generic().execute(() -> {
            try {
                executeParallelSearches(targetIndex, queryA, pipelineA, queryB, pipelineB, testId, configAUuid, configBUuid, listener);
            } catch (Exception e) {
                listener.onFailure(new SearchRelevanceException("ABTestSearch failed", e, RestStatus.INTERNAL_SERVER_ERROR));
            }
        });
    }

    private void executeParallelSearches(
        String targetIndex,
        String queryA,
        String pipelineA,
        String queryB,
        String pipelineB,
        String testId,
        String configAUuid,
        String configBUuid,
        ActionListener<ABTestSearchResponse> listener
    ) {
        SearchRequest searchRequestA = buildSearchRequest(targetIndex, queryA, pipelineA);
        SearchRequest searchRequestB = buildSearchRequest(targetIndex, queryB, pipelineB);
        CountDownLatch latch = new CountDownLatch(2);
        AtomicReference<SearchResponse> responseA = new AtomicReference<>();
        AtomicReference<SearchResponse> responseB = new AtomicReference<>();
        AtomicReference<Exception> error = new AtomicReference<>();

        StashedThreadContext.run(client, () -> client.search(searchRequestA, new ActionListener<SearchResponse>() {
            @Override
            public void onResponse(SearchResponse r) {
                responseA.set(r);
                latch.countDown();
            }

            @Override
            public void onFailure(Exception e) {
                error.compareAndSet(null, e);
                latch.countDown();
            }
        }));
        StashedThreadContext.run(client, () -> client.search(searchRequestB, new ActionListener<SearchResponse>() {
            @Override
            public void onResponse(SearchResponse r) {
                responseB.set(r);
                latch.countDown();
            }

            @Override
            public void onFailure(Exception e) {
                error.compareAndSet(null, e);
                latch.countDown();
            }
        }));

        try {
            if (!latch.await(SEARCH_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                listener.onFailure(new SearchRelevanceException("Search timeout", RestStatus.GATEWAY_TIMEOUT));
                return;
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            listener.onFailure(new SearchRelevanceException("Search interrupted", e, RestStatus.INTERNAL_SERVER_ERROR));
            return;
        }
        if (error.get() != null) {
            listener.onFailure(new SearchRelevanceException("Search execution failed", error.get(), RestStatus.INTERNAL_SERVER_ERROR));
            return;
        }

        List<SearchHit> hitsA = Arrays.asList(responseA.get().getHits().getHits());
        List<SearchHit> hitsB = Arrays.asList(responseB.get().getHits().getHits());
        TeamDraftInterleaver.Result tdiResult = interleaver.interleave(hitsA, hitsB, Math.max(hitsA.size(), hitsB.size()));

        List<Map<String, Object>> responseHits = new ArrayList<>();
        Set<String> teamADocs = tdiResult.getTeamA();
        for (SearchHit hit : tdiResult.getInterleavedHits()) {
            String uuid = teamADocs.contains(hit.getId()) ? configAUuid : configBUuid;
            responseHits.add(mapHit(hit, uuid));
        }
        listener.onResponse(new ABTestSearchResponse(testId, true, responseHits));
    }

    private void executeSingleSearch(
        String targetIndex,
        String query,
        String pipeline,
        String testId,
        String configUuid,
        ActionListener<ABTestSearchResponse> listener
    ) {
        SearchRequest searchRequest = buildSearchRequest(targetIndex, query, pipeline);
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<SearchResponse> responseRef = new AtomicReference<>();
        AtomicReference<Exception> error = new AtomicReference<>();

        StashedThreadContext.run(client, () -> client.search(searchRequest, new ActionListener<SearchResponse>() {
            @Override
            public void onResponse(SearchResponse r) {
                responseRef.set(r);
                latch.countDown();
            }

            @Override
            public void onFailure(Exception e) {
                error.set(e);
                latch.countDown();
            }
        }));

        try {
            if (!latch.await(SEARCH_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                listener.onFailure(new SearchRelevanceException("Search timeout", RestStatus.GATEWAY_TIMEOUT));
                return;
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            listener.onFailure(new SearchRelevanceException("Search interrupted", e, RestStatus.INTERNAL_SERVER_ERROR));
            return;
        }
        if (error.get() != null) {
            listener.onFailure(new SearchRelevanceException("Search execution failed", error.get(), RestStatus.INTERNAL_SERVER_ERROR));
            return;
        }

        List<Map<String, Object>> responseHits = new ArrayList<>();
        for (SearchHit hit : responseRef.get().getHits().getHits()) {
            responseHits.add(mapHit(hit, configUuid));
        }
        listener.onResponse(new ABTestSearchResponse(testId, false, responseHits));
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

    private SearchRequest buildSearchRequest(String targetIndex, String queryBody, String searchPipeline) {
        SearchRequest searchRequest = new SearchRequest(targetIndex);
        SearchSourceBuilder sourceBuilder = new SearchSourceBuilder();
        try {
            Map<String, Object> queryMap = org.opensearch.common.xcontent.XContentHelper.convertToMap(
                org.opensearch.common.xcontent.XContentType.JSON.xContent(),
                queryBody,
                false
            );
            if (queryMap.containsKey("query")) {
                @SuppressWarnings("unchecked")
                Map<String, Object> querySection = (Map<String, Object>) queryMap.get("query");
                org.opensearch.core.xcontent.XContentBuilder xBuilder = org.opensearch.common.xcontent.XContentFactory.jsonBuilder();
                xBuilder.map(querySection);
                sourceBuilder.query(new org.opensearch.index.query.WrapperQueryBuilder(xBuilder.toString()));
            }
            if (queryMap.containsKey("size")) {
                sourceBuilder.size(((Number) queryMap.get("size")).intValue());
            }
            if (queryMap.containsKey("_source")) {
                @SuppressWarnings("unchecked")
                Map<String, Object> sourceSection = (Map<String, Object>) queryMap.get("_source");
                if (sourceSection.containsKey("excludes")) {
                    @SuppressWarnings("unchecked")
                    List<String> excludes = (List<String>) sourceSection.get("excludes");
                    sourceBuilder.fetchSource(null, excludes.toArray(new String[0]));
                }
            }
        } catch (IOException e) {
            throw new SearchRelevanceException("Failed to parse query body", e, RestStatus.BAD_REQUEST);
        }
        searchRequest.source(sourceBuilder);
        if (searchPipeline != null && !searchPipeline.isEmpty()) {
            searchRequest.pipeline(searchPipeline);
        }
        return searchRequest;
    }

    private String substituteParams(String template, Map<String, String> params) {
        String searchText = params.get("SearchText");
        if (searchText == null || searchText.isEmpty()) {
            throw new SearchRelevanceException("SearchText is required in query_params", RestStatus.BAD_REQUEST);
        }
        return template.replace(SEARCH_TEXT_PLACEHOLDER, searchText);
    }
}
