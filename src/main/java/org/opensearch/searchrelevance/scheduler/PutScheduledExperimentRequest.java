/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */
package org.opensearch.searchrelevance.scheduler;

import java.util.List;

import org.opensearch.searchrelevance.model.ExperimentType;

import lombok.Getter;
import reactor.util.annotation.NonNull;

@Getter
public class PutScheduledExperimentRequest {
    private final ExperimentType type;
    private final String jobIndexName;
    private final String scheduledExperimentResultId;
    private final String querySetId;
    private final List<String> searchConfigurationList;
    private final List<String> judgmentList;
    private final int size;

    public PutScheduledExperimentRequest(
        @NonNull ExperimentType type,
        @NonNull String jobIndexName,
        @NonNull String querySetId,
        @NonNull String scheduledExperimentResultId,
        @NonNull List<String> searchConfigurationList,
        @NonNull List<String> judgmentList,
        int size
    ) {
        this.type = type;
        this.jobIndexName = jobIndexName;
        this.querySetId = querySetId;
        this.scheduledExperimentResultId = scheduledExperimentResultId;
        this.searchConfigurationList = searchConfigurationList;
        this.judgmentList = judgmentList;
        this.size = size;
    }
}
