/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */
package org.opensearch.searchrelevance.transport.experiment;

import java.io.IOException;

import org.opensearch.Version;
import org.opensearch.action.ActionRequest;
import org.opensearch.action.ActionRequestValidationException;
import org.opensearch.core.common.io.stream.StreamInput;
import org.opensearch.core.common.io.stream.StreamOutput;

import lombok.Getter;

/**
 * Request for patching (partially updating) an experiment's metadata.
 * Supports updating name and/or description fields.
 */
@Getter
public class PatchExperimentRequest extends ActionRequest {
    private final String experimentId;
    private final String name;
    private final String description;

    /**
     * Creates a new patch experiment request.
     *
     * @param experimentId the ID of the experiment to update (required, validated in {@link #validate()})
     * @param name the new name for the experiment (optional, null to keep existing)
     * @param description the new description for the experiment (optional, null to keep existing)
     */
    public PatchExperimentRequest(String experimentId, String name, String description) {
        this.experimentId = experimentId;
        this.name = name;
        this.description = description;
    }

    public PatchExperimentRequest(StreamInput in) throws IOException {
        super(in);
        this.experimentId = in.readString();
        if (in.getVersion().onOrAfter(Version.V_3_6_0)) {
            this.name = in.readOptionalString();
            this.description = in.readOptionalString();
        } else {
            this.name = null;
            this.description = null;
        }
    }

    @Override
    public void writeTo(StreamOutput out) throws IOException {
        super.writeTo(out);
        out.writeString(experimentId);
        if (out.getVersion().onOrAfter(Version.V_3_6_0)) {
            out.writeOptionalString(name);
            out.writeOptionalString(description);
        }
    }

    @Override
    public ActionRequestValidationException validate() {
        ActionRequestValidationException validationException = null;
        if (experimentId == null || experimentId.trim().isEmpty()) {
            validationException = new ActionRequestValidationException();
            validationException.addValidationError("Experiment ID cannot be null or empty");
        }
        // At least one field should be provided for update
        if (name == null && description == null) {
            if (validationException == null) {
                validationException = new ActionRequestValidationException();
            }
            validationException.addValidationError("At least one of 'name' or 'description' must be provided for update");
        }
        return validationException;
    }
}
