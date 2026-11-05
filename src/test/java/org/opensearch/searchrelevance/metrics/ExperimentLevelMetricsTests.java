/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */
package org.opensearch.searchrelevance.metrics;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.opensearch.test.OpenSearchTestCase;

public class ExperimentLevelMetricsTests extends OpenSearchTestCase {

    public void testAggregateAveragesNonDcgMetrics() {
        List<Map<String, Object>> aggregated = ExperimentLevelMetrics.aggregate(
            List.of(List.of(metric("jaccard", 0.5), metric("NDCG@10", 0.8)), List.of(metric("jaccard", 0.7), metric("NDCG@10", 1.0)))
        );

        assertEquals(2, aggregated.size());
        assertEquals(0.6, valueOf(aggregated, "jaccard"), 0.0001);
        assertEquals(0.9, valueOf(aggregated, "NDCG@10"), 0.0001);
    }

    public void testAggregateSumsDcgMetrics() {
        List<Map<String, Object>> aggregated = ExperimentLevelMetrics.aggregate(
            List.of(List.of(metric("DCG@10", 1.25), metric("NDCG@10", 0.5)), List.of(metric("DCG@10", 2.75), metric("NDCG@10", 0.7)))
        );

        assertEquals(4.0, valueOf(aggregated, "DCG@10"), 0.0001);
        assertEquals(0.6, valueOf(aggregated, "NDCG@10"), 0.0001);
    }

    public void testAggregateSkipsMissingAndNonFiniteValues() {
        Map<String, Object> bad = new HashMap<>();
        bad.put("metric", "jaccard");
        bad.put("value", Double.NaN);

        List<Map<String, Object>> aggregated = ExperimentLevelMetrics.aggregate(
            List.of(List.of(metric("jaccard", 0.4), bad), List.of(metric("jaccard", 0.6)), List.of())
        );

        assertEquals(1, aggregated.size());
        assertEquals(0.5, valueOf(aggregated, "jaccard"), 0.0001);
    }

    public void testAggregateEmptyInput() {
        assertTrue(ExperimentLevelMetrics.aggregate(null).isEmpty());
        assertTrue(ExperimentLevelMetrics.aggregate(List.of()).isEmpty());
    }

    public void testExtractFromPairwiseAndPointwiseResults() {
        List<Map<String, Object>> results = List.of(
            Map.of("queryText", "q1", "metrics", List.of(metric("jaccard", 0.5))),
            Map.of("queryText", "q2", "metrics", List.of(metric("jaccard", 0.7)))
        );

        List<Map<String, Object>> aggregated = ExperimentLevelMetrics.fromExperimentResults(results);
        assertEquals(0.6, valueOf(aggregated, "jaccard"), 0.0001);
    }

    public void testExtractFromHybridNestedEvaluationResults() {
        Map<String, Object> hybridResult = new HashMap<>();
        hybridResult.put("queryText", "q1");
        hybridResult.put(
            "evaluationResults",
            List.of(
                Map.of("evaluationId", "e1", "experimentVariantId", "v1", "metrics", List.of(metric("DCG@5", 1.0))),
                Map.of("evaluationId", "e2", "experimentVariantId", "v2", "metrics", List.of(metric("DCG@5", 2.0)))
            )
        );

        List<Map<String, Object>> aggregated = ExperimentLevelMetrics.fromExperimentResults(List.of(hybridResult));
        assertEquals(3.0, valueOf(aggregated, "DCG@5"), 0.0001);
    }

    public void testIsSumMetricRecognizesDcgOnly() {
        assertTrue(ExperimentLevelMetrics.isSumMetric("DCG@10"));
        assertTrue(ExperimentLevelMetrics.isSumMetric("dcg@5"));
        assertFalse(ExperimentLevelMetrics.isSumMetric("NDCG@10"));
        assertFalse(ExperimentLevelMetrics.isSumMetric("jaccard"));
    }

    private static Map<String, Object> metric(String name, double value) {
        Map<String, Object> metric = new HashMap<>();
        metric.put("metric", name);
        metric.put("value", value);
        return metric;
    }

    private static double valueOf(List<Map<String, Object>> metrics, String name) {
        for (Map<String, Object> metric : metrics) {
            if (name.equals(metric.get("metric"))) {
                return ((Number) metric.get("value")).doubleValue();
            }
        }
        fail("Missing metric " + name);
        return Double.NaN;
    }
}
