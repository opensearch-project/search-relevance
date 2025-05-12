/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */
package org.opensearch.searchrelevance.metrics;

import static org.opensearch.searchrelevance.metrics.calculator.PairComparison.FREQUENCY_WEIGHTED_SIMILARITY_FIELD_NAME;
import static org.opensearch.searchrelevance.metrics.calculator.PairComparison.JACCARD_SIMILARITY_FIELD_NAME;
import static org.opensearch.searchrelevance.metrics.calculator.PairComparison.RBO_50_SIMILARITY_FIELD_NAME;
import static org.opensearch.searchrelevance.metrics.calculator.PairComparison.RBO_90_SIMILARITY_FIELD_NAME;
import static org.opensearch.searchrelevance.metrics.calculator.PairComparison.calculateFrequencyWeightedSimilarity;
import static org.opensearch.searchrelevance.metrics.calculator.PairComparison.calculateJaccardSimilarity;
import static org.opensearch.searchrelevance.metrics.calculator.PairComparison.calculateRBOSimilarity;
import static org.opensearch.searchrelevance.common.MetricsConstants.PAIRWISE_FIELD_NAME_A;
import static org.opensearch.searchrelevance.common.MetricsConstants.PAIRWISE_FIELD_NAME_B;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Pairwise Comparison Metrics.
 */
public class PairwiseComparisonMetrics {

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
    public static Map<String, Double> calculatePairwiseMetrics(Map<String, List<String>> indexToDocIdMap) {
        Map<String, Double> pairwiseMetrics = new HashMap<>();
        List<String> docIdListA = indexToDocIdMap.get(PAIRWISE_FIELD_NAME_A);
        List<String> docIdListB = indexToDocIdMap.get(PAIRWISE_FIELD_NAME_B);

        pairwiseMetrics.put(JACCARD_SIMILARITY_FIELD_NAME, calculateJaccardSimilarity(docIdListA, docIdListB));
        pairwiseMetrics.put(RBO_50_SIMILARITY_FIELD_NAME, calculateRBOSimilarity(docIdListA, docIdListB, 0.5));
        pairwiseMetrics.put(RBO_90_SIMILARITY_FIELD_NAME, calculateRBOSimilarity(docIdListA, docIdListB, 0.9));
        pairwiseMetrics.put(FREQUENCY_WEIGHTED_SIMILARITY_FIELD_NAME, calculateFrequencyWeightedSimilarity(docIdListA, docIdListB));

        return pairwiseMetrics;
    }
}
