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
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

import org.opensearch.core.rest.RestStatus;
import org.opensearch.searchrelevance.exception.SearchRelevanceException;
import org.opensearch.test.OpenSearchTestCase;

/**
 * Tests for {@link PairComparison} metric calculators.
 */
public class PairComparisonTests extends OpenSearchTestCase {

    // ----------------------------------------------------------------
    // RBO tests
    // ----------------------------------------------------------------

    public void testIdenticalListsRboIsOne() {
        List<String> list = Arrays.asList("a", "b", "c", "d", "e");
        assertEquals(1.0, PairComparison.calculateRBOSimilarity(list, list, 0.5), 0.001);
        assertEquals(1.0, PairComparison.calculateRBOSimilarity(list, list, 0.9), 0.001);
    }

    public void testDisjointListsRboIsZero() {
        List<String> listA = Arrays.asList("a", "b", "c");
        List<String> listB = Arrays.asList("d", "e", "f");
        assertEquals(0.0, PairComparison.calculateRBOSimilarity(listA, listB, 0.5), 0.001);
        assertEquals(0.0, PairComparison.calculateRBOSimilarity(listA, listB, 0.9), 0.001);
    }

    public void testPartialOverlap() {
        List<String> listA = Arrays.asList("a", "b", "c");
        List<String> listB = Arrays.asList("a", "c", "b");
        // d=0: overlap=1, d=1: overlap=1/2, d=2: overlap=3/3
        // sum = 1 + 0.5*0.5 + 0.25*1 = 1.5
        // rbo = 1.5 * 0.5 / (1 - 0.125) = 0.8571... -> 0.86
        assertEquals(0.86, PairComparison.calculateRBOSimilarity(listA, listB, 0.5), 0.001);
    }

    public void testReversedOrder() {
        List<String> listA = Arrays.asList("a", "b");
        List<String> listB = Arrays.asList("b", "a");
        // d=0: overlap=0/1, d=1: overlap=2/2
        // sum = 0 + 0.5*1 = 0.5
        // rbo = 0.5 * 0.5 / (1 - 0.25) = 0.3333... -> 0.33
        assertEquals(0.33, PairComparison.calculateRBOSimilarity(listA, listB, 0.5), 0.001);
    }

    public void testDifferentLengths() {
        List<String> listA = Arrays.asList("a", "b", "c", "d");
        List<String> listB = Arrays.asList("a", "b");
        // d=0: overlap=1, d=1: overlap=2/2, d=2: overlap=2/3, d=3: overlap=2/4
        // sum = 1 + 0.5*1 + 0.25*2/3 + 0.125*2/4
        // = 1 + 0.5 + 0.1667 + 0.0625 = 1.7292
        // rbo = 1.7292 * 0.5 / (1 - 0.0625) = 0.8646 / 0.9375 = 0.9222 -> 0.92
        assertEquals(0.92, PairComparison.calculateRBOSimilarity(listA, listB, 0.5), 0.001);
    }

    public void testRboDuplicates() {
        List<String> listA = Arrays.asList("a", "a", "b");
        List<String> listB = Arrays.asList("a", "b", "b");
        // d=0: overlap=1, d=1: overlap=1/2, d=2: overlap=2/2
        // sum = 1 + 0.5*0.5 + 0.25*1 = 1.5
        // rbo = 1.5 * 0.5 / (1 - 0.125) = 0.8571... -> 0.86
        assertEquals(0.86, PairComparison.calculateRBOSimilarity(listA, listB, 0.5), 0.001);
    }

    public void testSingleElementMatch() {
        List<String> listA = Collections.singletonList("a");
        List<String> listB = Collections.singletonList("a");
        assertEquals(1.0, PairComparison.calculateRBOSimilarity(listA, listB, 0.5), 0.001);
    }

    public void testSingleElementMismatch() {
        List<String> listA = Collections.singletonList("a");
        List<String> listB = Collections.singletonList("b");
        assertEquals(0.0, PairComparison.calculateRBOSimilarity(listA, listB, 0.5), 0.001);
    }

    public void testInvalidPThrowsException() {
        List<String> list = Collections.singletonList("a");

        SearchRelevanceException ex = expectThrows(
            SearchRelevanceException.class,
            () -> PairComparison.calculateRBOSimilarity(list, list, 0.0)
        );
        assertEquals(RestStatus.INTERNAL_SERVER_ERROR, ex.status());
        assertTrue(ex.getMessage().contains("p must be between 0 and 1"));

        ex = expectThrows(SearchRelevanceException.class, () -> PairComparison.calculateRBOSimilarity(list, list, 1.0));
        assertEquals(RestStatus.INTERNAL_SERVER_ERROR, ex.status());

        ex = expectThrows(SearchRelevanceException.class, () -> PairComparison.calculateRBOSimilarity(list, list, -0.1));
        assertEquals(RestStatus.INTERNAL_SERVER_ERROR, ex.status());
    }

    public void testRboIncrementalResultMatchesNaiveImplementation() {
        Random random = random();
        for (int trial = 0; trial < 100; trial++) {
            List<String> listA = randomStringList(random, 1 + random.nextInt(50));
            List<String> listB = randomStringList(random, 1 + random.nextInt(50));
            double p = 0.1 + random.nextDouble() * 0.89;

            double optimized = PairComparison.calculateRBOSimilarity(listA, listB, p);
            double naive = calculateRBOSimilarityNaive(listA, listB, p);
            assertEquals("Mismatch for lists " + listA + " and " + listB + " with p=" + p, naive, optimized, 0.0001);
        }
    }

    // ----------------------------------------------------------------
    // Frequency Weighted similarity tests
    // ----------------------------------------------------------------

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

    // ----------------------------------------------------------------
    // Helpers
    // ----------------------------------------------------------------

    private List<String> randomStringList(Random random, int size) {
        String[] tokens = { "a", "b", "c", "d", "e", "f", "g", "h" };
        List<String> list = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            list.add(tokens[random.nextInt(tokens.length)]);
        }
        return list;
    }

    /**
     * Reference implementation that mirrors the original O(n^2) RBO algorithm.
     */
    private double calculateRBOSimilarityNaive(List<String> listA, List<String> listB, double p) {
        if (p <= 0 || p >= 1) {
            throw new SearchRelevanceException("p must be between 0 and 1", RestStatus.INTERNAL_SERVER_ERROR);
        }

        int maxDepth = Math.max(listA.size(), listB.size());
        double sum = 0;
        double weight = 1;

        for (int d = 0; d < maxDepth; d++) {
            Set<String> setA = new HashSet<>(listA.subList(0, Math.min(d + 1, listA.size())));
            Set<String> setB = new HashSet<>(listB.subList(0, Math.min(d + 1, listB.size())));

            Set<String> intersection = new HashSet<>(setA);
            intersection.retainAll(setB);
            double overlap = intersection.size() / (double) Math.max(setA.size(), setB.size());

            sum += weight * overlap;
            weight *= p;
        }

        double rboSimilarity = sum * (1 - p) / (1 - Math.pow(p, maxDepth));
        return Math.round(rboSimilarity * 100.0) / 100.0;
    }

    /**
     * Reference implementation that mirrors the original O(n^2) frequency-weighted algorithm.
     */
    private double calculateFrequencyWeightedSimilarityNaive(List<String> listA, List<String> listB) {
        Map<String, Double> weightsA = calculateFrequencyWeightsNaive(listA);
        Map<String, Double> weightsB = calculateFrequencyWeightsNaive(listB);

        Set<String> allItems = new HashSet<>(weightsA.keySet());
        allItems.addAll(weightsB.keySet());

        Map<String, Double> combinedWeights = new HashMap<>();
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

    private Map<String, Double> calculateFrequencyWeightsNaive(List<String> list) {
        Map<String, Integer> frequencies = new HashMap<>();
        for (String item : list) {
            frequencies.put(item, frequencies.getOrDefault(item, 0) + 1);
        }
        double totalFrequency = frequencies.values().stream().mapToInt(Integer::intValue).sum();
        Map<String, Double> weights = new HashMap<>();
        for (Map.Entry<String, Integer> entry : frequencies.entrySet()) {
            weights.put(entry.getKey(), entry.getValue() / totalFrequency);
        }
        return weights;
    }
}
