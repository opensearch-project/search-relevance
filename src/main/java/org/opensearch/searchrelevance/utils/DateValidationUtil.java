/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */
package org.opensearch.searchrelevance.utils;

import java.time.format.DateTimeParseException;

import org.opensearch.common.time.DateFormatter;

public class DateValidationUtil {
    public static class DateValidationResult {
        private final boolean valid;
        private final String errorMessage;

        public DateValidationResult(boolean valid, String errorMessage) {
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
     * Validates date so that it is either empty, null, or fits the yyyy-MM-dd format
     *
     * @param date The date to validate
     * @return DateValidationResult indicating if the date is valid
     */
    public static DateValidationResult validateDate(String date) {
        if (date.equals("") || date == null) {
            return new DateValidationResult(true, null);
        }
        DateFormatter formatter = DateFormatter.forPattern("yyyy-MM-dd");
        try {
            formatter.parse(date);
        } catch (IllegalArgumentException e) {
            return new DateValidationResult(false, "failed to parse date field [" + date + "] with format [yyyy-MM-dd]");
        } catch (DateTimeParseException e) {
            return new DateValidationResult(false, "failed to parse date field [" + date + "] with format [yyyy-MM-dd]");
        }
        return new DateValidationResult(true, null);
    }
}
