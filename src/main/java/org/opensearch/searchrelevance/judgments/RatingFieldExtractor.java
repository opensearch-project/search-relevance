/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */
package org.opensearch.searchrelevance.judgments;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import lombok.Value;
import lombok.extern.log4j.Log4j2;

/**
 * Utility for extracting rating scores from LLM response data.
 * Implements a strategy pattern to handle various field name conventions that different
 * LLM models may use in their responses.
 *
 */
@Log4j2
public final class RatingFieldExtractor {

    /**
     * Ordered list of field names to try when extracting rating scores.
     * Listed in order of preference/likelihood based on common LLM response patterns.
     */
    private static final List<String> RATING_FIELD_CANDIDATES = Arrays.asList(
        "rating_score",    // Most explicit and preferred
        "rating",          // Most common
        "score",           // Generic but widely used
        "relevance_score", // Semantic search context
        "relevance"        // Shortened semantic form
    );

    /**
     * Result class that encapsulates the extraction outcome with detailed information.
     */
    @Value
    public static class ExtractionResult {
        final boolean success;
        final Double ratingScore;
        final String fieldUsed;
        final String errorMessage;

        public static ExtractionResult success(Double ratingScore, String fieldUsed) {
            return new ExtractionResult(true, ratingScore, fieldUsed, null);
        }

        public static ExtractionResult failure(String errorMessage) {
            return new ExtractionResult(false, null, null, errorMessage);
        }
    }

    private RatingFieldExtractor() {
        throw new UnsupportedOperationException("Utility class cannot be instantiated");
    }

    /**
     * Extracts a rating score from the given rating data using a priority-based field matching strategy.
     *
     * @param ratingData The map containing rating data from LLM response
     * @return ExtractionResult containing either the successfully extracted score or error details
     */
    public static ExtractionResult extractRatingScore(Map<String, Object> ratingData) {
        if (ratingData == null || ratingData.isEmpty()) {
            return ExtractionResult.failure("Rating data is null or empty");
        }

        // Try each candidate field in priority order
        for (String fieldName : RATING_FIELD_CANDIDATES) {
            Optional<ExtractionResult> result = tryExtractFromField(ratingData, fieldName);
            if (result.isPresent()) {
                return result.get();
            }
        }

        // If no candidate fields worked, provide detailed failure information
        return ExtractionResult.failure(
            String.format(
                Locale.ROOT,
                "No valid rating field found. Available fields: %s. Expected one of: %s",
                ratingData.keySet(),
                RATING_FIELD_CANDIDATES
            )
        );
    }

    /**
     * Attempts to extract a rating score from a specific field in the rating data.
     *
     * @param ratingData The rating data map
     * @param fieldName The field name to try
     * @return Optional containing ExtractionResult if successful, empty if field not usable
     */
    private static Optional<ExtractionResult> tryExtractFromField(Map<String, Object> ratingData, String fieldName) {
        Object fieldValue = ratingData.get(fieldName);

        if (fieldValue == null) {
            // Field doesn't exist, try next candidate
            return Optional.empty();
        }

        try {
            Optional<Double> ratingScore = parseNumericValue(fieldValue);
            if (ratingScore.isPresent() && isValidRatingScore(ratingScore.get())) {
                log.debug("Successfully extracted rating {} from field '{}'", ratingScore, fieldName);
                return Optional.of(ExtractionResult.success(ratingScore.get(), fieldName));
            } else {
                log.debug("Field '{}' contains invalid rating value: {}", fieldName, fieldValue);
                return Optional.empty();
            }
        } catch (Exception e) {
            log.debug("Failed to parse field '{}' with value '{}': {}", fieldName, fieldValue, e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * Parses various numeric value types into a Double.
     * Handles different numeric types that may come from JSON parsing.
     *
     * @param value The value to parse
     * @return Optional with double value if parseable, empty otherwise
     */
    private static Optional<Double> parseNumericValue(Object value) {
        if (value instanceof Number) {
            return Optional.of(((Number) value).doubleValue());
        }

        if (value instanceof String) {
            String stringValue = ((String) value).trim();
            if (stringValue.isEmpty()) {
                return null;
            }
            try {
                return Optional.of(Double.parseDouble(stringValue));
            } catch (NumberFormatException e) {
                return Optional.empty();
            }
        }

        return Optional.empty();
    }

    /**
     * Validates that the extracted rating score is within acceptable bounds.
     *
     * @param ratingScore The rating score to validate
     * @return true if the rating is valid, false otherwise
     */
    private static boolean isValidRatingScore(Double ratingScore) {
        if (ratingScore == null) {
            return false;
        }
        // Rating scores should be non-negative and finite
        if (ratingScore < 0.0 || Double.isFinite(ratingScore) == false) {
            return false;
        }
        return true;
    }
}
