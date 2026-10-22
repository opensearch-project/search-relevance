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
import java.util.Map;

import org.opensearch.core.common.io.stream.StreamInput;
import org.opensearch.core.common.io.stream.StreamOutput;
import org.opensearch.searchrelevance.model.JudgmentType;

import reactor.util.annotation.NonNull;

public class PutImportJudgmentRequest extends PutJudgmentRequest {
    private List<Map<String, Object>> judgmentScores;

    public PutImportJudgmentRequest(
        @NonNull JudgmentType type,
        @NonNull String name,
        @NonNull String description,
        @NonNull List<Map<String, Object>> judgmentScores
    ) {
        super(type, name, description);
        this.judgmentScores = judgmentScores;
    }

    public PutImportJudgmentRequest(StreamInput in) throws IOException {
        super(in);
        this.judgmentScores = in.readList(StreamInput::readMap);
    }

    @Override
    public void writeTo(StreamOutput out) throws IOException {
        super.writeTo(out);
        out.writeCollection(judgmentScores, StreamOutput::writeMap);
    }

    public List<Map<String, Object>> getJudgmentRatings() {
        return judgmentScores;
    }

}
