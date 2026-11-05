/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */
package org.opensearch.searchrelevance.metrics;

import static org.opensearch.searchrelevance.common.MetricsConstants.METRICS_FIELD_NAME;
import static org.opensearch.searchrelevance.common.MetricsConstants.PAIRWISE_FIELD_NAME_METRIC;
import static org.opensearch.searchrelevance.common.MetricsConstants.PAIRWISE_FIELD_NAME_VALUE;
import static org.opensearch.searchrelevance.metrics.calculator.Evaluation.METRICS_DISCOUNTED_CUMULATIVE_GAIN_AT;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Aggregates per-query metrics into experiment-level metrics.
 * DCG is summed; other metrics are averaged. Values are rounded to two decimals.
 */
public final class ExperimentLevelMetrics {

    private static final double MAX_ROUNDABLE = Long.MAX_VALUE / 100.0;

    private ExperimentLevelMetrics() {}

    public static List<Map<String, Object>> aggregate(List<List<Map<String, Object>>> perQueryMetrics) {
        if (perQueryMetrics == null || perQueryMetrics.isEmpty()) {
            return List.of();
        }

        Map<String, double[]> sumsAndCounts = new LinkedHashMap<>();
        for (List<Map<String, Object>> queryMetrics : perQueryMetrics) {
            if (queryMetrics == null) {
                continue;
            }
            for (Map<String, Object> metricEntry : queryMetrics) {
                if (metricEntry == null) {
                    continue;
                }
                Object nameObj = metricEntry.get(PAIRWISE_FIELD_NAME_METRIC);
                Object valueObj = metricEntry.get(PAIRWISE_FIELD_NAME_VALUE);
                if (!(nameObj instanceof String metricName) || metricName.isBlank() || valueObj == null) {
                    continue;
                }
                double value;
                if (valueObj instanceof Number number) {
                    value = number.doubleValue();
                } else {
                    try {
                        value = Double.parseDouble(String.valueOf(valueObj));
                    } catch (NumberFormatException e) {
                        continue;
                    }
                }
                if (!Double.isFinite(value)) {
                    continue;
                }
                double[] sumAndCount = sumsAndCounts.computeIfAbsent(metricName, ignored -> new double[2]);
                sumAndCount[0] += value;
                sumAndCount[1] += 1.0;
            }
        }

        List<Map<String, Object>> aggregated = new ArrayList<>(sumsAndCounts.size());
        for (Map.Entry<String, double[]> entry : sumsAndCounts.entrySet()) {
            String metricName = entry.getKey();
            double sum = entry.getValue()[0];
            double count = entry.getValue()[1];
            if (count <= 0) {
                continue;
            }
            double aggregatedValue = isSumMetric(metricName) ? sum : (sum / count);
            Map<String, Object> metric = new HashMap<>();
            metric.put(PAIRWISE_FIELD_NAME_METRIC, metricName);
            metric.put(PAIRWISE_FIELD_NAME_VALUE, roundToTwoDecimals(aggregatedValue));
            aggregated.add(metric);
        }
        return aggregated;
    }

    /**
     * Pairwise/pointwise keep metrics at the result top level; hybrid nests them under evaluationResults.
     */
    @SuppressWarnings("unchecked")
    public static List<List<Map<String, Object>>> extractPerQueryMetrics(List<Map<String, Object>> results) {
        List<List<Map<String, Object>>> perQueryMetrics = new ArrayList<>();
        if (results == null) {
            return perQueryMetrics;
        }
        for (Map<String, Object> result : results) {
            if (result == null) {
                continue;
            }
            Object topLevelMetrics = result.get(METRICS_FIELD_NAME);
            if (topLevelMetrics instanceof List<?> topLevelList && !topLevelList.isEmpty() && isMetricList(topLevelList)) {
                perQueryMetrics.add((List<Map<String, Object>>) topLevelList);
                continue;
            }
            Object evaluationResults = result.get("evaluationResults");
            if (evaluationResults instanceof List<?> evaluationList) {
                for (Object evaluationObj : evaluationList) {
                    if (!(evaluationObj instanceof Map<?, ?> evaluationMap)) {
                        continue;
                    }
                    Object nestedMetrics = evaluationMap.get(METRICS_FIELD_NAME);
                    if (nestedMetrics instanceof List<?> nestedList && !nestedList.isEmpty() && isMetricList(nestedList)) {
                        perQueryMetrics.add((List<Map<String, Object>>) nestedList);
                    }
                }
            }
        }
        return perQueryMetrics;
    }

    public static List<Map<String, Object>> fromExperimentResults(List<Map<String, Object>> results) {
        return aggregate(extractPerQueryMetrics(results));
    }

    static boolean isSumMetric(String metricName) {
        String normalized = metricName.toUpperCase(Locale.ROOT);
        return normalized.equals("DCG") || normalized.startsWith(METRICS_DISCOUNTED_CUMULATIVE_GAIN_AT.toUpperCase(Locale.ROOT));
    }

    private static boolean isMetricList(List<?> list) {
        Object first = list.get(0);
        if (!(first instanceof Map<?, ?> map)) {
            return false;
        }
        return map.containsKey(PAIRWISE_FIELD_NAME_METRIC) || map.containsKey(PAIRWISE_FIELD_NAME_VALUE);
    }

    private static double roundToTwoDecimals(double value) {
        if (!Double.isFinite(value) || Math.abs(value) >= MAX_ROUNDABLE) {
            throw new IllegalArgumentException(
                "Metric value [" + value + "] is not a finite number that can be rounded; check per-query metrics"
            );
        }
        return Math.round(value * 100.0) / 100.0;
    }
}
