/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */
package org.opensearch.searchrelevance.transport.judgment;

import java.io.IOException;
import java.util.List;

import org.opensearch.core.common.io.stream.StreamInput;
import org.opensearch.core.common.io.stream.StreamOutput;
import org.opensearch.searchrelevance.model.JudgmentType;

import reactor.util.annotation.NonNull;

public class PutLlmJudgmentRequest extends PutJudgmentRequest {

    /**
     * LLM defaulted token limits
     */
    public static final Integer DEFAULTED_TOKEN_LIMIT = 1000;

    private final String modelId;
    private final String querySetId;
    private final List<String> searchConfigurationList;
    private int size;

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

    public PutLlmJudgmentRequest(
        @NonNull JudgmentType type,
        @NonNull String name,
        @NonNull String description,
        @NonNull String modelId,
        @NonNull String querySetId,
        @NonNull List<String> searchConfigurationList,
        int size,
        int tokenLimit,
        List<String> contextFields
    ) {
        super(type, name, description);
        this.modelId = modelId;
        this.querySetId = querySetId;
        this.searchConfigurationList = searchConfigurationList;
        this.size = size;
        this.tokenLimit = tokenLimit;
        this.contextFields = contextFields;
    }

    public PutLlmJudgmentRequest(StreamInput in) throws IOException {
        super(in);
        this.modelId = in.readString();
        this.querySetId = in.readString();
        this.searchConfigurationList = in.readStringList();
        this.size = in.readInt();
        this.tokenLimit = in.readOptionalInt();
        this.contextFields = in.readOptionalStringList();
    }

    @Override
    public void writeTo(StreamOutput out) throws IOException {
        super.writeTo(out);
        out.writeString(modelId);
        out.writeString(querySetId);
        out.writeStringArray(searchConfigurationList.toArray(new String[0]));
        out.writeInt(size);
        out.writeOptionalInt(tokenLimit);
        out.writeOptionalStringArray(contextFields.toArray(new String[0]));
    }

    public String getModelId() {
        return modelId;
    }

    public String getQuerySetId() {
        return querySetId;
    }

    public List<String> getSearchConfigurationList() {
        return searchConfigurationList;
    }

    public int getSize() {
        return size;
    }

    public int getTokenLimit() {
        return tokenLimit;
    }

    public List<String> getContextFields() {
        return contextFields;
    }

}
