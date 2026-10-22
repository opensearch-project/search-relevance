/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */
package org.opensearch.searchrelevance.transport.experiment;

import static org.opensearch.searchrelevance.common.PluginConstants.TRANSPORT_ACTION_NAME_PREFIX;

import org.opensearch.action.ActionType;
import org.opensearch.action.search.SearchResponse;

/**
 * External action for searching experiments.
 */
public class SearchExperimentAction extends ActionType<SearchResponse> {
    /** The name of this action. */
    public static final String NAME = TRANSPORT_ACTION_NAME_PREFIX + "experiment/search";
    /** An instance of this action. */
    public static final SearchExperimentAction INSTANCE = new SearchExperimentAction();

    private SearchExperimentAction() {
        super(NAME, SearchResponse::new);
    }
}
