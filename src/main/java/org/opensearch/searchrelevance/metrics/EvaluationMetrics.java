/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */
package org.opensearch.searchrelevance.metrics;

import static org.opensearch.searchrelevance.common.MetricsConstants.PAIRWISE_FIELD_NAME_METRIC;
import static org.opensearch.searchrelevance.common.MetricsConstants.PAIRWISE_FIELD_NAME_VALUE;
import static org.opensearch.searchrelevance.metrics.calculator.Evaluation.METRICS_DISCOUNTED_CUMULATIVE_GAIN_AT;
import static org.opensearch.searchrelevance.metrics.calculator.Evaluation.METRICS_MEAN_AVERAGE_PRECISION_AT;
import static org.opensearch.searchrelevance.metrics.calculator.Evaluation.METRICS_MEAN_RECIPROCAL_RANK;
import static org.opensearch.searchrelevance.metrics.calculator.Evaluation.METRICS_NORMALIZED_DISCOUNTED_CUMULATIVE_GAIN_AT;
import static org.opensearch.searchrelevance.metrics.calculator.Evaluation.METRICS_PRECISION_AT;
import static org.opensearch.searchrelevance.metrics.calculator.Evaluation.METRICS_RECALL_AT;
import static org.opensearch.searchrelevance.metrics.calculator.Evaluation.calculateDCGAtK;
import static org.opensearch.searchrelevance.metrics.calculator.Evaluation.calculateMAPAtK;
import static org.opensearch.searchrelevance.metrics.calculator.Evaluation.calculateNDCGAtK;
import static org.opensearch.searchrelevance.metrics.calculator.Evaluation.calculatePrecisionAtK;
import static org.opensearch.searchrelevance.metrics.calculator.Evaluation.calculateRecallAtK;
import static org.opensearch.searchrelevance.metrics.calculator.Evaluation.calculateReciprocalRank;
import static org.opensearch.searchrelevance.metrics.calculator.JudgmentThresholdCalculator.computeThreshold;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Evaluation Metrics.
 */
public class EvaluationMetrics {

    private static void addMetric(List<Map<String, Object>> metrics, String metricName, double value) {
        Map<String, Object> metric = new HashMap<>();
        metric.put(PAIRWISE_FIELD_NAME_METRIC, metricName);
        metric.put(PAIRWISE_FIELD_NAME_VALUE, value);
        metrics.add(metric);
    }

    /**
     * Calculate evaluation metrics with evaluation calculators.
     *
     * <p>
     * A dynamic binary-relevance threshold is computed from the judgment
     * distribution
     * and applied to Precision@K and MAP@K. NDCG uses graded relevance and is not
     * affected.
     */
    public static List<Map<String, Object>> calculateEvaluationMetrics(List<String> docIds, Map<String, String> judgments, int k) {
        List<Map<String, Object>> metrics = new ArrayList<>();
        List<String> docsWithScores = docIds.stream().filter(judgments::containsKey).toList();

        // calculate coverage statistics
        int totalCount = docIds.size();
        int totalDocsWithScores = docsWithScores.size();

        double coverage = totalCount > 0 ? Math.round((double) totalDocsWithScores / totalCount * 100.0) / 100.0 : 0.0;

        // Compute dynamic threshold: T = max(0.5 * J_max, P90)
        double threshold = computeThreshold(judgments);

        // TODO: it's not guarantee that each docId will have its score, especially for
        // UBI data.
        // Need to define a reliable rate. say, coverage > 80%, then the results become
        // reliable
        addMetric(metrics, String.format(Locale.ROOT, "Coverage@%d", k), coverage);
        addMetric(metrics, METRICS_PRECISION_AT + k, calculatePrecisionAtK(docIds, judgments, k, threshold));
        addMetric(metrics, METRICS_RECALL_AT + k, calculateRecallAtK(docIds, judgments, k, threshold));
        addMetric(metrics, METRICS_MEAN_AVERAGE_PRECISION_AT + k, calculateMAPAtK(docIds, judgments, k, threshold));
        addMetric(metrics, METRICS_MEAN_RECIPROCAL_RANK, calculateReciprocalRank(docIds, judgments, k, threshold));
        addMetric(metrics, METRICS_DISCOUNTED_CUMULATIVE_GAIN_AT + k, calculateDCGAtK(docIds, judgments, k));
        addMetric(metrics, METRICS_NORMALIZED_DISCOUNTED_CUMULATIVE_GAIN_AT + k, calculateNDCGAtK(docIds, judgments, k));

        return metrics;
    }
}
