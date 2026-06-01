/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */
package org.opensearch.searchrelevance.model;

import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import org.opensearch.common.xcontent.XContentHelper;
import org.opensearch.common.xcontent.XContentType;
import org.opensearch.core.xcontent.ToXContent.Params;
import org.opensearch.core.xcontent.ToXContentObject;
import org.opensearch.core.xcontent.XContentBuilder;

/**
 * QuerySetEntry represents a single query entry in a query set.
 * Stores queryText and customFields as separate fields — no delimiter-based concatenation.
 */
public class QuerySetEntry implements ToXContentObject {

    public static final String QUERY_TEXT = "queryText";
    public static final String CUSTOM_FIELDS = "customFields";

    private final String queryText;
    private final Map<String, String> customFields;

    public QuerySetEntry(String queryText, Map<String, String> customFields) {
        this.queryText = queryText;
        this.customFields = customFields != null ? customFields : Collections.emptyMap();
    }

    @Override
    public XContentBuilder toXContent(XContentBuilder builder, Params params) throws IOException {
        XContentBuilder xContentBuilder = builder.startObject();
        xContentBuilder.field(QUERY_TEXT, this.queryText);
        // Always write customFields so that fromStoredMap can distinguish new-format
        // documents (key present, possibly empty) from legacy documents (key absent).
        xContentBuilder.field(CUSTOM_FIELDS, this.customFields);
        return xContentBuilder.endObject();
    }

    public String queryText() {
        return queryText;
    }

    public Map<String, String> customFields() {
        return Collections.unmodifiableMap(customFields);
    }

    /**
     * Creates a QuerySetEntry from a stored map (e.g., from OpenSearch document source).
     * Handles both the new format (separate queryText and customFields) and legacy
     * delimiter-based formats for backward compatibility.
     */
    @SuppressWarnings("unchecked")
    public static QuerySetEntry fromStoredMap(Map<String, Object> entryMap) {
        String queryText = (String) entryMap.get(QUERY_TEXT);
        Map<String, String> customFields = Collections.emptyMap();

        if (entryMap.containsKey(CUSTOM_FIELDS)) {
            // New format: customFields key is present (may be null or a map)
            Object customFieldsObj = entryMap.get(CUSTOM_FIELDS);
            if (customFieldsObj instanceof Map) {
                customFields = new HashMap<>();
                for (java.util.Map.Entry<?, ?> e : ((Map<?, ?>) customFieldsObj).entrySet()) {
                    if (e.getKey() != null && e.getValue() != null) {
                        customFields.put(String.valueOf(e.getKey()), String.valueOf(e.getValue()));
                    }
                }
            }
        } else if (queryText != null) {
            // Legacy format: no customFields key — attempt to parse delimiter from queryText
            customFields = parseLegacyQueryText(queryText);
            if (!customFields.isEmpty()) {
                queryText = customFields.remove("__queryText");
            }
        }
        return new QuerySetEntry(queryText, customFields);
    }

    /**
     * Parses legacy queryText that may contain delimiter-separated custom fields.
     * Returns a map where the query text is stored under "__queryText" key.
     *
     * <p>Handles the legacy # delimiter format: "queryText#{\"key\":\"value\"}".
     * Only treats # as a delimiter if the content after it is valid JSON.
     * This avoids misparsing queries like "What is C#?".
     */
    private static Map<String, String> parseLegacyQueryText(String queryText) {
        Map<String, String> result = new HashMap<>();
        if (queryText == null) {
            return result;
        }

        // Check for legacy # delimiter ONLY if content after # is valid JSON.
        int hashIndex = queryText.indexOf("#");
        if (hashIndex >= 0) {
            String afterHash = hashIndex < queryText.length() - 1 ? queryText.substring(hashIndex + 1) : "";
            if (!afterHash.isEmpty()) {
                String trimmed = afterHash.trim();
                if (trimmed.startsWith("{") && trimmed.endsWith("}")) {
                    Map<String, String> jsonMap = tryParseJsonToStringMap(afterHash);
                    if (jsonMap != null) {
                        result.put("__queryText", queryText.substring(0, hashIndex));
                        result.putAll(jsonMap);
                        return result;
                    }
                    // Not valid JSON — fall through to plain text handling
                }
                // Legacy plain text format: queryText#referenceAnswer
                result.put("__queryText", queryText.substring(0, hashIndex));
                result.put("referenceAnswer", afterHash);
                return result;
            }
        }

        // No delimiter found - entire string is the query text
        return result;
    }

    /**
     * Attempts to parse a JSON string into a Map of String to String.
     * Uses OpenSearch's XContentHelper instead of external Jackson dependency.
     *
     * @return parsed map, or null if parsing fails
     */
    @SuppressWarnings("unchecked")
    private static Map<String, String> tryParseJsonToStringMap(String json) {
        try {
            Map<String, Object> parsed = XContentHelper.convertToMap(XContentType.JSON.xContent(), json, false);
            Map<String, String> result = new HashMap<>();
            for (Map.Entry<String, Object> entry : parsed.entrySet()) {
                if (entry.getValue() != null) {
                    result.put(entry.getKey(), String.valueOf(entry.getValue()));
                }
            }
            return result;
        } catch (Exception e) {
            return null;
        }
    }

    public static class Builder {
        private String queryText;
        private Map<String, String> customFields;

        private Builder() {}

        private Builder(QuerySetEntry entry) {
            this.queryText = entry.queryText;
            this.customFields = entry.customFields;
        }

        public Builder queryText(String queryText) {
            this.queryText = queryText;
            return this;
        }

        public Builder customFields(Map<String, String> customFields) {
            this.customFields = customFields;
            return this;
        }

        public QuerySetEntry build() {
            return new QuerySetEntry(this.queryText, this.customFields);
        }

        public static Builder builder() {
            return new Builder();
        }

        public static Builder builder(QuerySetEntry entry) {
            return new Builder(entry);
        }
    }
}
