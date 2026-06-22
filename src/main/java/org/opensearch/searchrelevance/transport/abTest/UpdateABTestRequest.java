/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */
package org.opensearch.searchrelevance.transport.abTest;

import java.io.IOException;

import org.opensearch.action.ActionRequest;
import org.opensearch.action.ActionRequestValidationException;
import org.opensearch.core.common.io.stream.StreamInput;
import org.opensearch.core.common.io.stream.StreamOutput;

import lombok.Getter;

@Getter
public class UpdateABTestRequest extends ActionRequest {
    private final String testId;
    private final Boolean enabled;
    private final String searchConfigurationA;
    private final String searchConfigurationB;

    public UpdateABTestRequest(String testId, Boolean enabled, String searchConfigurationA, String searchConfigurationB) {
        this.testId = testId;
        this.enabled = enabled;
        this.searchConfigurationA = searchConfigurationA;
        this.searchConfigurationB = searchConfigurationB;
    }

    public UpdateABTestRequest(StreamInput in) throws IOException {
        super(in);
        this.testId = in.readString();
        this.enabled = in.readOptionalBoolean();
        this.searchConfigurationA = in.readOptionalString();
        this.searchConfigurationB = in.readOptionalString();
    }

    @Override
    public void writeTo(StreamOutput out) throws IOException {
        super.writeTo(out);
        out.writeString(testId);
        out.writeOptionalBoolean(enabled);
        out.writeOptionalString(searchConfigurationA);
        out.writeOptionalString(searchConfigurationB);
    }

    @Override
    public ActionRequestValidationException validate() {
        return null;
    }
}
