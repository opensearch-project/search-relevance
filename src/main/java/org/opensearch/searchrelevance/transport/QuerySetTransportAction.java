/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */
package org.opensearch.searchrelevance.transport;

import org.opensearch.action.support.ActionFilters;
import org.opensearch.action.support.HandledTransportAction;
import org.opensearch.action.support.clustermanager.AcknowledgedResponse;
import org.opensearch.cluster.service.ClusterService;
import org.opensearch.common.inject.Inject;
import org.opensearch.core.action.ActionListener;
import org.opensearch.tasks.Task;
import org.opensearch.transport.TransportService;
import org.opensearch.transport.client.Client;

/**
 * TransportAction to handle create queryset operation
 */
public class QuerySetTransportAction extends HandledTransportAction<QuerySetRequest, AcknowledgedResponse> {

    private final ClusterService clusterService;
    private final Client client;

    @Inject
    public QuerySetTransportAction(
        ClusterService clusterService,
        TransportService transportService,
        ActionFilters actionFilters,
        Client client
    ) {
        super(QuerySetAction.NAME, transportService, actionFilters, QuerySetRequest::new);
        this.clusterService = clusterService;
        this.client = client;
    }

    @Override
    protected void doExecute(Task task, QuerySetRequest request, ActionListener<AcknowledgedResponse> actionListener) {
        actionListener.onResponse(new AcknowledgedResponse(true));
    }
}
