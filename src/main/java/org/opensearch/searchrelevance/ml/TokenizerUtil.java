/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */
package org.opensearch.searchrelevance.ml;

import java.util.List;

import com.knuddels.jtokkit.Encodings;
import com.knuddels.jtokkit.api.Encoding;
import com.knuddels.jtokkit.api.EncodingRegistry;
import com.knuddels.jtokkit.api.EncodingType;
import com.knuddels.jtokkit.api.ModelType;

/**
 *  For OpenAI models, use their official tiktoken library - https://github.com/knuddelsgmbh/jtokkit
 */
public class TokenizerUtil {
    private static final EncodingRegistry registry = Encodings.newDefaultEncodingRegistry();
    // cl100k_base is used by GPT-3.5/GPT-4 and is a good default choice
    private static final Encoding encoding = registry.getEncoding(EncodingType.CL100K_BASE);

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
        List<Integer> tokens = encode(text);
        if (tokens.size() <= tokenLimit) { // no truncation needed
            return text;
        }
        return decode(tokens.subList(0, tokenLimit));
    }

    // Method to encode text to tokens
    private static List<Integer> encode(String text) {
        return encoding.encode(text);
    }

    // Method to decode tokens back to text
    private static String decode(List<Integer> tokens) {
        return encoding.decode(tokens);
    }

}
