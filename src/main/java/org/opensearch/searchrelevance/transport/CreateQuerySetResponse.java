/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */
package org.opensearch.searchrelevance.transport;

import java.io.IOException;

import org.opensearch.core.action.ActionResponse;
import org.opensearch.core.common.io.stream.StreamInput;
import org.opensearch.core.common.io.stream.StreamOutput;

public class CreateQuerySetResponse extends ActionResponse {
    private final String querySetId;

    public CreateQuerySetResponse(String querySetId) {
        this.querySetId = querySetId;
    }

    public CreateQuerySetResponse(StreamInput in) throws IOException {
        super(in);
        this.querySetId = in.readOptionalString();
    }

    public String getQuerySetId() {
        return querySetId;
    }

    @Override
    public void writeTo(StreamOutput out) throws IOException {
        out.writeString(querySetId);
    }
}
