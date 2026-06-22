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
public class PutABTestRequest extends ActionRequest {
    private final String testId;
    private final String searchConfigurationA;
    private final String searchConfigurationB;
    private final Boolean enabled;

    public PutABTestRequest(String testId, String searchConfigurationA, String searchConfigurationB, Boolean enabled) {
        this.testId = testId;
        this.searchConfigurationA = searchConfigurationA;
        this.searchConfigurationB = searchConfigurationB;
        this.enabled = enabled;
    }

    public PutABTestRequest(StreamInput in) throws IOException {
        super(in);
        this.testId = in.readString();
        this.searchConfigurationA = in.readString();
        this.searchConfigurationB = in.readString();
        this.enabled = in.readOptionalBoolean();
    }

    @Override
    public void writeTo(StreamOutput out) throws IOException {
        super.writeTo(out);
        out.writeString(testId);
        out.writeString(searchConfigurationA);
        out.writeString(searchConfigurationB);
        out.writeOptionalBoolean(enabled);
    }

    @Override
    public ActionRequestValidationException validate() {
        return null;
    }
}
