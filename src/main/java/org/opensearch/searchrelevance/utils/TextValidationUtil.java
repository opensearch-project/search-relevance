/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */
package org.opensearch.searchrelevance.utils;

import static org.opensearch.searchrelevance.common.MLConstants.PLACEHOLDER_HITS;
import static org.opensearch.searchrelevance.common.MLConstants.PLACEHOLDER_QUERY_TEXT;
import static org.opensearch.searchrelevance.common.MLConstants.PLACEHOLDER_RESULTS;
import static org.opensearch.searchrelevance.common.MLConstants.PLACEHOLDER_SEARCH_TEXT;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import org.opensearch.searchrelevance.model.QueryWithReference;

public class TextValidationUtil {
    private static final int DEFAULT_MAX_TEXT_LENGTH = 2000;
    private static final int MAX_NAME_LENGTH = 50;
    private static final int MAX_DESCRIPTION_LENGTH = 250;
    private static final int MAX_PROMPT_TEMPLATE_LENGTH = 10000;
    // Characters that could break JSON or cause security issues
    private static final String DANGEROUS_CHARS_PATTERN = "[\"\\\\<>]+";  // Excludes quotes, backslashes, and HTML tags
    // Characters that could break QuerySet parsing logic
    // Newline (\n), delimiter (#), and colon (:) are reserved for the format: "queryText#\nkey: value"
    private static final String QUERYSET_RESERVED_CHARS_PATTERN = "[\\r\\n#:]+";  // Excludes newline, carriage return, #, and colon

    public static class ValidationResult {
        private final boolean valid;
        private final String errorMessage;

        public ValidationResult(boolean valid, String errorMessage) {
            this.valid = valid;
            this.errorMessage = errorMessage;
        }

        public boolean isValid() {
            return valid;
        }

        public String getErrorMessage() {
            return errorMessage;
        }
    }

    /**
     * Validates text with default maximum length (2000 characters)
     *
     * @param text The text to validate
     * @return ValidationResult indicating if the text is valid
     */
    public static ValidationResult validateText(String text) {
        return validateText(text, DEFAULT_MAX_TEXT_LENGTH);
    }

    /**
     * Validates text with a specified maximum length
     *
     * @param text The text to validate
     * @param maxLength The maximum allowed length
     * @return ValidationResult indicating if the text is valid
     */
    public static ValidationResult validateText(String text, int maxLength) {
        if (text == null) {
            return new ValidationResult(false, "Text cannot be null");
        }

        if (text.isEmpty()) {
            return new ValidationResult(false, "Text cannot be empty");
        }

        if (text.length() > maxLength) {
            return new ValidationResult(false, "Text exceeds maximum length of " + maxLength + " characters");
        }

        if (text.matches(".*" + DANGEROUS_CHARS_PATTERN + ".*")) {
            return new ValidationResult(false, "Text contains invalid characters (quotes, backslashes, or HTML tags are not allowed)");
        }

        return new ValidationResult(true, null);
    }

    /**
     * Validates name field with maximum length of 50 characters
     *
     * @param name The name to validate
     * @return ValidationResult indicating if the name is valid
     */
    public static ValidationResult validateName(String name) {
        return validateText(name, MAX_NAME_LENGTH);
    }

    /**
     * Validates description field with maximum length of 250 characters
     *
     * @param description The description to validate
     * @return ValidationResult indicating if the description is valid
     */
    public static ValidationResult validateDescription(String description) {
        return validateText(description, MAX_DESCRIPTION_LENGTH);
    }

    /**
     * Validates QuerySet field values (queryText and custom field values).
     * Checks for reserved characters that would break the QuerySet parsing logic:
     * - Newline (\n) - used to separate key-value pairs in the new format
     * - Hash (#) - used as delimiter between queryText and custom fields
     * - Colon (:) - used to separate keys from values in the new format
     *
     * @param text The text to validate
     * @return ValidationResult indicating if the text is valid for QuerySet
     */
    public static ValidationResult validateQuerySetValue(String text) {
        return validateQuerySetValue(text, DEFAULT_MAX_TEXT_LENGTH);
    }

    /**
     * Validates QuerySet field values with a specified maximum length.
     * Checks for reserved characters that would break the QuerySet parsing logic:
     * - Newline (\n) - used to separate key-value pairs in the new format
     * - Hash (#) - used as delimiter between queryText and custom fields
     * - Colon (:) - used to separate keys from values in the new format
     *
     * @param text The text to validate
     * @param maxLength The maximum allowed length
     * @return ValidationResult indicating if the text is valid for QuerySet
     */
    public static ValidationResult validateQuerySetValue(String text, int maxLength) {
        if (text == null) {
            return new ValidationResult(false, "Text cannot be null");
        }

        if (text.isEmpty()) {
            return new ValidationResult(false, "Text cannot be empty");
        }

        if (text.length() > maxLength) {
            return new ValidationResult(false, "Text exceeds maximum length of " + maxLength + " characters");
        }

        if (text.matches(".*" + DANGEROUS_CHARS_PATTERN + ".*")) {
            return new ValidationResult(false, "Text contains invalid characters (quotes, backslashes, or HTML tags are not allowed)");
        }

        // Check for reserved characters - use contains() for better detection including newlines
        if (text.contains("\n") || text.contains("\r") || text.contains("#") || text.contains(":")) {
            return new ValidationResult(false, "Text contains reserved characters (newline, #, or : are not allowed in QuerySet values)");
        }

        return new ValidationResult(true, null);
    }

    /**
     * Validates QuerySet custom field keys.
     * Keys have additional restrictions to ensure they are valid identifiers.
     *
     * @param key The key to validate
     * @return ValidationResult indicating if the key is valid
     */
    public static ValidationResult validateQuerySetKey(String key) {
        if (key == null) {
            return new ValidationResult(false, "Key cannot be null");
        }

        if (key.isEmpty()) {
            return new ValidationResult(false, "Key cannot be empty");
        }

        if (key.length() > MAX_NAME_LENGTH) {
            return new ValidationResult(false, "Key exceeds maximum length of " + MAX_NAME_LENGTH + " characters");
        }

        // Keys should not contain reserved characters - use contains() for better detection including newlines
        if (key.contains("\n") || key.contains("\r") || key.contains("#") || key.contains(":")) {
            return new ValidationResult(false, "Key contains reserved characters (newline, #, or : are not allowed in QuerySet keys)");
        }

        // Keys should not contain whitespace (except single spaces within the key, not at start/end)
        if (key.trim().length() != key.length()) {
            return new ValidationResult(false, "Key cannot have leading or trailing whitespace");
        }

        // Reserved key name
        if ("queryText".equals(key)) {
            return new ValidationResult(false, "Key 'queryText' is reserved and cannot be used as a custom field name");
        }

        return new ValidationResult(true, null);
    }

    /**
     * Result class for QueryWithReference validation
     */
    public static class QueryValidationResult {
        private final boolean valid;
        private final String errorMessage;
        private final QueryWithReference queryWithReference;

        private QueryValidationResult(boolean valid, String errorMessage, QueryWithReference queryWithReference) {
            this.valid = valid;
            this.errorMessage = errorMessage;
            this.queryWithReference = queryWithReference;
        }

        public static QueryValidationResult success(QueryWithReference queryWithReference) {
            return new QueryValidationResult(true, null, queryWithReference);
        }

        public static QueryValidationResult failure(String errorMessage) {
            return new QueryValidationResult(false, errorMessage, null);
        }

        public boolean isValid() {
            return valid;
        }

        public String getErrorMessage() {
            return errorMessage;
        }

        public QueryWithReference getQueryWithReference() {
            return queryWithReference;
        }
    }

    /**
     * Validates that a prompt template contains the required placeholders and meets formatting requirements.
     * - Must contain {{hits}} or {{results}} to provide documents to the LLM for rating
     * - Must contain {{queryText}} or {{searchText}} to provide the search query
     * - Must not contain the reserved delimiter character (#)
     * - Must not exceed maximum length
     *
     * @param promptTemplate The prompt template to validate
     * @return ValidationResult indicating if the template is valid
     */
    public static ValidationResult validatePromptTemplate(String promptTemplate) {
        if (promptTemplate == null || promptTemplate.trim().isEmpty()) {
            // Null/empty templates are allowed - they will use defaults
            return new ValidationResult(true, null);
        }

        // Check length
        if (promptTemplate.length() > MAX_PROMPT_TEMPLATE_LENGTH) {
            return new ValidationResult(false, "Prompt template exceeds maximum length of " + MAX_PROMPT_TEMPLATE_LENGTH + " characters");
        }

        // Check for reserved delimiter character
        if (promptTemplate.contains(QueryWithReference.DELIMITER)) {
            return new ValidationResult(
                false,
                "Prompt template cannot contain the reserved delimiter character '"
                    + QueryWithReference.DELIMITER
                    + "' which is used to separate query text from custom fields"
            );
        }

        // Check if template contains {{hits}} or {{results}} placeholder
        boolean hasHits = promptTemplate.contains("{{" + PLACEHOLDER_HITS + "}}")
            || promptTemplate.contains("{{" + PLACEHOLDER_RESULTS + "}}");
        if (!hasHits) {
            return new ValidationResult(
                false,
                String.format(
                    Locale.ROOT,
                    "Prompt template must include either {{%s}} or {{%s}} placeholder to provide documents for rating. "
                        + "Example: 'Query: {{%s}}\\n\\nDocuments: {{%s}}'",
                    PLACEHOLDER_HITS,
                    PLACEHOLDER_RESULTS,
                    PLACEHOLDER_QUERY_TEXT,
                    PLACEHOLDER_HITS
                )
            );
        }

        // Check if template contains {{queryText}} or {{searchText}} placeholder
        boolean hasQuery = promptTemplate.contains("{{" + PLACEHOLDER_QUERY_TEXT + "}}")
            || promptTemplate.contains("{{" + PLACEHOLDER_SEARCH_TEXT + "}}");
        if (!hasQuery) {
            return new ValidationResult(
                false,
                String.format(
                    Locale.ROOT,
                    "Prompt template must include either {{%s}} or {{%s}} placeholder to provide the search query. "
                        + "Example: 'Query: {{%s}}\\n\\nDocuments: {{%s}}'",
                    PLACEHOLDER_QUERY_TEXT,
                    PLACEHOLDER_SEARCH_TEXT,
                    PLACEHOLDER_QUERY_TEXT,
                    PLACEHOLDER_HITS
                )
            );
        }

        return new ValidationResult(true, null);
    }

    /**
     * Validates and parses a query map into a QueryWithReference object.
     * Extracts queryText and validates all fields including custom key-value pairs.
     *
     * @param queryMap The raw query map from the request
     * @return QueryValidationResult containing either the validated QueryWithReference or an error message
     */
    public static QueryValidationResult validateAndParseQuery(Map<String, Object> queryMap) {
        if (queryMap == null) {
            return QueryValidationResult.failure("Query object cannot be null");
        }

        // Extract queryText
        Object queryTextObj = queryMap.get("queryText");
        if (queryTextObj == null) {
            return QueryValidationResult.failure("queryText is required");
        }
        String queryText = String.valueOf(queryTextObj);

        // Validate queryText
        ValidationResult queryTextValidation = validateQuerySetValue(queryText);
        if (!queryTextValidation.isValid()) {
            return QueryValidationResult.failure("Invalid queryText: " + queryTextValidation.getErrorMessage());
        }

        // Create customizedKeyValueMap with all entries except queryText, converting values to strings
        Map<String, String> customizedKeyValueMap = new HashMap<>();
        for (Map.Entry<String, Object> entry : queryMap.entrySet()) {
            if (!"queryText".equals(entry.getKey()) && entry.getValue() != null) {
                String key = entry.getKey();
                String value = String.valueOf(entry.getValue());

                // Validate key
                ValidationResult keyValidation = validateQuerySetKey(key);
                if (!keyValidation.isValid()) {
                    return QueryValidationResult.failure("Invalid field name '" + key + "': " + keyValidation.getErrorMessage());
                }

                // Validate value (if not empty)
                if (!value.isEmpty()) {
                    ValidationResult valueValidation = validateQuerySetValue(value);
                    if (!valueValidation.isValid()) {
                        return QueryValidationResult.failure("Invalid value for field '" + key + "': " + valueValidation.getErrorMessage());
                    }
                }

                customizedKeyValueMap.put(key, value);
            }
        }

        return QueryValidationResult.success(new QueryWithReference(queryText, customizedKeyValueMap));
    }

}
