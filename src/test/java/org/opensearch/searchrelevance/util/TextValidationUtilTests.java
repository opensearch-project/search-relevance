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
}
