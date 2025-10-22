/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */
package org.opensearch.searchrelevance.util;

import java.util.List;

import org.opensearch.searchrelevance.plugin.SearchRelevanceRestTestCase;
import org.opensearch.searchrelevance.utils.TextValidationUtil;

public class TextValidationUtilTests extends SearchRelevanceRestTestCase {

    public void testNullText() {
        TextValidationUtil.ValidationResult result = TextValidationUtil.validateText(null);
        assertFalse(result.isValid());
        assertEquals("Text cannot be null", result.getErrorMessage());
    }

    public void testEmptyText() {
        TextValidationUtil.ValidationResult result = TextValidationUtil.validateText("");
        assertFalse(result.isValid());
        assertEquals("Text cannot be empty", result.getErrorMessage());
    }

    public void testTextTooLong() {
        String longText = "a".repeat(2001);
        TextValidationUtil.ValidationResult result = TextValidationUtil.validateText(longText);
        assertFalse(result.isValid());
        assertEquals("Text exceeds maximum length of 2000 characters", result.getErrorMessage());
    }

    public void testValidText() {
        List<String> inputs = List.of(
            "Hello, World!",
            "Test_123",
            "What's up?",
            "OpenSearch-2.0",
            "#hashtag",
            "user@domain",
            "some_variable_name",
            "Path/to/file",
            "[bracket]",
            "(parenthesis)",
            "{curly}",
            "100%",
            "$price",
            "value=123",
            "a+b",
            "item1;item2",
            "key:value"
        );
        for (String input : inputs) {
            TextValidationUtil.ValidationResult result = TextValidationUtil.validateText(input);
            assertTrue(result.isValid());
            assertNull(result.getErrorMessage());
        }
    }

    public void testInvalidCharacters() {
        List<String> inputs = List.of(
            "Invalid\"quote",
            "Invalid\\backslash",
            "Invalid<tag>",
            "Invalid>arrow",
            "String with \"quotes\"",
            "Path\\to\\file"
        );
        for (String input : inputs) {
            TextValidationUtil.ValidationResult result = TextValidationUtil.validateText(input);
            assertFalse(result.isValid());
            assertEquals("Text contains invalid characters (quotes, backslashes, or HTML tags are not allowed)", result.getErrorMessage());
        }
    }

    public void testMaximumLengthText() {
        String maxLengthText = "a".repeat(2000);
        TextValidationUtil.ValidationResult result = TextValidationUtil.validateText(maxLengthText);
        assertTrue(result.isValid());
        assertNull(result.getErrorMessage());
    }

    public void testValidateWithCustomLength() {
        String text = "a".repeat(100);
        TextValidationUtil.ValidationResult result = TextValidationUtil.validateText(text, 50);
        assertFalse(result.isValid());
        assertEquals("Text exceeds maximum length of 50 characters", result.getErrorMessage());

        result = TextValidationUtil.validateText(text, 200);
        assertTrue(result.isValid());
        assertNull(result.getErrorMessage());
    }

    public void testValidateName() {
        String validName = "a".repeat(50);
        TextValidationUtil.ValidationResult result = TextValidationUtil.validateName(validName);
        assertTrue(result.isValid());
        assertNull(result.getErrorMessage());

        String invalidName = "a".repeat(51);
        result = TextValidationUtil.validateName(invalidName);
        assertFalse(result.isValid());
        assertEquals("Text exceeds maximum length of 50 characters", result.getErrorMessage());
    }

    public void testValidateDescription() {
        String validDesc = "a".repeat(250);
        TextValidationUtil.ValidationResult result = TextValidationUtil.validateDescription(validDesc);
        assertTrue(result.isValid());
        assertNull(result.getErrorMessage());

        String invalidDesc = "a".repeat(251);
        result = TextValidationUtil.validateDescription(invalidDesc);
        assertFalse(result.isValid());
        assertEquals("Text exceeds maximum length of 250 characters", result.getErrorMessage());
    }

    // ============================================
    // QuerySet Value Validation Tests
    // ============================================

    public void testValidateQuerySetValue_ValidValues() {
        // Test valid values that don't contain reserved characters
        List<String> validValues = List.of(
            "What is OpenSearch?",
            "red shoes",
            "High quality leather shoes",
            "OpenSearch is a search and analytics suite",
            "Category footwear",
            "Expected score 0.95",
            "user@example.com",
            "path/to/resource",
            "100%",
            "$price",
            "value=123",
            "a+b",
            "item1;item2"
        );

        for (String value : validValues) {
            TextValidationUtil.ValidationResult result = TextValidationUtil.validateQuerySetValue(value);
            assertTrue("Value should be valid: " + value, result.isValid());
            assertNull("Error message should be null for valid value: " + value, result.getErrorMessage());
        }
    }

    public void testValidateQuerySetValue_ReservedCharacter_Newline() {
        // Test that newline character is rejected
        String valueWithNewline = "text with\nnewline";
        TextValidationUtil.ValidationResult result = TextValidationUtil.validateQuerySetValue(valueWithNewline);
        assertFalse("Value with newline should be invalid", result.isValid());
        assertEquals("Text contains reserved characters (newline, #, or : are not allowed in QuerySet values)", result.getErrorMessage());
    }

    public void testValidateQuerySetValue_ReservedCharacter_Hash() {
        // Test that hash character is rejected
        String valueWithHash = "text with # hash";
        TextValidationUtil.ValidationResult result = TextValidationUtil.validateQuerySetValue(valueWithHash);
        assertFalse("Value with # should be invalid", result.isValid());
        assertEquals("Text contains reserved characters (newline, #, or : are not allowed in QuerySet values)", result.getErrorMessage());
    }

    public void testValidateQuerySetValue_ReservedCharacter_Colon() {
        // Test that colon character is rejected
        String valueWithColon = "text with: colon";
        TextValidationUtil.ValidationResult result = TextValidationUtil.validateQuerySetValue(valueWithColon);
        assertFalse("Value with : should be invalid", result.isValid());
        assertEquals("Text contains reserved characters (newline, #, or : are not allowed in QuerySet values)", result.getErrorMessage());
    }

    public void testValidateQuerySetValue_MultipleReservedCharacters() {
        // Test values with multiple reserved characters
        List<String> invalidValues = List.of(
            "query#text",
            "key: value",
            "line1\nline2",
            "query#\nkey: value",
            "text#with:multiple\nreserved"
        );

        for (String value : invalidValues) {
            TextValidationUtil.ValidationResult result = TextValidationUtil.validateQuerySetValue(value);
            assertFalse("Value should be invalid: " + value, result.isValid());
            assertEquals(
                "Text contains reserved characters (newline, #, or : are not allowed in QuerySet values)",
                result.getErrorMessage()
            );
        }
    }

    public void testValidateQuerySetValue_NullAndEmpty() {
        // Test null value
        TextValidationUtil.ValidationResult result = TextValidationUtil.validateQuerySetValue(null);
        assertFalse(result.isValid());
        assertEquals("Text cannot be null", result.getErrorMessage());

        // Test empty value
        result = TextValidationUtil.validateQuerySetValue("");
        assertFalse(result.isValid());
        assertEquals("Text cannot be empty", result.getErrorMessage());
    }

    public void testValidateQuerySetValue_DangerousCharacters() {
        // Test that dangerous characters are still caught
        List<String> dangerousValues = List.of("text with \"quotes\"", "text with \\backslash", "text with <tag>");

        for (String value : dangerousValues) {
            TextValidationUtil.ValidationResult result = TextValidationUtil.validateQuerySetValue(value);
            assertFalse("Value with dangerous char should be invalid: " + value, result.isValid());
            assertTrue(
                "Error should mention dangerous characters",
                result.getErrorMessage().contains("invalid characters (quotes, backslashes, or HTML tags")
            );
        }
    }

    public void testValidateQuerySetValue_MaxLength() {
        String validValue = "a".repeat(2000);
        TextValidationUtil.ValidationResult result = TextValidationUtil.validateQuerySetValue(validValue);
        assertTrue(result.isValid());
        assertNull(result.getErrorMessage());

        String invalidValue = "a".repeat(2001);
        result = TextValidationUtil.validateQuerySetValue(invalidValue);
        assertFalse(result.isValid());
        assertEquals("Text exceeds maximum length of 2000 characters", result.getErrorMessage());
    }

    // ============================================
    // QuerySet Key Validation Tests
    // ============================================

    public void testValidateQuerySetKey_ValidKeys() {
        // Test valid keys
        List<String> validKeys = List.of(
            "referenceAnswer",
            "category",
            "brand",
            "price",
            "expectedScore",
            "productCategory",
            "targetAudience",
            "priceRange",
            "color",
            "size",
            "metadata"
        );

        for (String key : validKeys) {
            TextValidationUtil.ValidationResult result = TextValidationUtil.validateQuerySetKey(key);
            assertTrue("Key should be valid: " + key, result.isValid());
            assertNull("Error message should be null for valid key: " + key, result.getErrorMessage());
        }
    }

    public void testValidateQuerySetKey_ReservedKeyName() {
        // Test that "queryText" is a reserved key name
        String reservedKey = "queryText";
        TextValidationUtil.ValidationResult result = TextValidationUtil.validateQuerySetKey(reservedKey);
        assertFalse("'queryText' should be a reserved key", result.isValid());
        assertEquals("Key 'queryText' is reserved and cannot be used as a custom field name", result.getErrorMessage());
    }

    public void testValidateQuerySetKey_ReservedCharacters() {
        // Test keys with reserved characters
        List<String> invalidKeys = List.of("key#with#hash", "key:with:colon", "key\nwith\nnewline", "key#with:multiple\nreserved");

        for (String key : invalidKeys) {
            TextValidationUtil.ValidationResult result = TextValidationUtil.validateQuerySetKey(key);
            assertFalse("Key with reserved char should be invalid: " + key, result.isValid());
            assertEquals("Key contains reserved characters (newline, #, or : are not allowed in QuerySet keys)", result.getErrorMessage());
        }
    }

    public void testValidateQuerySetKey_LeadingTrailingWhitespace() {
        // Test keys with leading/trailing whitespace
        List<String> keysWithWhitespace = List.of(" leadingSpace", "trailingSpace ", " both ", "\tkey", "key\t");

        for (String key : keysWithWhitespace) {
            TextValidationUtil.ValidationResult result = TextValidationUtil.validateQuerySetKey(key);
            assertFalse("Key with whitespace should be invalid: '" + key + "'", result.isValid());
            assertEquals("Key cannot have leading or trailing whitespace", result.getErrorMessage());
        }
    }

    public void testValidateQuerySetKey_ValidWithInternalWhitespace() {
        // Test that keys can have internal whitespace
        String keyWithSpace = "expected score";
        TextValidationUtil.ValidationResult result = TextValidationUtil.validateQuerySetKey(keyWithSpace);
        assertTrue("Key with internal whitespace should be valid", result.isValid());
        assertNull(result.getErrorMessage());
    }

    public void testValidateQuerySetKey_NullAndEmpty() {
        // Test null key
        TextValidationUtil.ValidationResult result = TextValidationUtil.validateQuerySetKey(null);
        assertFalse(result.isValid());
        assertEquals("Key cannot be null", result.getErrorMessage());

        // Test empty key
        result = TextValidationUtil.validateQuerySetKey("");
        assertFalse(result.isValid());
        assertEquals("Key cannot be empty", result.getErrorMessage());
    }

    public void testValidateQuerySetKey_MaxLength() {
        String validKey = "a".repeat(50);
        TextValidationUtil.ValidationResult result = TextValidationUtil.validateQuerySetKey(validKey);
        assertTrue(result.isValid());
        assertNull(result.getErrorMessage());

        String invalidKey = "a".repeat(51);
        result = TextValidationUtil.validateQuerySetKey(invalidKey);
        assertFalse(result.isValid());
        assertEquals("Key exceeds maximum length of 50 characters", result.getErrorMessage());
    }

    // ============================================
    // Integration Test: Validation Flow
    // ============================================

    public void testQuerySetValidation_CompleteFlow() {
        // Simulate a complete QuerySet entry validation
        String queryText = "What is OpenSearch?";
        String referenceAnswerKey = "referenceAnswer";
        String referenceAnswerValue = "OpenSearch is a search and analytics suite";
        String categoryKey = "category";
        String categoryValue = "technology";

        // Validate queryText
        TextValidationUtil.ValidationResult result = TextValidationUtil.validateQuerySetValue(queryText);
        assertTrue("QueryText should be valid", result.isValid());

        // Validate referenceAnswer key
        result = TextValidationUtil.validateQuerySetKey(referenceAnswerKey);
        assertTrue("ReferenceAnswer key should be valid", result.isValid());

        // Validate referenceAnswer value
        result = TextValidationUtil.validateQuerySetValue(referenceAnswerValue);
        assertTrue("ReferenceAnswer value should be valid", result.isValid());

        // Validate category key
        result = TextValidationUtil.validateQuerySetKey(categoryKey);
        assertTrue("Category key should be valid", result.isValid());

        // Validate category value
        result = TextValidationUtil.validateQuerySetValue(categoryValue);
        assertTrue("Category value should be valid", result.isValid());
    }

    public void testQuerySetValidation_InvalidScenarios() {
        // Test invalid queryText with reserved character
        String invalidQueryText = "query#with#hash";
        TextValidationUtil.ValidationResult result = TextValidationUtil.validateQuerySetValue(invalidQueryText);
        assertFalse("QueryText with # should be invalid", result.isValid());

        // Test invalid key name (reserved)
        result = TextValidationUtil.validateQuerySetKey("queryText");
        assertFalse("Reserved key 'queryText' should be invalid", result.isValid());

        // Test invalid value with colon
        String invalidValue = "value: with colon";
        result = TextValidationUtil.validateQuerySetValue(invalidValue);
        assertFalse("Value with : should be invalid", result.isValid());

        // Test invalid key with newline
        String invalidKey = "key\nwith\nnewline";
        result = TextValidationUtil.validateQuerySetKey(invalidKey);
        assertFalse("Key with newline should be invalid", result.isValid());
    }
}
