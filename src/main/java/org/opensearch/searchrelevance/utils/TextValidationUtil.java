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

}
