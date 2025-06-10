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
import static org.opensearch.searchrelevance.common.MetricsConstants.PAIRWISE_FIELD_NAME_METRIC;
import static org.opensearch.searchrelevance.common.MetricsConstants.PAIRWISE_FIELD_NAME_VALUE;
import static org.opensearch.searchrelevance.metrics.calculator.PairComparison.FREQUENCY_WEIGHTED_SIMILARITY_FIELD_NAME;
import static org.opensearch.searchrelevance.metrics.calculator.PairComparison.JACCARD_SIMILARITY_FIELD_NAME;
import static org.opensearch.searchrelevance.metrics.calculator.PairComparison.RBO_50_SIMILARITY_FIELD_NAME;
import static org.opensearch.searchrelevance.metrics.calculator.PairComparison.RBO_90_SIMILARITY_FIELD_NAME;
import static org.opensearch.searchrelevance.metrics.calculator.PairComparison.calculateFrequencyWeightedSimilarity;
import static org.opensearch.searchrelevance.metrics.calculator.PairComparison.calculateJaccardSimilarity;
import static org.opensearch.searchrelevance.metrics.calculator.PairComparison.calculateRBOSimilarity;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Pairwise Comparison Metrics.
 */
public class PairwiseComparisonMetrics {

    private static void addMetric(List<Map<String, Object>> metrics, String metricName, double value) {
        Map<String, Object> metric = new HashMap<>();
        metric.put(PAIRWISE_FIELD_NAME_METRIC, metricName);
        metric.put(PAIRWISE_FIELD_NAME_VALUE, value);
        metrics.add(metric);
    }

    /**
     * calculate pairwise metrics with pairwise comparison calculators
     * @param indexToDocIdMap - searchConfigId to list of docIds map
     * pairwise metrics example:
     * {
     *     "jaccard":0.33,
     *     "rbo90":0.1,
     *     "frequencyWeighted":0.67,
     *     "rbo50":0.05
     * }
     */
    public static List<Map<String, Object>> calculatePairwiseMetrics(Map<String, List<String>> indexToDocIdMap) {
        List<Map<String, Object>> pairwiseMetrics = new ArrayList<>();
        List<String> docIdListA = indexToDocIdMap.get(PAIRWISE_FIELD_NAME_A);
        List<String> docIdListB = indexToDocIdMap.get(PAIRWISE_FIELD_NAME_B);

        addMetric(pairwiseMetrics, JACCARD_SIMILARITY_FIELD_NAME, calculateJaccardSimilarity(docIdListA, docIdListB));
        addMetric(pairwiseMetrics, RBO_50_SIMILARITY_FIELD_NAME, calculateRBOSimilarity(docIdListA, docIdListB, 0.5));
        addMetric(pairwiseMetrics, RBO_90_SIMILARITY_FIELD_NAME, calculateRBOSimilarity(docIdListA, docIdListB, 0.9));
        addMetric(pairwiseMetrics, FREQUENCY_WEIGHTED_SIMILARITY_FIELD_NAME, calculateFrequencyWeightedSimilarity(docIdListA, docIdListB));

        return pairwiseMetrics;
    }
}
