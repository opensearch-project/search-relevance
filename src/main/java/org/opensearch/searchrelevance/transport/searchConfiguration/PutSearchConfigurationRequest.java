/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */
package org.opensearch.searchrelevance.transport.searchConfiguration;

import java.io.IOException;

import org.opensearch.action.ActionRequest;
import org.opensearch.action.ActionRequestValidationException;
import org.opensearch.common.Nullable;
import org.opensearch.core.common.io.stream.StreamInput;
import org.opensearch.core.common.io.stream.StreamOutput;

public class PutSearchConfigurationRequest extends ActionRequest {
    private String name;
    private String queryBody;
    private String searchPipeline;

    public PutSearchConfigurationRequest(String name, String queryBody, String searchPipeline) {
        this.name = name;
        this.queryBody = queryBody;
        this.searchPipeline = searchPipeline;
    }

    public PutSearchConfigurationRequest(StreamInput in) throws IOException {
        super(in);
        this.name = in.readString();
        this.queryBody = in.readString();
        this.searchPipeline = in.readString();
    }

    @Override
    public void writeTo(StreamOutput out) throws IOException {
        super.writeTo(out);
        out.writeString(name);
        out.writeString(queryBody);
        out.writeOptionalString(searchPipeline);
    }

    public String getName() {
        return name;
    }

    public String getQueryBody() {
        return queryBody;
    }

    @Nullable
    public String getSearchPipeline() {
        return searchPipeline;
    }

    @Override
    public ActionRequestValidationException validate() {
        return null;
    }
}
