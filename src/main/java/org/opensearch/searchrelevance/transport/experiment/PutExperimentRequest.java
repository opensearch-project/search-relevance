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
    private final String index;
    private final ExperimentType type;
    private final String querySetId;
    private final List<String> searchConfigurationList;
    private int k;

    public PutExperimentRequest(
        @NonNull String index,
        @NonNull ExperimentType type,
        @NonNull String querySetId,
        @NonNull List<String> searchConfigurationList,
        int k
    ) {
        this.index = index;
        this.type = type;
        this.querySetId = querySetId;
        this.searchConfigurationList = searchConfigurationList;
        this.k = k;
    }

    public PutExperimentRequest(StreamInput in) throws IOException {
        super(in);
        this.index = in.readString();
        this.type = in.readEnum(ExperimentType.class);
        ;
        this.querySetId = in.readString();
        this.searchConfigurationList = in.readStringList();
        this.k = in.readInt();
    }

    @Override
    public void writeTo(StreamOutput out) throws IOException {
        super.writeTo(out);
        out.writeString(index);
        out.writeEnum(type);
        out.writeString(querySetId);
        out.writeStringArray(searchConfigurationList.toArray(new String[0]));
        out.writeInt(k);
    }

    public String getIndex() {
        return index;
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

    public int getK() {
        return k;
    }

    @Override
    public ActionRequestValidationException validate() {
        return null;
    }
}
