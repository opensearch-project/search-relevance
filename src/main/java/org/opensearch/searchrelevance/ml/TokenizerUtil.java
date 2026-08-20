/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */
package org.opensearch.searchrelevance.ml;

import java.util.ArrayList;
import java.util.List;

import com.knuddels.jtokkit.Encodings;
import com.knuddels.jtokkit.api.Encoding;
import com.knuddels.jtokkit.api.EncodingRegistry;
import com.knuddels.jtokkit.api.EncodingType;
import com.knuddels.jtokkit.api.IntArrayList;
import com.knuddels.jtokkit.api.ModelType;

/**
 *  For OpenAI models, use their official tiktoken library - https://github.com/knuddelsgmbh/jtokkit
 */
public class TokenizerUtil {
    private static final EncodingRegistry registry = Encodings.newDefaultEncodingRegistry();
    // cl100k_base is used by GPT-3.5/GPT-4 and is a good default choice
    private static final Encoding encoding = registry.getEncoding(EncodingType.CL100K_BASE);

    // Cut-point separators, highest priority first: paragraph, line, sentence, clause, word.
    private static final String[] BOUNDARY_SEPARATORS = { "\n\n", "\n", ". ", "! ", "? ", "; ", ", ", " " };
    // Emitted when a token cut lands inside a multi-byte codepoint (e.g. CJK, emoji).
    private static final char REPLACEMENT_CHAR = '\uFFFD';
    // Lowest fraction of the fitting window to keep when backing up to a coarser boundary.
    private static final double MIN_RETENTION_RATIO = 0.5;

    /**
     * helper method to count tokens if no model type is provided
     */
    public static int countTokens(String text) {
        if (text == null || text.isEmpty()) {
            return 0;
        }
        return encoding.countTokens(text);
    }

    /**
     * helper method to count tokens if a specific model type is provided
     */
    public static int countTokens(String text, ModelType modelType) {
        return registry.getEncodingForModel(modelType).countTokens(text);
    }

    /**
     * helper method to truncate text to token limit
     */
    public static String truncateString(String text, int tokenLimit) {
        IntArrayList tokens = encoding.encode(text);
        if (tokens.size() <= tokenLimit) { // no truncation needed
            return text;
        }
        // Convert to List for subList operation
        List<Integer> tokenList = new ArrayList<>();
        for (int i = 0; i < tokens.size(); i++) {
            tokenList.add(tokens.get(i));
        }
        return decode(tokenList.subList(0, tokenLimit));
    }

    /**
     * helper method to truncate text to token limit at a clean separator boundary,
     * falling back to a hard token cut when none fits
     */
    public static String truncateStringAtBoundary(String text, int tokenLimit) {
        if (text == null || text.isEmpty()) {
            return text;
        }
        if (tokenLimit <= 0) {
            return "";
        }
        if (countTokens(text) <= tokenLimit) { // no truncation needed
            return text;
        }

        // largest window that fits; any prefix of it is still within the budget
        String window = stripTrailingReplacementChars(truncateString(text, tokenLimit));
        if (window.isEmpty()) {
            return window;
        }

        int minLength = (int) Math.floor(window.length() * MIN_RETENTION_RATIO);
        for (String separator : BOUNDARY_SEPARATORS) {
            int idx = window.lastIndexOf(separator);
            if (idx <= 0) {
                continue;
            }
            int cut = idx + separator.length(); // keep the separator with the retained text
            if (cut >= minLength) {
                return window.substring(0, cut);
            }
        }
        return window; // no acceptable boundary: hard cut
    }

    private static String stripTrailingReplacementChars(String text) {
        int end = text.length();
        while (end > 0 && text.charAt(end - 1) == REPLACEMENT_CHAR) {
            end--;
        }
        return text.substring(0, end);
    }

    // Method to decode tokens back to text
    private static String decode(List<Integer> tokens) {
        // Convert List<Integer> to IntArrayList for decoding
        IntArrayList intArrayList = new IntArrayList();
        for (Integer token : tokens) {
            intArrayList.add(token);
        }
        return encoding.decode(intArrayList);
    }

}
