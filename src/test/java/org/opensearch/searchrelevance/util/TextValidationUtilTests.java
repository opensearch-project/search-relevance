/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */
package org.opensearch.searchrelevance.util;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

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

    // ============================================
    // Prompt Template Validation Tests
    // ============================================

    public void testValidatePromptTemplate_WithHitsPlaceholder() {
        // Test valid templates with {{hits}} placeholder and query placeholders
        List<String> validTemplates = List.of(
            "Query: {{queryText}}\n\nDocuments: {{hits}}",
            "Rate these documents: {{hits}}\nQuery: {{queryText}}",
            "Query: {{queryText}}\nCategory: {{category}}\nDocuments: {{hits}}",
            "{{queryText}} - {{hits}} - {{referenceAnswer}}",
            "Search: {{searchText}}\nResults: {{hits}}"
        );

        for (String template : validTemplates) {
            TextValidationUtil.ValidationResult result = TextValidationUtil.validatePromptTemplate(template);
            assertTrue("Template with {{hits}} should be valid: " + template, result.isValid());
            assertNull("Error message should be null for valid template", result.getErrorMessage());
        }
    }

    public void testValidatePromptTemplate_WithResultsPlaceholder() {
        // Test valid templates with {{results}} placeholder and query placeholders
        List<String> validTemplates = List.of(
            "Query: {{queryText}}\n\nDocuments: {{results}}",
            "Rate these documents: {{results}}\nQuery: {{queryText}}",
            "Query: {{queryText}}\nCategory: {{category}}\nDocuments: {{results}}",
            "Search: {{searchText}}\nDocs: {{results}}"
        );

        for (String template : validTemplates) {
            TextValidationUtil.ValidationResult result = TextValidationUtil.validatePromptTemplate(template);
            assertTrue("Template with {{results}} should be valid: " + template, result.isValid());
            assertNull("Error message should be null for valid template", result.getErrorMessage());
        }
    }

    public void testValidatePromptTemplate_NullOrEmpty() {
        // Null and empty templates are allowed (will use defaults)
        TextValidationUtil.ValidationResult result = TextValidationUtil.validatePromptTemplate(null);
        assertTrue("Null template should be valid (uses defaults)", result.isValid());
        assertNull(result.getErrorMessage());

        result = TextValidationUtil.validatePromptTemplate("");
        assertTrue("Empty template should be valid (uses defaults)", result.isValid());
        assertNull(result.getErrorMessage());

        result = TextValidationUtil.validatePromptTemplate("   ");
        assertTrue("Whitespace-only template should be valid (uses defaults)", result.isValid());
        assertNull(result.getErrorMessage());
    }

    public void testValidatePromptTemplate_MissingHitsPlaceholder() {
        // Test templates missing both {{hits}} and {{results}} placeholders
        List<String> invalidTemplates = List.of(
            "Query: {{queryText}}",
            "Rate relevance from 0.0 to 1.0\nQuery: {{queryText}}\nCategory: {{category}}",
            "{{queryText}} - {{referenceAnswer}}",
            "Query: {{query}}\nReference: {{reference}}"
        );

        for (String template : invalidTemplates) {
            TextValidationUtil.ValidationResult result = TextValidationUtil.validatePromptTemplate(template);
            assertFalse("Template without {{hits}} or {{results}} should be invalid: " + template, result.isValid());
            assertTrue(
                "Error should mention missing hits placeholder",
                result.getErrorMessage().contains("must include either {{hits}} or {{results}} placeholder")
            );
            assertTrue("Error should provide example", result.getErrorMessage().contains("Example:"));
        }
    }

    public void testValidatePromptTemplate_MissingQueryPlaceholder() {
        // Test templates missing queryText/searchText placeholders
        List<String> invalidTemplates = List.of(
            "Documents: {{hits}}",
            "Rate these documents: {{hits}}\nCategory: {{category}}",
            "{{hits}} - {{referenceAnswer}}",
            "Results: {{results}}\nReference: {{reference}}"
        );

        for (String template : invalidTemplates) {
            TextValidationUtil.ValidationResult result = TextValidationUtil.validatePromptTemplate(template);
            assertFalse("Template without query placeholder should be invalid: " + template, result.isValid());
            assertTrue(
                "Error should mention missing query placeholder",
                result.getErrorMessage().contains("must include either {{queryText}} or {{searchText}} placeholder")
            );
            assertTrue("Error should provide example", result.getErrorMessage().contains("Example:"));
        }
    }

    public void testValidatePromptTemplate_MissingBothPlaceholders() {
        // Test template missing both required placeholders
        String template = "Just some plain text without placeholders";
        TextValidationUtil.ValidationResult result = TextValidationUtil.validatePromptTemplate(template);
        assertFalse("Template without any placeholders should be invalid", result.isValid());
        // Should fail on the first check (hits/results)
        assertTrue(
            "Error should mention missing hits placeholder",
            result.getErrorMessage().contains("must include either {{hits}} or {{results}} placeholder")
        );
    }

    public void testValidatePromptTemplate_CaseSensitive() {
        // Test that placeholder matching is case-sensitive
        List<String> invalidTemplates = List.of(
            "Query: {{queryText}}\nDocuments: {{HITS}}",
            "Query: {{queryText}}\nDocuments: {{Hits}}",
            "Query: {{queryText}}\nDocuments: {{Results}}",
            "Query: {{queryText}}\nDocuments: {{RESULTS}}"
        );

        for (String template : invalidTemplates) {
            TextValidationUtil.ValidationResult result = TextValidationUtil.validatePromptTemplate(template);
            assertFalse("Case-sensitive: " + template + " should be invalid", result.isValid());
        }
    }

    public void testValidatePromptTemplate_BothPlaceholders() {
        // Test that template can have both {{hits}} and {{results}} (though unusual)
        String template = "Query: {{queryText}}\nPrimary: {{hits}}\nAlternate: {{results}}";
        TextValidationUtil.ValidationResult result = TextValidationUtil.validatePromptTemplate(template);
        assertTrue("Template with both hits and results placeholders should be valid", result.isValid());
        assertNull(result.getErrorMessage());
    }

    public void testValidatePromptTemplate_ContainsDelimiter() {
        // Test that template cannot contain the reserved delimiter character (#)
        String template = "Query: {{queryText}}#Documents: {{hits}}";
        TextValidationUtil.ValidationResult result = TextValidationUtil.validatePromptTemplate(template);
        assertFalse("Template with delimiter character should be invalid", result.isValid());
        assertTrue(
            "Error should mention delimiter character",
            result.getErrorMessage().contains("reserved delimiter character") && result.getErrorMessage().contains("#")
        );
    }

    public void testValidatePromptTemplate_ExceedsMaxLength() {
        // Test that template cannot exceed maximum length (10000 characters)
        StringBuilder longTemplate = new StringBuilder("Query: {{queryText}}\nDocuments: {{hits}}\n");
        while (longTemplate.length() < 10001) {
            longTemplate.append("This is a very long template that exceeds the maximum allowed length. ");
        }
        TextValidationUtil.ValidationResult result = TextValidationUtil.validatePromptTemplate(longTemplate.toString());
        assertFalse("Template exceeding max length should be invalid", result.isValid());
        assertTrue("Error should mention maximum length", result.getErrorMessage().contains("exceeds maximum length"));
        assertTrue("Error should mention 10000 characters", result.getErrorMessage().contains("10000"));
    }

    public void testValidatePromptTemplate_ValidLongTemplate() {
        // Test that a long but valid template (under 10000 characters) is accepted
        StringBuilder longTemplate = new StringBuilder("Query: {{queryText}}\nDocuments: {{hits}}\n");
        while (longTemplate.length() < 9990) {
            longTemplate.append("This is a long template. ");
        }
        TextValidationUtil.ValidationResult result = TextValidationUtil.validatePromptTemplate(longTemplate.toString());
        assertTrue("Valid long template should be accepted", result.isValid());
        assertNull(result.getErrorMessage());
    }

    // ============================================
    // Experiment Name Validation Tests
    // ============================================

    public void testValidateExperimentName_ValidNames() {
        // Test valid experiment names within 50 character limit (same as other
        // entities)
        List<String> validNames = List.of("My Experiment", "PAIRWISE_COMPARISON-a1b2c3d4", "Test Experiment 123", "a".repeat(50));

        for (String name : validNames) {
            TextValidationUtil.ValidationResult result = TextValidationUtil.validateName(name);
            assertTrue("Name should be valid: " + name, result.isValid());
            assertNull(result.getErrorMessage());
        }
    }

    public void testValidateExperimentName_TooLong() {
        // Test that experiment names over 50 characters are rejected (same limit as
        // other entities)
        String longName = "a".repeat(51);
        TextValidationUtil.ValidationResult result = TextValidationUtil.validateName(longName);
        assertFalse("Name over 50 chars should be invalid", result.isValid());
        assertEquals("Text exceeds maximum length of 50 characters", result.getErrorMessage());
    }

    public void testValidateExperimentName_InvalidCharacters() {
        // Test that dangerous characters are rejected
        List<String> invalidNames = List.of("Name with \"quotes\"", "Name with <html>", "Name with \\backslash");

        for (String name : invalidNames) {
            TextValidationUtil.ValidationResult result = TextValidationUtil.validateName(name);
            assertFalse("Name with invalid chars should be invalid: " + name, result.isValid());
        }
    }

    // ============================================
    // ParsedField Tests
    // ============================================

    public void testParsedField_Valid() {
        TextValidationUtil.ParsedField field = TextValidationUtil.ParsedField.valid("test value");
        assertTrue(field.isValid());
        assertTrue(field.isPresent());
        assertFalse(field.hasError());
        assertEquals("test value", field.getValue());
        assertNull(field.getErrorMessage());
        assertTrue(field.asOptional().isPresent());
        assertEquals("test value", field.asOptional().get());
    }

    public void testParsedField_Invalid() {
        TextValidationUtil.ParsedField field = TextValidationUtil.ParsedField.invalid("error message");
        assertFalse(field.isValid());
        assertTrue(field.isPresent());
        assertTrue(field.hasError());
        assertNull(field.getValue());
        assertEquals("error message", field.getErrorMessage());
        assertFalse(field.asOptional().isPresent());
    }

    public void testParsedField_Absent() {
        TextValidationUtil.ParsedField field = TextValidationUtil.ParsedField.absent();
        assertTrue(field.isValid());
        assertFalse(field.isPresent());
        assertFalse(field.hasError());
        assertNull(field.getValue());
        assertNull(field.getErrorMessage());
        assertFalse(field.asOptional().isPresent());
    }

    // ============================================
    // parseOptionalExperimentName Tests
    // ============================================

    public void testParseOptionalExperimentName_ValidName() {
        Map<String, Object> source = new HashMap<>();
        source.put("name", "My Experiment");

        TextValidationUtil.ParsedField result = TextValidationUtil.parseOptionalExperimentName(source, "name");
        assertTrue(result.isValid());
        assertTrue(result.isPresent());
        assertEquals("My Experiment", result.getValue());
    }

    public void testParseOptionalExperimentName_TrimsWhitespace() {
        Map<String, Object> source = new HashMap<>();
        source.put("name", "  My Experiment  ");

        TextValidationUtil.ParsedField result = TextValidationUtil.parseOptionalExperimentName(source, "name");
        assertTrue(result.isValid());
        assertTrue(result.isPresent());
        assertEquals("My Experiment", result.getValue());
    }

    public void testParseOptionalExperimentName_NullSource() {
        TextValidationUtil.ParsedField result = TextValidationUtil.parseOptionalExperimentName(null, "name");
        assertTrue(result.isValid());
        assertFalse(result.isPresent());
        assertNull(result.getValue());
    }

    public void testParseOptionalExperimentName_NullFieldName() {
        Map<String, Object> source = new HashMap<>();
        source.put("name", "My Experiment");

        TextValidationUtil.ParsedField result = TextValidationUtil.parseOptionalExperimentName(source, null);
        assertTrue(result.isValid());
        assertFalse(result.isPresent());
    }

    public void testParseOptionalExperimentName_MissingField() {
        Map<String, Object> source = new HashMap<>();
        source.put("other", "value");

        TextValidationUtil.ParsedField result = TextValidationUtil.parseOptionalExperimentName(source, "name");
        assertTrue(result.isValid());
        assertFalse(result.isPresent());
        assertNull(result.getValue());
    }

    public void testParseOptionalExperimentName_NullValue() {
        Map<String, Object> source = new HashMap<>();
        source.put("name", null);

        TextValidationUtil.ParsedField result = TextValidationUtil.parseOptionalExperimentName(source, "name");
        assertTrue(result.isValid());
        assertFalse(result.isPresent());
    }

    public void testParseOptionalExperimentName_EmptyValue() {
        Map<String, Object> source = new HashMap<>();
        source.put("name", "");

        TextValidationUtil.ParsedField result = TextValidationUtil.parseOptionalExperimentName(source, "name");
        assertTrue(result.isValid());
        assertFalse(result.isPresent());
    }

    public void testParseOptionalExperimentName_BlankValue() {
        Map<String, Object> source = new HashMap<>();
        source.put("name", "   ");

        TextValidationUtil.ParsedField result = TextValidationUtil.parseOptionalExperimentName(source, "name");
        assertTrue(result.isValid());
        assertFalse(result.isPresent());
    }

    public void testParseOptionalExperimentName_NonStringValue() {
        Map<String, Object> source = new HashMap<>();
        source.put("name", 12345);

        TextValidationUtil.ParsedField result = TextValidationUtil.parseOptionalExperimentName(source, "name");
        assertFalse(result.isValid());
        assertTrue(result.hasError());
        assertTrue(result.getErrorMessage().contains("must be a string"));
    }

    public void testParseOptionalDescription_NonStringValue() {
        Map<String, Object> source = new HashMap<>();
        source.put("description", 12345);

        TextValidationUtil.ParsedField result = TextValidationUtil.parseOptionalDescription(source, "description");
        assertFalse(result.isValid());
        assertTrue(result.hasError());
        assertTrue(result.getErrorMessage().contains("must be a string"));
    }

    public void testParseOptionalExperimentName_TooLong() {
        Map<String, Object> source = new HashMap<>();
        source.put("name", "a".repeat(51));

        TextValidationUtil.ParsedField result = TextValidationUtil.parseOptionalExperimentName(source, "name");
        assertFalse(result.isValid());
        assertTrue(result.hasError());
        assertTrue(result.getErrorMessage().contains("exceeds maximum length"));
    }

    public void testParseOptionalExperimentName_InvalidCharacters() {
        Map<String, Object> source = new HashMap<>();
        source.put("name", "Name with \"quotes\"");

        TextValidationUtil.ParsedField result = TextValidationUtil.parseOptionalExperimentName(source, "name");
        assertFalse(result.isValid());
        assertTrue(result.hasError());
        assertTrue(result.getErrorMessage().contains("invalid characters"));
    }

    // ============================================
    // parseOptionalDescription Tests
    // ============================================

    public void testParseOptionalDescription_ValidDescription() {
        Map<String, Object> source = new HashMap<>();
        source.put("description", "This is a valid description");

        TextValidationUtil.ParsedField result = TextValidationUtil.parseOptionalDescription(source, "description");
        assertTrue(result.isValid());
        assertTrue(result.isPresent());
        assertEquals("This is a valid description", result.getValue());
    }

    public void testParseOptionalDescription_TrimsWhitespace() {
        Map<String, Object> source = new HashMap<>();
        source.put("description", "  Description with spaces  ");

        TextValidationUtil.ParsedField result = TextValidationUtil.parseOptionalDescription(source, "description");
        assertTrue(result.isValid());
        assertTrue(result.isPresent());
        assertEquals("Description with spaces", result.getValue());
    }

    public void testParseOptionalDescription_MissingField() {
        Map<String, Object> source = new HashMap<>();

        TextValidationUtil.ParsedField result = TextValidationUtil.parseOptionalDescription(source, "description");
        assertTrue(result.isValid());
        assertFalse(result.isPresent());
    }

    public void testParseOptionalDescription_TooLong() {
        Map<String, Object> source = new HashMap<>();
        source.put("description", "a".repeat(251));

        TextValidationUtil.ParsedField result = TextValidationUtil.parseOptionalDescription(source, "description");
        assertFalse(result.isValid());
        assertTrue(result.hasError());
        assertTrue(result.getErrorMessage().contains("exceeds maximum length"));
    }

    public void testParseOptionalDescription_InvalidCharacters() {
        Map<String, Object> source = new HashMap<>();
        source.put("description", "Description with <html>");

        TextValidationUtil.ParsedField result = TextValidationUtil.parseOptionalDescription(source, "description");
        assertFalse(result.isValid());
        assertTrue(result.hasError());
        assertTrue(result.getErrorMessage().contains("invalid characters"));
    }
}
