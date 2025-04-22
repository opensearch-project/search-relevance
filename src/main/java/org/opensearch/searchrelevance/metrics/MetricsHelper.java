/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */
package org.opensearch.searchrelevance.metrics;

import static org.opensearch.searchrelevance.calculator.PairComparison.FREQUENCY_WEIGHTED_SIMILARITY_FIELD_NAME;
import static org.opensearch.searchrelevance.calculator.PairComparison.JACCARD_SIMILARITY_FIELD_NAME;
import static org.opensearch.searchrelevance.calculator.PairComparison.RBO_50_SIMILARITY_FIELD_NAME;
import static org.opensearch.searchrelevance.calculator.PairComparison.RBO_90_SIMILARITY_FIELD_NAME;
import static org.opensearch.searchrelevance.calculator.PairComparison.calculateFrequencyWeightedSimilarity;
import static org.opensearch.searchrelevance.calculator.PairComparison.calculateJaccardSimilarity;
import static org.opensearch.searchrelevance.calculator.PairComparison.calculateRBOSimilarity;
import static org.opensearch.searchrelevance.common.PluginConstants.WILDCARD_QUERY_TEXT;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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

    public static final String METRICS_FIELD_NAME = "metrics";
    public static final String METRICS_QUERY_TEXT_FIELD_NAME = "queryTexts";
    public static final String METRICS_INDEX_AND_QUERY_BODY_FIELD_NAME = "indexAndQueryBodies";
    public static final String METRICS_PAIRWISE_COMPARISON_FIELD_NAME = "pairwiseComparison";
    public static final String PAIRWISE_FIELD_NAME_A = "0";
    public static final String PAIRWISE_FIELD_NAME_B = "1";

    @Inject
    public MetricsHelper(@NonNull ClusterService clusterService, @NonNull Client client) {
        this.clusterService = clusterService;
        this.client = client;
    }

    /**
     * Build metrics for all queryTexts with step listener
     */
    public void getMetricsAsync(
        Map<String, Object> results,
        List<String> queryTexts,
        List<List<String>> indexAndQueryBodies,
        int k,
        StepListener<Map<String, Object>> stepListener
    ) {
        Map<String, Object> metrics = new HashMap<>();
        AtomicInteger pendingTexts = new AtomicInteger(queryTexts.size());
        LOGGER.debug("Processing to getMetricsAsync for {} queryTexts", pendingTexts);
        try {
            for (String queryText : queryTexts) {
                getMetricsForQueryText(queryText, indexAndQueryBodies, k, new ActionListener<Map<String, Object>>() {
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
     *     "ranked": {        // key - query index, value - top k doc ids
     *       "0": ["docId01", "docId02", ... ],
     *       "1": ["docId02", "docId01", ...],
     *     }
     *   }
     */
    public Map<String, Object> getMetricsForQueryText(
        final String queryText,
        final List<List<String>> indexAndQueryBodies,
        final int k,
        ActionListener<Map<String, Object>> listener
    ) {
        Map<String, Object> results = new HashMap<>();
        AtomicInteger pendingQueries = new AtomicInteger(indexAndQueryBodies.size());
        Map<String, Object> indexToDocIdMap = new ConcurrentHashMap<>();
        boolean isPairwiseComparison = indexAndQueryBodies.size() == 2;

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
                                    }
                                } catch (Exception e) {
                                    LOGGER.error("Error processing response for query index: " + queryIndex, e);
                                    indexToDocIdMap.put(String.valueOf(queryIndex), Collections.singletonList("Error: " + e.getMessage()));
                                } finally {
                                    if (pendingQueries.decrementAndGet() == 0) {
                                        processResults(indexToDocIdMap, isPairwiseComparison, results, listener);
                                    }
                                }
                            }

                            @Override
                            public void onFailure(Exception e) {
                                LOGGER.error("Search failed for query index: " + queryIndex, e);
                                indexToDocIdMap.put(String.valueOf(queryIndex), Collections.singletonList("Error: " + e.getMessage()));
                                if (pendingQueries.decrementAndGet() == 0) {
                                    processResults(indexToDocIdMap, isPairwiseComparison, results, listener);
                                }
                            }
                        });
                    } catch (Exception e) {
                        LOGGER.error("Failed to execute search for query index: " + queryIndex, e);
                        indexToDocIdMap.put(String.valueOf(queryIndex), Collections.singletonList("Error: " + e.getMessage()));
                        if (pendingQueries.decrementAndGet() == 0) {
                            processResults(indexToDocIdMap, isPairwiseComparison, results, listener);
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

    private void processResults(
        Map<String, Object> indexToDocIdMap,
        boolean isPairwiseComparison,
        Map<String, Object> results,
        ActionListener<Map<String, Object>> listener
    ) {
        results.putAll(indexToDocIdMap);

        if (isPairwiseComparison) {
            try {
                Map<String, Double> pairwiseMetrics = getPairwiseMetrics(indexToDocIdMap);
                results.put(METRICS_PAIRWISE_COMPARISON_FIELD_NAME, pairwiseMetrics);
            } catch (Exception e) {
                LOGGER.error("Error calculating pairwise metrics", e);
                results.put("pairwiseComparison", Collections.singletonMap("error", e.getMessage()));
            }
        }

        listener.onResponse(results);
    }

    /**
     * get the pairwise calculator metrics from queryTextMetrics
     * @param queryTextMetrics - queryTextMetrics that has the key "0" and "1" for comparison
     */
    private Map<String, Double> getPairwiseMetrics(Map<String, Object> queryTextMetrics) {
        Map<String, Double> metrics = new HashMap<>();
        List<String> docIdListA = (List<String>) queryTextMetrics.get(PAIRWISE_FIELD_NAME_A);
        List<String> docIdListB = (List<String>) queryTextMetrics.get(PAIRWISE_FIELD_NAME_B);
        try {
            double jaccardSimilarity = calculateJaccardSimilarity(docIdListA, docIdListB);
            metrics.put(JACCARD_SIMILARITY_FIELD_NAME, jaccardSimilarity);
        } catch (Exception ex) {
            throw new SearchRelevanceException("failed to calculate jaccard", ex, RestStatus.INTERNAL_SERVER_ERROR);
        }

        try {
            double rboSimilarity50 = calculateRBOSimilarity(docIdListA, docIdListB, 0.5);
            double rboSimilarity90 = calculateRBOSimilarity(docIdListA, docIdListB, 0.9);
            metrics.put(RBO_50_SIMILARITY_FIELD_NAME, rboSimilarity50);
            metrics.put(RBO_90_SIMILARITY_FIELD_NAME, rboSimilarity90);
        } catch (Exception ex) {
            throw new SearchRelevanceException("failed to calculate rbo", ex, RestStatus.INTERNAL_SERVER_ERROR);
        }

        try {
            double frequencyWeightedSimilarity = calculateFrequencyWeightedSimilarity(docIdListA, docIdListB);
            metrics.put(FREQUENCY_WEIGHTED_SIMILARITY_FIELD_NAME, frequencyWeightedSimilarity);
        } catch (Exception ex) {
            throw new SearchRelevanceException("failed to calculate frequencyWeighted", ex, RestStatus.INTERNAL_SERVER_ERROR);
        }
        return metrics;
    }
}
