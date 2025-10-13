/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */
package org.opensearch.searchrelevance.common;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.opensearch.searchrelevance.model.LLMJudgmentRatingType;

/**
 * Processor for handling LLM rating outputs, including sanitization and validation.
 */
public class RatingOutputProcessor {

    private RatingOutputProcessor() {}

    /**
     * Sanitize LLM response without rating type validation (backward compatibility).
     * @param response The raw LLM response
     * @return Sanitized JSON array string
     */
    public static String sanitizeLLMResponse(String response) {
        return sanitizeLLMResponse(response, null);
    }

    /**
     * Sanitize LLM response and optionally validate ratings based on rating type.
     * @param response The raw LLM response
     * @param ratingType The expected rating type (nullable for backward compatibility)
     * @return Sanitized JSON array string
     */
    public static String sanitizeLLMResponse(String response, LLMJudgmentRatingType ratingType) {
        if (response == null || response.trim().isEmpty()) return "[]";

        String cleaned = response.trim();

        // Remove markdown code blocks if present
        cleaned = cleaned.replaceAll("```json\\s*", "").replaceAll("```\\s*", "");

        // Remove backticks
        cleaned = cleaned.replace("`", "");

        // Remove extra whitespace and newlines for cleaner parsing
        cleaned = cleaned.replaceAll("\\s+", " ").trim();

        // If response doesn't start with '[', try to extract JSON array or wrap it
        if (!cleaned.startsWith("[")) {
            // Try to find JSON array within the response
            int arrayStart = cleaned.indexOf('[');
            int arrayEnd = cleaned.lastIndexOf(']');

            if (arrayStart != -1 && arrayEnd != -1 && arrayEnd > arrayStart) {
                // Extract the array portion
                cleaned = cleaned.substring(arrayStart, arrayEnd + 1);
            } else {
                // No array found, try to extract JSON object and wrap it
                int objectStart = cleaned.indexOf('{');
                int objectEnd = cleaned.lastIndexOf('}');

                if (objectStart != -1 && objectEnd != -1 && objectEnd > objectStart) {
                    // Found a JSON object, wrap it in array
                    cleaned = "[" + cleaned.substring(objectStart, objectEnd + 1) + "]";
                } else {
                    // No valid JSON structure found, return empty array
                    return "[]";
                }
            }
        }

        // If ratingType is provided, validate and potentially fix rating values
        if (ratingType != null) {
            cleaned = validateAndFixRatings(cleaned, ratingType);
        }

        return cleaned;
    }

    /**
     * Validate and potentially fix rating values based on the expected rating type.
     * This method performs validation and clamping to ensure ratings conform to
     * the expected format for each rating type.
     *
     * @param jsonArrayString The sanitized JSON array string
     * @param ratingType The expected rating type
     * @return The JSON string with validated/fixed rating values
     */
    static String validateAndFixRatings(String jsonArrayString, LLMJudgmentRatingType ratingType) {
        if (ratingType == null || jsonArrayString == null || jsonArrayString.isEmpty()) {
            return jsonArrayString;
        }

        switch (ratingType) {
            case SCORE0_1:
                return validateAndClampNumericRatings(jsonArrayString, 0.0, 1.0);
            case SCORE1_5:
                return validateAndClampNumericRatings(jsonArrayString, 1.0, 5.0);
            case RELEVANT_IRRELEVANT:
                return validateBinaryRatings(jsonArrayString);
            default:
                return jsonArrayString;
        }
    }

    /**
     * Validate and clamp numeric ratings to be within the specified range.
     * Finds all "rating_score": value pairs and ensures values are within [min, max].
     *
     * @param jsonArrayString The JSON array string
     * @param min Minimum allowed rating value
     * @param max Maximum allowed rating value
     * @return JSON string with clamped rating values
     */
    private static String validateAndClampNumericRatings(String jsonArrayString, double min, double max) {
        // Pattern to match "rating_score": <number>
        Pattern pattern = Pattern.compile("\"rating_score\"\\s*:\\s*(-?\\d+\\.?\\d*)");
        Matcher matcher = pattern.matcher(jsonArrayString);
        StringBuffer result = new StringBuffer();

        while (matcher.find()) {
            String ratingStr = matcher.group(1);
            try {
                double rating = Double.parseDouble(ratingStr);
                // Clamp the rating to the valid range
                double clampedRating = Math.max(min, Math.min(max, rating));

                // Format the replacement with appropriate decimal places
                String replacement;
                if (clampedRating == Math.floor(clampedRating)) {
                    // Integer value
                    replacement = "\"rating_score\": " + (int) clampedRating;
                } else {
                    // Decimal value
                    replacement = "\"rating_score\": " + clampedRating;
                }

                matcher.appendReplacement(result, replacement);
            } catch (NumberFormatException e) {
                // Keep original if parsing fails
                matcher.appendReplacement(result, matcher.group(0));
            }
        }
        matcher.appendTail(result);

        return result.toString();
    }

    /**
     * Validate binary ratings (RELEVANT/IRRELEVANT) and normalize them if needed.
     * Handles various formats like "relevant", "RELEVANT", "true", "1", etc.
     *
     * @param jsonArrayString The JSON array string
     * @return JSON string with normalized binary rating values
     */
    private static String validateBinaryRatings(String jsonArrayString) {
        // Pattern to match "rating_score": <value> where value could be string or number
        Pattern pattern = Pattern.compile("\"rating_score\"\\s*:\\s*\"?([^,}\\s\"]+)\"?");
        Matcher matcher = pattern.matcher(jsonArrayString);
        StringBuffer result = new StringBuffer();

        while (matcher.find()) {
            String ratingStr = matcher.group(1).trim().toUpperCase(Locale.ROOT);

            // Normalize to RELEVANT or IRRELEVANT
            String normalizedRating;
            if (ratingStr.equals("RELEVANT") || ratingStr.equals("TRUE") || ratingStr.equals("1") || ratingStr.equals("1.0")) {
                normalizedRating = "\"rating_score\": \"RELEVANT\"";
            } else if (ratingStr.equals("IRRELEVANT") || ratingStr.equals("FALSE") || ratingStr.equals("0") || ratingStr.equals("0.0")) {
                normalizedRating = "\"rating_score\": \"IRRELEVANT\"";
            } else {
                // Default to IRRELEVANT for unrecognized values
                normalizedRating = "\"rating_score\": \"IRRELEVANT\"";
            }

            matcher.appendReplacement(result, normalizedRating);
        }
        matcher.appendTail(result);

        return result.toString();
    }
}
