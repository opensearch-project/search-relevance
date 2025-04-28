/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */
package org.opensearch.searchrelevance.metrics;

import java.util.HashMap;
import java.util.Map;

/**
 * Evaluation Metrics.
 */
public class EvaluationMetrics {

    /**
     * calculate evaluation metrics with evaluation calculators.
     * @param queryTextMetrics - queryTextMetrics that has the information from all search configurations for current queryText
     * e.g:
     * {
     *     "0": ["docId2"],
     *     "1": ["docId2"]
     *     "2": ["docId1","docId2","docId3"],
     *     "judgments": {
     *       "docId1": 0.9,
     *       "docId2": 0.85,
     *       "docId3": 0.8
     *     },
     *     "evaluation": {
     *        "precision": 0.85    // please add more metrics here
     *     }
     * }
     */
    public static Map<String, Double> calculateEvaluationMetrics(Map<String, Object> queryTextMetrics, Map<String, Double> judgments) {
        Map<String, Double> example_metrics = new HashMap<>();
        example_metrics.put("precision", 0.85);
        return example_metrics;
    }
}
