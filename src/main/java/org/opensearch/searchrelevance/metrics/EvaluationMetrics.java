/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */
package org.opensearch.searchrelevance.metrics;

import static org.opensearch.searchrelevance.metrics.calculator.Evaluation.METRICS_MEAN_AVERAGE_PRECISION_AT;
import static org.opensearch.searchrelevance.metrics.calculator.Evaluation.METRICS_NORMALIZED_DISCOUNTED_CUMULATIVE_GAIN_AT;
import static org.opensearch.searchrelevance.metrics.calculator.Evaluation.METRICS_PRECISION_AT;
import static org.opensearch.searchrelevance.metrics.calculator.Evaluation.calculateMAPAtK;
import static org.opensearch.searchrelevance.metrics.calculator.Evaluation.calculateNDCGAtK;
import static org.opensearch.searchrelevance.metrics.calculator.Evaluation.calculatePrecisionAtK;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Evaluation Metrics.
 */
public class EvaluationMetrics {

    /**
     * calculate evaluation metrics with evaluation calculators.
     */
    public static Map<String, String> calculateEvaluationMetrics(List<String> docIds, Map<String, String> judgments, int k) {
        Map<String, String> currSearchConfigMetrics = new HashMap<>();

        List<String> docsWithScores = docIds.stream().filter(judgments::containsKey).toList();

        // calculate coverage statistics
        int totalCount = docIds.size();
        int totalDocsWithScores = docsWithScores.size();

        double coverage = totalCount > 0 ? Math.round((double) totalDocsWithScores / totalCount * 100.0) / 100.0 : 0.0;

        // docIds that don't have a score will be assumed irrelevant with a score of 0
        // Coverage is the metric of what percentage of docIds per query have score
        currSearchConfigMetrics.put(String.format(Locale.ROOT, "Coverage@%d", k), String.valueOf(coverage));
        currSearchConfigMetrics.put(METRICS_PRECISION_AT + k, String.valueOf(calculatePrecisionAtK(docIds, judgments, k)));
        currSearchConfigMetrics.put(METRICS_MEAN_AVERAGE_PRECISION_AT + k, String.valueOf(calculateMAPAtK(docIds, judgments, k)));
        currSearchConfigMetrics.put(
            METRICS_NORMALIZED_DISCOUNTED_CUMULATIVE_GAIN_AT + k,
            String.valueOf(calculateNDCGAtK(docIds, judgments, k))
        );
        return currSearchConfigMetrics;
    }
}
