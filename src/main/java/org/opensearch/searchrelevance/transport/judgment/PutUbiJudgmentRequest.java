/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */
package org.opensearch.searchrelevance.transport.judgment;

import java.io.IOException;

import org.opensearch.core.common.io.stream.StreamInput;
import org.opensearch.core.common.io.stream.StreamOutput;
import org.opensearch.searchrelevance.model.JudgmentType;

import reactor.util.annotation.NonNull;

public class PutUbiJudgmentRequest extends PutJudgmentRequest {
    private String clickModel;
    private int maxRank;
    private String startDate;
    private String endDate;

    public PutUbiJudgmentRequest(
        @NonNull JudgmentType type,
        @NonNull String name,
        @NonNull String description,
        @NonNull String clickModel,
        int maxRank,
        @NonNull String startDate,
        @NonNull String endDate
    ) {
        super(type, name, description);
        this.clickModel = clickModel;
        this.maxRank = maxRank;
        this.startDate = startDate;
        this.endDate = endDate;
    }

    public PutUbiJudgmentRequest(StreamInput in) throws IOException {
        super(in);
        this.clickModel = in.readString();
        this.maxRank = in.readInt();
        this.startDate = in.readString();
        this.endDate = in.readString();
    }

    @Override
    public void writeTo(StreamOutput out) throws IOException {
        super.writeTo(out);
        out.writeString(clickModel);
        out.writeInt(maxRank);
        out.writeString(startDate);
        out.writeString(endDate);
    }

    public String getClickModel() {
        return clickModel;
    }

    public int getMaxRank() {
        return maxRank;
    }

    public String getStartDate() {
        return startDate;
    }

    public String getEndDate() {
        return endDate;
    }
}
