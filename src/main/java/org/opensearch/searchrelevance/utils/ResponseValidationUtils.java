/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */
package org.opensearch.searchrelevance.utils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.opensearch.common.xcontent.XContentFactory;
import org.opensearch.core.xcontent.XContentBuilder;
import org.opensearch.core.xcontent.XContentParser;

import lombok.extern.log4j.Log4j2;

/**
 * Utility class for validating and ensuring proper response formats for remote search responses.
 * This helps prevent jq errors and ensures consistent response structures.
 */
@Log4j2
public class ResponseValidationUtils {

    /**
     * Validate that a JSON response has the proper OpenSearch structure for jq compatibility
     *
     * @param jsonResponse The JSON response to validate
     * @return true if the response is valid, false otherwise
     */
    public static boolean isValidOpenSearchResponse(String jsonResponse) {
        if (jsonResponse == null || jsonResponse.trim().isEmpty() || "null".equals(jsonResponse.trim())) {
            return false;
        }

        try {
            Map<String, Object> responseMap = parseJsonToMap(jsonResponse);

            // Check for error structure (valid but indicates an error)
            if (responseMap.containsKey("error")) {
                return true; // Error responses are valid JSON structures
            }

            // Check for hits structure (required for search responses)
            if (!responseMap.containsKey("hits")) {
                return false;
            }

            Object hitsObj = responseMap.get("hits");
            if (!(hitsObj instanceof Map)) {
                return false;
            }

            @SuppressWarnings("unchecked")
            Map<String, Object> hits = (Map<String, Object>) hitsObj;

            // Validate hits structure has required fields
            if (!hits.containsKey("total") || !hits.containsKey("hits")) {
                return false;
            }

            // Validate hits.hits is an array
            Object hitsArray = hits.get("hits");
            if (!(hitsArray instanceof List)) {
                return false;
            }

            return true;

        } catch (Exception e) {
            log.debug("Response validation failed: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Ensure a response has the proper OpenSearch structure, creating one if necessary
     *
     * @param jsonResponse The JSON response to validate and fix
     * @return A valid OpenSearch-structured JSON response
     */
    public static String ensureValidOpenSearchResponse(String jsonResponse) {
        if (isValidOpenSearchResponse(jsonResponse)) {
            return jsonResponse;
        }

        log.debug("Response validation failed, creating empty OpenSearch response");
        return createEmptyOpenSearchResponse();
    }

    /**
     * Create a standard empty OpenSearch response structure
     *
     * @return JSON string representing an empty OpenSearch response
     */
    public static String createEmptyOpenSearchResponse() {
        try {
            Map<String, Object> total = new HashMap<>();
            total.put("value", 0);
            total.put("relation", "eq");

            Map<String, Object> hitsContainer = new HashMap<>();
            hitsContainer.put("total", total);
            hitsContainer.put("max_score", null);
            hitsContainer.put("hits", new ArrayList<>());

            Map<String, Object> response = new HashMap<>();
            response.put("hits", hitsContainer);
            response.put("took", 0);
            response.put("timed_out", false);

            return mapToJson(response);
        } catch (Exception e) {
            log.error("Failed to create empty OpenSearch response: {}", e.getMessage());
            // Fallback to hardcoded JSON
            return "{\"hits\":{\"total\":{\"value\":0,\"relation\":\"eq\"},\"max_score\":null,\"hits\":[]},\"took\":0,\"timed_out\":false}";
        }
    }

    /**
     * Create an error response in OpenSearch format
     *
     * @param errorType The type of error
     * @param errorReason The error reason/message
     * @return JSON string representing an error response
     */
    public static String createErrorResponse(String errorType, String errorReason) {
        try {
            Map<String, Object> error = new HashMap<>();
            error.put("type", errorType != null ? errorType : "remote_search_exception");
            error.put("reason", errorReason != null ? errorReason : "Unknown error");

            Map<String, Object> response = new HashMap<>();
            response.put("error", error);

            return mapToJson(response);
        } catch (Exception e) {
            log.error("Failed to create error response: {}", e.getMessage());
            // Fallback to hardcoded JSON
            String safeReason = errorReason != null ? errorReason.replace("\"", "\\\"") : "Unknown error";
            return "{\"error\":{\"type\":\"remote_search_exception\",\"reason\":\"" + safeReason + "\"}}";
        }
    }

    /**
     * Validate that a response contains the expected number of hits
     *
     * @param jsonResponse The JSON response to check
     * @param expectedMinHits Minimum expected number of hits
     * @return true if the response has at least the expected number of hits
     */
    public static boolean hasMinimumHits(String jsonResponse, int expectedMinHits) {
        try {
            Map<String, Object> responseMap = parseJsonToMap(jsonResponse);

            if (!responseMap.containsKey("hits")) {
                return false;
            }

            @SuppressWarnings("unchecked")
            Map<String, Object> hits = (Map<String, Object>) responseMap.get("hits");

            if (!hits.containsKey("hits")) {
                return false;
            }

            @SuppressWarnings("unchecked")
            List<Object> hitsArray = (List<Object>) hits.get("hits");

            return hitsArray.size() >= expectedMinHits;

        } catch (Exception e) {
            log.debug("Failed to check minimum hits: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Extract the total number of hits from a response
     *
     * @param jsonResponse The JSON response
     * @return The total number of hits, or 0 if not found
     */
    public static int getTotalHits(String jsonResponse) {
        try {
            Map<String, Object> responseMap = parseJsonToMap(jsonResponse);

            if (!responseMap.containsKey("hits")) {
                return 0;
            }

            @SuppressWarnings("unchecked")
            Map<String, Object> hits = (Map<String, Object>) responseMap.get("hits");

            if (!hits.containsKey("total")) {
                return 0;
            }

            Object totalObj = hits.get("total");
            if (totalObj instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> total = (Map<String, Object>) totalObj;
                Object valueObj = total.get("value");
                if (valueObj instanceof Number) {
                    return ((Number) valueObj).intValue();
                }
            } else if (totalObj instanceof Number) {
                return ((Number) totalObj).intValue();
            }

            return 0;

        } catch (Exception e) {
            log.debug("Failed to extract total hits: {}", e.getMessage());
            return 0;
        }
    }

    /**
     * Parse JSON string to Map
     */
    private static Map<String, Object> parseJsonToMap(String json) throws Exception {
        if (json == null || json.trim().isEmpty()) {
            return new HashMap<>();
        }

        try (XContentParser parser = XContentFactory.jsonBuilder().contentType().xContent().createParser(null, null, json)) {
            return parser.map();
        }
    }

    /**
     * Convert Map to JSON string
     */
    private static String mapToJson(Map<String, Object> map) throws Exception {
        try (XContentBuilder builder = XContentFactory.jsonBuilder()) {
            builder.map(map);
            return builder.toString();
        }
    }
}
