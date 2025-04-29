/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */
package org.opensearch.searchrelevance.metrics;

import static org.opensearch.searchrelevance.calculator.Evaluation.METRICS_MEAN_AVERAGE_PRECISION;
import static org.opensearch.searchrelevance.calculator.Evaluation.METRICS_NORMALIZED_DISCOUNTED_CUMULATIVE_GAIN;
import static org.opensearch.searchrelevance.calculator.Evaluation.METRICS_PRECISION_AT_10;
import static org.opensearch.searchrelevance.calculator.Evaluation.METRICS_PRECISION_AT_5;
import static org.opensearch.searchrelevance.calculator.Evaluation.calculateMAP;
import static org.opensearch.searchrelevance.calculator.Evaluation.calculateNDCG;
import static org.opensearch.searchrelevance.calculator.Evaluation.calculatePrecisionAtK;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Evaluation Metrics.
 */
public class EvaluationMetrics {

    /**
     * calculate evaluation metrics with evaluation calculators.
     * @param indexToDocIdMap - indexToDocIdMap that has search configuration index to doc id list
     * e.g:
     * // indexToDocIdMap
     * {
     *     "0": ["docId2"],
     *     "1": ["docId2"]
     *     "2": ["docId1","docId2","docId3"]
     * }
     * // judgments
     * {
     *     "docId1": 0.9,
     *     "docId2": 0.85,
     *     "docId3": 0.8
     * }
     * // evaluationMetrics
     * {
     *     "precision": 0.85   // please add more metrics here
     * }
     */
    public static Map<String, Map<String, Double>> calculateEvaluationMetrics(
        Map<String, Object> indexToDocIdMap,
        Map<String, Double> judgments
    ) {
        Map<String, Map<String, Double>> evaluationMetrics = new HashMap<>();
        for (int i = 0; i < indexToDocIdMap.size(); i++) {
            String searchConfigIndex = String.valueOf(i);
            List<String> docIds = (List<String>) indexToDocIdMap.get(searchConfigIndex);
            Map<String, Double> currSearchConfigMetrics = new HashMap<>();
            currSearchConfigMetrics.put(METRICS_PRECISION_AT_5, calculatePrecisionAtK(docIds, judgments, 5));
            currSearchConfigMetrics.put(METRICS_PRECISION_AT_10, calculatePrecisionAtK(docIds, judgments, 10));
            currSearchConfigMetrics.put(METRICS_MEAN_AVERAGE_PRECISION, calculateMAP(docIds, judgments));
            currSearchConfigMetrics.put(METRICS_NORMALIZED_DISCOUNTED_CUMULATIVE_GAIN, calculateNDCG(docIds, judgments));
            evaluationMetrics.put(searchConfigIndex, currSearchConfigMetrics);
        }
        return evaluationMetrics;
    }
}
