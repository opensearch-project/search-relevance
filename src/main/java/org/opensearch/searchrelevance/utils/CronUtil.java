/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */
package org.opensearch.searchrelevance.utils;

import java.time.Duration;
import java.time.ZonedDateTime;

import org.opensearch.jobscheduler.repackage.com.cronutils.model.CronType;
import org.opensearch.jobscheduler.repackage.com.cronutils.model.definition.CronDefinitionBuilder;
import org.opensearch.jobscheduler.repackage.com.cronutils.model.time.ExecutionTime;
import org.opensearch.jobscheduler.repackage.com.cronutils.parser.CronParser;
import org.opensearch.searchrelevance.settings.SearchRelevanceSettingsAccessor;

import lombok.AllArgsConstructor;

@AllArgsConstructor
public class CronUtil {
    private SearchRelevanceSettingsAccessor settingsAccessor;

    public static class CronValidationResult {
        private final boolean valid;
        private final String errorMessage;

        public CronValidationResult(boolean valid, String errorMessage) {
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
     * Validates cron expression so that it is non-empty, non-null, and adheres to the
     * cron job syntax https://en.wikipedia.org/wiki/Cron
     *
     * @param cronExpression The expression to validate
     * @return CronValidationResult indicating if the cron expression is valid
     */
    public CronValidationResult validateCron(String cronExpression) {
        if (cronExpression.equals("") || cronExpression == null) {
            return new CronValidationResult(false, "cron expression cannot be null or empty");
        }

        CronParser cronParser = new CronParser(CronDefinitionBuilder.instanceDefinitionFor(CronType.UNIX));
        try {
            cronParser.parse(cronExpression);
        } catch (IllegalArgumentException e) {
            return new CronValidationResult(
                false,
                "failed to parse cron expression. For more information check out https://en.wikipedia.org/wiki/Cron. For example, \"12 * * * *\""
            );
        }
        ZonedDateTime now = ZonedDateTime.now();

        ExecutionTime executionTime = ExecutionTime.forCron(cronParser.parse(cronExpression));
        ZonedDateTime nextExecution = executionTime.nextExecution(now).get();
        ZonedDateTime nextNextExecution = executionTime.nextExecution(nextExecution).get();

        Duration interval = Duration.between(nextExecution, nextNextExecution);

        if (interval.toSeconds() < this.settingsAccessor.getScheduledExperimentsMinimumInterval().getSeconds()) {
            return new CronValidationResult(false, "The interval between scheduled experiment runs is too short.");
        }
        return new CronValidationResult(true, null);
    }
}
