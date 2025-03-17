/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */
package org.opensearch.searchrelevance.transport;

import org.opensearch.action.ActionType;
import org.opensearch.action.support.clustermanager.AcknowledgedResponse;

public class QuerySetAction extends ActionType<AcknowledgedResponse> {
    public static final QuerySetAction INSTANCE = new QuerySetAction();
    public static final String NAME = "cluster:admin/queryset_action";

    private QuerySetAction() {
        super(NAME, AcknowledgedResponse::new);
    }
}
