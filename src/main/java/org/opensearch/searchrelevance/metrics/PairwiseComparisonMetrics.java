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
     * @param indexToDocIdMap - indexToDocIdMap that has the key "0" and "1" for comparison
     * e.g:
     * // indexToDocIdMap
     * {
     *     "0": ["3"],
     *     "1": ["1","2","3"],
     * }
     * // pairwiseMetrics
     * {
     *         "jaccard":0.33,
     *         "rbo90":0.1,
     *         "frequencyWeighted":0.67,
     *         "rbo50":0.05
     *     }
     * }
     */
    public static Map<String, Double> calculatePairwiseMetrics(Map<String, Object> indexToDocIdMap) {
        Map<String, Double> pairwiseMetrics = new HashMap<>();
        List<String> docIdListA = (List<String>) indexToDocIdMap.get(PAIRWISE_FIELD_NAME_A);
        List<String> docIdListB = (List<String>) indexToDocIdMap.get(PAIRWISE_FIELD_NAME_B);

        pairwiseMetrics.put(JACCARD_SIMILARITY_FIELD_NAME, calculateJaccardSimilarity(docIdListA, docIdListB));
        pairwiseMetrics.put(RBO_50_SIMILARITY_FIELD_NAME, calculateRBOSimilarity(docIdListA, docIdListB, 0.5));
        pairwiseMetrics.put(RBO_90_SIMILARITY_FIELD_NAME, calculateRBOSimilarity(docIdListA, docIdListB, 0.9));
        pairwiseMetrics.put(FREQUENCY_WEIGHTED_SIMILARITY_FIELD_NAME, calculateFrequencyWeightedSimilarity(docIdListA, docIdListB));

        return pairwiseMetrics;
    }
}
