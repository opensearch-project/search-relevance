/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */
package org.opensearch.searchrelevance.utils;

public class TextValidationUtil {
    private static final int MAX_TEXT_LENGTH = 2000;
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

    public static ValidationResult validateText(String text) {
        if (text == null) {
            return new ValidationResult(false, "Text cannot be null");
        }

        if (text.isEmpty()) {
            return new ValidationResult(false, "Text cannot be empty");
        }

        if (text.length() > MAX_TEXT_LENGTH) {
            return new ValidationResult(false, "Text exceeds maximum length of " + MAX_TEXT_LENGTH + " characters");
        }

        if (text.matches(".*" + DANGEROUS_CHARS_PATTERN + ".*")) {
            return new ValidationResult(false, "Text contains invalid characters (quotes, backslashes, or HTML tags are not allowed)");
        }

        return new ValidationResult(true, null);
    }

}
