/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */
package org.opensearch.searchrelevance.common;

import java.util.HashMap;
import java.util.Map;

import org.opensearch.test.OpenSearchTestCase;

public class MLConstantsTests extends OpenSearchTestCase {

    public void testValidateTokenLimit_ValidInteger() {
        Map<String, Object> source = new HashMap<>();
        source.put("tokenLimit", 2000);

        int result = MLConstants.validateTokenLimit(source);
        assertEquals(2000, result);
    }

    public void testValidateTokenLimit_ValidString() {
        Map<String, Object> source = new HashMap<>();
        source.put("tokenLimit", "3000");

        int result = MLConstants.validateTokenLimit(source);
        assertEquals(3000, result);
    }

    public void testValidateTokenLimit_MissingField() {
        Map<String, Object> source = new HashMap<>();

        int result = MLConstants.validateTokenLimit(source);
        assertEquals((int) MLConstants.DEFAULTED_TOKEN_LIMIT, result);
    }

    public void testValidateTokenLimit_BelowMinimum() {
        Map<String, Object> source = new HashMap<>();
        source.put("tokenLimit", 500);

        IllegalArgumentException exception = expectThrows(IllegalArgumentException.class, () -> MLConstants.validateTokenLimit(source));
        assertTrue(exception.getMessage().contains("must be between"));
    }

    public void testValidateTokenLimit_AboveMaximum() {
        Map<String, Object> source = new HashMap<>();
        source.put("tokenLimit", 600000);

        IllegalArgumentException exception = expectThrows(IllegalArgumentException.class, () -> MLConstants.validateTokenLimit(source));
        assertTrue(exception.getMessage().contains("must be between"));
    }

    public void testValidateTokenLimit_InvalidType() {
        Map<String, Object> source = new HashMap<>();
        source.put("tokenLimit", new Object());

        IllegalArgumentException exception = expectThrows(IllegalArgumentException.class, () -> MLConstants.validateTokenLimit(source));
        assertTrue(exception.getMessage().contains("Invalid tokenLimit type"));
    }

    public void testEscapeJson_NullInput() {
        String result = MLConstants.escapeJson(null);
        assertEquals("", result);
    }

    public void testEscapeJson_WithSpecialCharacters() {
        String input = "Line1\nLine2\tTab\"Quote\\Backslash\rReturn";
        String result = MLConstants.escapeJson(input);

        assertTrue(result.contains("\\n"));
        assertTrue(result.contains("\\t"));
        assertTrue(result.contains("\\\""));
        assertTrue(result.contains("\\\\"));
        assertTrue(result.contains("\\r"));
    }
}
