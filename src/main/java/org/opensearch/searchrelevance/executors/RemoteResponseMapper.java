/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */
package org.opensearch.searchrelevance.executors;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.opensearch.common.xcontent.XContentFactory;
import org.opensearch.core.xcontent.XContentBuilder;
import org.opensearch.core.xcontent.XContentParser;

import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.extern.log4j.Log4j2;

/**
 * RemoteResponseMapper handles mapping of remote search engine responses to OpenSearch format
 * using JSON path-based field mapping. This enables experiments to work with responses from
 * different search engines by transforming them into a consistent format.
 */
@Log4j2
public class RemoteResponseMapper {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    /**
     * Map a remote search response to OpenSearch format using response template
     *
     * @param rawResponse The raw JSON response from remote search engine
     * @param responseTemplate The response template defining field mappings (JSON path format)
     * @return Mapped response in OpenSearch format
     */
    public String mapResponse(String rawResponse, String responseTemplate) {
        // Enhanced null/empty response handling
        if (rawResponse == null || rawResponse.trim().isEmpty() || "null".equals(rawResponse.trim())) {
            log.debug("Received null or empty raw response, returning empty OpenSearch response");
            return createEmptyResponse();
        }

        if (responseTemplate == null || responseTemplate.trim().isEmpty()) {
            // No template provided, attempt default OpenSearch format detection
            return mapWithDefaultTemplate(rawResponse);
        }

        try {
            // Parse the raw response with enhanced error handling
            Map<String, Object> rawData = parseJsonToMap(rawResponse);
            if (rawData == null || rawData.isEmpty()) {
                log.debug("Raw response parsed to null or empty map, returning empty OpenSearch response");
                return createEmptyResponse();
            }

            // Check if response template is a template string (contains ${}) or JSON mapping
            if (responseTemplate.contains("${")) {
                // This is a template string, not a JSON mapping - apply template substitution
                log.debug("Response template contains template variables, applying template substitution");
                String result = applyTemplateSubstitution(rawData, responseTemplate);

                // Validate the result
                if (result == null || "null".equals(result.trim()) || result.trim().isEmpty()) {
                    log.debug("Template substitution resulted in null or empty result, falling back to default mapping");
                    return mapWithDefaultTemplate(rawResponse);
                }

                return result;
            } else {
                // This should be a JSON mapping configuration
                Map<String, Object> template = parseJsonToMap(responseTemplate);
                if (template == null || template.isEmpty()) {
                    log.debug("Response template parsed to null or empty map, falling back to default mapping");
                    return mapWithDefaultTemplate(rawResponse);
                }

                // Apply the mapping
                Map<String, Object> mappedData = applyMapping(rawData, template);

                // Validate mapped data has proper structure
                if (mappedData == null || mappedData.isEmpty()) {
                    log.debug("Mapping resulted in null or empty data, returning empty OpenSearch response");
                    return createEmptyResponse();
                }

                // Convert back to JSON
                String result = mapToJson(mappedData);

                // Final validation - ensure result is not null or "null"
                if (result == null || "null".equals(result.trim()) || result.trim().isEmpty()) {
                    log.debug("Final mapping result is null or empty, returning empty OpenSearch response");
                    return createEmptyResponse();
                }

                return result;
            }

        } catch (Exception e) {
            log.debug("Failed to map remote response: {}", e.getMessage());
            return createErrorResponse(e.getMessage());
        }
    }

    /**
     * Apply template substitution for template strings containing ${} variables
     */
    private String applyTemplateSubstitution(Map<String, Object> rawData, String template) {
        try {
            String result = template;

            // Simple template variable substitution for ${path} syntax
            // This is a basic implementation - for production use, consider a proper template engine
            while (result.contains("${")) {
                int startIndex = result.indexOf("${");
                int endIndex = result.indexOf("}", startIndex);

                if (endIndex == -1) {
                    // Malformed template variable, break to avoid infinite loop
                    log.debug("Malformed template variable in response template, missing closing }");
                    break;
                }

                String variable = result.substring(startIndex + 2, endIndex);
                Object value = extractValueByPath(rawData, variable);

                String replacement;
                if (value == null) {
                    replacement = "null";
                } else if (value instanceof String) {
                    replacement = "\"" + value.toString().replace("\"", "\\\"") + "\"";
                } else if (value instanceof List || value instanceof Map) {
                    // For complex objects (arrays or objects), serialize directly to JSON without wrapping
                    try {
                        replacement = OBJECT_MAPPER.writeValueAsString(value);
                    } catch (Exception e) {
                        replacement = "null";
                    }
                } else {
                    replacement = value.toString();
                }

                result = result.substring(0, startIndex) + replacement + result.substring(endIndex + 1);
            }

            // Validate that the result is valid JSON
            try {
                parseJsonToMap(result);
                return result;
            } catch (Exception e) {
                log.debug("Template substitution resulted in invalid JSON: {}", e.getMessage());
                log.debug("Template substitution result was: {}", result);
                // Fall back to default mapping
                return null;
            }

        } catch (Exception e) {
            log.debug("Failed to apply template substitution: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Apply default mapping for responses that might already be in OpenSearch format
     */
    private String mapWithDefaultTemplate(String rawResponse) {
        try {
            Map<String, Object> rawData = parseJsonToMap(rawResponse);

            // Check if it's already in OpenSearch format
            if (rawData.containsKey("hits")) {
                return rawResponse; // Already in correct format
            }

            // Detect Solr JSON Response API: response.docs under 'response'
            if (rawData.containsKey("response") && rawData.get("response") instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> response = (Map<String, Object>) rawData.get("response");
                if (response.get("docs") instanceof List) {
                    return mapSolrFormat(rawData);
                }
            }

            // Try to detect common search response patterns
            if (rawData.containsKey("results") || rawData.containsKey("documents")) {
                return mapCommonFormat(rawData);
            }

            // If we can't detect the format, wrap it in a basic structure
            return wrapInBasicFormat(rawData);

        } catch (Exception e) {
            log.debug("Failed to apply default mapping, returning raw response: {}", e.getMessage());
            return rawResponse;
        }
    }

    /**
     * Apply mapping based on template configuration
     */
    private Map<String, Object> applyMapping(Map<String, Object> rawData, Map<String, Object> template) {
        Map<String, Object> result = new HashMap<>();

        for (Map.Entry<String, Object> entry : template.entrySet()) {
            String targetField = entry.getKey();
            Object mappingConfig = entry.getValue();

            if (mappingConfig instanceof String) {
                // Simple JSON path mapping
                String jsonPath = (String) mappingConfig;
                Object value = extractValueByPath(rawData, jsonPath);
                if (value != null) {
                    result.put(targetField, value);
                }
            } else if (mappingConfig instanceof Map) {
                // Complex mapping configuration or nested structure
                @SuppressWarnings("unchecked")
                Map<String, Object> config = (Map<String, Object>) mappingConfig;

                // Check if this is a mapping configuration or nested structure
                if (config.containsKey("path") || config.containsKey("type") || config.containsKey("default")) {
                    Object value = applyComplexMapping(rawData, config);
                    // Always add the value, even if null, because applyComplexMapping handles defaults
                    result.put(targetField, value);
                } else {
                    // Nested structure: recursively apply mapping
                    Map<String, Object> nestedResult = applyMapping(rawData, config);
                    if (!nestedResult.isEmpty()) {
                        result.put(targetField, nestedResult);
                    }
                }
            }
        }

        return result;
    }

    /**
     * Apply complex mapping with transformations
     */
    private Object applyComplexMapping(Map<String, Object> rawData, Map<String, Object> config) {
        String path = (String) config.get("path");
        String type = (String) config.get("type");
        Object defaultValue = config.get("default");

        if (path == null) {
            return defaultValue;
        }

        Object value = extractValueByPath(rawData, path);

        if (value == null) {
            return defaultValue;
        }

        // Apply type transformations
        if (type != null) {
            value = transformValue(value, type);
        }

        return value;
    }

    /**
     * Extract value from nested map using JSON path notation
     */
    private Object extractValueByPath(Map<String, Object> data, String path) {
        if (path == null || path.trim().isEmpty()) {
            return null;
        }

        // Handle simple field access
        if (!path.contains(".") && !path.contains("[")) {
            return data.get(path);
        }

        // Parse the path more carefully to handle array notation
        Object current = data;
        String remainingPath = path;

        while (!remainingPath.isEmpty() && current != null) {
            String nextPart;
            String restOfPath;

            // Check if we have a dot separator
            int dotIndex = remainingPath.indexOf('.');
            if (dotIndex == -1) {
                // No more dots, this is the last part
                nextPart = remainingPath;
                restOfPath = "";
            } else {
                nextPart = remainingPath.substring(0, dotIndex);
                restOfPath = remainingPath.substring(dotIndex + 1);
            }

            // Handle array access in this part
            if (nextPart.contains("[") && nextPart.contains("]")) {
                String fieldName = nextPart.substring(0, nextPart.indexOf('['));
                String indexStr = nextPart.substring(nextPart.indexOf('[') + 1, nextPart.indexOf(']'));

                // First get the field
                if (current instanceof Map) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> map = (Map<String, Object>) current;
                    current = map.get(fieldName);
                } else {
                    return null;
                }

                // Then access the array index
                if (current instanceof List) {
                    @SuppressWarnings("unchecked")
                    List<Object> list = (List<Object>) current;
                    try {
                        int index = Integer.parseInt(indexStr);
                        if (index >= 0 && index < list.size()) {
                            current = list.get(index);
                        } else {
                            return null;
                        }
                    } catch (NumberFormatException e) {
                        return null;
                    }
                } else {
                    return null;
                }
            } else {
                // Simple field access
                if (current instanceof Map) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> map = (Map<String, Object>) current;
                    current = map.get(nextPart);
                } else {
                    return null;
                }
            }

            remainingPath = restOfPath;
        }

        return current;
    }

    /**
     * Transform value to specified type
     */
    private Object transformValue(Object value, String type) {
        if (value == null) {
            return null;
        }

        try {
            switch (type.toLowerCase(Locale.ROOT)) {
                case "string":
                    return value.toString();
                case "integer":
                case "int":
                    if (value instanceof Number) {
                        return ((Number) value).intValue();
                    }
                    return Integer.parseInt(value.toString());
                case "long":
                    if (value instanceof Number) {
                        return ((Number) value).longValue();
                    }
                    return Long.parseLong(value.toString());
                case "double":
                    if (value instanceof Number) {
                        return ((Number) value).doubleValue();
                    }
                    return Double.parseDouble(value.toString());
                case "boolean":
                    if (value instanceof Boolean) {
                        return value;
                    }
                    return Boolean.parseBoolean(value.toString());
                default:
                    return value;
            }
        } catch (Exception e) {
            log.debug("Failed to transform value {} to type {}: {}", value, type, e.getMessage());
            return value;
        }
    }

    /**
     * Map common search response formats to OpenSearch format
     */
    private String mapCommonFormat(Map<String, Object> rawData) {
        try {
            Map<String, Object> opensearchFormat = new HashMap<>();

            // Extract hits
            Object resultsObj = rawData.get("results");
            if (resultsObj == null) {
                resultsObj = rawData.get("documents");
            }

            List<Map<String, Object>> hits = new ArrayList<>();
            int totalHits = 0;

            if (resultsObj instanceof List) {
                @SuppressWarnings("unchecked")
                List<Object> results = (List<Object>) resultsObj;
                totalHits = results.size();

                for (int i = 0; i < results.size(); i++) {
                    Object item = results.get(i);
                    if (item instanceof Map) {
                        @SuppressWarnings("unchecked")
                        Map<String, Object> doc = (Map<String, Object>) item;

                        Map<String, Object> hit = new HashMap<>();
                        hit.put("_index", "remote");
                        hit.put("_id", doc.getOrDefault("id", String.valueOf(i)));
                        hit.put("_score", doc.getOrDefault("score", 1.0));
                        hit.put("_source", doc);

                        hits.add(hit);
                    }
                }
            }

            // Build OpenSearch response structure
            Map<String, Object> total = new HashMap<>();
            total.put("value", totalHits);
            total.put("relation", "eq");

            Map<String, Object> hitsContainer = new HashMap<>();
            hitsContainer.put("total", total);
            hitsContainer.put("max_score", hits.isEmpty() ? null : 1.0);
            hitsContainer.put("hits", hits);

            opensearchFormat.put("hits", hitsContainer);
            opensearchFormat.put("took", rawData.getOrDefault("took", 1));
            opensearchFormat.put("timed_out", false);

            return mapToJson(opensearchFormat);

        } catch (Exception e) {
            log.debug("Failed to map common format: {}", e.getMessage());
            return createErrorResponse(e.getMessage());
        }
    }

    /**
     * Map Solr JSON response (response.docs/numFound and optional responseHeader.QTime) to OpenSearch format
     */
    private String mapSolrFormat(Map<String, Object> rawData) {
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> response = (Map<String, Object>) rawData.get("response");

            @SuppressWarnings("unchecked")
            List<Object> docs = (List<Object>) response.get("docs");

            int totalHits = 0;
            Object numFoundObj = response.get("numFound");
            if (numFoundObj instanceof Number) {
                totalHits = ((Number) numFoundObj).intValue();
            } else if (numFoundObj != null) {
                try {
                    totalHits = Integer.parseInt(numFoundObj.toString());
                } catch (NumberFormatException ignore) {
                    // keep default
                }
            }

            List<Map<String, Object>> hits = new ArrayList<>();
            if (docs != null) {
                for (int i = 0; i < docs.size(); i++) {
                    Object item = docs.get(i);
                    if (item instanceof Map) {
                        @SuppressWarnings("unchecked")
                        Map<String, Object> doc = (Map<String, Object>) item;

                        Map<String, Object> hit = new HashMap<>();
                        hit.put("_index", "remote");
                        Object id = doc.getOrDefault("id", String.valueOf(i));
                        hit.put("_id", id);

                        double score = 1.0;
                        Object scoreObj = doc.get("score");
                        if (scoreObj instanceof Number) {
                            score = ((Number) scoreObj).doubleValue();
                        } else if (scoreObj != null) {
                            try {
                                score = Double.parseDouble(scoreObj.toString());
                            } catch (Exception ignore) {
                                // keep default
                            }
                        }
                        hit.put("_score", score);
                        hit.put("_source", doc);

                        hits.add(hit);
                    }
                }
            }

            Map<String, Object> total = new HashMap<>();
            total.put("value", totalHits);
            total.put("relation", "eq");

            Map<String, Object> hitsContainer = new HashMap<>();
            hitsContainer.put("total", total);
            hitsContainer.put("max_score", hits.isEmpty() ? null : 1.0);
            hitsContainer.put("hits", hits);

            Map<String, Object> opensearchFormat = new HashMap<>();
            opensearchFormat.put("hits", hitsContainer);

            // took from responseHeader.QTime if available (milliseconds)
            int took = 1;
            Object headerObj = rawData.get("responseHeader");
            if (headerObj instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> header = (Map<String, Object>) headerObj;
                Object qtime = header.get("QTime");
                if (qtime instanceof Number) {
                    took = ((Number) qtime).intValue();
                } else if (qtime != null) {
                    try {
                        took = Integer.parseInt(qtime.toString());
                    } catch (Exception ignore) {
                        // keep default
                    }
                }
            }
            opensearchFormat.put("took", took);
            opensearchFormat.put("timed_out", false);

            return mapToJson(opensearchFormat);
        } catch (Exception e) {
            log.debug("Failed to map Solr format: {}", e.getMessage());
            return createErrorResponse(e.getMessage());
        }
    }

    /**
     * Wrap unknown format in basic OpenSearch structure
     */
    private String wrapInBasicFormat(Map<String, Object> rawData) {
        try {
            Map<String, Object> hit = new HashMap<>();
            hit.put("_index", "remote");
            hit.put("_id", "1");
            hit.put("_score", 1.0);
            hit.put("_source", rawData);

            Map<String, Object> total = new HashMap<>();
            total.put("value", 1);
            total.put("relation", "eq");

            Map<String, Object> hitsContainer = new HashMap<>();
            hitsContainer.put("total", total);
            hitsContainer.put("max_score", 1.0);
            hitsContainer.put("hits", List.of(hit));

            Map<String, Object> opensearchFormat = new HashMap<>();
            opensearchFormat.put("hits", hitsContainer);
            opensearchFormat.put("took", 1);
            opensearchFormat.put("timed_out", false);

            return mapToJson(opensearchFormat);

        } catch (Exception e) {
            log.debug("Failed to wrap in basic format: {}", e.getMessage());
            return createErrorResponse(e.getMessage());
        }
    }

    /**
     * Parse JSON string to Map
     */
    private Map<String, Object> parseJsonToMap(String json) throws Exception {
        if (json == null || json.trim().isEmpty()) {
            return new HashMap<>();
        }

        // Clean the JSON string to remove any potential BOM or invisible characters
        String cleanedJson = json;

        // Remove BOM if present
        if (cleanedJson.startsWith("\uFEFF")) {
            cleanedJson = cleanedJson.substring(1);
        }

        // Remove any leading/trailing whitespace and control characters
        cleanedJson = cleanedJson.trim();

        // Remove any non-printable characters at the beginning
        while (cleanedJson.length() > 0
            && cleanedJson.charAt(0) < 32
            && cleanedJson.charAt(0) != '\t'
            && cleanedJson.charAt(0) != '\n'
            && cleanedJson.charAt(0) != '\r') {
            cleanedJson = cleanedJson.substring(1);
        }

        // Parse JSON directly without aggressive whitespace cleaning
        try (XContentParser parser = XContentFactory.jsonBuilder().contentType().xContent().createParser(null, null, cleanedJson)) {
            return parser.map();
        }
    }

    /**
     * Convert Map to JSON string
     */
    private String mapToJson(Map<String, Object> map) throws Exception {
        try (XContentBuilder builder = XContentFactory.jsonBuilder()) {
            builder.map(map);
            return builder.toString();
        }
    }

    /**
     * Create empty response in OpenSearch format
     */
    private String createEmptyResponse() {
        try {
            Map<String, Object> total = new HashMap<>();
            total.put("value", 0);
            total.put("relation", "eq");

            Map<String, Object> hitsContainer = new HashMap<>();
            hitsContainer.put("total", total);
            hitsContainer.put("max_score", null);
            hitsContainer.put("hits", List.of());

            Map<String, Object> response = new HashMap<>();
            response.put("hits", hitsContainer);
            response.put("took", 0);
            response.put("timed_out", false);

            return mapToJson(response);
        } catch (Exception e) {
            log.debug("Failed to create empty response: {}", e.getMessage());
            return "{\"hits\":{\"total\":{\"value\":0,\"relation\":\"eq\"},\"hits\":[]}}";
        }
    }

    /**
     * Create error response
     */
    private String createErrorResponse(String errorMessage) {
        try {
            Map<String, Object> error = new HashMap<>();
            error.put("type", "remote_mapping_exception");
            error.put("reason", errorMessage);

            Map<String, Object> response = new HashMap<>();
            response.put("error", error);

            return mapToJson(response);
        } catch (Exception e) {
            return "{\"error\":{\"type\":\"remote_mapping_exception\",\"reason\":\"" + errorMessage.replace("\"", "\\\"") + "\"}}";
        }
    }
}
