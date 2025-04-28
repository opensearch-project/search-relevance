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

    public PutLlmExperimentRequest(
        @NonNull String index,
        @NonNull ExperimentType type,
        @NonNull String querySetId,
        @NonNull List<String> searchConfigurationList,
        int k,
        @NonNull String modelId
    ) {
        super(index, type, querySetId, searchConfigurationList, k);
        this.modelId = modelId;
    }

    public PutLlmExperimentRequest(StreamInput in) throws IOException {
        super(in);
        this.modelId = in.readString();
    }

    @Override
    public void writeTo(StreamOutput out) throws IOException {
        super.writeTo(out);
        out.writeString(modelId);
    }

    public String getModelId() {
        return modelId;
    }
}
