/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */
package org.opensearch.searchrelevance.transport.stats;

import java.io.IOException;

import org.opensearch.action.support.nodes.BaseNodesRequest;
import org.opensearch.core.common.io.stream.StreamInput;
import org.opensearch.core.common.io.stream.StreamOutput;
import org.opensearch.searchrelevance.stats.SearchRelevanceStatsInput;

import lombok.Getter;

/**
 * SearchRelevanceStatsRequest gets node (cluster) level Stats for search relevance
 * By default, all parameters will be true
 */
public class SearchRelevanceStatsRequest extends BaseNodesRequest<SearchRelevanceStatsRequest> {

    /**
     * Key indicating all stats should be retrieved
     */
    @Getter
    private final SearchRelevanceStatsInput searchRelevanceStatsInput;

    /**
     * Empty constructor needed for SearchRelevanceStatsTransportAction
     */
    public SearchRelevanceStatsRequest() {
        super((String[]) null);
        this.searchRelevanceStatsInput = new SearchRelevanceStatsInput();
    }

    /**
     * Constructor
     *
     * @param in input stream
     * @throws IOException in case of I/O errors
     */
    public SearchRelevanceStatsRequest(StreamInput in) throws IOException {
        super(in);
        this.searchRelevanceStatsInput = new SearchRelevanceStatsInput(in);
    }

    /**
     * Constructor
     *
     * @param nodeIds NodeIDs from which to retrieve stats
     */
    public SearchRelevanceStatsRequest(String[] nodeIds, SearchRelevanceStatsInput searchRelevanceStatsInput) {
        super(nodeIds);
        this.searchRelevanceStatsInput = searchRelevanceStatsInput;
    }

    @Override
    public void writeTo(StreamOutput out) throws IOException {
        super.writeTo(out);
        searchRelevanceStatsInput.writeTo(out);
    }
}
