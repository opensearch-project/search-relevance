/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */
package org.opensearch.searchrelevance.transport.abTest;

import java.io.IOException;
import java.util.Map;

import org.opensearch.action.ActionRequest;
import org.opensearch.action.ActionRequestValidationException;
import org.opensearch.core.common.io.stream.StreamInput;
import org.opensearch.core.common.io.stream.StreamOutput;

import lombok.Getter;

@Getter
public class ABTestSearchRequest extends ActionRequest {
    private final String testId;
    private final Map<String, String> params;

    public ABTestSearchRequest(String testId, Map<String, String> params) {
        this.testId = testId;
        this.params = params;
    }

    public ABTestSearchRequest(StreamInput in) throws IOException {
        super(in);
        this.testId = in.readString();
        this.params = in.readMap(StreamInput::readString, StreamInput::readString);
    }

    @Override
    public void writeTo(StreamOutput out) throws IOException {
        super.writeTo(out);
        out.writeString(testId);
        out.writeMap(params, StreamOutput::writeString, StreamOutput::writeString);
    }

    @Override
    public ActionRequestValidationException validate() {
        return null;
    }
}
