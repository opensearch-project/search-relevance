/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */
package org.opensearch.searchrelevance.metrics.calculator;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Computes a dynamic binary-relevance threshold from a judgment score
 * distribution.
 *
 * <p>
 * The threshold is defined as {@code T = max(0.5 * J_max, P90)} where:
 * <ul>
 * <li>{@code J_max} is the maximum judgment value across all ratings</li>
 * <li>{@code P90} is the 90th percentile of all ratings (nearest-rank
 * method)</li>
 * </ul>
 *
 * <p>
 * A document is considered relevant when its judgment {@code j >= T} and
 * {@code j > 0}.
 *
 * <p>
 * Edge cases handled:
 * <ul>
 * <li><b>Empty input</b>: returns 0.0 (falls back to {@code > 0} behavior)</li>
 * <li><b>All-zero ratings</b> ("Low Variety" fallback): returns 0.0</li>
 * <li><b>Constant non-zero value</b>: P90 == J_max, so T = J_max, keeping all
 * docs relevant</li>
 * <li><b>Small datasets</b>: High-Water Mark ({@code 0.5 * J_max}) prevents an
 * overly low threshold</li>
 * </ul>
 */
public final class JudgmentThresholdCalculator {

    private JudgmentThresholdCalculator() {}

    /**
     * Compute the dynamic binary-relevance threshold for a set of judgment scores.
     *
     * @param judgmentScores mapping from document ID to its judgment rating (as a
     *                       String-encoded number)
     * @return the computed threshold; 0.0 when the input is empty or all ratings
     *         are zero
     */
    public static double computeThreshold(Map<String, String> judgmentScores) {
        if (judgmentScores == null || judgmentScores.isEmpty()) {
            return 0.0;
        }

        List<Double> values = new ArrayList<>();
        for (String raw : judgmentScores.values()) {
            try {
                values.add(Double.parseDouble(raw));
            } catch (NumberFormatException e) {
                // skip unparseable entries
            }
        }

        if (values.isEmpty()) {
            return 0.0;
        }

        Collections.sort(values);

        double jMax = values.get(values.size() - 1);

        // "Low Variety" fallback: if maximum judgment is 0 (all ratings are zero)
        if (jMax == 0.0) {
            return 0.0;
        }

        double p90 = percentile(values, 90);
        double highWaterMark = 0.5 * jMax;
        return Math.max(highWaterMark, p90);
    }

    /**
     * Nearest-rank percentile on an already-sorted list.
     *
     * @param sortedValues a sorted (ascending) list of values; must not be empty
     * @param p            the desired percentile, in the range [0, 100]
     * @return the value at the given percentile
     */
    static double percentile(List<Double> sortedValues, int p) {
        if (p <= 0) {
            return sortedValues.get(0);
        }
        if (p >= 100) {
            return sortedValues.get(sortedValues.size() - 1);
        }
        // nearest-rank: index = ceil(p/100 * n) - 1
        int index = (int) Math.ceil(p / 100.0 * sortedValues.size()) - 1;
        return sortedValues.get(Math.min(index, sortedValues.size() - 1));
    }
}
