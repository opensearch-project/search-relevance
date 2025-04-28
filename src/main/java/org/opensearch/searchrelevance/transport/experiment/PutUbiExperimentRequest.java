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

public class PutUbiExperimentRequest extends PutExperimentRequest {
    private final List<String> judgmentIds;

    public PutUbiExperimentRequest(
        @NonNull String index,
        @NonNull ExperimentType type,
        @NonNull String querySetId,
        @NonNull List<String> searchConfigurationList,
        int k,
        @NonNull List<String> judgmentIds
    ) {
        super(index, type, querySetId, searchConfigurationList, k);
        this.judgmentIds = judgmentIds;
    }

    public PutUbiExperimentRequest(StreamInput in) throws IOException {
        super(in);
        this.judgmentIds = in.readStringList();
    }

    @Override
    public void writeTo(StreamOutput out) throws IOException {
        super.writeTo(out);
        out.writeStringArray(judgmentIds.toArray(new String[0]));
    }

    public List<String> getJudgmentIds() {
        return judgmentIds;
    }
}
