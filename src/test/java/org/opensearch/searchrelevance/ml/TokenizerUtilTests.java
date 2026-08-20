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

    // ============================================
    // Boundary-aware truncation tests
    // ============================================

    public void testTruncateAtBoundaryWithinLimitReturnsUnchanged() {
        String input = "This is a short sentence.";
        assertEquals(input, TokenizerUtil.truncateStringAtBoundary(input, 50));
    }

    public void testTruncateAtBoundaryNullOrEmpty() {
        assertNull(TokenizerUtil.truncateStringAtBoundary(null, 5));
        assertEquals("", TokenizerUtil.truncateStringAtBoundary("", 5));
    }

    public void testTruncateAtBoundaryZeroLimitReturnsEmpty() {
        assertEquals("", TokenizerUtil.truncateStringAtBoundary("Any text here", 0));
    }

    public void testTruncateAtBoundaryFitsAndIsPrefix() {
        String input = "The quick brown fox jumps over the lazy dog and then keeps on running down the road.";
        int limit = 6;
        String result = TokenizerUtil.truncateStringAtBoundary(input, limit);
        assertTrue("result must be a prefix of the input", input.startsWith(result));
        assertTrue("result must fit within the token budget", TokenizerUtil.countTokens(result) <= limit);
        assertTrue("result should be shorter than input", result.length() < input.length());
    }

    public void testTruncateAtBoundaryDoesNotEndMidWord() {
        String input = "The quick brown fox jumps over the lazy dog and then keeps on running down the road.";
        String result = TokenizerUtil.truncateStringAtBoundary(input, 6);
        // whitespace-separated input: cut must land on whitespace, not inside a word
        char lastKept = result.charAt(result.length() - 1);
        assertTrue("cut should land on a whitespace boundary but ended with '" + lastKept + "'", Character.isWhitespace(lastKept));
    }

    public void testTruncateAtBoundaryPrefersParagraphBreak() {
        String first = "First paragraph has several words in it right here.";
        String input = first
            + "\n\n"
            + "Second paragraph continues on well past the available token budget with many extra words that overflow.";
        // only a couple tokens past the break, so it stays within the retention window
        int limit = TokenizerUtil.countTokens(first + "\n\n") + 2;
        String result = TokenizerUtil.truncateStringAtBoundary(input, limit);
        assertTrue("should keep the paragraph break", result.contains("\n\n"));
        assertFalse("should not leak second-paragraph words past the break", result.contains("continues"));
    }

    public void testTruncateAtBoundaryPrefersSentenceOverWord() {
        String firstSentence = "Alpha beta gamma delta epsilon zeta eta theta.";
        String input = firstSentence + " Second sentence has even more words that push well beyond the limit.";
        int limit = TokenizerUtil.countTokens(firstSentence) + 2;
        String result = TokenizerUtil.truncateStringAtBoundary(input, limit);
        assertTrue("should keep the first sentence", result.contains("epsilon"));
        assertFalse("should not leak second-sentence words", result.contains("Second"));
    }

    public void testTruncateAtBoundaryFallsBackToHardCutWithoutSeparators() {
        // no separators to back up to, so it falls back to the hard token cut
        String input = "x".repeat(2000);
        int limit = 5;
        String result = TokenizerUtil.truncateStringAtBoundary(input, limit);
        assertEquals(TokenizerUtil.truncateString(input, limit), result);
        assertTrue(TokenizerUtil.countTokens(result) <= limit);
    }

    public void testTruncateAtBoundaryFallsBackWhenOnlySeparatorIsTooEarly() {
        // separators exist only at the very start; the retention guard rejects them, so it hard-cuts
        String input = "hi.\n\n" + "x".repeat(2000);
        int limit = 20;
        String result = TokenizerUtil.truncateStringAtBoundary(input, limit);
        assertTrue("result must be a prefix of the input", input.startsWith(result));
        assertTrue("result must fit within the token budget", TokenizerUtil.countTokens(result) <= limit);
        assertTrue("cut should fall through to the hard cut", result.endsWith("x"));
    }

    public void testTruncateAtBoundaryDoesNotProduceMalformedMultibyteText() {
        // multi-byte content built from code points (CJK + emoji); a cut inside a byte sequence
        // decodes to U+FFFD, which must be stripped
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 200; i++) {
            sb.appendCodePoint(0x4E00 + (i % 100)); // CJK Unified Ideographs block
        }
        sb.appendCodePoint(0x1F600); // grinning face emoji

        String input = sb.toString();
        int limit = 8;
        String result = TokenizerUtil.truncateStringAtBoundary(input, limit);
        assertTrue("result must not contain the Unicode replacement character", result.indexOf('\uFFFD') < 0);
        assertTrue("result must be a prefix of the input", input.startsWith(result));
        assertTrue("result must fit within the token budget", TokenizerUtil.countTokens(result) <= limit);
    }

}
