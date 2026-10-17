/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */
package org.opensearch.searchrelevance.transport.judgment;

import static org.opensearch.action.ValidateActions.addValidationError;

import java.io.IOException;

import org.opensearch.action.ActionRequest;
import org.opensearch.action.ActionRequestValidationException;
import org.opensearch.core.common.io.stream.StreamInput;
import org.opensearch.core.common.io.stream.StreamOutput;

/**
 * Transport Request for retrying failed documents in an existing LLM judgment.
 */
public class RetryFailedJudgmentRequest extends ActionRequest {
    private final String judgmentId;

    /**
     * @param judgmentId the ID of the judgment whose failed documents should be retried
     */
    public RetryFailedJudgmentRequest(String judgmentId) {
        this.judgmentId = judgmentId;
    }

    public RetryFailedJudgmentRequest(StreamInput in) throws IOException {
        super(in);
        this.judgmentId = in.readString();
    }

    @Override
    public void writeTo(StreamOutput out) throws IOException {
        super.writeTo(out);
        out.writeString(judgmentId);
    }

    public String getJudgmentId() {
        return judgmentId;
    }

    @Override
    public ActionRequestValidationException validate() {
        ActionRequestValidationException validationException = null;
        if (judgmentId == null || judgmentId.trim().isEmpty()) {
            validationException = addValidationError("judgmentId must not be null or empty", validationException);
        }
        return validationException;
    }
}
