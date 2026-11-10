/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */
package org.opensearch.searchrelevance.utils;

import org.opensearch.core.rest.RestStatus;
import org.opensearch.searchrelevance.exception.SearchRelevanceException;

/** Shared validation for imported ratings at the request boundary and during processing. */
public final class ImportJudgmentRatingValidator {
    /** Smallest rating accepted on import; gain-based metrics assume non-negative relevance. */
    private static final double MIN_RATING = 0.0;

    /**
     * Largest rating accepted on import. DCG/NDCG use exponential gain ({@code 2^rating - 1}), and a DCG
     * value has to stay below {@code Long.MAX_VALUE / 100} to be rounded to two decimals. With up to
     * 10,000 results (the default {@code index.max_result_window}) that holds for ratings up to 46;
     * 40 leaves a margin. Graded scales such as 0-3 or 0-4 are the recommended choice.
     */
    private static final double MAX_RATING = 40.0;

    private ImportJudgmentRatingValidator() {}

    /**
     * Validate using the same numeric precision as the metric calculators.
     * @param rating imported numeric rating or its string representation
     * @param queryText query associated with the rating, included in errors
     * @throws SearchRelevanceException if the rating is not finite or outside [0, 40]
     */
    public static void validate(Object rating, String queryText) {
        if (rating == null) {
            throw new SearchRelevanceException("rating for queryText " + queryText + " must not be null", RestStatus.BAD_REQUEST);
        }
        double value;
        try {
            value = Double.parseDouble(String.valueOf(rating));
            if (!Double.isFinite(value)) {
                throw new NumberFormatException("non-finite rating");
            }
        } catch (NumberFormatException e) {
            throw new SearchRelevanceException(
                "rating '" + rating + "' for queryText " + queryText + " must be a finite number",
                RestStatus.BAD_REQUEST
            );
        }
        if (value < MIN_RATING || value > MAX_RATING) {
            throw new SearchRelevanceException(
                "rating '"
                    + rating
                    + "' for queryText "
                    + queryText
                    + " must be between "
                    + (int) MIN_RATING
                    + " and "
                    + (int) MAX_RATING
                    + "; gain-based metrics such as DCG cannot evaluate ratings outside this range",
                RestStatus.BAD_REQUEST
            );
        }
    }
}
