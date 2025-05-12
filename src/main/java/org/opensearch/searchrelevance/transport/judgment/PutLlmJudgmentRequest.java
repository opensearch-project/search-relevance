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

    private final String modelId;
    private final String querySetId;
    private final List<String> searchConfigurationList;
    private int size;

    public PutLlmJudgmentRequest(
        @NonNull JudgmentType type,
        @NonNull String name,
        @NonNull String description,
        @NonNull String modelId,
        @NonNull String querySetId,
        @NonNull List<String> searchConfigurationList,
        int size
    ) {
        super(type, name, description);
        this.modelId = modelId;
        this.querySetId = querySetId;
        this.searchConfigurationList = searchConfigurationList;
        this.size = size;
    }

    public PutLlmJudgmentRequest(StreamInput in) throws IOException {
        super(in);
        this.modelId = in.readString();
        this.querySetId = in.readString();
        this.searchConfigurationList = in.readStringList();
        this.size = in.readInt();
    }

    @Override
    public void writeTo(StreamOutput out) throws IOException {
        super.writeTo(out);
        out.writeString(modelId);
        out.writeString(querySetId);
        out.writeStringArray(searchConfigurationList.toArray(new String[0]));
        out.writeInt(size);
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

}
