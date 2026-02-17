/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */
package org.opensearch.searchrelevance.metrics.calculator;

import java.util.*;

import org.junit.Test;

public class DCGDebugTests {

    @Test
    public void testDCGScenarios() {
        // Scenario: 5 documents with relevance 4
        // If 0-4 scale is used.
        List<String> docs = new ArrayList<>();
        Map<String, String> judgments = new HashMap<>();
        for (int i = 0; i < 5; i++) {
            String id = "d" + i;
            docs.add(id);
            judgments.put(id, "4");
        }

        // 1. Current Implementation (Exponential Gain)
        // Rank 1: (16-1)/1 = 15
        // Rank 2: 15/1.585 = 9.46
        // Rank 3: 15/2.0 = 7.5
        // Rank 4: 15/2.32 = 6.46
        // Rank 5: 15/2.58 = 5.8
        // Sum ≈ 44.22
        double expDCG = Evaluation.calculateDCGAtK(docs, judgments, 5);
        System.out.println("Exponential DCG (Code): " + expDCG);

        // 2. Linear Gain (What user claims I implemented)
        // Rank 1: 4/1 = 4
        // Rank 2: 4/1.585 = 2.52
        // Rank 3: 4/2.0 = 2.0
        // Rank 4: 4/2.32 = 1.72
        // Rank 5: 4/2.58 = 1.55
        // Sum ≈ 11.79
        System.out.println("Linear DCG (Hypothetical): " + calculateLinearDCG(docs, judgments, 5));

        // 3. Raw Sum (No Log Discount)
        // 4 + 4 + 4 + 4 + 4 = 20.0
        // This matches the "20.27" (approx) the user reported!
        // If judgments were slightly higher (e.g. 4.05 on average?), or maybe 20.27
        // comes from a specific dataset.
        System.out.println("Raw Sum (Hypothetical): " + calculateRawSum(docs, judgments, 5));
    }

    private double calculateLinearDCG(List<String> docIds, Map<String, String> judgments, int k) {
        double dcg = 0.0;
        for (int i = 0; i < k; i++) {
            String docId = docIds.get(i);
            double relevance = Double.parseDouble(judgments.get(docId));
            dcg += relevance / (Math.log(i + 2) / Math.log(2));
        }
        return dcg;
    }

    private double calculateRawSum(List<String> docIds, Map<String, String> judgments, int k) {
        double sum = 0.0;
        for (int i = 0; i < k; i++) {
            String docId = docIds.get(i);
            double relevance = Double.parseDouble(judgments.get(docId));
            sum += relevance;
        }
        return sum;
    }
}
