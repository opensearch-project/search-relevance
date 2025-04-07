/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */
package org.opensearch.searchrelevance.transport;

import static org.opensearch.searchrelevance.indices.SearchRelevanceIndices.QUERY_SET;

import org.opensearch.ResourceNotFoundException;
import org.opensearch.action.delete.DeleteResponse;
import org.opensearch.action.support.ActionFilters;
import org.opensearch.action.support.HandledTransportAction;
import org.opensearch.cluster.service.ClusterService;
import org.opensearch.common.inject.Inject;
import org.opensearch.core.action.ActionListener;
import org.opensearch.searchrelevance.dao.QuerySetDao;
import org.opensearch.tasks.Task;
import org.opensearch.transport.TransportService;
import org.opensearch.transport.client.Client;

public class DeleteQuerySetTransportAction extends HandledTransportAction<QuerySetRequest, DeleteResponse> {
    private final ClusterService clusterService;
    private final Client client;
    private final QuerySetDao querySetDao;

    @Inject
    public DeleteQuerySetTransportAction(
        ClusterService clusterService,
        TransportService transportService,
        ActionFilters actionFilters,
        Client client
    ) {
        super(DeleteQuerySetAction.NAME, transportService, actionFilters, QuerySetRequest::new);
        this.clusterService = clusterService;
        this.client = client;
        this.querySetDao = new QuerySetDao(client, clusterService);
    }

    @Override
    protected void doExecute(Task task, QuerySetRequest request, ActionListener<DeleteResponse> listener) {
        // Validate cluster health first
        if (!clusterService.state().routingTable().hasIndex(QUERY_SET.getIndexName())) {
            listener.onFailure(new ResourceNotFoundException("Index [" + QUERY_SET.getIndexName() + "] not found"));
            return;
        }

        try {
            String querySetId = request.getQuerySetId();
            if (querySetId == null || querySetId.trim().isEmpty()) {
                listener.onFailure(new IllegalArgumentException("Query set ID cannot be null or empty"));
                return;
            }
            querySetDao.deleteQuerySet(querySetId, listener);
        } catch (Exception e) {
            listener.onFailure(e);
        }
    }
}
