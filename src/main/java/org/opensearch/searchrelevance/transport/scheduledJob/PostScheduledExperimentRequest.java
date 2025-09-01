/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */
package org.opensearch.searchrelevance.transport.scheduledJob;

import java.io.IOException;

import org.opensearch.action.ActionRequest;
import org.opensearch.action.ActionRequestValidationException;
import org.opensearch.core.common.io.stream.StreamInput;
import org.opensearch.core.common.io.stream.StreamOutput;
import org.opensearch.searchrelevance.utils.CronUtils;

import reactor.util.annotation.NonNull;

public class PostScheduledExperimentRequest extends ActionRequest {
    private final String experimentId;
    private final String cronExpression;

    public PostScheduledExperimentRequest(@NonNull String experimentId, @NonNull String cronExpression) {
        this.experimentId = experimentId;
        this.cronExpression = cronExpression;
    }

    public PostScheduledExperimentRequest(StreamInput in) throws IOException {
        super(in);
        this.experimentId = in.readString();
        this.cronExpression = in.readString();
    }

    @Override
    public ActionRequestValidationException validate() {
        if ((experimentId == null) || (experimentId.equals(""))) {
            return new ActionRequestValidationException();
        }
        if (CronUtils.validateCron(cronExpression).isValid() == false) {
            return new ActionRequestValidationException();
        }
        return null;
    }

    @Override
    public void writeTo(StreamOutput out) throws IOException {
        super.writeTo(out);
        out.writeString(experimentId);
        out.writeString(cronExpression);
    }

    public String getExperimentId() {
        return experimentId;
    }

    public String getCronExpression() {
        return cronExpression;
    }
}
