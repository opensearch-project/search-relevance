/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */
package org.opensearch.searchrelevance.metrics;

import static org.opensearch.searchrelevance.common.MetricsConstants.JUDGMENT_IDS;
import static org.opensearch.searchrelevance.common.MetricsConstants.METRICS_EVALUATION_FIELD_NAME;
import static org.opensearch.searchrelevance.common.MetricsConstants.METRICS_FIELD_NAME;
import static org.opensearch.searchrelevance.common.MetricsConstants.METRICS_JUDGMENTS_FIELD_NAME;
import static org.opensearch.searchrelevance.common.MetricsConstants.METRICS_PAIRWISE_COMPARISON_FIELD_NAME;
import static org.opensearch.searchrelevance.common.MetricsConstants.MODEL_ID;
import static org.opensearch.searchrelevance.common.PluginConstants.WILDCARD_QUERY_TEXT;
import static org.opensearch.searchrelevance.metrics.EvaluationMetrics.calculateEvaluationMetrics;
import static org.opensearch.searchrelevance.model.ExperimentType.LLM_EVALUATION;
import static org.opensearch.searchrelevance.model.ExperimentType.PAIRWISE_COMPARISON;
import static org.opensearch.searchrelevance.model.ExperimentType.UBI_EVALUATION;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.opensearch.action.StepListener;
import org.opensearch.action.search.SearchRequest;
import org.opensearch.action.search.SearchResponse;
import org.opensearch.cluster.service.ClusterService;
import org.opensearch.common.inject.Inject;
import org.opensearch.core.action.ActionListener;
import org.opensearch.core.rest.RestStatus;
import org.opensearch.index.query.QueryBuilders;
import org.opensearch.search.SearchHit;
import org.opensearch.search.builder.SearchSourceBuilder;
import org.opensearch.searchrelevance.exception.SearchRelevanceException;
import org.opensearch.searchrelevance.ml.MLAccessor;
import org.opensearch.searchrelevance.model.ExperimentType;
import org.opensearch.searchrelevance.shared.StashedThreadContext;
import org.opensearch.transport.client.Client;

import reactor.util.annotation.NonNull;

/**
 * Manager for other local index operations.
 */
public class MetricsHelper {
    private static final Logger LOGGER = LogManager.getLogger(MetricsHelper.class);

    private final ClusterService clusterService;
    private final Client client;
    private MLAccessor mlAccessor;

    @Inject
    public MetricsHelper(@NonNull ClusterService clusterService, @NonNull Client client, @NonNull MLAccessor mlAccessor) {
        this.clusterService = clusterService;
        this.client = client;
        this.mlAccessor = mlAccessor;
    }

    /**
     * Build metrics for all queryTexts with step listener
     */
    public void getMetricsAsync(
        Map<String, Object> results,
        List<String> queryTexts,
        List<List<String>> indexAndQueryBodies,
        int k,
        ExperimentType type,
        Map<String, Object> metadata,
        StepListener<Map<String, Object>> stepListener
    ) {
        Map<String, Object> metrics = new HashMap<>();
        AtomicInteger pendingTexts = new AtomicInteger(queryTexts.size());
        LOGGER.debug("Processing to getMetricsAsync for {} queryTexts", pendingTexts);
        try {
            for (String queryText : queryTexts) {
                getMetricsForQueryText(queryText, type, metadata, indexAndQueryBodies, k, new ActionListener<Map<String, Object>>() {
                    @Override
                    public void onResponse(Map<String, Object> queryMetrics) {
                        synchronized (metrics) {
                            metrics.put(queryText, queryMetrics);
                        }
                        if (pendingTexts.decrementAndGet() == 0) {
                            results.put(METRICS_FIELD_NAME, metrics);
                            stepListener.onResponse(results);
                        }
                    }

                    @Override
                    public void onFailure(Exception e) {
                        LOGGER.error("Failed to get ranks for query text: " + queryText, e);
                        synchronized (metrics) {
                            metrics.put(queryText, Collections.singletonMap("error", e.getMessage()));
                        }
                        if (pendingTexts.decrementAndGet() == 0) {
                            results.put(METRICS_FIELD_NAME, metrics);
                            stepListener.onResponse(results);
                        }
                    }
                });
            }

        } catch (Exception e) {
            LOGGER.error("Error calculating metrics", e);
            stepListener.onFailure(new SearchRelevanceException("Failed to calculate metrics", e, RestStatus.INTERNAL_SERVER_ERROR));
        }
    }

    /**
     * Get metrics for a single queryText.
     * @param queryText - queryText to be searched
     * @param indexAndQueryBodies - list of index + queryBody with wildcard queryText
     * @param k - number of documents to be returned
     * @return queryTextMetrics
     *   {
     *     "${queryText}": {
     *       "0": ["docId01", "docId02", ... ],  // for searchConfig at index 0
     *       "1": ["docId02", "docId01", ...],   // for searchConfig at index 1
     *       ...
     *       "pairwiseComparison"/"evaluation": {
     *         "precision": 0.9                  // aggregated metrics
     *       },
     *       "judgements": {
     *         "docId01": "${judgment_score}"    // doc level metrics
     *       }
     *     }
     *   }
     */
    public Map<String, Object> getMetricsForQueryText(
        final String queryText,
        final ExperimentType type,
        final Map<String, Object> metadata,
        final List<List<String>> indexAndQueryBodies,
        final int k,
        ActionListener<Map<String, Object>> listener
    ) {
        Map<String, Object> results = new HashMap<>();
        AtomicInteger pendingQueries = new AtomicInteger(indexAndQueryBodies.size());
        Map<String, Object> indexToDocIdMap = new ConcurrentHashMap<>();
        Set<SearchHit> unionHits = new HashSet<>();

        try {
            for (int i = 0; i < indexAndQueryBodies.size(); i++) {
                final List<String> currentIndexAndQueryBody = indexAndQueryBodies.get(i);
                String queryBody = currentIndexAndQueryBody.get(1).replace(WILDCARD_QUERY_TEXT, queryText);
                final int queryIndex = i;

                SearchRequest searchRequest = new SearchRequest(currentIndexAndQueryBody.get(0));
                SearchSourceBuilder sourceBuilder = new SearchSourceBuilder();

                // Set the query and size
                sourceBuilder.query(QueryBuilders.wrapperQuery(queryBody));
                sourceBuilder.size(k);
                searchRequest.source(sourceBuilder);

                StashedThreadContext.run(client, () -> {
                    try {
                        client.search(searchRequest, new ActionListener<SearchResponse>() {
                            @Override
                            public void onResponse(SearchResponse response) {
                                try {
                                    if (response.getHits().getTotalHits().value() == 0) {
                                        LOGGER.warn("No hits found for query index: {}", queryIndex);
                                        indexToDocIdMap.put(String.valueOf(queryIndex), Collections.emptyList());
                                    } else {
                                        List<String> docIds = Arrays.stream(response.getHits().getHits())
                                            .map(SearchHit::getId)
                                            .collect(Collectors.toList());
                                        indexToDocIdMap.put(String.valueOf(queryIndex), docIds);
                                        unionHits.addAll(List.of(response.getHits().getHits()));
                                    }
                                } catch (Exception e) {
                                    LOGGER.error("Error processing response for query index: " + queryIndex, e);
                                    indexToDocIdMap.put(String.valueOf(queryIndex), Collections.singletonList("Error: " + e.getMessage()));
                                } finally {
                                    if (pendingQueries.decrementAndGet() == 0) {
                                        processQueryTextMetrics(indexToDocIdMap, type, metadata, results, queryText, unionHits, listener);
                                    }
                                }
                            }

                            @Override
                            public void onFailure(Exception e) {
                                LOGGER.error("Search failed for query index: " + queryIndex, e);
                                indexToDocIdMap.put(String.valueOf(queryIndex), Collections.singletonList("Error: " + e.getMessage()));
                                if (pendingQueries.decrementAndGet() == 0) {
                                    processQueryTextMetrics(indexToDocIdMap, type, metadata, results, queryText, unionHits, listener);
                                }
                            }
                        });
                    } catch (Exception e) {
                        LOGGER.error("Failed to execute search for query index: " + queryIndex, e);
                        indexToDocIdMap.put(String.valueOf(queryIndex), Collections.singletonList("Error: " + e.getMessage()));
                        if (pendingQueries.decrementAndGet() == 0) {
                            processQueryTextMetrics(indexToDocIdMap, type, metadata, results, queryText, unionHits, listener);
                        }
                    }
                });
            }

        } catch (Exception e) {
            LOGGER.error("Error initiating searches for query text: " + queryText, e);
            listener.onFailure(new SearchRelevanceException("Failed to initiate searches", e, RestStatus.INTERNAL_SERVER_ERROR));
        }
        return results;
    }

    /**
     * process query text level metrics
     */
    private void processQueryTextMetrics(
        Map<String, Object> indexToDocIdMap,
        ExperimentType type,
        Map<String, Object> metadata,
        Map<String, Object> results,
        String queryText,
        Set<SearchHit> unionHits,
        ActionListener<Map<String, Object>> listener
    ) {
        // add indexToDocIdMap under queryText key
        results.putAll(indexToDocIdMap);

        // add metrics based on experiment type
        try {
            switch (type) {
                case PAIRWISE_COMPARISON -> {
                    Map<String, Double> pairwiseMetrics = PairwiseComparisonMetrics.calculatePairwiseMetrics(indexToDocIdMap);
                    results.put(METRICS_PAIRWISE_COMPARISON_FIELD_NAME, pairwiseMetrics);
                    break;
                }
                case LLM_EVALUATION -> {
                    // Add LLM-specific metrics processing
                    String modelId = (String) metadata.get(MODEL_ID);
                    LOGGER.debug("calculating LLM evaluation with modelId: {}", modelId);
                    Map<String, Double> docIdToScore = getLlmJudgments(queryText, modelId, unionHits);
                    results.put(METRICS_JUDGMENTS_FIELD_NAME, docIdToScore);
                    results.put(METRICS_EVALUATION_FIELD_NAME, calculateEvaluationMetrics(indexToDocIdMap, docIdToScore));
                    break;
                }
                case UBI_EVALUATION -> {
                    // Add UBI-specific metrics processing
                    List<String> judgmentIds = (List<String>) metadata.get(JUDGMENT_IDS);
                    LOGGER.debug("calculating UBI evaluation with judgmentIds: {}", judgmentIds);
                    Map<String, Double> docIdToScore = getUbiJudgments(judgmentIds);
                    results.put(METRICS_JUDGMENTS_FIELD_NAME, docIdToScore);
                    results.put(METRICS_EVALUATION_FIELD_NAME, calculateEvaluationMetrics(indexToDocIdMap, docIdToScore));
                    break;
                }
            }

            listener.onResponse(results);
        } catch (Exception ex) {
            listener.onFailure(ex);
        }
    }

    private Map<String, Double> getLlmJudgments(String queryText, String modelId, Set<SearchHit> searchHits) {
        // please add LLM judgment business logics here.
        Map<String, Double> example_judgments = new HashMap<>();
        example_judgments.put("docId01", 0.9);
        example_judgments.put("docId02", 0.85);
        example_judgments.put("docId03", 0.8);
        return example_judgments;
    }

    private Map<String, Double> getUbiJudgments(List<String> judgmentIdList) {
        // please add UBI judgment business logics here.
        Map<String, Double> example_judgments = new HashMap<>();
        example_judgments.put("docId01", 0.9);
        example_judgments.put("docId02", 0.85);
        example_judgments.put("docId03", 0.8);
        return example_judgments;
    }
}
