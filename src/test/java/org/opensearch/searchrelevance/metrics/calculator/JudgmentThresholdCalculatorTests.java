/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */
package org.opensearch.searchrelevance.metrics.calculator;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.opensearch.test.OpenSearchTestCase;

/**
 * Tests for {@link JudgmentThresholdCalculator}.
 *
 * Verifies the dynamic binary‐relevance threshold formula
 * {@code T = max(0.5 × J_max, P90)} across a wide variety of
 * score distributions and edge cases.
 */
public class JudgmentThresholdCalculatorTests extends OpenSearchTestCase {

    // ----------------------------------------------------------------
    // Edge case: null or empty input → fallback threshold 0.0
    // ----------------------------------------------------------------

    public void testNullInput() {
        assertEquals(0.0, JudgmentThresholdCalculator.computeThreshold(null), 0.001);
    }

    public void testEmptyMap() {
        assertEquals(0.0, JudgmentThresholdCalculator.computeThreshold(Collections.emptyMap()), 0.001);
    }

    // ----------------------------------------------------------------
    // "Low Variety" fallback: all ratings are zero
    // ----------------------------------------------------------------

    public void testAllZeros() {
        Map<String, String> scores = new HashMap<>();
        for (int i = 0; i < 10; i++) {
            scores.put("d" + i, "0");
        }
        // J_max = 0 → returns 0.0
        assertEquals(0.0, JudgmentThresholdCalculator.computeThreshold(scores), 0.001);
    }

    // ----------------------------------------------------------------
    // "Constant Value" case: all ratings identical (non-zero)
    // ----------------------------------------------------------------

    public void testConstantValueAllOnes() {
        Map<String, String> scores = new HashMap<>();
        for (int i = 0; i < 10; i++) {
            scores.put("d" + i, "1");
        }
        // J_max=1, P90=1; T = max(0.5, 1) = 1.0
        assertEquals(1.0, JudgmentThresholdCalculator.computeThreshold(scores), 0.001);
    }

    public void testConstantValueAllThrees() {
        Map<String, String> scores = new HashMap<>();
        for (int i = 0; i < 5; i++) {
            scores.put("d" + i, "3");
        }
        // J_max=3, P90=3; T = max(1.5, 3) = 3.0
        assertEquals(3.0, JudgmentThresholdCalculator.computeThreshold(scores), 0.001);
    }

    // ----------------------------------------------------------------
    // Binary 0/1 distribution
    // ----------------------------------------------------------------

    public void testBinaryMostlyOnes() {
        // 7 ones, 3 zeros → sorted: [0,0,0,1,1,1,1,1,1,1]
        Map<String, String> scores = new HashMap<>();
        for (int i = 0; i < 7; i++)
            scores.put("d" + i, "1");
        for (int i = 7; i < 10; i++)
            scores.put("d" + i, "0");
        // J_max=1, P90 index = ceil(0.9*10)-1 = 8 → value=1
        // T = max(0.5, 1) = 1.0
        assertEquals(1.0, JudgmentThresholdCalculator.computeThreshold(scores), 0.001);
    }

    public void testBinaryMostlyZeros() {
        // 2 ones, 8 zeros → sorted: [0,0,0,0,0,0,0,0,1,1]
        Map<String, String> scores = new HashMap<>();
        for (int i = 0; i < 2; i++)
            scores.put("d" + i, "1");
        for (int i = 2; i < 10; i++)
            scores.put("d" + i, "0");
        // J_max=1, P90 index = ceil(0.9*10)-1 = 8 → value=1
        // T = max(0.5, 1) = 1.0
        assertEquals(1.0, JudgmentThresholdCalculator.computeThreshold(scores), 0.001);
    }

    // ----------------------------------------------------------------
    // P90 = 0 fallback (>90% are zeros)
    // ----------------------------------------------------------------

    public void testP90IsZero() {
        // 100 entries: 91 zeros, 9 ones
        Map<String, String> scores = new HashMap<>();
        for (int i = 0; i < 91; i++)
            scores.put("z" + i, "0");
        for (int i = 0; i < 9; i++)
            scores.put("o" + i, "1");
        // sorted: [0×91, 1×9]; P90 index = ceil(0.9*100)-1 = 89 → value=0
        // J_max=1; T = max(0.5*1, 0) = 0.5
        assertEquals(0.5, JudgmentThresholdCalculator.computeThreshold(scores), 0.001);
    }

    // ----------------------------------------------------------------
    // Graded 1-3 scale (LLM judgments)
    // ----------------------------------------------------------------

    public void testGrated1To3() {
        // 20 docs: rating = i%3 (values 0,1,2)
        Map<String, String> scores = new HashMap<>();
        for (int i = 1; i <= 20; i++) {
            scores.put("d" + i, Integer.toString(i % 3));
        }
        // sorted 20 values: seven 0s, seven 1s, six 2s
        // J_max=2, P90 index = ceil(0.9*20)-1 = 17 → sorted[17]=2
        // T = max(1.0, 2.0) = 2.0
        assertEquals(2.0, JudgmentThresholdCalculator.computeThreshold(scores), 0.001);
    }

    // ----------------------------------------------------------------
    // Graded 1-5 scale (LLM judgments)
    // ----------------------------------------------------------------

    public void testGraded1To5() {
        // 10 docs with ratings 1,2,3,4,5,1,2,3,4,5
        Map<String, String> scores = new HashMap<>();
        for (int i = 0; i < 10; i++) {
            scores.put("d" + i, Integer.toString((i % 5) + 1));
        }
        // sorted: [1,1,2,2,3,3,4,4,5,5]; P90 index = ceil(0.9*10)-1 = 8 → value=5
        // J_max=5; T = max(2.5, 5) = 5.0
        assertEquals(5.0, JudgmentThresholdCalculator.computeThreshold(scores), 0.001);
    }

    public void testGraded1To5LargerSet() {
        // 50 docs uniformly distributed 1-5
        Map<String, String> scores = new HashMap<>();
        for (int i = 0; i < 50; i++) {
            scores.put("d" + i, Integer.toString((i % 5) + 1));
        }
        // 10 each of 1,2,3,4,5; sorted: [1×10,2×10,3×10,4×10,5×10]
        // P90 index = ceil(0.9*50)-1 = 44 → value=5
        // J_max=5; T = max(2.5, 5) = 5.0
        assertEquals(5.0, JudgmentThresholdCalculator.computeThreshold(scores), 0.001);
    }

    // ----------------------------------------------------------------
    // Floating-point ratings (0.0 – 1.0 scale)
    // ----------------------------------------------------------------

    public void testFloatScale() {
        // Ratings: 0.0, 0.1, 0.2, ..., 0.9, 1.0 (11 docs)
        Map<String, String> scores = new LinkedHashMap<>();
        for (int i = 0; i <= 10; i++) {
            scores.put("d" + i, String.format(java.util.Locale.ROOT, "%.1f", i / 10.0));
        }
        // sorted: [0.0, 0.1, 0.2, ..., 1.0]; P90 index = ceil(0.9*11)-1 = 9 → value=0.9
        // J_max=1.0; T = max(0.5, 0.9) = 0.9
        assertEquals(0.9, JudgmentThresholdCalculator.computeThreshold(scores), 0.001);
    }

    // ----------------------------------------------------------------
    // Small dataset (< 10 entries) — High-Water Mark prevents low threshold
    // ----------------------------------------------------------------

    public void testSmallDataset3Entries() {
        Map<String, String> scores = new HashMap<>();
        scores.put("d1", "1");
        scores.put("d2", "2");
        scores.put("d3", "3");
        // sorted: [1,2,3]; P90 index = ceil(0.9*3)-1 = 2 → value=3
        // J_max=3; T = max(1.5, 3) = 3.0
        assertEquals(3.0, JudgmentThresholdCalculator.computeThreshold(scores), 0.001);
    }

    public void testSmallDataset2Entries() {
        Map<String, String> scores = new HashMap<>();
        scores.put("d1", "0");
        scores.put("d2", "5");
        // sorted: [0, 5]; P90 index = ceil(0.9*2)-1 = 1 → value=5
        // J_max=5; T = max(2.5, 5) = 5.0
        assertEquals(5.0, JudgmentThresholdCalculator.computeThreshold(scores), 0.001);
    }

    public void testSingleEntry() {
        Map<String, String> scores = Map.of("d1", "4");
        // J_max=4, P90=4; T = max(2, 4) = 4.0
        assertEquals(4.0, JudgmentThresholdCalculator.computeThreshold(scores), 0.001);
    }

    public void testSingleZeroEntry() {
        Map<String, String> scores = Map.of("d1", "0");
        // J_max=0 → 0.0
        assertEquals(0.0, JudgmentThresholdCalculator.computeThreshold(scores), 0.001);
    }

    // ----------------------------------------------------------------
    // High-Water Mark correctly dominates when P90 is low
    // ----------------------------------------------------------------

    public void testHighWaterMarkDominates() {
        // 20 entries: 18 with score=0, 1 with score=1, 1 with score=10
        Map<String, String> scores = new HashMap<>();
        for (int i = 0; i < 18; i++)
            scores.put("z" + i, "0");
        scores.put("low", "1");
        scores.put("high", "10");
        // sorted: [0×18, 1, 10]; P90 index = ceil(0.9*20)-1 = 17 → value=0
        // J_max=10; T = max(5.0, 0) = 5.0
        assertEquals(5.0, JudgmentThresholdCalculator.computeThreshold(scores), 0.001);
    }

    // ----------------------------------------------------------------
    // Large dataset
    // ----------------------------------------------------------------

    public void testLargeDataset() {
        Map<String, String> scores = new HashMap<>();
        for (int i = 0; i < 1000; i++) {
            scores.put("d" + i, Integer.toString(i % 5));
        }
        // Values: 200 each of 0,1,2,3,4
        // sorted: [0×200, 1×200, 2×200, 3×200, 4×200]
        // P90 index = ceil(0.9*1000)-1 = 899 → value=4
        // J_max=4; T = max(2.0, 4) = 4.0
        assertEquals(4.0, JudgmentThresholdCalculator.computeThreshold(scores), 0.001);
    }

    // ----------------------------------------------------------------
    // Unparseable entries are skipped gracefully
    // ----------------------------------------------------------------

    public void testUnparseableEntriesSkipped() {
        Map<String, String> scores = new HashMap<>();
        scores.put("d1", "3");
        scores.put("d2", "not_a_number");
        scores.put("d3", "5");
        // Parseable values: [3, 5]; J_max=5; P90 index = ceil(0.9*2)-1 = 1 → value=5
        // T = max(2.5, 5) = 5.0
        assertEquals(5.0, JudgmentThresholdCalculator.computeThreshold(scores), 0.001);
    }

    public void testAllUnparseable() {
        Map<String, String> scores = Map.of("d1", "abc", "d2", "xyz");
        // No parseable values → 0.0
        assertEquals(0.0, JudgmentThresholdCalculator.computeThreshold(scores), 0.001);
    }

    // ----------------------------------------------------------------
    // percentile() boundary tests
    // ----------------------------------------------------------------

    public void testPercentileBoundaries() {
        List<Double> sorted = Arrays.asList(1.0, 2.0, 3.0, 4.0, 5.0);
        // P0 → first element
        assertEquals(1.0, JudgmentThresholdCalculator.percentile(sorted, 0), 0.001);
        // P100 → last element
        assertEquals(5.0, JudgmentThresholdCalculator.percentile(sorted, 100), 0.001);
        // P50 → ceil(0.5*5)-1 = 2 → value=3.0
        assertEquals(3.0, JudgmentThresholdCalculator.percentile(sorted, 50), 0.001);
    }

    // ----------------------------------------------------------------
    // Negative ratings (unlikely but defensive)
    // ----------------------------------------------------------------

    public void testNegativeRatings() {
        Map<String, String> scores = new HashMap<>();
        scores.put("d1", "-1");
        scores.put("d2", "0");
        scores.put("d3", "3");
        // sorted: [-1, 0, 3]; J_max=3; P90 index = ceil(0.9*3)-1=2 → value=3
        // T = max(1.5, 3) = 3.0
        assertEquals(3.0, JudgmentThresholdCalculator.computeThreshold(scores), 0.001);
    }
}
