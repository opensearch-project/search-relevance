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
import org.opensearch.action.index.IndexResponse;

public class UpdateABTestAction extends ActionType<IndexResponse> {
    public static final String NAME = TRANSPORT_ACTION_NAME_PREFIX + "ab_test/update";
    public static final UpdateABTestAction INSTANCE = new UpdateABTestAction();

    private UpdateABTestAction() {
        super(NAME, IndexResponse::new);
    }
}
