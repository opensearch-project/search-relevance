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

import org.opensearch.action.ActionRequest;
import org.opensearch.action.ActionRequestValidationException;
import org.opensearch.core.common.io.stream.StreamInput;
import org.opensearch.core.common.io.stream.StreamOutput;
import org.opensearch.searchrelevance.model.ExperimentType;

import reactor.util.annotation.NonNull;

public class PutExperimentRequest extends ActionRequest {
    private final ExperimentType type;
    private final String querySetId;
    private final List<String> searchConfigurationList;
    private final List<String> judgmentList;
    private int size;

    public PutExperimentRequest(
        @NonNull ExperimentType type,
        @NonNull String querySetId,
        @NonNull List<String> searchConfigurationList,
        @NonNull List<String> judgmentList,
        int size
    ) {
        this.type = type;
        this.querySetId = querySetId;
        this.searchConfigurationList = searchConfigurationList;
        this.judgmentList = judgmentList;
        this.size = size;
    }

    public PutExperimentRequest(StreamInput in) throws IOException {
        super(in);
        this.type = in.readEnum(ExperimentType.class);
        ;
        this.querySetId = in.readString();
        this.searchConfigurationList = in.readStringList();
        this.judgmentList = in.readStringList();
        this.size = in.readInt();
    }

    @Override
    public void writeTo(StreamOutput out) throws IOException {
        super.writeTo(out);
        out.writeEnum(type);
        out.writeString(querySetId);
        out.writeStringArray(searchConfigurationList.toArray(new String[0]));
        out.writeStringArray(judgmentList.toArray(new String[0]));
        out.writeInt(size);
    }

    public ExperimentType getType() {
        return type;
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

    public List<String> getJudgmentList() {
        return judgmentList;
    }

    @Override
    public ActionRequestValidationException validate() {
        return null;
    }
}
