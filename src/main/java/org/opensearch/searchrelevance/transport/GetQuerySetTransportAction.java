/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */
package org.opensearch.searchrelevance.transport;

import static org.opensearch.searchrelevance.indices.SearchRelevanceIndices.QUERY_SET;

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
import org.opensearch.searchrelevance.dao.QuerySetDao;
import org.opensearch.searchrelevance.exception.SearchRelevanceException;
import org.opensearch.tasks.Task;
import org.opensearch.transport.TransportService;
import org.opensearch.transport.client.Client;

public class GetQuerySetTransportAction extends HandledTransportAction<QuerySetRequest, SearchResponse> {
    private static final Logger LOGGER = LogManager.getLogger(GetQuerySetTransportAction.class);
    private final ClusterService clusterService;
    private final Client client;
    private final QuerySetDao querySetDao;

    @Inject
    public GetQuerySetTransportAction(
        ClusterService clusterService,
        TransportService transportService,
        ActionFilters actionFilters,
        Client client
    ) {
        super(GetQuerySetAction.NAME, transportService, actionFilters, QuerySetRequest::new);
        this.clusterService = clusterService;
        this.client = client;
        this.querySetDao = new QuerySetDao(client, clusterService);
    }

    @Override
    protected void doExecute(Task task, QuerySetRequest request, ActionListener<SearchResponse> listener) {
        // Validate cluster health first
        if (!clusterService.state().routingTable().hasIndex(QUERY_SET.getIndexName())) {
            listener.onFailure(new ResourceNotFoundException("Index [" + QUERY_SET.getIndexName() + "] not found"));
            return;
        }

        try {
            if (request.getQuerySetId() != null) {
                // Handle single query set request
                querySetDao.getQuerySet(request.getQuerySetId(), listener);
            } else {
                // Handle list request
                querySetDao.listQuerySet(request.getSearchSourceBuilder(), listener);
            }
        } catch (Exception e) {
            listener.onFailure(new SearchRelevanceException("Failed to get/list QuerySet", e, RestStatus.INTERNAL_SERVER_ERROR));
        }
    }
}
