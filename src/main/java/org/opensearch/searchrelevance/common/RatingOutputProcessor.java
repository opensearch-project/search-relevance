/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */
package org.opensearch.searchrelevance.common;

import org.opensearch.searchrelevance.model.LLMJudgmentRatingType;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Processor for handling LLM rating outputs with structured JSON parsing.
 * When using OpenAI's structured output feature, responses should already be properly formatted JSON.
 */
public class RatingOutputProcessor {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private RatingOutputProcessor() {}

    /**
     * Parse and extract the ratings array from LLM output.
     * Handles both structured output (GPT-4o with response_format) and unstructured output (GPT-3.5).
     *
     * For structured output: {"ratings": [{"id": "...", "rating_score": ...}, ...]}
     * For unstructured output: Extracts JSON from markdown code blocks or embedded JSON patterns
     *
     * @param response The raw LLM response
     * @return JSON array string containing the ratings
     */
    public static String sanitizeLLMResponse(String response) {
        if (response == null || response.trim().isEmpty()) {
            return "[]";
        }

        try {
            // Try to parse as structured JSON first (GPT-4o with response_format)
            JsonNode rootNode = OBJECT_MAPPER.readTree(response);

            // Extract the "ratings" array if it exists
            if (rootNode.has("ratings")) {
                JsonNode ratingsArray = rootNode.get("ratings");
                if (ratingsArray.isArray()) {
                    return ratingsArray.toString();
                }
            }

            // If the response is already an array, return it as-is
            if (rootNode.isArray()) {
                return rootNode.toString();
            }

            // If response is a single object, wrap it in an array
            if (rootNode.isObject()) {
                return "[" + response + "]";
            }

            return "[]";
        } catch (JsonProcessingException e) {
            // If JSON parsing fails, try to extract JSON from unstructured text (GPT-3.5)
            return extractJsonFromUnstructuredText(response);
        }
    }

    /**
     * Extracts JSON from unstructured text responses (for models like GPT-3.5 that don't support structured output).
     * Handles markdown code blocks and embedded JSON patterns.
     */
    private static String extractJsonFromUnstructuredText(String response) {
        if (response == null || response.trim().isEmpty()) {
            return "[]";
        }

        // Try to extract JSON from markdown code blocks (```json ... ``` or ``` ... ```)
        String jsonContent = extractFromMarkdownCodeBlock(response);
        if (jsonContent != null) {
            try {
                JsonNode node = OBJECT_MAPPER.readTree(jsonContent);
                if (node.has("ratings") && node.get("ratings").isArray()) {
                    return node.get("ratings").toString();
                }
                if (node.isArray()) {
                    return node.toString();
                }
            } catch (JsonProcessingException e) {
                // Continue to next extraction method
            }
        }

        // Try to find JSON object or array patterns in the text
        jsonContent = extractJsonPattern(response);
        if (jsonContent != null) {
            try {
                JsonNode node = OBJECT_MAPPER.readTree(jsonContent);
                if (node.has("ratings") && node.get("ratings").isArray()) {
                    return node.get("ratings").toString();
                }
                if (node.isArray()) {
                    return node.toString();
                }
                // If it's an object with ratings, extract it
                if (node.isObject()) {
                    return "[" + jsonContent + "]";
                }
            } catch (JsonProcessingException e) {
                // Parsing failed, return empty array
            }
        }

        return "[]";
    }

    /**
     * Extracts content from markdown code blocks.
     */
    private static String extractFromMarkdownCodeBlock(String text) {
        // Match ```json ... ``` or ``` ... ```
        java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("```(?:json)?\\s*\\n?([\\s\\S]*?)```");
        java.util.regex.Matcher matcher = pattern.matcher(text);
        if (matcher.find()) {
            return matcher.group(1).trim();
        }
        return null;
    }

    /**
     * Extracts JSON object or array patterns from text.
     * Looks for the first occurrence of a JSON structure, prioritizing arrays if they appear first.
     */
    private static String extractJsonPattern(String text) {
        int startObj = text.indexOf('{');
        int startArr = text.indexOf('[');

        // Determine which JSON structure appears first
        if (startArr != -1 && (startObj == -1 || startArr < startObj)) {
            // Array appears first or object not found
            int endArr = findMatchingBracket(text, startArr);
            if (endArr != -1) {
                return text.substring(startArr, endArr + 1);
            }
        }

        // Try to extract object if array extraction failed or object appears first
        if (startObj != -1) {
            int endObj = findMatchingBrace(text, startObj);
            if (endObj != -1) {
                return text.substring(startObj, endObj + 1);
            }
        }

        // Fallback: try array again if object extraction failed
        if (startArr != -1) {
            int endArr = findMatchingBracket(text, startArr);
            if (endArr != -1) {
                return text.substring(startArr, endArr + 1);
            }
        }

        return null;
    }

    /**
     * Finds the matching closing brace for an opening brace.
     */
    private static int findMatchingBrace(String text, int start) {
        int count = 0;
        for (int i = start; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '{') {
                count++;
            } else if (c == '}') {
                count--;
                if (count == 0) {
                    return i;
                }
            }
        }
        return -1;
    }

    /**
     * Finds the matching closing bracket for an opening bracket.
     */
    private static int findMatchingBracket(String text, int start) {
        int count = 0;
        for (int i = start; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '[') {
                count++;
            } else if (c == ']') {
                count--;
                if (count == 0) {
                    return i;
                }
            }
        }
        return -1;
    }

    /**
     * Convert rating score from LLM response to double value.
     * For RELEVANT_IRRELEVANT type: converts "RELEVANT" to 1.0 and "IRRELEVANT" to 0.0
     * For SCORE0_1 type: parses the number value to double
     *
     * Public for testing purposes.
     *
     * @param ratingScoreObj The rating_score object from LLM response
     * @param ratingType The judgment rating type
     * @return The rating score as a double value
     */
    public static Double convertRatingScore(Object ratingScoreObj, LLMJudgmentRatingType ratingType) {
        if (ratingType == LLMJudgmentRatingType.RELEVANT_IRRELEVANT) {
            // Handle binary string ratings
            String ratingStr = (String) ratingScoreObj;
            if ("RELEVANT".equals(ratingStr)) {
                return 1.0;
            } else if ("IRRELEVANT".equals(ratingStr)) {
                return 0.0;
            } else {
                throw new IllegalArgumentException("Invalid binary rating value: " + ratingStr + ". Expected RELEVANT or IRRELEVANT");
            }
        } else {
            // Handle numeric ratings (SCORE0_1)
            return ((Number) ratingScoreObj).doubleValue();
        }
    }
}
