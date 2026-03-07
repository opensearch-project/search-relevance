/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */
package org.opensearch.searchrelevance.metrics.calculator;

import java.util.*;

import org.opensearch.test.OpenSearchTestCase;

/**
 * Tests for the {@link Evaluation} metric calculators.
 *
 * Default test data: relevance judgments in [0..2], 20 documents.
 * Pattern: d1→1, d2→2, d3→0, d4→1, d5→2, d6→0, ... (i%3)
 *
 * With dynamic threshold (T = max(0.5×2, P90=2) = 2):
 * Relevant docs = {d2,d5,d8,d11,d14,d17,d20} (7 of 20)
 */
public class EvaluationTests extends OpenSearchTestCase {
    private Map<String, String> judgments;
    private List<String> results;

    /** Threshold computed from the test data: max(0.5*2, P90=2) = 2.0 */
    private double threshold;

    public void setUp() throws Exception {
        super.setUp();
        this.judgments = new HashMap<String, String>();
        this.results = new ArrayList<String>();
        for (int i = 1; i < 21; i++) {
            String rel = Integer.toString(i % 3);
            String doc = "d" + i;
            this.judgments.put(doc, rel);
            this.results.add(doc);
        }
        this.threshold = JudgmentThresholdCalculator.computeThreshold(this.judgments);
        // Verify the threshold is 2.0 for this dataset
        assertEquals(2.0, this.threshold, 0.001);
    }

    // ----------------------------------------------------------------
    // Precision@K tests with dynamic threshold
    // ----------------------------------------------------------------

    public void testCalculatePrecisionAtK() {
        // With threshold=2: relevant docs at positions 2,5,8,11,14,17,20 → 7/20 = 0.35
        double precision = Evaluation.calculatePrecisionAtK(this.results, this.judgments, 20, threshold);
        assertEquals(0.35, precision, 0.001);
        // Top 5: d1(1),d2(2),d3(0),d4(1),d5(2) → 2 relevant / 5 = 0.4
        precision = Evaluation.calculatePrecisionAtK(this.results, this.judgments, 5, threshold);
        assertEquals(0.4, precision, 0.001);
        // subList(0,5) with k=8 → only 5 docs examined, 2 relevant / 5 = 0.4
        precision = Evaluation.calculatePrecisionAtK(this.results.subList(0, 5), this.judgments, 8, threshold);
        assertEquals(0.4, precision, 0.001);
        // subList(0,8) with k=5 → top 5 examined, 2 relevant / 5 = 0.4
        precision = Evaluation.calculatePrecisionAtK(this.results.subList(0, 8), this.judgments, 5, threshold);
        assertEquals(0.4, precision, 0.001);
    }

    // ----------------------------------------------------------------
    // MAP@K tests with dynamic threshold
    // ----------------------------------------------------------------

    public void testCalculateMAPAtK() {
        // With threshold=2: 7 total relevant in judgments
        // Positions: 2→1/2, 5→2/5, 8→3/8, 11→4/11, 14→5/14, 17→6/17, 20→7/20
        // sum ≈ 2.6986, MAP = 2.6986/7 ≈ 0.39
        double map = Evaluation.calculateMAPAtK(this.results, this.judgments, 20, threshold);
        assertEquals(0.39, map, 0.001);
        // subList(0,5), k=5: relevant at 2→1/2, 5→2/5 ; sum=0.9; MAP=0.9/7≈0.13
        map = Evaluation.calculateMAPAtK(this.results.subList(0, 5), this.judgments, 5, threshold);
        assertEquals(0.13, map, 0.001);
        // subList(0,5), k=8: same as above since only 5 docs
        map = Evaluation.calculateMAPAtK(this.results.subList(0, 5), this.judgments, 8, threshold);
        assertEquals(0.13, map, 0.001);
        // subList(0,8), k=5: still top 5 docs, same result
        map = Evaluation.calculateMAPAtK(this.results.subList(0, 8), this.judgments, 5, threshold);
        assertEquals(0.13, map, 0.001);
    }

    // ----------------------------------------------------------------
    // NDCG@K tests — unchanged; NDCG uses graded relevance, not threshold
    // ----------------------------------------------------------------

    public void testCalculateNDCGAtK() {
        double ndcg = Evaluation.calculateNDCGAtK(this.results, this.judgments, 20);
        assertEquals(0.76, ndcg, 0.001);
        ndcg = Evaluation.calculateNDCGAtK(this.results.subList(0, 5), this.judgments, 5);
        assertEquals(0.51, ndcg, 0.001);
        ndcg = Evaluation.calculateNDCGAtK(this.results.subList(0, 5), this.judgments, 8);
        assertEquals(0.40, ndcg, 0.001);
        ndcg = Evaluation.calculateNDCGAtK(this.results.subList(0, 8), this.judgments, 5);
        assertEquals(0.51, ndcg, 0.001);
    }

    // ----------------------------------------------------------------
    // Explicit threshold=0 tests (legacy "greater than zero" behaviour)
    // ----------------------------------------------------------------

    public void testPrecisionWithZeroThreshold() {
        // threshold=0 → relevant if score > 0, same as old behaviour
        // 14 docs with rating > 0 out of 20 → 0.7
        double precision = Evaluation.calculatePrecisionAtK(this.results, this.judgments, 20, 0.0);
        assertEquals(0.7, precision, 0.001);
        precision = Evaluation.calculatePrecisionAtK(this.results, this.judgments, 5, 0.0);
        assertEquals(0.8, precision, 0.001);
    }

    public void testMAPWithZeroThreshold() {
        double map = Evaluation.calculateMAPAtK(this.results, this.judgments, 20, 0.0);
        assertEquals(0.76, map, 0.001);
    }

    // ----------------------------------------------------------------
    // Binary (0/1) judgments — threshold adapts to simpler distributions
    // ----------------------------------------------------------------

    public void testBinaryJudgments() {
        // 10 docs: 7 with rating=1, 3 with rating=0
        Map<String, String> binary = new LinkedHashMap<>();
        List<String> docs = new ArrayList<>();
        for (int i = 1; i <= 10; i++) {
            String id = "b" + i;
            docs.add(id);
            binary.put(id, i <= 7 ? "1" : "0");
        }
        // J_max=1, P90 of [0,0,0,1,1,1,1,1,1,1] = 1; T = max(0.5, 1) = 1
        double t = JudgmentThresholdCalculator.computeThreshold(binary);
        assertEquals(1.0, t, 0.001);
        // b1–b7 are relevant; top 10 → 7/10 = 0.7
        double p = Evaluation.calculatePrecisionAtK(docs, binary, 10, t);
        assertEquals(0.7, p, 0.001);
    }

    // ----------------------------------------------------------------
    // Edge case: all documents have same non-zero rating
    // ----------------------------------------------------------------

    public void testConstantRatingJudgments() {
        Map<String, String> constant = new HashMap<>();
        List<String> docs = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            String id = "c" + i;
            docs.add(id);
            constant.put(id, "3");
        }
        // J_max=3, P90=3; T = max(1.5, 3) = 3 → all docs are relevant (rating >= 3 and
        // > 0)
        double t = JudgmentThresholdCalculator.computeThreshold(constant);
        assertEquals(3.0, t, 0.001);
        double p = Evaluation.calculatePrecisionAtK(docs, constant, 5, t);
        assertEquals(1.0, p, 0.001);
    }

    // ----------------------------------------------------------------
    // Edge case: empty results
    // ----------------------------------------------------------------

    public void testEmptyResults() {
        double p = Evaluation.calculatePrecisionAtK(Collections.emptyList(), this.judgments, 5, threshold);
        assertEquals(0.0, p, 0.001);
        double map = Evaluation.calculateMAPAtK(Collections.emptyList(), this.judgments, 5, threshold);
        assertEquals(0.0, map, 0.001);
        double ndcg = Evaluation.calculateNDCGAtK(Collections.emptyList(), this.judgments, 5);
        assertEquals(0.0, ndcg, 0.001);
    }

    // ----------------------------------------------------------------
    // Edge case: k = 0
    // ----------------------------------------------------------------

    public void testKZero() {
        double p = Evaluation.calculatePrecisionAtK(this.results, this.judgments, 0, threshold);
        assertEquals(0.0, p, 0.001);
    }

    // ----------------------------------------------------------------
    // Edge case: no document has a judgment
    // ----------------------------------------------------------------

    public void testNoJudgments() {
        List<String> unknownDocs = List.of("x1", "x2", "x3");
        double p = Evaluation.calculatePrecisionAtK(unknownDocs, this.judgments, 3, threshold);
        assertEquals(0.0, p, 0.001);
        double map = Evaluation.calculateMAPAtK(unknownDocs, this.judgments, 3, threshold);
        assertEquals(0.0, map, 0.001);
    }

    // ----------------------------------------------------------------
    // Graded 1-5 scale
    // ----------------------------------------------------------------

    public void testGraded1To5Scale() {
        // Ratings: 1,2,3,4,5,1,2,3,4,5
        Map<String, String> graded = new LinkedHashMap<>();
        List<String> docs = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            String id = "g" + i;
            docs.add(id);
            graded.put(id, Integer.toString((i % 5) + 1));
        }
        // J_max=5, sorted=[1,1,2,2,3,3,4,4,5,5], P90 index = ceil(0.9*10)-1 = 8 →
        // value=5
        // T = max(2.5, 5) = 5
        double t = JudgmentThresholdCalculator.computeThreshold(graded);
        assertEquals(5.0, t, 0.001);
        // Relevant: g4(5), g9(5) → 2 out of 10 = 0.2
        double p = Evaluation.calculatePrecisionAtK(docs, graded, 10, t);
        assertEquals(0.2, p, 0.001);
    }

    // ----------------------------------------------------------------
    // Recall@K tests with dynamic threshold
    // ----------------------------------------------------------------

    public void testCalculateRecallAtK() {
        // With threshold=2: 7 total relevant docs in judgments (d2, d5, d8, d11, d14,
        // d17, d20)
        // Top 20: all 7 are retrieved -> Recall = 7/7 = 1.0
        double recall = Evaluation.calculateRecallAtK(this.results, this.judgments, 20, threshold);
        assertEquals(1.0, recall, 0.001);

        // Top 5: d1(1), d2(2), d3(0), d4(1), d5(2)
        // Relevant in top 5: d2, d5 (2 docs)
        // Recall@5 = 2/7 ~= 0.29
        recall = Evaluation.calculateRecallAtK(this.results, this.judgments, 5, threshold);
        assertEquals(0.29, recall, 0.001);

        // k=0 -> 0 relevant retrieved -> Recall = 0.0
        recall = Evaluation.calculateRecallAtK(this.results, this.judgments, 0, threshold);
        assertEquals(0.0, recall, 0.001);

        // Empty results -> Recall = 0.0
        recall = Evaluation.calculateRecallAtK(Collections.emptyList(), this.judgments, 5, threshold);
        assertEquals(0.0, recall, 0.001);
    }

    // ----------------------------------------------------------------
    // Reciprocal Rank (RR) tests with dynamic threshold
    // ----------------------------------------------------------------

    public void testCalculateReciprocalRank() {
        // With threshold=2, first relevant doc is d2 (index 1, rank 2)
        // RR = 1/2 = 0.5
        double rr = Evaluation.calculateReciprocalRank(this.results, this.judgments, 20, threshold);
        assertEquals(0.5, rr, 0.001);

        // If top 1 doc (d1, rating 1) is checked: not relevant -> RR = 0.0
        rr = Evaluation.calculateReciprocalRank(this.results, this.judgments, 1, threshold);
        assertEquals(0.0, rr, 0.001);

        // Case where no relevant docs exist at all
        // Create disjoint judgments/results
        List<String> badResults = List.of("bad1", "bad2");
        rr = Evaluation.calculateReciprocalRank(badResults, this.judgments, 5, threshold);
        assertEquals(0.0, rr, 0.001);
    }

    // ----------------------------------------------------------------
    // DCG@K tests (uses graded relevance directly)
    // ----------------------------------------------------------------

    public void testCalculateDCGAtK() {
        // Standard dataset: d1(1), d2(2), d3(0), d4(1), d5(2) ...
        // i=0: d1(1) -> (2^1 - 1) / log2(2) = 1/1 = 1.0
        // i=1: d2(2) -> (2^2 - 1) / log2(3) = 3/1.585 = 1.892
        // i=2: d3(0) -> 0
        // i=3: d4(1) -> (2^1 - 1) / log2(5) = 1/2.321 = 0.430
        // i=4: d5(2) -> (2^2 - 1) / log2(6) = 3/2.585 = 1.160
        // Total DCG@5 = 1.0 + 1.892 + 0 + 0.430 + 1.160 = 4.482
        double dcg = Evaluation.calculateDCGAtK(this.results, this.judgments, 5);
        assertEquals(4.48, dcg, 0.01);

        // k=0 -> 0.0
        dcg = Evaluation.calculateDCGAtK(this.results, this.judgments, 0);
        assertEquals(0.0, dcg, 0.001);

        // Empty results -> 0.0
        dcg = Evaluation.calculateDCGAtK(Collections.emptyList(), this.judgments, 5);
        assertEquals(0.0, dcg, 0.001);
    }
}
