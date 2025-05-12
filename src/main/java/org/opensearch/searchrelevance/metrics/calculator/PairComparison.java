/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */
package org.opensearch.searchrelevance.metrics.calculator;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.opensearch.core.rest.RestStatus;
import org.opensearch.searchrelevance.exception.SearchRelevanceException;

/**
 * Calculators used for pairwise comparison.
 */
public class PairComparison {

    public static final String JACCARD_SIMILARITY_FIELD_NAME = "jaccard";
    public static final String RBO_50_SIMILARITY_FIELD_NAME = "rbo50";
    public static final String RBO_90_SIMILARITY_FIELD_NAME = "rbo90";
    public static final String FREQUENCY_WEIGHTED_SIMILARITY_FIELD_NAME = "frequencyWeighted";

    /**
     * Jaccard
     */
    public static double calculateJaccardSimilarity(List<String> listA, List<String> listB) {
        Set<String> setA = new HashSet<>(listA);
        Set<String> setB = new HashSet<>(listB);

        Set<String> intersection = new HashSet<>(setA);
        intersection.retainAll(setB);

        Set<String> union = new HashSet<>(setA);
        union.addAll(setB);

        if (union.isEmpty()) {
            return 0.0;
        }

        double jaccardSimilarity = (double) intersection.size() / union.size();
        return Math.round(jaccardSimilarity * 100.0) / 100.0;
    }

    /**
     * RankBiasedOverlap
     */
    public static double calculateRBOSimilarity(List<String> listA, List<String> listB, double p) {
        if (p <= 0 || p >= 1) {
            throw new SearchRelevanceException("p must be between 0 and 1", RestStatus.INTERNAL_SERVER_ERROR);
        }

        int maxDepth = Math.max(listA.size(), listB.size());
        double sum = 0;
        double weight = 1;
        double sumWeight = 0;

        // Calculate overlap at each depth
        for (int d = 0; d < maxDepth; d++) {
            Set<String> setA = new HashSet<>(listA.subList(0, Math.min(d + 1, listA.size())));
            Set<String> setB = new HashSet<>(listB.subList(0, Math.min(d + 1, listB.size())));

            // Calculate overlap at current depth
            Set<String> intersection = new HashSet<>(setA);
            intersection.retainAll(setB);
            double overlap = intersection.size() / (double) Math.max(setA.size(), setB.size());

            // Add weighted overlap to sum
            sum += weight * overlap;
            sumWeight += weight;
            weight *= p;
        }

        // Calculate final RBO score
        double rboSimilarity = sum * (1 - p) / (1 - Math.pow(p, maxDepth));
        return Math.round(rboSimilarity * 100.0) / 100.0;
    }

    /**
     * Frequency Weighted similarity
     */
    public static double calculateFrequencyWeightedSimilarity(List<String> listA, List<String> listB) {
        Map<String, Double> weights = calculateCombinedWeights(listA, listB);

        // Calculate intersection weight
        double intersectionWeight = 0.0;
        for (String item : new HashSet<>(listA)) {
            if (listB.contains(item)) {
                intersectionWeight += weights.get(item);
            }
        }

        // Calculate union weight
        double unionWeight = weights.values().stream().mapToDouble(Double::doubleValue).sum();

        double frequencyWeightedSimilarity = unionWeight == 0 ? 0 : intersectionWeight / unionWeight;
        return Math.round(frequencyWeightedSimilarity * 100.0) / 100.0;
    }

    private static Map<String, Double> calculateCombinedWeights(List<String> listA, List<String> listB) {
        FrequencyStats statsA = calculateFrequencyWeights(listA);
        FrequencyStats statsB = calculateFrequencyWeights(listB);

        // Combine unique items from both lists
        Map<String, Double> combinedWeights = new HashMap<>();

        // Process all items from both lists
        Set<String> allItems = new HashSet<>();
        allItems.addAll(statsA.weights.keySet());
        allItems.addAll(statsB.weights.keySet());

        for (String item : allItems) {
            double weightA = statsA.weights.getOrDefault(item, 0.0);
            double weightB = statsB.weights.getOrDefault(item, 0.0);
            // Average of weights from both lists
            combinedWeights.put(item, (weightA + weightB) / 2.0);
        }

        return combinedWeights;
    }

    private static FrequencyStats calculateFrequencyWeights(List<String> list) {
        // Count frequencies
        Map<String, Integer> frequencies = new HashMap<>();
        for (String item : list) {
            frequencies.put(item, frequencies.getOrDefault(item, 0) + 1);
        }

        // Calculate total frequency
        double totalFrequency = frequencies.values().stream().mapToInt(Integer::intValue).sum();

        // Calculate weights (normalized frequencies)
        Map<String, Double> weights = new HashMap<>();
        for (Map.Entry<String, Integer> entry : frequencies.entrySet()) {
            weights.put(entry.getKey(), entry.getValue() / totalFrequency);
        }

        return new FrequencyStats(weights, frequencies, totalFrequency);
    }

    private static class FrequencyStats {
        public final Map<String, Double> weights;
        public final Map<String, Integer> frequencies;
        public final double totalFrequency;

        public FrequencyStats(Map<String, Double> weights, Map<String, Integer> frequencies, double totalFrequency) {
            this.weights = weights;
            this.frequencies = frequencies;
            this.totalFrequency = totalFrequency;
        }
    }
}
