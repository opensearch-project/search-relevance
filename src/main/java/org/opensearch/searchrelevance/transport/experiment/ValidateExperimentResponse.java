/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */
package org.opensearch.searchrelevance.transport.experiment;

import java.io.IOException;
import java.util.List;
import java.util.Objects;

import org.opensearch.core.action.ActionResponse;
import org.opensearch.core.common.io.stream.StreamInput;
import org.opensearch.core.common.io.stream.StreamOutput;
import org.opensearch.core.xcontent.ToXContent;
import org.opensearch.core.xcontent.ToXContentObject;
import org.opensearch.core.xcontent.XContentBuilder;

/**
 * Response for {@link ValidateExperimentAction} describing whether experiment inputs have drifted.
 */
public class ValidateExperimentResponse extends ActionResponse implements ToXContentObject {
    public static final String STATUS = "status";
    public static final String DRIFTED_INPUTS = "drifted_inputs";
    public static final String MESSAGE = "message";

    public static final String STATUS_VALID = "VALID";
    public static final String STATUS_DRIFTED = "DRIFTED";
    public static final String STATUS_UNAVAILABLE = "UNAVAILABLE";

    private final String status;
    private final List<String> driftedInputs;
    private final String message;

    public ValidateExperimentResponse(String status, List<String> driftedInputs, String message) {
        this.status = Objects.requireNonNull(status, "status");
        this.driftedInputs = driftedInputs == null ? List.of() : List.copyOf(driftedInputs);
        this.message = message == null ? "" : message;
    }

    public ValidateExperimentResponse(StreamInput in) throws IOException {
        super(in);
        this.status = in.readString();
        this.driftedInputs = in.readStringList();
        this.message = in.readString();
    }

    public static ValidateExperimentResponse valid() {
        return new ValidateExperimentResponse(STATUS_VALID, List.of(), "Experiment inputs match the stored execution fingerprint.");
    }

    public static ValidateExperimentResponse unavailable(String message) {
        return new ValidateExperimentResponse(STATUS_UNAVAILABLE, List.of(), message);
    }

    public static ValidateExperimentResponse drifted(List<String> driftedInputs, String message) {
        return new ValidateExperimentResponse(STATUS_DRIFTED, driftedInputs, message);
    }

    public String status() {
        return status;
    }

    public List<String> driftedInputs() {
        return driftedInputs;
    }

    public String message() {
        return message;
    }

    @Override
    public void writeTo(StreamOutput out) throws IOException {
        out.writeString(status);
        out.writeStringCollection(driftedInputs);
        out.writeString(message);
    }

    @Override
    public XContentBuilder toXContent(XContentBuilder builder, ToXContent.Params params) throws IOException {
        XContentBuilder b = builder.startObject();
        b.field(STATUS, status);
        b.field(DRIFTED_INPUTS, driftedInputs);
        b.field(MESSAGE, message);
        return b.endObject();
    }
}
