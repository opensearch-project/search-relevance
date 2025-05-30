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
 * Test set, relevance judgments in [0..2], 20 total returned.
 d1,1
 d2,2
 d3,0
 d4,1
 d5,2
 d6,0
 d7,1
 d8,2
 d9,0
 d10,1
 d11,2
 d12,0
 d13,1
 d14,2
 d15,0
 d16,1
 d17,2
 d18,0
 d19,1
 d20,2
 */
public class EvaluationTests extends OpenSearchTestCase {
    private Map<String, String> judgments;
    private List<String> results;

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
    }

    public void testCalculatePrecisionAtK() {
        double precision = Evaluation.calculatePrecisionAtK(this.results, this.judgments, 20);
        assertEquals(0.7, precision, 0.001);
        precision = Evaluation.calculatePrecisionAtK(this.results, this.judgments, 5);
        assertEquals(0.8, precision, 0.001);
    }

    public void testCalculateMAP() {
        double map = Evaluation.calculateMAP(this.results, this.judgments);
        assertEquals(0.76, map, 0.001);
        map = Evaluation.calculateMAP(this.results.subList(0, 5), this.judgments);
        assertEquals(0.25, map, 0.001);
    }

    public void testCalculateNDCG() {
        double ndcg = Evaluation.calculateNDCG(this.results, this.judgments);
        assertEquals(0.76, ndcg, 0.001);
        ndcg = Evaluation.calculateNDCG(this.results.subList(0, 5), this.judgments);
        assertEquals(0.51, ndcg, 0.001);
    }
}
