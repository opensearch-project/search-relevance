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

public class ABTestSearchAction extends ActionType<ABTestSearchResponse> {
    public static final String NAME = TRANSPORT_ACTION_NAME_PREFIX + "ab_test/search";
    public static final ABTestSearchAction INSTANCE = new ABTestSearchAction();

    private ABTestSearchAction() {
        super(NAME, ABTestSearchResponse::new);
    }
}
