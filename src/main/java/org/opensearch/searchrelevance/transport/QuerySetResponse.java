/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */
package org.opensearch.searchrelevance.transport;

import static org.opensearch.searchrelevance.common.Constants.QUERYSET_ID;

import java.io.IOException;

import org.opensearch.core.action.ActionResponse;
import org.opensearch.core.common.io.stream.StreamInput;
import org.opensearch.core.common.io.stream.StreamOutput;
import org.opensearch.core.xcontent.ToXContentObject;
import org.opensearch.core.xcontent.XContentBuilder;

/**
 * Transport Response from creating a queryset
 */
public class QuerySetResponse extends ActionResponse implements ToXContentObject {
    private String querySetId;

    public QuerySetResponse(String querySetId) {
        this.querySetId = querySetId;
    }

    public QuerySetResponse(StreamInput in) throws IOException {
        super(in);
        this.querySetId = in.readString();
    }

    public String getQuerySetId() {
        return this.querySetId;
    }

    @Override
    public void writeTo(StreamOutput out) throws IOException {
        out.writeString(querySetId);
    }

    @Override
    public XContentBuilder toXContent(XContentBuilder builder, Params params) throws IOException {
        return builder.startObject().field(QUERYSET_ID, this.querySetId).endObject();
    }
}
