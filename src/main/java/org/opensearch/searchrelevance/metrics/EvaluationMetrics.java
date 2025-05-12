/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */
package org.opensearch.searchrelevance.metrics;

import static org.opensearch.searchrelevance.metrics.calculator.Evaluation.METRICS_MEAN_AVERAGE_PRECISION;
import static org.opensearch.searchrelevance.metrics.calculator.Evaluation.METRICS_NORMALIZED_DISCOUNTED_CUMULATIVE_GAIN;
import static org.opensearch.searchrelevance.metrics.calculator.Evaluation.METRICS_PRECISION_AT_10;
import static org.opensearch.searchrelevance.metrics.calculator.Evaluation.METRICS_PRECISION_AT_5;
import static org.opensearch.searchrelevance.metrics.calculator.Evaluation.calculateMAP;
import static org.opensearch.searchrelevance.metrics.calculator.Evaluation.calculateNDCG;
import static org.opensearch.searchrelevance.metrics.calculator.Evaluation.calculatePrecisionAtK;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Evaluation Metrics.
 */
public class EvaluationMetrics {

    /**
     * calculate evaluation metrics with evaluation calculators.
     */
    public static Map<String, String> calculateEvaluationMetrics(List<String> docIds, Map<String, String> judgments) {
        Map<String, String> currSearchConfigMetrics = new HashMap<>();

        List<String> docsWithScores = docIds.stream().filter(judgments::containsKey).toList();

        // calculate coverage statistics
        int totalCount = docIds.size();
        int totalDocsWithScores = docsWithScores.size();

        double coverage = totalCount > 0 ? Math.round((double) totalDocsWithScores / totalCount * 100.0) / 100.0 : 0.0;

        // TODO: it's not guarantee that each docId will have its score, especially for UBI data.
        // Need to define a reliable rate. say, coverage > 80%, then the results become reliable
        currSearchConfigMetrics.put("coverage", String.valueOf(coverage));

        currSearchConfigMetrics.put(METRICS_PRECISION_AT_5, String.valueOf(calculatePrecisionAtK(docIds, judgments, 5)));
        currSearchConfigMetrics.put(METRICS_PRECISION_AT_10, String.valueOf(calculatePrecisionAtK(docIds, judgments, 10)));
        currSearchConfigMetrics.put(METRICS_MEAN_AVERAGE_PRECISION, String.valueOf(calculateMAP(docIds, judgments)));
        currSearchConfigMetrics.put(METRICS_NORMALIZED_DISCOUNTED_CUMULATIVE_GAIN, String.valueOf(calculateNDCG(docIds, judgments)));
        return currSearchConfigMetrics;
    }
}
