/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */
package org.opensearch.searchrelevance.common;

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
     * Parse and extract the ratings array from LLM structured output.
     * With OpenAI's structured output, the response should follow the schema:
     * {"ratings": [{"id": "...", "rating_score": ...}, ...]}
     *
     * @param response The raw LLM response
     * @return JSON array string containing the ratings
     */
    public static String sanitizeLLMResponse(String response) {
        if (response == null || response.trim().isEmpty()) {
            return "[]";
        }

        try {
            // Parse the JSON response
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
            // If JSON parsing fails, return empty array
            // This maintains backward compatibility and prevents errors
            return "[]";
        }
    }
}
