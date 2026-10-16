/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */
package org.opensearch.searchrelevance.ml;

import static org.opensearch.searchrelevance.common.MLConstants.INPUT_FORMAT_SEARCH;
import static org.opensearch.searchrelevance.common.MLConstants.INPUT_FORMAT_SEARCH_WITH_REFERENCE;
import static org.opensearch.searchrelevance.common.MLConstants.PLACEHOLDER_HITS;
import static org.opensearch.searchrelevance.common.MLConstants.PLACEHOLDER_QUERY_TEXT;
import static org.opensearch.searchrelevance.common.MLConstants.PLACEHOLDER_REFERENCE;
import static org.opensearch.searchrelevance.common.MLConstants.PLACEHOLDER_REFERENCE_ANSWER;
import static org.opensearch.searchrelevance.common.MLConstants.PLACEHOLDER_RESULTS;
import static org.opensearch.searchrelevance.common.MLConstants.PLACEHOLDER_SEARCH_TEXT;

import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Factory class for building user prompts with template variable replacement.
 * Handles both custom prompt templates and default formats.
 */
public class UserPromptFactory {

    private static final Pattern TEMPLATE_VARIABLE_PATTERN = Pattern.compile("\\{\\{([^}]+)\\}\\}");

    private UserPromptFactory() {}

    /**
     * Build user content for the LLM prompt.
     * If promptTemplate is provided, replaces template variables with actual values.
     * If promptTemplate is null/empty, uses default INPUT_FORMAT_SEARCH or INPUT_FORMAT_SEARCH_WITH_REFERENCE.
     *
     * @param searchText The search query text
     * @param referenceData Map of reference data (e.g., {"referenceAnswer": "value", "category": "value"})
     * @param hitsJson The JSON string representation of search hits
     * @param promptTemplate Optional custom prompt template with {{variable}} placeholders
     * @return The formatted user content string
     */
    public static String buildUserContent(String searchText, Map<String, String> referenceData, String hitsJson, String promptTemplate) {
        // If no template provided, use default format
        if (promptTemplate == null || promptTemplate.trim().isEmpty()) {
            return buildDefaultUserContent(searchText, referenceData, hitsJson);
        }

        // Replace template variables
        return replaceTemplateVariables(promptTemplate, searchText, referenceData, hitsJson);
    }

    /**
     * Build default user content using INPUT_FORMAT_SEARCH or INPUT_FORMAT_SEARCH_WITH_REFERENCE.
     */
    private static String buildDefaultUserContent(String searchText, Map<String, String> referenceData, String hitsJson) {
        if (referenceData == null || referenceData.isEmpty()) {
            return String.format(Locale.ROOT, INPUT_FORMAT_SEARCH, searchText, hitsJson);
        } else {
            // Use referenceAnswer if available, otherwise use all reference data as a single string
            String referenceValue = getReferenceValue(referenceData);
            return String.format(Locale.ROOT, INPUT_FORMAT_SEARCH_WITH_REFERENCE, searchText, referenceValue, hitsJson);
        }
    }

    /**
     * Get reference value from referenceData map.
     * Prioritizes "referenceAnswer" key, falls back to concatenating all values.
     */
    private static String getReferenceValue(Map<String, String> referenceData) {
        if (referenceData.containsKey(PLACEHOLDER_REFERENCE_ANSWER)) {
            return referenceData.get(PLACEHOLDER_REFERENCE_ANSWER);
        }
        // Fallback: concatenate all values with delimiter
        return String.join("; ", referenceData.values());
    }

    /**
     * Replace template variables in the prompt template with actual values.
     * Supports placeholders like {{variable_name}}.
     *
     * Supported variables:
     * - {{queryText}} or {{searchText}} - replaced with the search query
     * - {{reference}} or {{referenceAnswer}} - replaced with reference answer if available
     * - {{hits}} or {{results}} - replaced with the JSON string of search hits
     * - {{key_name}} - any key from referenceData map (e.g., {{category}}, {{expectedScore}})
     *
     * @param template The template string with {{variable}} placeholders
     * @param searchText The search query text
     * @param referenceData Map of reference data
     * @param hitsJson The JSON string representation of search hits
     * @return The template with all placeholders replaced
     */
    private static String replaceTemplateVariables(String template, String searchText, Map<String, String> referenceData, String hitsJson) {
        if (template == null || template.isEmpty()) {
            return "";
        }

        String result = template;
        Matcher matcher = TEMPLATE_VARIABLE_PATTERN.matcher(template);

        while (matcher.find()) {
            String variableName = matcher.group(1).trim();
            String replacement = getVariableValue(variableName, searchText, referenceData, hitsJson);
            result = result.replace("{{" + variableName + "}}", replacement);
        }

        return result;
    }

    /**
     * Get the value for a template variable.
     */
    private static String getVariableValue(String variableName, String searchText, Map<String, String> referenceData, String hitsJson) {
        // Handle queryText/searchText
        if (PLACEHOLDER_QUERY_TEXT.equals(variableName) || PLACEHOLDER_SEARCH_TEXT.equals(variableName)) {
            return searchText != null ? searchText : "";
        }

        // Handle hits/results
        if (PLACEHOLDER_HITS.equals(variableName) || PLACEHOLDER_RESULTS.equals(variableName)) {
            return hitsJson != null ? hitsJson : "";
        }

        // Handle reference/referenceAnswer
        if (PLACEHOLDER_REFERENCE.equals(variableName) || PLACEHOLDER_REFERENCE_ANSWER.equals(variableName)) {
            if (referenceData != null && referenceData.containsKey(PLACEHOLDER_REFERENCE_ANSWER)) {
                return referenceData.get(PLACEHOLDER_REFERENCE_ANSWER);
            }
            return "";
        }

        // Handle any custom key from referenceData
        if (referenceData != null && referenceData.containsKey(variableName)) {
            return referenceData.get(variableName);
        }

        // Variable not found, return empty string
        return "";
    }
}
