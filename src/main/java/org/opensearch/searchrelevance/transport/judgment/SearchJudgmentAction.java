/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */
package org.opensearch.searchrelevance.transport.judgment;

import static org.opensearch.searchrelevance.common.PluginConstants.TRANSPORT_ACTION_NAME_PREFIX;

import org.opensearch.action.ActionType;
import org.opensearch.action.search.SearchResponse;

/**
 * External action for searching judgments.
 */
public class SearchJudgmentAction extends ActionType<SearchResponse> {
    /** The name of this action. */
    public static final String NAME = TRANSPORT_ACTION_NAME_PREFIX + "judgment/search";
    /** An instance of this action. */
    public static final SearchJudgmentAction INSTANCE = new SearchJudgmentAction();

    private SearchJudgmentAction() {
        super(NAME, SearchResponse::new);
    }
}
