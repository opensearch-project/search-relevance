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
import org.opensearch.action.index.IndexResponse;

/**
 * External Action for public facing RestUpdateJudgmentRatingsAction.
 *
 * <p>Updates the judgmentRatings of an existing LLM judgment in place (e.g. a manual edit that moves
 * a doc between ratings and failures, or overwrites a rating). No model call is made.
 */
public class UpdateJudgmentRatingsAction extends ActionType<IndexResponse> {
    /** The name of this action */
    public static final String NAME = TRANSPORT_ACTION_NAME_PREFIX + "judgment/update_ratings";

    /** An instance of this action */
    public static final UpdateJudgmentRatingsAction INSTANCE = new UpdateJudgmentRatingsAction();

    private UpdateJudgmentRatingsAction() {
        super(NAME, IndexResponse::new);
    }
}
