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

public class Evaluation {
    public static final String METRICS_PRECISION_AT = "Precision@";
    public static final String METRICS_MEAN_AVERAGE_PRECISION_AT = "MAP@";
    public static final String METRICS_NORMALIZED_DISCOUNTED_CUMULATIVE_GAIN_AT = "NDCG@";

    /**
     * Precision@K - measures precision at a specific rank k
     */
    public static double calculatePrecisionAtK(List<String> docIds, Map<String, String> judgmentScores, int k) {
        int relevantCount = 0;
        int count = 0;

        for (String docId : docIds) {
            if (count >= k) break;
            if (judgmentScores.containsKey(docId) && Double.valueOf(judgmentScores.get(docId)) > 0) {
                relevantCount++;
            }
            count++;
        }

        double precision = k > 0 ? (double) relevantCount / Math.min(k, docIds.size()) : 0.0;
        return Math.round(precision * 100.0) / 100.0;
    }

    /**
     *
     * @param judgmentRatings the docid->judgment mapping for a query
     * @return the total number of documents with judgment > 0 in the ratings
     */
    private static int countRelevant(Map<String, String> judgmentRatings) {
        int numRel = 0;
        for (String value : judgmentRatings.values()) {
            if (Double.valueOf(value) > 0) {
                numRel++;
            }
        }
        return numRel;
    }

    /**
     * Mean Average Precision (MAP)
     */
    public static double calculateMAPAtK(List<String> docIds, Map<String, String> judgmentScores, int k) {
        double sum = 0.0;
        int relevantCount = 0;
        int numRel = countRelevant(judgmentScores);
        int size = Math.min(k, docIds.size());
        for (int i = 0; i < size; i++) {
            String docId = docIds.get(i);
            if (judgmentScores.containsKey(docId) && Double.valueOf(judgmentScores.get(docId)) > 0) {
                relevantCount++;
                sum += (double) relevantCount / (i + 1);
            }
        }
        // MAP is computed over the full set of relevant documents, not just the ones retrieved.
        // see https://en.wikipedia.org/wiki/Evaluation_measures_(information_retrieval)#Average_precision
        double map = relevantCount > 0 ? sum / numRel : 0.0;
        return Math.round(map * 100.0) / 100.0;
    }

    /**
     * Normalized Discounted Cumulative Gain (NDCG)
     */
    public static double calculateNDCGAtK(List<String> docIds, Map<String, String> judgmentScores, int k) {
        double dcg = 0.0;
        double idcg = calculateIDCG(docIds, judgmentScores, k);
        int size = Math.min(k, docIds.size());

        for (int i = 0; i < size; i++) {
            String docId = docIds.get(i);
            if (judgmentScores.containsKey(docId)) {
                double relevance = Double.valueOf(judgmentScores.get(docId));
                dcg += (Math.pow(2, relevance) - 1) / (Math.log(i + 2) / Math.log(2));
            }
        }

        double ndcg = idcg > 0 ? dcg / idcg : 0.0;
        return Math.round(ndcg * 100.0) / 100.0;
    }

    private static double calculateIDCG(List<String> docIds, Map<String, String> judgmentScores, int k) {
        List<Double> relevanceScores = new ArrayList<>();
        // IDCG is computed on the full set of relevant documents
        // we truncate the list after sorting
        for (String rel : judgmentScores.values()) {
            relevanceScores.add(Double.valueOf(rel));
        }

        Collections.sort(relevanceScores, Collections.reverseOrder());
        // we truncate the list to k if the list is larger than k. Otherwise we keep the full list
        relevanceScores = relevanceScores.subList(0, Math.min(relevanceScores.size(), k));
        double idcg = 0.0;

        for (int i = 0; i < relevanceScores.size(); i++) {
            idcg += (Math.pow(2, relevanceScores.get(i)) - 1) / (Math.log(i + 2) / Math.log(2));
        }

        return idcg;
    }
}
