/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */
package org.opensearch.searchrelevance.scheduler;

/**
 * Token for signaling whether a scheduled experiment has been cancelled
 */
public class ScheduledExperimentCancellationToken extends AbstractCancellationToken {
    private final String scheduledExperimentResultId;

    public ScheduledExperimentCancellationToken(String scheduledExperimentResultId) {
        this.scheduledExperimentResultId = scheduledExperimentResultId;
    }

    public String getScheduledExperimentResultId() {
        return scheduledExperimentResultId;
    }
}
