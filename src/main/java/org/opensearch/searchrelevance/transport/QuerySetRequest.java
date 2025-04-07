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
import org.opensearch.search.builder.SearchSourceBuilder;

/**
 * Transport Request to get or delete a queryset.
 */
public class QuerySetRequest extends ActionRequest {
    private final String querySetId;
    private final SearchSourceBuilder searchSourceBuilder;

    public QuerySetRequest(String querySetId) {
        this.querySetId = querySetId;
        this.searchSourceBuilder = new SearchSourceBuilder();
    }

    public QuerySetRequest(SearchSourceBuilder searchSourceBuilder) {
        this.querySetId = null;
        this.searchSourceBuilder = searchSourceBuilder;
    }

    public QuerySetRequest(StreamInput in) throws IOException {
        super(in);
        this.querySetId = in.readOptionalString();
        this.searchSourceBuilder = new SearchSourceBuilder(in);
    }

    @Override
    public void writeTo(StreamOutput out) throws IOException {
        super.writeTo(out);
        out.writeOptionalString(querySetId);
        searchSourceBuilder.writeTo(out);
    }

    public String getQuerySetId() {
        return this.querySetId;
    }

    public SearchSourceBuilder getSearchSourceBuilder() {
        return this.searchSourceBuilder;
    }

    @Override
    public ActionRequestValidationException validate() {
        return null;
    }
}
