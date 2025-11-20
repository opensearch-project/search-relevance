/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */
package org.opensearch.searchrelevance.transport.scheduledJob;

import static org.opensearch.searchrelevance.common.PluginConstants.TRANSPORT_ACTION_NAME_PREFIX;

import org.opensearch.action.ActionType;
import org.opensearch.action.search.SearchResponse;
import org.opensearch.common.annotation.ExperimentalApi;

/**
 * External Action for public facing RestGetScheduledExperimentAction
 */
@ExperimentalApi
public class GetScheduledExperimentAction extends ActionType<SearchResponse> {
    /** The name of this action */
    public static final String NAME = TRANSPORT_ACTION_NAME_PREFIX + "scheduledexperiment/get";

    /** An instance of this action */
    public static final GetScheduledExperimentAction INSTANCE = new GetScheduledExperimentAction();

    private GetScheduledExperimentAction() {
        super(NAME, SearchResponse::new);
    }
}
