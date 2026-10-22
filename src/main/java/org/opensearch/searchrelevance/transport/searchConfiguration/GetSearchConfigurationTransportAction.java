/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */
package org.opensearch.searchrelevance.transport.searchConfiguration;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
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

public class GetSearchConfigurationTransportAction extends HandledTransportAction<OpenSearchDocRequest, SearchResponse> {
    private static final Logger LOGGER = LogManager.getLogger(GetSearchConfigurationTransportAction.class);
    private final ClusterService clusterService;
    private final SearchConfigurationDao searchConfigurationDao;

    @Inject
    public GetSearchConfigurationTransportAction(
        ClusterService clusterService,
        TransportService transportService,
        ActionFilters actionFilters,
        SearchConfigurationDao searchConfigurationDao
    ) {
        super(GetSearchConfigurationAction.NAME, transportService, actionFilters, OpenSearchDocRequest::new);
        this.clusterService = clusterService;
        this.searchConfigurationDao = searchConfigurationDao;
    }

    @Override
    protected void doExecute(Task task, OpenSearchDocRequest request, ActionListener<SearchResponse> listener) {
        try {
            if (request.getId() != null) {
                // Handle single search configuration request
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
