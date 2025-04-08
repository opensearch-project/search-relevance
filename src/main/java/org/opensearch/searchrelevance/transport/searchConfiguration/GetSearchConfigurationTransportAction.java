/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */
package org.opensearch.searchrelevance.transport.searchConfiguration;

import static org.opensearch.searchrelevance.indices.SearchRelevanceIndices.SEARCH_CONFIGURATION;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.opensearch.ResourceNotFoundException;
import org.opensearch.action.search.SearchResponse;
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
import org.opensearch.transport.client.Client;

public class GetSearchConfigurationTransportAction extends HandledTransportAction<OpenSearchDocRequest, SearchResponse> {
    private static final Logger LOGGER = LogManager.getLogger(GetSearchConfigurationTransportAction.class);
    private final ClusterService clusterService;
    private final Client client;
    private final SearchConfigurationDao searchConfigurationDao;

    @Inject
    public GetSearchConfigurationTransportAction(
        ClusterService clusterService,
        TransportService transportService,
        ActionFilters actionFilters,
        Client client
    ) {
        super(GetSearchConfigurationAction.NAME, transportService, actionFilters, OpenSearchDocRequest::new);
        this.clusterService = clusterService;
        this.client = client;
        this.searchConfigurationDao = new SearchConfigurationDao(client, clusterService);
    }

    @Override
    protected void doExecute(Task task, OpenSearchDocRequest request, ActionListener<SearchResponse> listener) {
        // Validate cluster health first
        if (!clusterService.state().routingTable().hasIndex(SEARCH_CONFIGURATION.getIndexName())) {
            listener.onFailure(new ResourceNotFoundException("Index [" + SEARCH_CONFIGURATION.getIndexName() + "] not found"));
            return;
        }

        try {
            if (request.getId() != null) {
                // Handle single query set request
                searchConfigurationDao.getSearchConfiguration(request.getId(), listener);
            } else {
                // Handle list request
                searchConfigurationDao.listSearchConfiguration(request.getSearchSourceBuilder(), listener);
            }
        } catch (Exception e) {
            listener.onFailure(new SearchRelevanceException("Failed to get/list SearchConfiguration", e, RestStatus.INTERNAL_SERVER_ERROR));
        }
    }
}
