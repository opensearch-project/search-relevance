/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */
package org.opensearch.searchrelevance.rest;

import static java.util.Collections.singletonList;
import static org.opensearch.rest.RestRequest.Method.POST;
import static org.opensearch.searchrelevance.common.Constants.QUERYSET_ID;
import static org.opensearch.searchrelevance.common.Constants.QUERYSET_URI;

import java.util.List;

import org.opensearch.rest.BaseRestHandler;
import org.opensearch.rest.RestRequest;
import org.opensearch.rest.action.RestToXContentListener;
import org.opensearch.searchrelevance.transport.QuerySetAction;
import org.opensearch.searchrelevance.transport.QuerySetRequest;
import org.opensearch.transport.client.node.NodeClient;

public class RestCreateQuerySetAction extends BaseRestHandler {

    public static final String NAME = "queryset_action";

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public List<Route> routes() {
        return singletonList(new Route(POST, QUERYSET_URI));
    }

    @Override
    protected RestChannelConsumer prepareRequest(RestRequest request, NodeClient client) {
        String querySetId = request.param(QUERYSET_ID);
        QuerySetRequest querySetRequest = new QuerySetRequest(querySetId);
        return channel -> client.execute(QuerySetAction.INSTANCE, querySetRequest, new RestToXContentListener<>(channel));
    }
}
