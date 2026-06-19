/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */
package org.opensearch.searchrelevance.metrics.calculator;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

import org.opensearch.test.OpenSearchTestCase;

/**
 * Tests for {@link PairComparison} metric calculators.
 */
public class PairComparisonTests extends OpenSearchTestCase {

    public void testIdenticalListsFrequencyWeightedSimilarityIsOne() {
        List<String> list = Arrays.asList("a", "b", "c");
        assertEquals(1.0, PairComparison.calculateFrequencyWeightedSimilarity(list, list), 0.001);
    }

    public void testDisjointListsFrequencyWeightedSimilarityIsZero() {
        List<String> listA = Arrays.asList("a", "b", "c");
        List<String> listB = Arrays.asList("d", "e", "f");
        assertEquals(0.0, PairComparison.calculateFrequencyWeightedSimilarity(listA, listB), 0.001);
    }

    public void testFrequencyWeightedPartialOverlap() {
        List<String> listA = Arrays.asList("a", "a", "b");
        List<String> listB = Arrays.asList("a", "c", "c");
        // freqA: a=2/3, b=1/3
        // freqB: a=1/3, c=2/3
        // combined weights: a=0.5, b=1/6, c=1/3
        // intersection = {a} -> 0.5
        // union = {a,b,c} -> 0.5 + 1/6 + 1/3 = 1.0
        // similarity = 0.5
        assertEquals(0.5, PairComparison.calculateFrequencyWeightedSimilarity(listA, listB), 0.001);
    }

    public void testFrequencyWeightedWithDuplicates() {
        List<String> listA = Arrays.asList("a", "a", "b");
        List<String> listB = Arrays.asList("a", "b", "b");
        // Both lists contain the same unique items {a,b} with symmetric frequencies,
        // so the combined weights for a and b sum to 1 and intersection equals union.
        assertEquals(1.0, PairComparison.calculateFrequencyWeightedSimilarity(listA, listB), 0.001);
    }

    public void testFrequencyWeightedSingleElementMatch() {
        List<String> listA = Collections.singletonList("a");
        List<String> listB = Collections.singletonList("a");
        assertEquals(1.0, PairComparison.calculateFrequencyWeightedSimilarity(listA, listB), 0.001);
    }

    public void testFrequencyWeightedSingleElementMismatch() {
        List<String> listA = Collections.singletonList("a");
        List<String> listB = Collections.singletonList("b");
        assertEquals(0.0, PairComparison.calculateFrequencyWeightedSimilarity(listA, listB), 0.001);
    }

    public void testFrequencyWeightedIncrementalResultMatchesNaiveImplementation() {
        Random random = random();
        for (int trial = 0; trial < 100; trial++) {
            List<String> listA = randomStringList(random, 1 + random.nextInt(50));
            List<String> listB = randomStringList(random, 1 + random.nextInt(50));

            double optimized = PairComparison.calculateFrequencyWeightedSimilarity(listA, listB);
            double naive = calculateFrequencyWeightedSimilarityNaive(listA, listB);
            assertEquals("Mismatch for lists " + listA + " and " + listB, naive, optimized, 0.0001);
        }
    }

    private List<String> randomStringList(Random random, int size) {
        String[] tokens = { "a", "b", "c", "d", "e", "f", "g", "h" };
        List<String> list = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            list.add(tokens[random.nextInt(tokens.length)]);
        }
        return list;
    }

    /**
     * Reference implementation that mirrors the original O(n^2) algorithm.
     * Used only to verify the optimized implementation.
     */
    private double calculateFrequencyWeightedSimilarityNaive(List<String> listA, List<String> listB) {
        // Reproduce the original computeCombinedWeights logic inline.
        java.util.Map<String, Double> weightsA = calculateFrequencyWeightsNaive(listA);
        java.util.Map<String, Double> weightsB = calculateFrequencyWeightsNaive(listB);

        Set<String> allItems = new HashSet<>(weightsA.keySet());
        allItems.addAll(weightsB.keySet());

        java.util.Map<String, Double> combinedWeights = new java.util.HashMap<>();
        for (String item : allItems) {
            double weightA = weightsA.getOrDefault(item, 0.0);
            double weightB = weightsB.getOrDefault(item, 0.0);
            combinedWeights.put(item, (weightA + weightB) / 2.0);
        }

        double intersectionWeight = 0.0;
        for (String item : new HashSet<>(listA)) {
            if (listB.contains(item)) {
                intersectionWeight += combinedWeights.get(item);
            }
        }

        double unionWeight = combinedWeights.values().stream().mapToDouble(Double::doubleValue).sum();
        double similarity = unionWeight == 0 ? 0 : intersectionWeight / unionWeight;
        return Math.round(similarity * 100.0) / 100.0;
    }

    private java.util.Map<String, Double> calculateFrequencyWeightsNaive(List<String> list) {
        java.util.Map<String, Integer> frequencies = new java.util.HashMap<>();
        for (String item : list) {
            frequencies.put(item, frequencies.getOrDefault(item, 0) + 1);
        }
        double totalFrequency = frequencies.values().stream().mapToInt(Integer::intValue).sum();
        java.util.Map<String, Double> weights = new java.util.HashMap<>();
        for (java.util.Map.Entry<String, Integer> entry : frequencies.entrySet()) {
            weights.put(entry.getKey(), entry.getValue() / totalFrequency);
        }
        return weights;
    }
}
