/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */
package org.opensearch.searchrelevance.common;

import org.opensearch.test.OpenSearchTestCase;

/**
 * Tests for RatingOutputProcessor with OpenAI structured output.
 * These tests focus on parsing properly formatted JSON responses from OpenAI's structured output feature.
 */
public class RatingOutputProcessorTests extends OpenSearchTestCase {

    // ============================================
    // Structured Output Format Tests
    // ============================================

    public void testSanitizeLLMResponse_StructuredOutputWithRatingsArray() {
        String response = "{\"ratings\": [{\"id\": \"1\", \"rating_score\": 4}, {\"id\": \"2\", \"rating_score\": 3}]}";
        String sanitized = RatingOutputProcessor.sanitizeLLMResponse(response);

        assertTrue(sanitized.startsWith("["));
        assertTrue(sanitized.endsWith("]"));
        assertTrue(sanitized.contains("\"id\":\"1\"") || sanitized.contains("\"id\": \"1\""));
        assertTrue(sanitized.contains("\"rating_score\":4") || sanitized.contains("\"rating_score\": 4"));
    }

    public void testSanitizeLLMResponse_StructuredOutputNumericRatings() {
        String response = "{\"ratings\": [{\"id\": \"doc1\", \"rating_score\": 0.75}]}";
        String sanitized = RatingOutputProcessor.sanitizeLLMResponse(response);

        assertTrue(sanitized.contains("0.75"));
        assertTrue(sanitized.contains("doc1"));
    }

    public void testSanitizeLLMResponse_StructuredOutputBinaryRatings() {
        String response =
            "{\"ratings\": [{\"id\": \"1\", \"rating_score\": \"RELEVANT\"}, {\"id\": \"2\", \"rating_score\": \"IRRELEVANT\"}]}";
        String sanitized = RatingOutputProcessor.sanitizeLLMResponse(response);

        assertTrue(sanitized.contains("RELEVANT"));
        assertTrue(sanitized.contains("IRRELEVANT"));
    }

    // ============================================
    // Direct Array Format Tests
    // ============================================

    public void testSanitizeLLMResponse_DirectJsonArray() {
        String response = "[{\"id\": \"1\", \"rating_score\": 4}, {\"id\": \"2\", \"rating_score\": 3}]";
        String sanitized = RatingOutputProcessor.sanitizeLLMResponse(response);

        assertTrue(sanitized.startsWith("["));
        assertTrue(sanitized.endsWith("]"));
        assertTrue(sanitized.contains("\"id\":\"1\"") || sanitized.contains("\"id\": \"1\""));
    }

    public void testSanitizeLLMResponse_SingleObjectWrapping() {
        String response = "{\"id\": \"1\", \"rating_score\": 3}";
        String sanitized = RatingOutputProcessor.sanitizeLLMResponse(response);

        assertTrue(sanitized.startsWith("["));
        assertTrue(sanitized.endsWith("]"));
        assertTrue(sanitized.contains("\"rating_score\""));
    }

    // ============================================
    // Edge Cases
    // ============================================

    public void testSanitizeLLMResponse_EmptyString() {
        String response = "";
        String sanitized = RatingOutputProcessor.sanitizeLLMResponse(response);

        assertEquals("[]", sanitized);
    }

    public void testSanitizeLLMResponse_NullInput() {
        String sanitized = RatingOutputProcessor.sanitizeLLMResponse(null);

        assertEquals("[]", sanitized);
    }

    public void testSanitizeLLMResponse_InvalidJson() {
        String response = "This is not valid JSON";
        String sanitized = RatingOutputProcessor.sanitizeLLMResponse(response);

        assertEquals("[]", sanitized);
    }

    public void testSanitizeLLMResponse_MalformedJson() {
        String response = "{\"ratings\": [{\"id\": \"1\", \"rating_score\": }";
        String sanitized = RatingOutputProcessor.sanitizeLLMResponse(response);

        assertEquals("[]", sanitized);
    }

    // ============================================
    // Multiple Items Tests
    // ============================================

    public void testSanitizeLLMResponse_MultipleRatings() {
        String response = "{\"ratings\": ["
            + "{\"id\": \"1\", \"rating_score\": 5}, "
            + "{\"id\": \"2\", \"rating_score\": 4}, "
            + "{\"id\": \"3\", \"rating_score\": 3}"
            + "]}";
        String sanitized = RatingOutputProcessor.sanitizeLLMResponse(response);

        assertTrue(sanitized.contains("\"id\":\"1\"") || sanitized.contains("\"id\": \"1\""));
        assertTrue(sanitized.contains("\"id\":\"2\"") || sanitized.contains("\"id\": \"2\""));
        assertTrue(sanitized.contains("\"id\":\"3\"") || sanitized.contains("\"id\": \"3\""));
    }

    public void testSanitizeLLMResponse_MixedNumericRatings() {
        String response = "{\"ratings\": ["
            + "{\"id\": \"doc1\", \"rating_score\": 0.0}, "
            + "{\"id\": \"doc2\", \"rating_score\": 0.5}, "
            + "{\"id\": \"doc3\", \"rating_score\": 1.0}"
            + "]}";
        String sanitized = RatingOutputProcessor.sanitizeLLMResponse(response);

        assertTrue(sanitized.contains("0.0"));
        assertTrue(sanitized.contains("0.5"));
        assertTrue(sanitized.contains("1.0"));
    }

    // ============================================
    // Different Rating Types (all handled the same way now)
    // ============================================

    public void testSanitizeLLMResponse_NumericRating01() {
        String response = "{\"ratings\": [{\"id\": \"1\", \"rating_score\": 0.8}]}";
        String sanitized = RatingOutputProcessor.sanitizeLLMResponse(response);

        assertTrue(sanitized.contains("0.8"));
    }

    public void testSanitizeLLMResponse_NumericRating15() {
        String response = "{\"ratings\": [{\"id\": \"1\", \"rating_score\": 4.5}]}";
        String sanitized = RatingOutputProcessor.sanitizeLLMResponse(response);

        assertTrue(sanitized.contains("4.5"));
    }

    public void testSanitizeLLMResponse_BinaryRating() {
        String response = "{\"ratings\": [{\"id\": \"1\", \"rating_score\": \"RELEVANT\"}]}";
        String sanitized = RatingOutputProcessor.sanitizeLLMResponse(response);

        assertTrue(sanitized.contains("RELEVANT"));
    }

    // ============================================
    // Special Characters and IDs Tests
    // ============================================

    public void testSanitizeLLMResponse_SpecialCharactersInId() {
        String response = "{\"ratings\": [{\"id\": \"test_products#123\", \"rating_score\": 4.5}]}";
        String sanitized = RatingOutputProcessor.sanitizeLLMResponse(response);

        assertTrue(sanitized.contains("test_products#123"));
        assertTrue(sanitized.contains("4.5"));
    }

    public void testSanitizeLLMResponse_LongIdStrings() {
        String response = "{\"ratings\": [{\"id\": \"very-long-document-identifier-with-multiple-segments-12345\", \"rating_score\": 3}]}";
        String sanitized = RatingOutputProcessor.sanitizeLLMResponse(response);

        assertTrue(sanitized.contains("very-long-document-identifier-with-multiple-segments-12345"));
    }

    // ============================================
    // Whitespace and Formatting Tests
    // ============================================

    public void testSanitizeLLMResponse_CompactJson() {
        String response = "{\"ratings\":[{\"id\":\"1\",\"rating_score\":5}]}";
        String sanitized = RatingOutputProcessor.sanitizeLLMResponse(response);

        assertTrue(sanitized.startsWith("["));
        assertTrue(sanitized.contains("\"id\""));
        assertTrue(sanitized.contains("\"rating_score\""));
    }

    public void testSanitizeLLMResponse_PrettyPrintedJson() {
        String response = "{\n  \"ratings\": [\n    {\n      \"id\": \"1\",\n      \"rating_score\": 4\n    }\n  ]\n}";
        String sanitized = RatingOutputProcessor.sanitizeLLMResponse(response);

        assertTrue(sanitized.contains("\"rating_score\""));
    }
}
