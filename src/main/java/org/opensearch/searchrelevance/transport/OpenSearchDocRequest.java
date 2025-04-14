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
 * Transport Request to get or delete a document from system indices.
 */
public class OpenSearchDocRequest extends ActionRequest {
    private final String id;
    private final SearchSourceBuilder searchSourceBuilder;

    public OpenSearchDocRequest(String id) {
        this.id = id;
        this.searchSourceBuilder = new SearchSourceBuilder();
    }

    public OpenSearchDocRequest(SearchSourceBuilder searchSourceBuilder) {
        this.id = null;
        this.searchSourceBuilder = searchSourceBuilder;
    }

    public OpenSearchDocRequest(StreamInput in) throws IOException {
        super(in);
        this.id = in.readString();
        this.searchSourceBuilder = new SearchSourceBuilder(in);
    }

    @Override
    public void writeTo(StreamOutput out) throws IOException {
        super.writeTo(out);
        out.writeString(id);
        searchSourceBuilder.writeTo(out);
    }

    public String getId() {
        return this.id;
    }

    public SearchSourceBuilder getSearchSourceBuilder() {
        return this.searchSourceBuilder;
    }

    @Override
    public ActionRequestValidationException validate() {
        return null;
    }
}
