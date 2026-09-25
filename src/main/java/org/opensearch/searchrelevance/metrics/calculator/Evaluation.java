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
    public static final String METRICS_DISCOUNTED_CUMULATIVE_GAIN_AT = "DCG@";
    public static final String METRICS_RECALL_AT = "Recall@";
    public static final String METRICS_MEAN_RECIPROCAL_RANK = "MRR";

    private static final double MAX_ROUNDABLE = Long.MAX_VALUE / 100.0;

    /**
     * Round a metric to two decimals, failing loudly on a value that cannot be a real score.
     * {@code Math.round} would otherwise turn NaN into 0 and saturate Infinity or any value too large
     * for {@code value * 100} to fit in a long to {@code Long.MAX_VALUE}, making a corrupt metric (e.g.
     * DCG overflowing from an extreme rating) look like a real score.
     *
     * @throws IllegalArgumentException if the value is not finite or too large to round
     */
    static double roundToTwoDecimals(double value) {
        if (!Double.isFinite(value) || Math.abs(value) >= MAX_ROUNDABLE) {
            throw new IllegalArgumentException(
                "Metric value [" + value + "] is not a finite number that can be rounded; check the judgment ratings"
            );
        }
        return Math.round(value * 100.0) / 100.0;
    }

    /**
     * Precision@K - measures precision at a specific rank k.
     *
     * @param docIds         ordered list of document IDs returned by search
     * @param judgmentScores mapping from doc ID to its judgment rating
     * @param k              rank cutoff
     * @param threshold      binary relevance threshold; a doc is relevant when its
     *                       rating {@code >= threshold} and {@code > 0}
     */
    public static double calculatePrecisionAtK(List<String> docIds, Map<String, String> judgmentScores, int k, double threshold) {
        int relevantCount = 0;
        int count = 0;

        for (String docId : docIds) {
            if (count >= k) break;
            if (isRelevant(docId, judgmentScores, threshold)) {
                relevantCount++;
            }
            count++;
        }

        // Guard the denominator directly: with no results it is 0, and 0/0 would be NaN.
        int evaluated = Math.min(k, docIds.size());
        double precision = evaluated > 0 ? (double) relevantCount / evaluated : 0.0;
        return roundToTwoDecimals(precision);
    }

    /**
     * Count the total number of relevant documents in the judgment set.
     *
     * @param judgmentRatings the docid-&gt;judgment mapping for a query
     * @param threshold       binary relevance threshold
     * @return the number of documents whose judgment {@code >= threshold} and
     *         {@code > 0}
     */
    private static int countRelevant(Map<String, String> judgmentRatings, double threshold) {
        int numRel = 0;
        for (String value : judgmentRatings.values()) {
            double v = Double.parseDouble(value);
            if (v >= threshold && v > 0) {
                numRel++;
            }
        }
        return numRel;
    }

    /**
     * Determine whether a document is considered relevant given the threshold.
     */
    private static boolean isRelevant(String docId, Map<String, String> judgmentScores, double threshold) {
        if (!judgmentScores.containsKey(docId)) {
            return false;
        }
        double score = Double.parseDouble(judgmentScores.get(docId));
        return score >= threshold && score > 0;
    }

    /**
     * Mean Average Precision (MAP).
     *
     * @param docIds         ordered list of document IDs returned by search
     * @param judgmentScores mapping from doc ID to its judgment rating
     * @param k              rank cutoff
     * @param threshold      binary relevance threshold
     */
    public static double calculateMAPAtK(List<String> docIds, Map<String, String> judgmentScores, int k, double threshold) {
        double sum = 0.0;
        int relevantCount = 0;
        int numRel = countRelevant(judgmentScores, threshold);
        int size = Math.min(k, docIds.size());
        for (int i = 0; i < size; i++) {
            String docId = docIds.get(i);
            if (isRelevant(docId, judgmentScores, threshold)) {
                relevantCount++;
                sum += (double) relevantCount / (i + 1);
            }
        }
        // MAP is computed over the full set of relevant documents, not just the ones retrieved.
        // see https://en.wikipedia.org/wiki/Evaluation_measures_(information_retrieval)#Average_precision
        double map = relevantCount > 0 ? sum / numRel : 0.0;
        return roundToTwoDecimals(map);
    }

    /**
     * Normalized Discounted Cumulative Gain (NDCG@K).
     * <p>
     * Uses unrounded DCG internally to avoid compounding rounding errors
     * when computing the ratio DCG/IDCG.
     *
     * @param docIds         ordered list of document IDs returned by search
     * @param judgmentScores mapping from doc ID to its judgment rating
     * @param k              rank cutoff
     * @return NDCG value rounded to 2 decimal places
     */
    public static double calculateNDCGAtK(List<String> docIds, Map<String, String> judgmentScores, int k) {
        double dcg = rawDCGAtK(docIds, judgmentScores, k);
        double idcg = calculateIDCG(docIds, judgmentScores, k);

        double ndcg = idcg > 0 ? dcg / idcg : 0.0;
        return roundToTwoDecimals(ndcg);
    }

    /**
     * Recall@K - measures the proportion of relevant documents retrieved in the top K.
     * <p>
     * Note: The total number of relevant documents is computed from the entire judgment set.
     * If the judgment set contains documents that are not present in the search index
     * (e.g., from UBI data or external sources), recall will be computed against all
     * judged relevant documents, potentially resulting in lower recall values.
     *
     * @param docIds         ordered list of document IDs returned by search
     * @param judgmentScores mapping from doc ID to its judgment rating
     * @param k              rank cutoff
     * @param threshold      binary relevance threshold
     * @return Recall value rounded to 2 decimal places
     */
    public static double calculateRecallAtK(List<String> docIds, Map<String, String> judgmentScores, int k, double threshold) {
        int relevantInTopK = 0;
        int size = Math.min(k, docIds.size());

        for (int i = 0; i < size; i++) {
            String docId = docIds.get(i);
            if (isRelevant(docId, judgmentScores, threshold)) {
                relevantInTopK++;
            }
        }

        int totalRelevant = countRelevant(judgmentScores, threshold);
        double recall = totalRelevant > 0 ? (double) relevantInTopK / totalRelevant : 0.0;
        return roundToTwoDecimals(recall);
    }

    /**
     * Reciprocal Rank (RR) - computes 1 / rank of the first relevant document for a single query.
     * <p>
     * This method computes RR for an individual query. To obtain Mean Reciprocal Rank (MRR),
     * average the RR values across multiple queries at the experiment aggregation level.
     * See: <a href="https://en.wikipedia.org/wiki/Mean_reciprocal_rank">Mean Reciprocal Rank</a>
     *
     * @param docIds         ordered list of document IDs returned by search
     * @param judgmentScores mapping from doc ID to its judgment rating
     * @param k              rank cutoff (considers only documents within top K)
     * @param threshold      binary relevance threshold
     * @return Reciprocal rank value (1/rank) rounded to 2 decimal places, or 0.0 if no relevant document found in top K
     */
    public static double calculateReciprocalRank(List<String> docIds, Map<String, String> judgmentScores, int k, double threshold) {
        int size = Math.min(k, docIds.size());
        for (int i = 0; i < size; i++) {
            String docId = docIds.get(i);
            if (isRelevant(docId, judgmentScores, threshold)) {
                // Rank is i + 1 (1-based)
                return roundToTwoDecimals((1.0 / (i + 1)));
            }
        }
        return 0.0;
    }

    /**
     * Discounted Cumulative Gain (DCG@K) - non-normalized version of NDCG.
     * <p>
     * Note: Documents without a judgment in judgmentScores are treated as having
     * relevance = 0 (i.e., they contribute nothing to the DCG score).
     *
     * @param docIds         ordered list of document IDs returned by search
     * @param judgmentScores mapping from doc ID to its judgment rating
     * @param k              rank cutoff
     * @return DCG value rounded to 2 decimal places
     */
    public static double calculateDCGAtK(List<String> docIds, Map<String, String> judgmentScores, int k) {
        return roundToTwoDecimals(rawDCGAtK(docIds, judgmentScores, k));
    }

    /**
     * Computes raw (unrounded) DCG@K for internal use.
     * <p>
     * This method is used internally by both {@link #calculateDCGAtK} (which rounds for display)
     * and {@link #calculateNDCGAtK} (which needs unrounded values for accurate ratio computation).
     *
     * @param docIds         ordered list of document IDs returned by search
     * @param judgmentScores mapping from doc ID to its judgment rating
     * @param k              rank cutoff
     * @return raw DCG value (not rounded)
     */
    private static double rawDCGAtK(List<String> docIds, Map<String, String> judgmentScores, int k) {
        double dcg = 0.0;
        int size = Math.min(k, docIds.size());

        for (int i = 0; i < size; i++) {
            String docId = docIds.get(i);
            if (judgmentScores.containsKey(docId)) {
                double relevance = Double.parseDouble(judgmentScores.get(docId));
                dcg += (Math.pow(2, relevance) - 1) / (Math.log(i + 2) / Math.log(2));
            }
        }
        return dcg;
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
        if (relevanceScores.size() > k) {
            relevanceScores = relevanceScores.subList(0, k);
        }
        double idcg = 0.0;

        for (int i = 0; i < relevanceScores.size(); i++) {
            idcg += (Math.pow(2, relevanceScores.get(i)) - 1) / (Math.log(i + 2) / Math.log(2));
        }

        return idcg;
    }
}
