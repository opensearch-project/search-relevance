/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */
package org.opensearch.searchrelevance.transport;

import java.io.IOException;

import org.opensearch.action.ActionRequest;
import org.opensearch.action.ActionRequestValidationException;
import org.opensearch.core.common.io.stream.StreamInput;
import org.opensearch.core.common.io.stream.StreamOutput;

import reactor.util.annotation.Nullable;

/**
 * Transport Request to create a queryset.
 */
public class QuerySetRequest extends ActionRequest {
    private String querySetId;

    public QuerySetRequest(@Nullable String querySetId) {
        this.querySetId = querySetId;
    }

    public QuerySetRequest(StreamInput in) throws IOException {
        super(in);
        this.querySetId = in.readOptionalString();
    }

    @Nullable
    public String getQuerySetId() {
        return this.querySetId;
    }

    @Override
    public void writeTo(StreamOutput out) throws IOException {
        super.writeTo(out);
        out.writeOptionalString(querySetId);
    }

    @Override
    public ActionRequestValidationException validate() {
        return null;
    }
}
