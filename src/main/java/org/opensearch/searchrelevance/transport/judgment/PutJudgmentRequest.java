/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */
package org.opensearch.searchrelevance.transport.judgment;

import java.io.IOException;

import org.opensearch.action.ActionRequest;
import org.opensearch.action.ActionRequestValidationException;
import org.opensearch.common.Nullable;
import org.opensearch.core.common.io.stream.StreamInput;
import org.opensearch.core.common.io.stream.StreamOutput;

public class PutJudgmentRequest extends ActionRequest {
    private String name;
    private String description;

    public PutJudgmentRequest(String name, String description) {
        this.name = name;
        this.description = description;
    }

    public PutJudgmentRequest(StreamInput in) throws IOException {
        super(in);
        this.name = in.readString();
        this.description = in.readString();
    }

    @Override
    public void writeTo(StreamOutput out) throws IOException {
        super.writeTo(out);
        out.writeString(name);
        out.writeString(description);
    }

    public String getName() {
        return name;
    }

    @Nullable
    public String getDescription() {
        return description;
    }

    @Override
    public ActionRequestValidationException validate() {
        return null;
    }
}
