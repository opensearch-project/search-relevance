/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */
package org.opensearch.searchrelevance.utils;

public class TextValidationUtil {
    private static final int DEFAULT_MAX_TEXT_LENGTH = 2000;
    private static final int MAX_NAME_LENGTH = 50;
    private static final int MAX_DESCRIPTION_LENGTH = 250;
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

}
