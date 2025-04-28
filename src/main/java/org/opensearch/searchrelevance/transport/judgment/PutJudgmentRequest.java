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

import reactor.util.annotation.NonNull;

public class PutJudgmentRequest extends ActionRequest {
    private String type;
    private String modelId;
    private String question;
    private String content;
    private String reference;

    public PutJudgmentRequest(
        @NonNull String type,
        @NonNull String modelId,
        @NonNull String question,
        @NonNull String content,
        String reference
    ) {
        this.type = type;
        this.modelId = modelId;
        this.question = question;
        this.content = content;
        this.reference = reference;
    }

    public PutJudgmentRequest(StreamInput in) throws IOException {
        super(in);
        this.type = in.readString();
        this.modelId = in.readString();
        this.question = in.readString();
        this.content = in.readString();
        this.reference = in.readOptionalString();
    }

    @Override
    public void writeTo(StreamOutput out) throws IOException {
        super.writeTo(out);
        out.writeString(type);
        out.writeString(modelId);
        out.writeString(question);
        out.writeString(content);
        out.writeOptionalString(reference);
    }

    public String getType() {
        return type;
    }

    public String getModelId() {
        return modelId;
    }

    public String getQuestion() {
        return question;
    }

    public String getContent() {
        return content;
    }

    @Nullable
    public String getReference() {
        return reference;
    }

    @Override
    public ActionRequestValidationException validate() {
        return null;
    }
}
