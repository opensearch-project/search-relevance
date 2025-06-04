/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */
package org.opensearch.searchrelevance.transport.stats;

import org.opensearch.action.ActionType;
import org.opensearch.core.common.io.stream.Writeable;

/**
 * SearchRelevanceStatsAction class
 */
public class SearchRelevanceStatsAction extends ActionType<SearchRelevanceStatsResponse> {

    public static final SearchRelevanceStatsAction INSTANCE = new SearchRelevanceStatsAction();
    public static final String NAME = "cluster:admin/search_relevance_stats_action";

    /**
     * Constructor
     */
    private SearchRelevanceStatsAction() {
        super(NAME, SearchRelevanceStatsResponse::new);
    }

    @Override
    public Writeable.Reader<SearchRelevanceStatsResponse> getResponseReader() {
        return SearchRelevanceStatsResponse::new;
    }
}
