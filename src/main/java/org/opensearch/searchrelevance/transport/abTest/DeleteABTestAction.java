/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */
package org.opensearch.searchrelevance.transport.abTest;

import static org.opensearch.searchrelevance.common.PluginConstants.TRANSPORT_ACTION_NAME_PREFIX;

import org.opensearch.action.ActionType;
import org.opensearch.action.delete.DeleteResponse;

public class DeleteABTestAction extends ActionType<DeleteResponse> {
    public static final String NAME = TRANSPORT_ACTION_NAME_PREFIX + "ab_test/delete";
    public static final DeleteABTestAction INSTANCE = new DeleteABTestAction();

    private DeleteABTestAction() {
        super(NAME, DeleteResponse::new);
    }
}
