/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */
package org.opensearch.searchrelevance.ml;

import org.opensearch.test.OpenSearchTestCase;

import com.knuddels.jtokkit.api.ModelType;

public class TokenizerUtilTests extends OpenSearchTestCase {

    public void testCountTokensWithNullOrEmptyString() {
        assertEquals(0, TokenizerUtil.countTokens(null));
        assertEquals(0, TokenizerUtil.countTokens(""));
    }

    public void testCountTokensWithSimpleString() {
        assertEquals(8, TokenizerUtil.countTokens("Hello, world! How are you?"));
    }

    public void testCountTokensWithModelType() {
        String text = "Hello, world! How are you?";
        assertEquals(8, TokenizerUtil.countTokens(text, ModelType.GPT_3_5_TURBO));
    }

    public void testTruncateStringWithinLimit() {
        String input = "This is a short sentence.";
        assertEquals(input, TokenizerUtil.truncateString(input, 10));
    }

    public void testTruncateStringExceedingLimit() {
        String input = "This is a longer sentence that will be truncated.";
        String truncated = TokenizerUtil.truncateString(input, 5);
        assertTrue(truncated.length() < input.length());
        assertEquals(5, TokenizerUtil.countTokens(truncated));
    }

    public void testTruncateStringWithZeroLimit() {
        String input = "Any text";
        assertEquals("", TokenizerUtil.truncateString(input, 0));
    }

}
