/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */
package org.opensearch.searchrelevance.transport.stats;

import java.io.IOException;

import org.opensearch.core.common.io.stream.StreamInput;
import org.opensearch.core.common.io.stream.StreamOutput;
import org.opensearch.transport.TransportRequest;

import lombok.Getter;

/**
 *  SearchRelevanceStatsNodeRequest represents the request to an individual node
 */
public class SearchRelevanceStatsNodeRequest extends TransportRequest {
    @Getter
    private SearchRelevanceStatsRequest request;

    /**
     * Constructor
     */
    public SearchRelevanceStatsNodeRequest() {
        super();
    }

    /**
     * Constructor
     *
     * @param in input stream
     * @throws IOException in case of I/O errors
     */
    public SearchRelevanceStatsNodeRequest(StreamInput in) throws IOException {
        super(in);
        request = new SearchRelevanceStatsRequest(in);
    }

    /**
     * Constructor
     *
     * @param request SearchRelevanceStatsRequest
     */
    public SearchRelevanceStatsNodeRequest(SearchRelevanceStatsRequest request) {
        this.request = request;
    }

    @Override
    public void writeTo(StreamOutput out) throws IOException {
        super.writeTo(out);
        request.writeTo(out);
    }
}
