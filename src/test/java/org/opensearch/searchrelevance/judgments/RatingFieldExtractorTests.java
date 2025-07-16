/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */
package org.opensearch.searchrelevance.judgments;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import org.opensearch.test.OpenSearchTestCase;

/**
 * Comprehensive unit tests for RatingFieldExtractor.
 * Tests all field extraction scenarios and edge cases.
 */
public class RatingFieldExtractorTests extends OpenSearchTestCase {

    public void testExtractRatingScore_WithRatingScore_ShouldSucceed() {
        Map<String, Object> ratingData = createRatingData("rating_score", 0.85);

        RatingFieldExtractor.ExtractionResult result = RatingFieldExtractor.extractRatingScore(ratingData);

        assertTrue("Should successfully extract rating_score", result.isSuccess());
        assertEquals("Should extract correct value", Double.valueOf(0.85), result.getRatingScore());
        assertEquals("Should use rating_score field", "rating_score", result.getFieldUsed());
    }

    public void testExtractRatingScore_WithRating_ShouldSucceed() {
        Map<String, Object> ratingData = createRatingData("rating", 0.72);

        RatingFieldExtractor.ExtractionResult result = RatingFieldExtractor.extractRatingScore(ratingData);

        assertTrue("Should successfully extract rating", result.isSuccess());
        assertEquals("Should extract correct value", Double.valueOf(0.72), result.getRatingScore());
        assertEquals("Should use rating field", "rating", result.getFieldUsed());
    }

    public void testExtractRatingScore_WithScore_ShouldSucceed() {
        Map<String, Object> ratingData = createRatingData("score", 0.91);

        RatingFieldExtractor.ExtractionResult result = RatingFieldExtractor.extractRatingScore(ratingData);

        assertTrue("Should successfully extract score", result.isSuccess());
        assertEquals("Should extract correct value", Double.valueOf(0.91), result.getRatingScore());
        assertEquals("Should use score field", "score", result.getFieldUsed());
    }

    public void testExtractRatingScore_WithRelevanceScore_ShouldSucceed() {
        Map<String, Object> ratingData = createRatingData("relevance_score", 0.66);

        RatingFieldExtractor.ExtractionResult result = RatingFieldExtractor.extractRatingScore(ratingData);

        assertTrue("Should successfully extract relevance_score", result.isSuccess());
        assertEquals("Should extract correct value", Double.valueOf(0.66), result.getRatingScore());
        assertEquals("Should use relevance_score field", "relevance_score", result.getFieldUsed());
    }

    public void testExtractRatingScore_WithRelevance_ShouldSucceed() {
        Map<String, Object> ratingData = createRatingData("relevance", 0.44);

        RatingFieldExtractor.ExtractionResult result = RatingFieldExtractor.extractRatingScore(ratingData);

        assertTrue("Should successfully extract relevance", result.isSuccess());
        assertEquals("Should extract correct value", Double.valueOf(0.44), result.getRatingScore());
        assertEquals("Should use relevance field", "relevance", result.getFieldUsed());
    }

    public void testExtractRatingScore_PriorityOrder_ShouldUsePreferredField() {
        Map<String, Object> ratingData = new HashMap<>();
        ratingData.put("score", 0.5);           // Lower priority
        ratingData.put("rating_score", 0.8);    // Higher priority
        ratingData.put("rating", 0.6);          // Medium priority

        RatingFieldExtractor.ExtractionResult result = RatingFieldExtractor.extractRatingScore(ratingData);

        assertTrue("Should successfully extract", result.isSuccess());
        assertEquals("Should use highest priority field", Double.valueOf(0.8), result.getRatingScore());
        assertEquals("Should use rating_score field", "rating_score", result.getFieldUsed());
    }

    public void testExtractRatingScore_IntegerValue_ShouldConvertToDouble() {
        Map<String, Object> ratingData = createRatingData("rating", 1);

        RatingFieldExtractor.ExtractionResult result = RatingFieldExtractor.extractRatingScore(ratingData);

        assertTrue("Should successfully extract integer", result.isSuccess());
        assertEquals("Should convert to double", Double.valueOf(1.0), result.getRatingScore());
    }

    public void testExtractRatingScore_LongValue_ShouldConvertToDouble() {
        Map<String, Object> ratingData = createRatingData("rating", 2L);

        RatingFieldExtractor.ExtractionResult result = RatingFieldExtractor.extractRatingScore(ratingData);

        assertTrue("Should successfully extract long", result.isSuccess());
        assertEquals("Should convert to double", Double.valueOf(2.0), result.getRatingScore());
    }

    public void testExtractRatingScore_FloatValue_ShouldConvertToDouble() {
        Map<String, Object> ratingData = createRatingData("rating", 0.75f);

        RatingFieldExtractor.ExtractionResult result = RatingFieldExtractor.extractRatingScore(ratingData);

        assertTrue("Should successfully extract float", result.isSuccess());
        assertEquals("Should convert to double", Double.valueOf(0.75), result.getRatingScore(), 0.001);
    }

    public void testExtractRatingScore_StringNumberValue_ShouldParseToDouble() {
        Map<String, Object> ratingData = createRatingData("rating", "0.42");

        RatingFieldExtractor.ExtractionResult result = RatingFieldExtractor.extractRatingScore(ratingData);

        assertTrue("Should successfully parse string number", result.isSuccess());
        assertEquals("Should parse to double", Double.valueOf(0.42), result.getRatingScore());
    }

    public void testExtractRatingScore_StringIntegerValue_ShouldParseToDouble() {
        Map<String, Object> ratingData = createRatingData("rating", "3");

        RatingFieldExtractor.ExtractionResult result = RatingFieldExtractor.extractRatingScore(ratingData);

        assertTrue("Should successfully parse string integer", result.isSuccess());
        assertEquals("Should parse to double", Double.valueOf(3.0), result.getRatingScore());
    }

    public void testExtractRatingScore_ZeroValue_ShouldSucceed() {
        Map<String, Object> ratingData = createRatingData("rating", 0.0);

        RatingFieldExtractor.ExtractionResult result = RatingFieldExtractor.extractRatingScore(ratingData);

        assertTrue("Should accept zero rating", result.isSuccess());
        assertEquals("Should extract zero value", Double.valueOf(0.0), result.getRatingScore());
    }

    public void testExtractRatingScore_NullRatingData_ShouldFail() {
        RatingFieldExtractor.ExtractionResult result = RatingFieldExtractor.extractRatingScore(null);

        assertFalse("Should fail with null data", result.isSuccess());
        assertNotNull("Should have error message", result.getErrorMessage());
        assertTrue("Error should mention null data", result.getErrorMessage().contains("null"));
    }

    public void testExtractRatingScore_EmptyRatingData_ShouldFail() {
        Map<String, Object> ratingData = Collections.emptyMap();

        RatingFieldExtractor.ExtractionResult result = RatingFieldExtractor.extractRatingScore(ratingData);

        assertFalse("Should fail with empty data", result.isSuccess());
        assertNotNull("Should have error message", result.getErrorMessage());
        assertTrue("Error should mention empty data", result.getErrorMessage().contains("empty"));
    }

    public void testExtractRatingScore_NoValidFields_ShouldFail() {
        Map<String, Object> ratingData = new HashMap<>();
        ratingData.put("other_field", 0.5);
        ratingData.put("irrelevant", 0.8);

        RatingFieldExtractor.ExtractionResult result = RatingFieldExtractor.extractRatingScore(ratingData);

        assertFalse("Should fail with no valid fields", result.isSuccess());
        assertNotNull("Should have error message", result.getErrorMessage());
        assertTrue("Error should mention available fields", result.getErrorMessage().contains("Available fields"));
        assertTrue("Error should mention expected fields", result.getErrorMessage().contains("Expected one of"));
    }

    public void testExtractRatingScore_NullFieldValue_ShouldTryNextField() {
        Map<String, Object> ratingData = new HashMap<>();
        ratingData.put("rating_score", null);  // Null highest priority field
        ratingData.put("rating", 0.7);         // Valid second priority field

        RatingFieldExtractor.ExtractionResult result = RatingFieldExtractor.extractRatingScore(ratingData);

        assertTrue("Should skip null field and use next", result.isSuccess());
        assertEquals("Should use second priority field", Double.valueOf(0.7), result.getRatingScore());
        assertEquals("Should use rating field", "rating", result.getFieldUsed());
    }

    public void testExtractRatingScore_InvalidStringValue_ShouldTryNextField() {
        Map<String, Object> ratingData = new HashMap<>();
        ratingData.put("rating_score", "invalid_number");  // Invalid string
        ratingData.put("rating", 0.6);                     // Valid fallback

        RatingFieldExtractor.ExtractionResult result = RatingFieldExtractor.extractRatingScore(ratingData);

        assertTrue("Should skip invalid string and use next", result.isSuccess());
        assertEquals("Should use fallback field", Double.valueOf(0.6), result.getRatingScore());
        assertEquals("Should use rating field", "rating", result.getFieldUsed());
    }

    public void testExtractRatingScore_EmptyStringValue_ShouldTryNextField() {
        Map<String, Object> ratingData = new HashMap<>();
        ratingData.put("rating_score", "");    // Empty string
        ratingData.put("rating", 0.4);         // Valid fallback

        RatingFieldExtractor.ExtractionResult result = RatingFieldExtractor.extractRatingScore(ratingData);

        assertTrue("Should skip empty string and use next", result.isSuccess());
        assertEquals("Should use fallback field", Double.valueOf(0.4), result.getRatingScore());
        assertEquals("Should use rating field", "rating", result.getFieldUsed());
    }

    public void testExtractRatingScore_WhitespaceStringValue_ShouldTryNextField() {
        Map<String, Object> ratingData = new HashMap<>();
        ratingData.put("rating_score", "   ");  // Whitespace string
        ratingData.put("rating", 0.3);          // Valid fallback

        RatingFieldExtractor.ExtractionResult result = RatingFieldExtractor.extractRatingScore(ratingData);

        assertTrue("Should skip whitespace string and use next", result.isSuccess());
        assertEquals("Should use fallback field", Double.valueOf(0.3), result.getRatingScore());
        assertEquals("Should use rating field", "rating", result.getFieldUsed());
    }

    public void testExtractRatingScore_NegativeValue_ShouldTryNextField() {
        Map<String, Object> ratingData = new HashMap<>();
        ratingData.put("rating_score", -0.5);  // Negative value (invalid)
        ratingData.put("rating", 0.2);         // Valid fallback

        RatingFieldExtractor.ExtractionResult result = RatingFieldExtractor.extractRatingScore(ratingData);

        assertTrue("Should skip negative value and use next", result.isSuccess());
        assertEquals("Should use fallback field", Double.valueOf(0.2), result.getRatingScore());
        assertEquals("Should use rating field", "rating", result.getFieldUsed());
    }

    public void testExtractRatingScore_InfiniteValue_ShouldTryNextField() {
        Map<String, Object> ratingData = new HashMap<>();
        ratingData.put("rating_score", Double.POSITIVE_INFINITY);  // Infinite value (invalid)
        ratingData.put("rating", 0.1);                            // Valid fallback

        RatingFieldExtractor.ExtractionResult result = RatingFieldExtractor.extractRatingScore(ratingData);

        assertTrue("Should skip infinite value and use next", result.isSuccess());
        assertEquals("Should use fallback field", Double.valueOf(0.1), result.getRatingScore());
        assertEquals("Should use rating field", "rating", result.getFieldUsed());
    }

    public void testExtractRatingScore_NaNValue_ShouldTryNextField() {
        Map<String, Object> ratingData = new HashMap<>();
        ratingData.put("rating_score", Double.NaN);  // NaN value (invalid)
        ratingData.put("rating", 0.9);               // Valid fallback

        RatingFieldExtractor.ExtractionResult result = RatingFieldExtractor.extractRatingScore(ratingData);

        assertTrue("Should skip NaN value and use next", result.isSuccess());
        assertEquals("Should use fallback field", Double.valueOf(0.9), result.getRatingScore());
        assertEquals("Should use rating field", "rating", result.getFieldUsed());
    }

    // Helper method to create rating data with a single field
    private Map<String, Object> createRatingData(String fieldName, Object value) {
        Map<String, Object> data = new HashMap<>();
        data.put(fieldName, value);
        return data;
    }
}
