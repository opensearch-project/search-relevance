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
import org.opensearch.common.Nullable;
import org.opensearch.core.common.io.stream.StreamInput;
import org.opensearch.core.common.io.stream.StreamOutput;

public class PutExperimentRequest extends ActionRequest {
    private String name;
    private String description;
    private String index;
    private List<String> judgmentList;
    private List<String> querySetList;

    public PutExperimentRequest(String name, String description, String index, List<String> judgmentList, List<String> querySetList) {
        this.name = name;
        this.description = description;
        this.index = index;
        this.judgmentList = judgmentList;
        this.querySetList = querySetList;
    }

    public PutExperimentRequest(StreamInput in) throws IOException {
        super(in);
        this.name = in.readString();
        this.description = in.readString();
        this.index = in.readString();
        this.judgmentList = in.readOptionalStringList();
        this.querySetList = in.readOptionalStringList();
    }

    @Override
    public void writeTo(StreamOutput out) throws IOException {
        super.writeTo(out);
        out.writeString(name);
        out.writeOptionalString(description);
        out.writeString(index);
        out.writeOptionalStringArray(judgmentList.toArray(new String[0]));
        out.writeOptionalStringArray(querySetList.toArray(new String[0]));
    }

    public String getName() {
        return name;
    }

    @Nullable
    public String getDescription() {
        return description;
    }

    public String getIndex() {
        return index;
    }

    @Nullable
    public List<String> getJudgmentList() {
        return judgmentList;
    }

    @Nullable
    public List<String> getQuerySetList() {
        return querySetList;
    }

    @Override
    public ActionRequestValidationException validate() {
        return null;
    }
}
