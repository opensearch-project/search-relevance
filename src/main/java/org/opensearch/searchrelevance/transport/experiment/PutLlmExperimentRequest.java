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

import org.opensearch.core.common.io.stream.StreamInput;
import org.opensearch.core.common.io.stream.StreamOutput;
import org.opensearch.searchrelevance.model.ExperimentType;

import reactor.util.annotation.NonNull;

public class PutLlmExperimentRequest extends PutExperimentRequest {

    private final String modelId;

    /**
     * The token limit sent to the LLM. This indicates the max token allowed.
     * A helpful rule of thumb is that one token generally corresponds to ~4 characters of text for common English text.
     * This translates to roughly ¾ of a word (so 100 tokens ~= 75 words).
     * Feel free to learn about language model tokenization - https://platform.openai.com/tokenizer
     */
    private int tokenLimit;

    /**
     * A list of fields contained in the document sources that will be used as context for judgment generation.
     */
    private List<String> contextFields;

    public PutLlmExperimentRequest(
        @NonNull ExperimentType type,
        @NonNull String querySetId,
        @NonNull List<String> searchConfigurationList,
        @NonNull List<String> judgmentList,
        @NonNull String modelId,
        int size,
        int tokenLimit,
        List<String> contextFields
    ) {
        super(type, querySetId, searchConfigurationList, judgmentList, size);
        this.modelId = modelId;
        this.tokenLimit = tokenLimit;
        this.contextFields = contextFields;
    }

    public PutLlmExperimentRequest(StreamInput in) throws IOException {
        super(in);
        this.modelId = in.readString();
        this.tokenLimit = in.readOptionalInt();
        this.contextFields = in.readOptionalStringList();
    }

    @Override
    public void writeTo(StreamOutput out) throws IOException {
        super.writeTo(out);
        out.writeString(modelId);
        out.writeOptionalInt(tokenLimit);
        out.writeOptionalStringArray(contextFields.toArray(new String[0]));
    }

    public String getModelId() {
        return modelId;
    }

    public int getTokenLimit() {
        return tokenLimit;
    }

    public List<String> getContextFields() {
        return contextFields;
    }
}
