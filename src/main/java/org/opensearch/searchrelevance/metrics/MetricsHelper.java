/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */
package org.opensearch.searchrelevance.metrics;

import static org.opensearch.searchrelevance.common.MetricsConstants.METRICS_PAIRWISE_COMPARISON_FIELD_NAME;
import static org.opensearch.searchrelevance.common.MetricsConstants.PAIRWISE_FIELD_NAME_A;
import static org.opensearch.searchrelevance.common.MetricsConstants.PAIRWISE_FIELD_NAME_B;
import static org.opensearch.searchrelevance.metrics.EvaluationMetrics.calculateEvaluationMetrics;
import static org.opensearch.searchrelevance.metrics.PairwiseComparisonMetrics.calculatePairwiseMetrics;
import static org.opensearch.searchrelevance.model.builder.SearchRequestBuilder.buildSearchRequest;

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
import org.opensearch.search.SearchHit;
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
        Map<String, List<String>> indexAndQueries,
        int size,
        ActionListener<Map<String, Object>> listener
    ) {
        Map<String, List<String>> searchConfigToDocIds = Collections.synchronizedMap(new HashMap<>());
        AtomicBoolean hasFailure = new AtomicBoolean(false);
        AtomicInteger pendingSearches = new AtomicInteger(indexAndQueries.size());

        for (Map.Entry<String, List<String>> entry : indexAndQueries.entrySet()) {
            String searchConfigId = entry.getKey();
            String index = entry.getValue().get(0);
            String query = entry.getValue().get(1);

            SearchRequest searchRequest = buildSearchRequest(index, query, queryText, null, size);

            client.search(searchRequest, new ActionListener<SearchResponse>() {
                @Override
                public void onResponse(SearchResponse response) {
                    if (hasFailure.get()) return;

                    try {
                        List<String> docIds = Arrays.stream(response.getHits().getHits())
                            .map(SearchHit::getId)
                            .distinct()
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
                results.put(METRICS_PAIRWISE_COMPARISON_FIELD_NAME, Collections.emptyMap());
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
            results.put(METRICS_PAIRWISE_COMPARISON_FIELD_NAME, calculatePairwiseMetrics(pairwiseInput));

            listener.onResponse(results);
        } catch (Exception e) {
            listener.onFailure(e);
        }
    }

    private void handleFailure(Exception error, AtomicBoolean hasFailure, ActionListener<?> listener) {
        if (hasFailure.compareAndSet(false, true)) {
            listener.onFailure(error);
        }
    }

    /**
     * Create evaluation results for provided queryText
     * @param queryText - queryText to be evaluated against
     * @param indexAndQueries - "${searchConfigId}" to ["$index", "$queryPattern"] map
     * And will add evaluationId back to experiment results
     *  "results" {
     *     "${queryText}": {
     *         "${searchConfigId}": "${evaluationId}"
     *     }
     *  }
     */
    public void processEvaluationMetrics(
        String queryText,
        Map<String, List<String>> indexAndQueries,
        int size,
        List<String> judgmentIds,
        ActionListener<Map<String, String>> listener
    ) {
        if (indexAndQueries.isEmpty() || judgmentIds.isEmpty()) {
            listener.onFailure(new IllegalArgumentException("Missing required parameters"));
            return;
        }

        try {
            Map<String, String> configToEvalIds = new HashMap<>();
            Map<String, String> docIdToScores = new HashMap<>();
            AtomicInteger completedJudgments = new AtomicInteger(0);

            for (String judgmentId : judgmentIds) {
                judgmentDao.getJudgment(judgmentId, new ActionListener<SearchResponse>() {
                    @Override
                    public void onResponse(SearchResponse judgmentResponse) {
                        try {
                            if (judgmentResponse.getHits().getTotalHits().value() == 0) {
                                LOGGER.warn("No judgment found for ID: {}", judgmentId);
                            } else {
                                Map<String, Object> sourceAsMap = judgmentResponse.getHits().getHits()[0].getSourceAsMap();
                                Map<String, Object> judgmentScores = (Map<String, Object>) sourceAsMap.getOrDefault(
                                    "judgmentScores",
                                    Collections.emptyMap()
                                );
                                List<Map<String, Object>> queryScores = (List<Map<String, Object>>) judgmentScores.getOrDefault(
                                    queryText,
                                    Collections.emptyList()
                                );

                                queryScores.forEach(
                                    docScore -> docIdToScores.put((String) docScore.get("docId"), (String) docScore.get("score"))
                                );
                            }

                            // Check if all judgments have been processed
                            if (completedJudgments.incrementAndGet() == judgmentIds.size()) {
                                if (docIdToScores.isEmpty()) {
                                    LOGGER.warn("No scores found for query: {} in any judgments", queryText);
                                }

                                processSearchConfigurations(
                                    queryText,
                                    indexAndQueries,
                                    size,
                                    judgmentIds,
                                    docIdToScores,
                                    configToEvalIds,
                                    listener
                                );
                            }
                        } catch (Exception e) {
                            listener.onFailure(e);
                        }
                    }

                    @Override
                    public void onFailure(Exception e) {
                        LOGGER.error("Failed to fetch judgment {}: {}", judgmentId, e);
                        if (completedJudgments.incrementAndGet() == judgmentIds.size()) {
                            if (docIdToScores.isEmpty()) {
                                listener.onFailure(new IllegalStateException("Failed to fetch any valid judgments"));
                            } else {
                                // Proceed with the judgments we were able to fetch
                                processSearchConfigurations(
                                    queryText,
                                    indexAndQueries,
                                    size,
                                    judgmentIds,
                                    docIdToScores,
                                    configToEvalIds,
                                    listener
                                );
                            }
                        }
                    }
                });
            }
        } catch (Exception e) {
            LOGGER.error("Unexpected error in evaluateQueryTextAsync: {}", e.getMessage());
            listener.onFailure(e);
        }
    }

    private void processSearchConfigurations(
        String queryText,
        Map<String, List<String>> indexAndQueries,
        int size,
        List<String> judgmentIds,
        Map<String, String> docIdToScores,
        Map<String, String> configToEvalIds,
        ActionListener<Map<String, String>> listener
    ) {
        AtomicInteger pendingConfigurations = new AtomicInteger(indexAndQueries.size());
        AtomicBoolean hasFailure = new AtomicBoolean(false);

        if (indexAndQueries.isEmpty()) {
            listener.onResponse(configToEvalIds);
            return;
        }

        for (String searchConfigurationId : indexAndQueries.keySet()) {
            if (hasFailure.get()) {
                return;
            }

            final String evaluationId = UUID.randomUUID().toString();
            String index = indexAndQueries.get(searchConfigurationId).get(0);
            String query = indexAndQueries.get(searchConfigurationId).get(1);
            String searchPipeline = indexAndQueries.get(searchConfigurationId).get(2);
            LOGGER.debug(
                "Configuration {}: index: {}, query: {}, searchPipeline: {}, evaluationId: {}",
                searchConfigurationId,
                index,
                query,
                searchPipeline,
                evaluationId
            );

            SearchRequest searchRequest = buildSearchRequest(index, query, queryText, searchPipeline, size);
            client.search(searchRequest, new ActionListener<SearchResponse>() {
                @Override
                public void onResponse(SearchResponse response) {
                    if (hasFailure.get()) return;

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
                            hasFailure.set(true);
                            listener.onFailure(error);
                        }));
                    } catch (Exception e) {
                        hasFailure.set(true);
                        listener.onFailure(e);
                    }
                }

                @Override
                public void onFailure(Exception e) {
                    hasFailure.set(true);
                    listener.onFailure(e);
                }
            });
        }
    }
}
