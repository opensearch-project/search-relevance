/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */
package org.opensearch.searchrelevance.common;

import org.opensearch.searchrelevance.model.LLMJudgmentRatingType;
import org.opensearch.test.OpenSearchTestCase;

public class RatingOutputProcessorTests extends OpenSearchTestCase {

    // ============================================
    // Basic Sanitization Tests (no rating type)
    // ============================================

    public void testSanitizeLLMResponse_ValidJsonArray() {
        String response = "[{\"id\": \"1\", \"rating_score\": 4}, {\"id\": \"2\", \"rating_score\": 3}]";
        String sanitized = RatingOutputProcessor.sanitizeLLMResponse(response);

        assertEquals("[{\"id\": \"1\", \"rating_score\": 4}, {\"id\": \"2\", \"rating_score\": 3}]", sanitized);
    }

    public void testSanitizeLLMResponse_WithMarkdownCodeBlocks() {
        String response = "```json\n[{\"id\": \"1\", \"rating_score\": 5}]\n```";
        String sanitized = RatingOutputProcessor.sanitizeLLMResponse(response);

        assertTrue(sanitized.contains("\"id\""));
        assertTrue(sanitized.contains("\"rating_score\""));
        assertTrue(sanitized.startsWith("["));
        assertTrue(sanitized.endsWith("]"));
    }

    public void testSanitizeLLMResponse_SingleObjectNeedsWrapping() {
        String response = "{\"id\": \"1\", \"rating_score\": 3}";
        String sanitized = RatingOutputProcessor.sanitizeLLMResponse(response);

        assertTrue(sanitized.startsWith("["));
        assertTrue(sanitized.endsWith("]"));
        assertTrue(sanitized.contains("\"id\": \"1\""));
    }

    public void testSanitizeLLMResponse_WithExplanationBeforeJson() {
        String response = "Here are the ratings:\n[{\"id\": \"1\", \"rating_score\": 4}]";
        String sanitized = RatingOutputProcessor.sanitizeLLMResponse(response);

        assertTrue(sanitized.startsWith("["));
        assertTrue(sanitized.contains("\"rating_score\": 4"));
    }

    public void testSanitizeLLMResponse_WithExplanationAndSingleObject() {
        String response = "Rating: {\"id\": \"1\", \"rating_score\": 5}";
        String sanitized = RatingOutputProcessor.sanitizeLLMResponse(response);

        assertTrue(sanitized.startsWith("["));
        assertTrue(sanitized.endsWith("]"));
        assertTrue(sanitized.contains("\"rating_score\": 5"));
    }

    public void testSanitizeLLMResponse_WithBackticksAndNewlines() {
        String response = "`\n[{\"id\": \"1\", \"rating_score\": 5}]\n`";
        String sanitized = RatingOutputProcessor.sanitizeLLMResponse(response);

        assertFalse(sanitized.contains("`"));
        assertTrue(sanitized.startsWith("["));
        assertTrue(sanitized.contains("\"rating_score\": 5"));
    }

    public void testSanitizeLLMResponse_EmptyString() {
        String response = "";
        String sanitized = RatingOutputProcessor.sanitizeLLMResponse(response);

        assertEquals("[]", sanitized);
    }

    public void testSanitizeLLMResponse_NullInput() {
        String sanitized = RatingOutputProcessor.sanitizeLLMResponse(null);

        assertEquals("[]", sanitized);
    }

    public void testSanitizeLLMResponse_NoValidJson() {
        String response = "The document is relevant with a rating of 5.0";
        String sanitized = RatingOutputProcessor.sanitizeLLMResponse(response);

        assertEquals("[]", sanitized);
    }

    public void testSanitizeLLMResponse_WithExtraWhitespace() {
        String response = "  \n  [{\"id\": \"1\", \"rating_score\": 4}]  \n  ";
        String sanitized = RatingOutputProcessor.sanitizeLLMResponse(response);

        assertTrue(sanitized.startsWith("["));
        assertTrue(sanitized.endsWith("]"));
        assertFalse(sanitized.contains("\n"));
    }

    public void testSanitizeLLMResponse_NestedArrayInText() {
        String response = "The ratings are: [{\"id\": \"doc1\", \"rating_score\": 3.5}] and that's all.";
        String sanitized = RatingOutputProcessor.sanitizeLLMResponse(response);

        assertTrue(sanitized.startsWith("["));
        assertTrue(sanitized.endsWith("]"));
        assertTrue(sanitized.contains("\"doc1\""));
        assertFalse(sanitized.contains("that's all"));
    }

    public void testSanitizeLLMResponse_MultipleObjects() {
        String response =
            "[{\"id\": \"1\", \"rating_score\": 5}, {\"id\": \"2\", \"rating_score\": 4}, {\"id\": \"3\", \"rating_score\": 3}]";
        String sanitized = RatingOutputProcessor.sanitizeLLMResponse(response);

        assertTrue(sanitized.contains("\"id\": \"1\""));
        assertTrue(sanitized.contains("\"id\": \"2\""));
        assertTrue(sanitized.contains("\"id\": \"3\""));
    }

    public void testSanitizeLLMResponse_WithFloatingPointScores() {
        String response = "[{\"id\": \"test_products#1\", \"rating_score\": 4.5}]";
        String sanitized = RatingOutputProcessor.sanitizeLLMResponse(response);

        assertTrue(sanitized.contains("4.5"));
        assertTrue(sanitized.contains("test_products#1"));
    }

    public void testSanitizeLLMResponse_ObjectWithoutArray() {
        String response = "{\"id\": \"product_1\", \"rating_score\": 2}";
        String sanitized = RatingOutputProcessor.sanitizeLLMResponse(response);

        // Should wrap the object in an array
        assertTrue(sanitized.startsWith("[{"));
        assertTrue(sanitized.endsWith("}]"));
        assertTrue(sanitized.contains("product_1"));
    }

    // ============================================
    // SCORE0_1 Rating Type Tests
    // ============================================

    public void testSanitizeLLMResponse_Score01_ValidRatings() {
        String response = "[{\"id\": \"1\", \"rating_score\": 0.5}, {\"id\": \"2\", \"rating_score\": 0.8}]";
        String sanitized = RatingOutputProcessor.sanitizeLLMResponse(response, LLMJudgmentRatingType.SCORE0_1);

        assertTrue(sanitized.contains("\"rating_score\": 0.5"));
        assertTrue(sanitized.contains("\"rating_score\": 0.8"));
    }

    public void testSanitizeLLMResponse_Score01_RatingsAboveMax() {
        String response = "[{\"id\": \"1\", \"rating_score\": 1.5}, {\"id\": \"2\", \"rating_score\": 2.0}]";
        String sanitized = RatingOutputProcessor.sanitizeLLMResponse(response, LLMJudgmentRatingType.SCORE0_1);

        // Should clamp to 1.0
        assertTrue(sanitized.contains("\"rating_score\": 1"));
        assertFalse(sanitized.contains("1.5"));
        assertFalse(sanitized.contains("2.0"));
    }

    public void testSanitizeLLMResponse_Score01_RatingsBelowMin() {
        String response = "[{\"id\": \"1\", \"rating_score\": -0.5}, {\"id\": \"2\", \"rating_score\": -1.0}]";
        String sanitized = RatingOutputProcessor.sanitizeLLMResponse(response, LLMJudgmentRatingType.SCORE0_1);

        // Should clamp to 0.0
        assertTrue(sanitized.contains("\"rating_score\": 0"));
        assertFalse(sanitized.contains("-0.5"));
        assertFalse(sanitized.contains("-1.0"));
    }

    public void testSanitizeLLMResponse_Score01_ExactBoundaries() {
        String response = "[{\"id\": \"1\", \"rating_score\": 0.0}, {\"id\": \"2\", \"rating_score\": 1.0}]";
        String sanitized = RatingOutputProcessor.sanitizeLLMResponse(response, LLMJudgmentRatingType.SCORE0_1);

        assertTrue(sanitized.contains("\"rating_score\": 0"));
        assertTrue(sanitized.contains("\"rating_score\": 1"));
    }

    // ============================================
    // SCORE1_5 Rating Type Tests
    // ============================================

    public void testSanitizeLLMResponse_Score15_ValidRatings() {
        String response = "[{\"id\": \"1\", \"rating_score\": 3}, {\"id\": \"2\", \"rating_score\": 4.5}]";
        String sanitized = RatingOutputProcessor.sanitizeLLMResponse(response, LLMJudgmentRatingType.SCORE1_5);

        assertTrue(sanitized.contains("\"rating_score\": 3"));
        assertTrue(sanitized.contains("\"rating_score\": 4.5"));
    }

    public void testSanitizeLLMResponse_Score15_RatingsAboveMax() {
        String response = "[{\"id\": \"1\", \"rating_score\": 6}, {\"id\": \"2\", \"rating_score\": 10}]";
        String sanitized = RatingOutputProcessor.sanitizeLLMResponse(response, LLMJudgmentRatingType.SCORE1_5);

        // Should clamp to 5
        assertTrue(sanitized.contains("\"rating_score\": 5"));
        assertFalse(sanitized.contains("\"rating_score\": 6"));
        assertFalse(sanitized.contains("\"rating_score\": 10"));
    }

    public void testSanitizeLLMResponse_Score15_RatingsBelowMin() {
        String response = "[{\"id\": \"1\", \"rating_score\": 0}, {\"id\": \"2\", \"rating_score\": -1}]";
        String sanitized = RatingOutputProcessor.sanitizeLLMResponse(response, LLMJudgmentRatingType.SCORE1_5);

        // Should clamp to 1
        assertTrue(sanitized.contains("\"rating_score\": 1"));
        assertFalse(sanitized.contains("\"rating_score\": 0"));
        assertFalse(sanitized.contains("\"rating_score\": -1"));
    }

    public void testSanitizeLLMResponse_Score15_ExactBoundaries() {
        String response = "[{\"id\": \"1\", \"rating_score\": 1}, {\"id\": \"2\", \"rating_score\": 5}]";
        String sanitized = RatingOutputProcessor.sanitizeLLMResponse(response, LLMJudgmentRatingType.SCORE1_5);

        assertTrue(sanitized.contains("\"rating_score\": 1"));
        assertTrue(sanitized.contains("\"rating_score\": 5"));
    }

    // ============================================
    // RELEVANT_IRRELEVANT Rating Type Tests
    // ============================================

    public void testSanitizeLLMResponse_Binary_ValidRelevant() {
        String response = "[{\"id\": \"1\", \"rating_score\": \"RELEVANT\"}]";
        String sanitized = RatingOutputProcessor.sanitizeLLMResponse(response, LLMJudgmentRatingType.RELEVANT_IRRELEVANT);

        assertTrue(sanitized.contains("\"rating_score\": \"RELEVANT\""));
    }

    public void testSanitizeLLMResponse_Binary_ValidIrrelevant() {
        String response = "[{\"id\": \"1\", \"rating_score\": \"IRRELEVANT\"}]";
        String sanitized = RatingOutputProcessor.sanitizeLLMResponse(response, LLMJudgmentRatingType.RELEVANT_IRRELEVANT);

        assertTrue(sanitized.contains("\"rating_score\": \"IRRELEVANT\""));
    }

    public void testSanitizeLLMResponse_Binary_LowercaseRelevant() {
        String response = "[{\"id\": \"1\", \"rating_score\": \"relevant\"}]";
        String sanitized = RatingOutputProcessor.sanitizeLLMResponse(response, LLMJudgmentRatingType.RELEVANT_IRRELEVANT);

        assertTrue(sanitized.contains("\"rating_score\": \"RELEVANT\""));
    }

    public void testSanitizeLLMResponse_Binary_TrueValue() {
        String response = "[{\"id\": \"1\", \"rating_score\": \"true\"}]";
        String sanitized = RatingOutputProcessor.sanitizeLLMResponse(response, LLMJudgmentRatingType.RELEVANT_IRRELEVANT);

        assertTrue(sanitized.contains("\"rating_score\": \"RELEVANT\""));
    }

    public void testSanitizeLLMResponse_Binary_NumericOne() {
        String response = "[{\"id\": \"1\", \"rating_score\": 1}]";
        String sanitized = RatingOutputProcessor.sanitizeLLMResponse(response, LLMJudgmentRatingType.RELEVANT_IRRELEVANT);

        assertTrue(sanitized.contains("\"rating_score\": \"RELEVANT\""));
    }

    public void testSanitizeLLMResponse_Binary_FalseValue() {
        String response = "[{\"id\": \"1\", \"rating_score\": \"false\"}]";
        String sanitized = RatingOutputProcessor.sanitizeLLMResponse(response, LLMJudgmentRatingType.RELEVANT_IRRELEVANT);

        assertTrue(sanitized.contains("\"rating_score\": \"IRRELEVANT\""));
    }

    public void testSanitizeLLMResponse_Binary_NumericZero() {
        String response = "[{\"id\": \"1\", \"rating_score\": 0}]";
        String sanitized = RatingOutputProcessor.sanitizeLLMResponse(response, LLMJudgmentRatingType.RELEVANT_IRRELEVANT);

        assertTrue(sanitized.contains("\"rating_score\": \"IRRELEVANT\""));
    }

    public void testSanitizeLLMResponse_Binary_UnrecognizedValue() {
        String response = "[{\"id\": \"1\", \"rating_score\": \"maybe\"}]";
        String sanitized = RatingOutputProcessor.sanitizeLLMResponse(response, LLMJudgmentRatingType.RELEVANT_IRRELEVANT);

        // Should default to IRRELEVANT
        assertTrue(sanitized.contains("\"rating_score\": \"IRRELEVANT\""));
    }

    public void testSanitizeLLMResponse_Binary_MixedValues() {
        String response =
            "[{\"id\": \"1\", \"rating_score\": \"RELEVANT\"}, {\"id\": \"2\", \"rating_score\": \"irrelevant\"}, {\"id\": \"3\", \"rating_score\": 1}]";
        String sanitized = RatingOutputProcessor.sanitizeLLMResponse(response, LLMJudgmentRatingType.RELEVANT_IRRELEVANT);

        // Check that all three are normalized correctly
        int relevantCount = sanitized.split("\"rating_score\": \"RELEVANT\"").length - 1;
        int irrelevantCount = sanitized.split("\"rating_score\": \"IRRELEVANT\"").length - 1;

        assertEquals(2, relevantCount); // "RELEVANT" and 1
        assertEquals(1, irrelevantCount); // "irrelevant"
    }

    // ============================================
    // Edge Cases with Rating Type Validation
    // ============================================

    public void testSanitizeLLMResponse_NullRatingType() {
        String response = "[{\"id\": \"1\", \"rating_score\": 10}]";
        String sanitized = RatingOutputProcessor.sanitizeLLMResponse(response, null);

        // Should not validate, just sanitize
        assertTrue(sanitized.contains("\"rating_score\": 10"));
    }

    public void testSanitizeLLMResponse_EmptyResponseWithRatingType() {
        String sanitized = RatingOutputProcessor.sanitizeLLMResponse("", LLMJudgmentRatingType.SCORE1_5);

        assertEquals("[]", sanitized);
    }

    public void testSanitizeLLMResponse_MarkdownWithValidation() {
        String response = "```json\n[{\"id\": \"1\", \"rating_score\": 10}]\n```";
        String sanitized = RatingOutputProcessor.sanitizeLLMResponse(response, LLMJudgmentRatingType.SCORE1_5);

        // Should sanitize markdown AND clamp rating
        assertFalse(sanitized.contains("```"));
        assertTrue(sanitized.contains("\"rating_score\": 5"));
        assertFalse(sanitized.contains("\"rating_score\": 10"));
    }
}
