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

/**
 * Validates whether mutable experiment inputs still match the fingerprint captured at execution time.
 */
public class ValidateExperimentAction extends ActionType<ValidateExperimentResponse> {
    public static final String NAME = TRANSPORT_ACTION_NAME_PREFIX + "experiment/validate";

    public static final ValidateExperimentAction INSTANCE = new ValidateExperimentAction();

    private ValidateExperimentAction() {
        super(NAME, ValidateExperimentResponse::new);
    }
}
