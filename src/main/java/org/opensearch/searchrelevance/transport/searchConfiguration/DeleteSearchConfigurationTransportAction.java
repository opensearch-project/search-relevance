/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */
package org.opensearch.searchrelevance.transport.searchConfiguration;

import static org.opensearch.searchrelevance.indices.SearchRelevanceIndices.SEARCH_CONFIGURATION;

import org.opensearch.ResourceNotFoundException;
import org.opensearch.action.delete.DeleteResponse;
import org.opensearch.action.support.ActionFilters;
import org.opensearch.action.support.HandledTransportAction;
import org.opensearch.cluster.service.ClusterService;
import org.opensearch.common.inject.Inject;
import org.opensearch.core.action.ActionListener;
import org.opensearch.core.rest.RestStatus;
import org.opensearch.searchrelevance.dao.SearchConfigurationDao;
import org.opensearch.searchrelevance.exception.SearchRelevanceException;
import org.opensearch.searchrelevance.transport.OpenSearchDocRequest;
import org.opensearch.tasks.Task;
import org.opensearch.transport.TransportService;

public class DeleteSearchConfigurationTransportAction extends HandledTransportAction<OpenSearchDocRequest, DeleteResponse> {
    private final ClusterService clusterService;
    private final SearchConfigurationDao searchConfigurationDao;

    @Inject
    public DeleteSearchConfigurationTransportAction(
        ClusterService clusterService,
        TransportService transportService,
        ActionFilters actionFilters,
        SearchConfigurationDao searchConfigurationDao
    ) {
        super(DeleteSearchConfigurationAction.NAME, transportService, actionFilters, OpenSearchDocRequest::new);
        this.clusterService = clusterService;
        this.searchConfigurationDao = searchConfigurationDao;
    }

    @Override
    protected void doExecute(Task task, OpenSearchDocRequest request, ActionListener<DeleteResponse> listener) {
        // Validate cluster health first
        if (!clusterService.state().routingTable().hasIndex(SEARCH_CONFIGURATION.getIndexName())) {
            listener.onFailure(new ResourceNotFoundException("Index [" + SEARCH_CONFIGURATION.getIndexName() + "] not found"));
            return;
        }

        try {
            String searchConfigurationId = request.getId();
            if (searchConfigurationId == null || searchConfigurationId.trim().isEmpty()) {
                listener.onFailure(new SearchRelevanceException("searchConfigurationId cannot be null or empty", RestStatus.BAD_REQUEST));
                return;
            }
            searchConfigurationDao.deleteSearchConfiguration(searchConfigurationId, listener);
        } catch (Exception e) {
            listener.onFailure(e);
        }
    }
}
