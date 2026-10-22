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
import org.opensearch.core.common.io.stream.StreamInput;
import org.opensearch.core.common.io.stream.StreamOutput;
import org.opensearch.searchrelevance.model.JudgmentType;

import reactor.util.annotation.NonNull;

public class PutJudgmentRequest extends ActionRequest {
    private final JudgmentType type;
    private final String name;
    private final String description;

    public PutJudgmentRequest(@NonNull JudgmentType type, @NonNull String name, @NonNull String description) {
        this.type = type;
        this.name = name;
        this.description = description;
    }

    public PutJudgmentRequest(StreamInput in) throws IOException {
        super(in);
        this.type = in.readEnum(JudgmentType.class);
        this.name = in.readString();
        this.description = in.readOptionalString();
    }

    @Override
    public ActionRequestValidationException validate() {
        return null;
    }

    @Override
    public void writeTo(StreamOutput out) throws IOException {
        super.writeTo(out);
        out.writeEnum(type);
        out.writeString(name);
        out.writeOptionalString(description);
    }

    public JudgmentType getType() {
        return type;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

}
