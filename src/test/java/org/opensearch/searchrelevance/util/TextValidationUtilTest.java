/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */
package org.opensearch.searchrelevance.util;

import java.util.List;

import org.opensearch.searchrelevance.utils.TextValidationUtil;
import org.opensearch.test.OpenSearchTestCase;

class TextValidationUtilTest extends OpenSearchTestCase {

    void testNullText() {
        TextValidationUtil.ValidationResult result = TextValidationUtil.validateText(null);
        assertFalse(result.isValid());
        assertEquals("Text cannot be null", result.getErrorMessage());
    }

    void testEmptyText() {
        TextValidationUtil.ValidationResult result = TextValidationUtil.validateText("");
        assertFalse(result.isValid());
        assertEquals("Text cannot be empty", result.getErrorMessage());
    }

    void testTextTooLong() {
        String longText = "a".repeat(1001);
        TextValidationUtil.ValidationResult result = TextValidationUtil.validateText(longText);
        assertFalse(result.isValid());
        assertEquals("Text exceeds maximum length of 1000 characters", result.getErrorMessage());
    }

    void testValidText() {
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

    void testInvalidCharacters() {
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
            assertEquals(
                "Text contains invalid characters. Only letters, numbers, and basic punctuation allowed",
                result.getErrorMessage()
            );
        }
    }

    void testMaximumLengthText() {
        String maxLengthText = "a".repeat(1000);
        TextValidationUtil.ValidationResult result = TextValidationUtil.validateText(maxLengthText);
        assertTrue(result.isValid());
        assertNull(result.getErrorMessage());
    }
}
