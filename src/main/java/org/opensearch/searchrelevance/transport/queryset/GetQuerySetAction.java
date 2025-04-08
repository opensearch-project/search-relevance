/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */
package org.opensearch.searchrelevance.transport.queryset;

import static org.opensearch.searchrelevance.common.PluginConstants.TRANSPORT_ACTION_NAME_PREFIX;

import org.opensearch.action.ActionType;
import org.opensearch.action.search.SearchResponse;

/**
 * External Action for public facing RestGetWorkflowAction
 */
public class GetQuerySetAction extends ActionType<SearchResponse> {
    /** The name of this action */
    public static final String NAME = TRANSPORT_ACTION_NAME_PREFIX + "queryset/get";

    /** An instance of this action */
    public static final GetQuerySetAction INSTANCE = new GetQuerySetAction();

    private GetQuerySetAction() {
        super(NAME, SearchResponse::new);
    }
}
