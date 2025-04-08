/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */
package org.opensearch.searchrelevance.transport.judgment;

import static org.opensearch.searchrelevance.indices.SearchRelevanceIndices.JUDGMENT;

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
import org.opensearch.searchrelevance.dao.JudgmentDao;
import org.opensearch.searchrelevance.exception.SearchRelevanceException;
import org.opensearch.searchrelevance.transport.OpenSearchDocRequest;
import org.opensearch.tasks.Task;
import org.opensearch.transport.TransportService;
import org.opensearch.transport.client.Client;

public class GetJudgmentTransportAction extends HandledTransportAction<OpenSearchDocRequest, SearchResponse> {
    private static final Logger LOGGER = LogManager.getLogger(GetJudgmentTransportAction.class);
    private final ClusterService clusterService;
    private final Client client;
    private final JudgmentDao judgmentDao;

    @Inject
    public GetJudgmentTransportAction(
        ClusterService clusterService,
        TransportService transportService,
        ActionFilters actionFilters,
        Client client
    ) {
        super(GetJudgmentAction.NAME, transportService, actionFilters, OpenSearchDocRequest::new);
        this.clusterService = clusterService;
        this.client = client;
        this.judgmentDao = new JudgmentDao(client, clusterService);
    }

    @Override
    protected void doExecute(Task task, OpenSearchDocRequest request, ActionListener<SearchResponse> listener) {
        // Validate cluster health first
        if (!clusterService.state().routingTable().hasIndex(JUDGMENT.getIndexName())) {
            listener.onFailure(new ResourceNotFoundException("Index [" + JUDGMENT.getIndexName() + "] not found"));
            return;
        }

        try {
            if (request.getId() != null) {
                // Handle single query set request
                judgmentDao.getJudgment(request.getId(), listener);
            } else {
                // Handle list request
                judgmentDao.listJudgment(request.getSearchSourceBuilder(), listener);
            }
        } catch (Exception e) {
            listener.onFailure(new SearchRelevanceException("Failed to get/list Judgment", e, RestStatus.INTERNAL_SERVER_ERROR));
        }
    }
}
