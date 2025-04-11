/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */
package org.opensearch.searchrelevance.transport.queryset;

import static org.opensearch.searchrelevance.indices.SearchRelevanceIndices.QUERY_SET;

import org.opensearch.ResourceNotFoundException;
import org.opensearch.action.delete.DeleteResponse;
import org.opensearch.action.support.ActionFilters;
import org.opensearch.action.support.HandledTransportAction;
import org.opensearch.cluster.service.ClusterService;
import org.opensearch.common.inject.Inject;
import org.opensearch.core.action.ActionListener;
import org.opensearch.core.rest.RestStatus;
import org.opensearch.searchrelevance.dao.QuerySetDao;
import org.opensearch.searchrelevance.exception.SearchRelevanceException;
import org.opensearch.searchrelevance.transport.OpenSearchDocRequest;
import org.opensearch.tasks.Task;
import org.opensearch.transport.TransportService;

public class DeleteQuerySetTransportAction extends HandledTransportAction<OpenSearchDocRequest, DeleteResponse> {
    private final ClusterService clusterService;
    private final QuerySetDao querySetDao;

    @Inject
    public DeleteQuerySetTransportAction(
        ClusterService clusterService,
        TransportService transportService,
        ActionFilters actionFilters,
        QuerySetDao querySetDao
    ) {
        super(DeleteQuerySetAction.NAME, transportService, actionFilters, OpenSearchDocRequest::new);
        this.clusterService = clusterService;
        this.querySetDao = querySetDao;
    }

    @Override
    protected void doExecute(Task task, OpenSearchDocRequest request, ActionListener<DeleteResponse> listener) {
        // Validate cluster health first
        if (!clusterService.state().routingTable().hasIndex(QUERY_SET.getIndexName())) {
            listener.onFailure(new ResourceNotFoundException("Index [" + QUERY_SET.getIndexName() + "] not found"));
            return;
        }

        try {
            String querySetId = request.getId();
            if (querySetId == null || querySetId.trim().isEmpty()) {
                listener.onFailure(new SearchRelevanceException("Query set ID cannot be null or empty", RestStatus.BAD_REQUEST));
                return;
            }
            querySetDao.deleteQuerySet(querySetId, listener);
        } catch (Exception e) {
            listener.onFailure(e);
        }
    }
}
