/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */
package org.opensearch.searchrelevance.judgments;

import org.opensearch.searchrelevance.model.LLMJudgmentRatingType;
import org.opensearch.searchrelevance.utils.RatingOutputProcessor;
import org.opensearch.test.OpenSearchTestCase;

/**
 * Unit tests for RatingOutputProcessor's convertRatingScore method.
 * These tests verify the conversion logic for different rating types.
 */
public class LlmJudgmentsProcessorRatingConversionTests extends OpenSearchTestCase {

    /**
     * Helper method to call the convertRatingScore method
     */
    private Double invokeConvertRatingScore(Object ratingScoreObj, LLMJudgmentRatingType ratingType) {
        return RatingOutputProcessor.convertRatingScore(ratingScoreObj, ratingType);
    }

    // ============================================
    // SCORE0_1 Rating Type Tests
    // ============================================

    /**
     * Test convertRatingScore for SCORE0_1 with Double input
     */
    public void testConvertRatingScore_SCORE0_1_WithDouble() throws Exception {
        Double result = invokeConvertRatingScore(0.9, LLMJudgmentRatingType.SCORE0_1);
        assertEquals("Should convert Double 0.9 correctly", 0.9, result, 0.0001);
    }

    /**
     * Test convertRatingScore for SCORE0_1 with Integer input
     */
    public void testConvertRatingScore_SCORE0_1_WithInteger() throws Exception {
        Double result = invokeConvertRatingScore(1, LLMJudgmentRatingType.SCORE0_1);
        assertEquals("Should convert Integer 1 to 1.0", 1.0, result, 0.0001);
    }

    /**
     * Test convertRatingScore for SCORE0_1 with Float input
     */
    public void testConvertRatingScore_SCORE0_1_WithFloat() throws Exception {
        Double result = invokeConvertRatingScore(0.75f, LLMJudgmentRatingType.SCORE0_1);
        assertEquals("Should convert Float 0.75 correctly", 0.75, result, 0.0001);
    }

    /**
     * Test convertRatingScore for SCORE0_1 with boundary values
     */
    public void testConvertRatingScore_SCORE0_1_BoundaryValues() throws Exception {
        // Minimum value
        Double min = invokeConvertRatingScore(0.0, LLMJudgmentRatingType.SCORE0_1);
        assertEquals("Should handle 0.0", 0.0, min, 0.0001);

        // Maximum value
        Double max = invokeConvertRatingScore(1.0, LLMJudgmentRatingType.SCORE0_1);
        assertEquals("Should handle 1.0", 1.0, max, 0.0001);

        // Mid value
        Double mid = invokeConvertRatingScore(0.5, LLMJudgmentRatingType.SCORE0_1);
        assertEquals("Should handle 0.5", 0.5, mid, 0.0001);
    }

    /**
     * Test convertRatingScore for SCORE0_1 with various numeric types
     */
    public void testConvertRatingScore_SCORE0_1_VariousNumericTypes() throws Exception {
        // Long
        Double fromLong = invokeConvertRatingScore(1L, LLMJudgmentRatingType.SCORE0_1);
        assertEquals("Should convert Long", 1.0, fromLong, 0.0001);

        // Short
        Short shortVal = 0;
        Double fromShort = invokeConvertRatingScore(shortVal, LLMJudgmentRatingType.SCORE0_1);
        assertEquals("Should convert Short", 0.0, fromShort, 0.0001);

        // Byte
        Byte byteVal = 1;
        Double fromByte = invokeConvertRatingScore(byteVal, LLMJudgmentRatingType.SCORE0_1);
        assertEquals("Should convert Byte", 1.0, fromByte, 0.0001);
    }

    // ============================================
    // RELEVANT_IRRELEVANT Rating Type Tests
    // ============================================

    /**
     * Test convertRatingScore for RELEVANT_IRRELEVANT with "RELEVANT"
     */
    public void testConvertRatingScore_RELEVANT_IRRELEVANT_Relevant() throws Exception {
        Double result = invokeConvertRatingScore("RELEVANT", LLMJudgmentRatingType.RELEVANT_IRRELEVANT);
        assertEquals("RELEVANT should convert to 1.0", 1.0, result, 0.0001);
    }

    /**
     * Test convertRatingScore for RELEVANT_IRRELEVANT with "IRRELEVANT"
     */
    public void testConvertRatingScore_RELEVANT_IRRELEVANT_Irrelevant() throws Exception {
        Double result = invokeConvertRatingScore("IRRELEVANT", LLMJudgmentRatingType.RELEVANT_IRRELEVANT);
        assertEquals("IRRELEVANT should convert to 0.0", 0.0, result, 0.0001);
    }

    /**
     * Test convertRatingScore for RELEVANT_IRRELEVANT with invalid value
     */
    public void testConvertRatingScore_RELEVANT_IRRELEVANT_InvalidValue() {
        IllegalArgumentException exception = expectThrows(IllegalArgumentException.class, () -> {
            invokeConvertRatingScore("MAYBE", LLMJudgmentRatingType.RELEVANT_IRRELEVANT);
        });

        assertTrue("Error message should mention invalid value", exception.getMessage().contains("Invalid binary rating value"));
        assertTrue("Error message should mention MAYBE", exception.getMessage().contains("MAYBE"));
    }

    /**
     * Test convertRatingScore for RELEVANT_IRRELEVANT with case-sensitive values
     */
    public void testConvertRatingScore_RELEVANT_IRRELEVANT_CaseSensitive() {
        // Lowercase "relevant" should fail (case-sensitive)
        IllegalArgumentException lowercase = expectThrows(IllegalArgumentException.class, () -> {
            invokeConvertRatingScore("relevant", LLMJudgmentRatingType.RELEVANT_IRRELEVANT);
        });
        assertNotNull("Lowercase should throw exception", lowercase);

        // Mixed case should fail
        IllegalArgumentException mixedCase = expectThrows(IllegalArgumentException.class, () -> {
            invokeConvertRatingScore("Relevant", LLMJudgmentRatingType.RELEVANT_IRRELEVANT);
        });
        assertNotNull("Mixed case should throw exception", mixedCase);
    }

    /**
     * Test convertRatingScore for RELEVANT_IRRELEVANT with null value
     */
    public void testConvertRatingScore_RELEVANT_IRRELEVANT_NullValue() {
        Exception exception = expectThrows(
            Exception.class,
            () -> { invokeConvertRatingScore(null, LLMJudgmentRatingType.RELEVANT_IRRELEVANT); }
        );
        assertNotNull("Should throw exception for null", exception);
    }

    /**
     * Test convertRatingScore for RELEVANT_IRRELEVANT with numeric value (wrong type)
     */
    public void testConvertRatingScore_RELEVANT_IRRELEVANT_WrongType() {
        Exception exception = expectThrows(
            Exception.class,
            () -> { invokeConvertRatingScore(1.0, LLMJudgmentRatingType.RELEVANT_IRRELEVANT); }
        );
        assertNotNull("Should throw exception for numeric value", exception);
    }

    // ============================================
    // Edge Cases and Error Handling
    // ============================================

    /**
     * Test convertRatingScore with null rating type
     * When ratingType is null, it falls through to the else clause and treats it as numeric (SCORE0_1)
     */
    public void testConvertRatingScore_NullRatingType() throws Exception {
        Double result = invokeConvertRatingScore(0.9, null);
        assertEquals("Null rating type should default to numeric conversion", 0.9, result, 0.0001);
    }

    /**
     * Test convertRatingScore for SCORE0_1 with null value
     */
    public void testConvertRatingScore_SCORE0_1_NullValue() {
        Exception exception = expectThrows(Exception.class, () -> { invokeConvertRatingScore(null, LLMJudgmentRatingType.SCORE0_1); });
        assertNotNull("Should throw exception for null value", exception);
    }

    /**
     * Test that SCORE0_1 accepts values outside 0-1 range (no validation)
     * Note: The method doesn't validate range, only converts the value
     */
    public void testConvertRatingScore_SCORE0_1_OutOfRangeValues() throws Exception {
        // Negative value
        Double negative = invokeConvertRatingScore(-0.5, LLMJudgmentRatingType.SCORE0_1);
        assertEquals("Should accept negative value", -0.5, negative, 0.0001);

        // Value greater than 1
        Double overOne = invokeConvertRatingScore(1.5, LLMJudgmentRatingType.SCORE0_1);
        assertEquals("Should accept value > 1", 1.5, overOne, 0.0001);

        // Large value
        Double large = invokeConvertRatingScore(100.0, LLMJudgmentRatingType.SCORE0_1);
        assertEquals("Should accept large value", 100.0, large, 0.0001);
    }

    // ============================================
    // Real-world Scenario Tests
    // ============================================

    /**
     * Test conversion with typical LLM responses for SCORE0_1
     */
    public void testConvertRatingScore_RealWorld_SCORE0_1() throws Exception {
        // LLM typically returns doubles between 0 and 1
        Double highRelevance = invokeConvertRatingScore(0.95, LLMJudgmentRatingType.SCORE0_1);
        assertEquals("High relevance score", 0.95, highRelevance, 0.0001);

        Double mediumRelevance = invokeConvertRatingScore(0.6, LLMJudgmentRatingType.SCORE0_1);
        assertEquals("Medium relevance score", 0.6, mediumRelevance, 0.0001);

        Double lowRelevance = invokeConvertRatingScore(0.2, LLMJudgmentRatingType.SCORE0_1);
        assertEquals("Low relevance score", 0.2, lowRelevance, 0.0001);

        Double noRelevance = invokeConvertRatingScore(0.0, LLMJudgmentRatingType.SCORE0_1);
        assertEquals("No relevance score", 0.0, noRelevance, 0.0001);
    }

    /**
     * Test conversion with typical LLM responses for RELEVANT_IRRELEVANT
     */
    public void testConvertRatingScore_RealWorld_RELEVANT_IRRELEVANT() throws Exception {
        // LLM returns "RELEVANT" or "IRRELEVANT" strings
        Double relevant = invokeConvertRatingScore("RELEVANT", LLMJudgmentRatingType.RELEVANT_IRRELEVANT);
        assertEquals("RELEVANT converts to 1.0", 1.0, relevant, 0.0001);

        Double irrelevant = invokeConvertRatingScore("IRRELEVANT", LLMJudgmentRatingType.RELEVANT_IRRELEVANT);
        assertEquals("IRRELEVANT converts to 0.0", 0.0, irrelevant, 0.0001);

        // Verify these can be directly used as rating strings
        assertEquals("1.0", relevant.toString());
        assertEquals("0.0", irrelevant.toString());
    }

    /**
     * Test that converted values can be properly used as strings
     */
    public void testConvertRatingScore_StringConversion() throws Exception {
        // SCORE0_1 to string
        Double score = invokeConvertRatingScore(0.85, LLMJudgmentRatingType.SCORE0_1);
        String scoreStr = score.toString();
        assertEquals("Should convert to string correctly", "0.85", scoreStr);

        // RELEVANT to string (should be "1.0")
        Double relevant = invokeConvertRatingScore("RELEVANT", LLMJudgmentRatingType.RELEVANT_IRRELEVANT);
        String relevantStr = relevant.toString();
        assertEquals("RELEVANT as string should be 1.0", "1.0", relevantStr);

        // IRRELEVANT to string (should be "0.0")
        Double irrelevant = invokeConvertRatingScore("IRRELEVANT", LLMJudgmentRatingType.RELEVANT_IRRELEVANT);
        String irrelevantStr = irrelevant.toString();
        assertEquals("IRRELEVANT as string should be 0.0", "0.0", irrelevantStr);
    }
}
