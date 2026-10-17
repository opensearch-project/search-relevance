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
import org.opensearch.action.update.UpdateResponse;

/**
 * Action for patching (partially updating) an experiment's metadata (name,
 * description).
 */
public class PatchExperimentAction extends ActionType<UpdateResponse> {
    /** The name of this action */
    public static final String NAME = TRANSPORT_ACTION_NAME_PREFIX + "experiment/patch";

    /** An instance of this action */
    public static final PatchExperimentAction INSTANCE = new PatchExperimentAction();

    private PatchExperimentAction() {
        super(NAME, UpdateResponse::new);
    }
}
