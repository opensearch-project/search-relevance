/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */
package org.opensearch.searchrelevance.metrics;

import static org.opensearch.searchrelevance.common.MetricsConstants.PAIRWISE_FIELD_NAME_A;
import static org.opensearch.searchrelevance.common.MetricsConstants.PAIRWISE_FIELD_NAME_B;
import static org.opensearch.searchrelevance.common.PluginConstants.WILDCARD_QUERY_TEXT;
import static org.opensearch.searchrelevance.metrics.EvaluationMetrics.calculateEvaluationMetrics;
import static org.opensearch.searchrelevance.metrics.PairwiseComparisonMetrics.calculatePairwiseMetrics;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.opensearch.action.search.SearchRequest;
import org.opensearch.action.search.SearchResponse;
import org.opensearch.cluster.service.ClusterService;
import org.opensearch.common.inject.Inject;
import org.opensearch.core.action.ActionListener;
import org.opensearch.index.query.QueryBuilders;
import org.opensearch.search.SearchHit;
import org.opensearch.search.builder.SearchSourceBuilder;
import org.opensearch.searchrelevance.dao.EvaluationResultDao;
import org.opensearch.searchrelevance.dao.JudgmentDao;
import org.opensearch.searchrelevance.model.EvaluationResult;
import org.opensearch.searchrelevance.utils.TimeUtils;
import org.opensearch.transport.client.Client;

import reactor.util.annotation.NonNull;

/**
 * Manager for other local index operations.
 */
public class MetricsHelper {
    private static final Logger LOGGER = LogManager.getLogger(MetricsHelper.class);
    private final ClusterService clusterService;
    private final Client client;
    private final JudgmentDao judgmentDao;
    private final EvaluationResultDao evaluationResultDao;

    @Inject
    public MetricsHelper(
        @NonNull ClusterService clusterService,
        @NonNull Client client,
        @NonNull JudgmentDao judgmentDao,
        @NonNull EvaluationResultDao evaluationResultDao
    ) {
        this.clusterService = clusterService;
        this.client = client;
        this.judgmentDao = judgmentDao;
        this.evaluationResultDao = evaluationResultDao;
    }

    /**
     * Create a pairwise comparison metrics in experiment results
     * Pairwise comparison will not read any judgment but directly comparing two docIds
     * Pairwise comparison will not create evaluation results
     */
    public void processPairwiseMetrics(
        String queryText,
        Map<String, List<String>> indexAndQueryBodies,
        int size,
        ActionListener<Map<String, Object>> listener
    ) {
        Map<String, List<String>> searchConfigToDocIds = Collections.synchronizedMap(new HashMap<>());
        AtomicBoolean hasFailure = new AtomicBoolean(false);
        AtomicInteger pendingSearches = new AtomicInteger(indexAndQueryBodies.size());

        for (Map.Entry<String, List<String>> entry : indexAndQueryBodies.entrySet()) {
            String searchConfigId = entry.getKey();
            String index = entry.getValue().get(0);
            String queryPattern = entry.getValue().get(1);

            SearchRequest searchRequest = createSearchRequest(index, queryPattern.replace(WILDCARD_QUERY_TEXT, queryText), size);

            client.search(searchRequest, new ActionListener<SearchResponse>() {
                @Override
                public void onResponse(SearchResponse response) {
                    if (hasFailure.get()) return;

                    try {
                        List<String> docIds = Arrays.stream(response.getHits().getHits())
                            .map(SearchHit::getId)
                            .collect(Collectors.toList());

                        searchConfigToDocIds.put(searchConfigId, docIds);
                        if (pendingSearches.decrementAndGet() == 0) {
                            createPairwiseResults(searchConfigToDocIds, listener);
                        }
                    } catch (Exception e) {
                        handleFailure(e, hasFailure, listener);
                    }
                }

                @Override
                public void onFailure(Exception e) {
                    handleFailure(e, hasFailure, listener);
                }
            });
        }
    }

    private void createPairwiseResults(Map<String, List<String>> searchConfigToDocIds, ActionListener<Map<String, Object>> listener) {
        try {
            Map<String, Object> results = new HashMap<>();

            if (searchConfigToDocIds == null || searchConfigToDocIds.isEmpty()) {
                results.put("pairwiseComparison", Collections.emptyMap());
                listener.onResponse(results);
                return;
            }
            // Add doc IDs for each search configuration
            searchConfigToDocIds.forEach((configId, docIds) -> results.put(configId, docIds != null ? docIds : Collections.emptyList()));

            // Prepare input for pairwise calculation
            Map<String, List<String>> pairwiseInput = new HashMap<>();
            List<String> configIds = new ArrayList<>(searchConfigToDocIds.keySet());

            if (configIds.size() >= 2) {
                pairwiseInput.put(PAIRWISE_FIELD_NAME_A, searchConfigToDocIds.get(configIds.get(0)));
                pairwiseInput.put(PAIRWISE_FIELD_NAME_B, searchConfigToDocIds.get(configIds.get(1)));
            }

            // Calculate and add pairwise metrics
            results.put("pairwiseComparison", calculatePairwiseMetrics(pairwiseInput));

            listener.onResponse(results);
        } catch (Exception e) {
            listener.onFailure(e);
        }
    }

    private SearchRequest createSearchRequest(String index, String queryBody, int size) {
        SearchRequest request = new SearchRequest(index);
        SearchSourceBuilder sourceBuilder = new SearchSourceBuilder().query(QueryBuilders.wrapperQuery(queryBody)).size(size);
        return request.source(sourceBuilder);
    }

    private void handleFailure(Exception error, AtomicBoolean hasFailure, ActionListener<?> listener) {
        if (hasFailure.compareAndSet(false, true)) {
            listener.onFailure(error);
        }
    }

    /**
     * Create evaluation results for provided queryText
     * @param queryText - queryText to be evaluated against
     * @param indexAndQueryBodies - "${searchConfigId}" to ["$index", "$queryPattern"] map
     * And will add evaluationId back to experiment results
     *  "results" {
     *     "${queryText}": {
     *         "${searchConfigId}": "${evaluationId}"
     *     }
     *  }
     */
    public void processEvaluationMetrics(
        String queryText,
        Map<String, List<String>> indexAndQueryBodies,
        int size,
        List<String> judgmentIds,
        ActionListener<Map<String, String>> listener
    ) {
        if (indexAndQueryBodies.isEmpty() || judgmentIds.isEmpty()) {
            listener.onFailure(new IllegalArgumentException("Missing required parameters"));
            return;
        }

        try {
            Map<String, String> configToEvalIds = new HashMap<>();

            // TODO: we support single judgmentId for a experiment for now.
            String judgmentId = judgmentIds.get(0);
            judgmentDao.getJudgment(judgmentIds.get(0), new ActionListener<SearchResponse>() {
                @Override
                public void onResponse(SearchResponse judgmentResponse) {
                    try {
                        if (judgmentResponse.getHits().getTotalHits().value() == 0) {
                            listener.onFailure(new IllegalStateException("No judgment found for ID: " + judgmentId));
                            return;
                        }

                        Map<String, Object> sourceAsMap = judgmentResponse.getHits().getHits()[0].getSourceAsMap();

                        Map<String, Object> judgmentScores = (Map<String, Object>) sourceAsMap.get("judgmentScores");

                        if (judgmentScores == null) {
                            listener.onFailure(new IllegalStateException("No judgment scores found for ID: " + judgmentId));
                            return;
                        }

                        Map<String, String> docIdToScores = (Map<String, String>) judgmentScores.get(queryText);

                        if (docIdToScores == null || docIdToScores.isEmpty()) {
                            LOGGER.warn("No scores found for query: {} in judgment: {}", queryText, judgmentId);
                            docIdToScores = new HashMap<>();
                        }
                        processSearchConfigurations(
                            queryText,
                            indexAndQueryBodies,
                            size,
                            judgmentIds,
                            docIdToScores,
                            configToEvalIds,
                            listener
                        );
                    } catch (Exception e) {
                        listener.onFailure(e);
                    }
                }

                @Override
                public void onFailure(Exception e) {
                    LOGGER.error("Failed to fetch judgment {}: {}", judgmentId, e);
                    listener.onFailure(e);
                }
            });
        } catch (Exception e) {
            LOGGER.error("Unexpected error in evaluateQueryTextAsync: {}", e.getMessage());
            listener.onFailure(e);
        }
    }

    private void processSearchConfigurations(
        String queryText,
        Map<String, List<String>> indexAndQueryBodies,
        int size,
        List<String> judgmentIds,
        Map<String, String> docIdToScores,
        Map<String, String> configToEvalIds,
        ActionListener<Map<String, String>> listener
    ) {
        AtomicInteger pendingConfigurations = new AtomicInteger(indexAndQueryBodies.size());
        AtomicBoolean hasFailure = new AtomicBoolean(false);

        if (indexAndQueryBodies.isEmpty()) {
            listener.onResponse(configToEvalIds);
            return;
        }

        for (String searchConfigurationId : indexAndQueryBodies.keySet()) {
            if (hasFailure.get()) {
                return;
            }

            final String evaluationId = UUID.randomUUID().toString();
            String index = indexAndQueryBodies.get(searchConfigurationId).get(0);
            String queryPattern = indexAndQueryBodies.get(searchConfigurationId).get(1);
            String searchPipeline = indexAndQueryBodies.get(searchConfigurationId).get(2);
            LOGGER.debug(
                "Configuration {}: index: {}, query pattern: {}, searchPipeline: {}, evaluationId: {}",
                searchConfigurationId,
                index,
                queryPattern,
                searchPipeline,
                evaluationId
            );

            String queryBody = queryPattern.replace(WILDCARD_QUERY_TEXT, queryText);
            SearchRequest searchRequest = new SearchRequest(index);
            SearchSourceBuilder sourceBuilder = new SearchSourceBuilder();
            if (searchPipeline != null && !searchPipeline.isEmpty()) {
                searchRequest.pipeline(searchPipeline);
            }

            sourceBuilder.query(QueryBuilders.wrapperQuery(queryBody));
            sourceBuilder.size(size);
            searchRequest.source(sourceBuilder);

            client.search(searchRequest, new ActionListener<SearchResponse>() {
                @Override
                public void onResponse(SearchResponse response) {
                    if (hasFailure.get()) {
                        return;
                    }

                    try {
                        if (response.getHits().getTotalHits().value() == 0) {
                            LOGGER.warn("No hits found for search config: {}", searchConfigurationId);
                            if (pendingConfigurations.decrementAndGet() == 0) {
                                listener.onResponse(configToEvalIds);
                            }
                            return;
                        }

                        SearchHit[] hits = response.getHits().getHits();
                        List<String> docIds = Arrays.stream(hits).map(SearchHit::getId).collect(Collectors.toList());

                        Map<String, String> metrics = calculateEvaluationMetrics(docIds, docIdToScores);
                        EvaluationResult evaluationResult = new EvaluationResult(
                            evaluationId,
                            TimeUtils.getTimestamp(),
                            searchConfigurationId,
                            queryText,
                            judgmentIds,
                            docIds,
                            metrics
                        );

                        evaluationResultDao.putEvaluationResult(evaluationResult, ActionListener.wrap(success -> {
                            configToEvalIds.put(searchConfigurationId, evaluationId);
                            if (pendingConfigurations.decrementAndGet() == 0) {
                                listener.onResponse(configToEvalIds);
                            }
                        }, error -> {
                            LOGGER.error("Failed to save evaluation result for config {}: {}", searchConfigurationId, error);
                            hasFailure.set(true);
                            listener.onFailure(error);
                        }));
                    } catch (Exception e) {
                        LOGGER.error("Error processing search response for config {}: {}", searchConfigurationId, e);
                        hasFailure.set(true);
                        listener.onFailure(e);
                    }
                }

                @Override
                public void onFailure(Exception e) {
                    LOGGER.error("Search failed for configuration {}: {}", searchConfigurationId, e.getMessage());
                    hasFailure.set(true);
                    listener.onFailure(e);
                }
            });
        }
    }
}
