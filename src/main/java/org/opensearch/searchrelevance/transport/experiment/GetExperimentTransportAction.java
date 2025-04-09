/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */
package org.opensearch.searchrelevance.transport.experiment;

import static org.opensearch.searchrelevance.indices.SearchRelevanceIndices.EXPERIMENT;

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
import org.opensearch.searchrelevance.dao.ExperimentDao;
import org.opensearch.searchrelevance.exception.SearchRelevanceException;
import org.opensearch.searchrelevance.transport.OpenSearchDocRequest;
import org.opensearch.tasks.Task;
import org.opensearch.transport.TransportService;

public class GetExperimentTransportAction extends HandledTransportAction<OpenSearchDocRequest, SearchResponse> {
    private static final Logger LOGGER = LogManager.getLogger(GetExperimentTransportAction.class);
    private final ClusterService clusterService;
    private final ExperimentDao experimentDao;

    @Inject
    public GetExperimentTransportAction(
        ClusterService clusterService,
        TransportService transportService,
        ActionFilters actionFilters,
        ExperimentDao experimentDao
    ) {
        super(GetExperimentAction.NAME, transportService, actionFilters, OpenSearchDocRequest::new);
        this.clusterService = clusterService;
        this.experimentDao = experimentDao;
    }

    @Override
    protected void doExecute(Task task, OpenSearchDocRequest request, ActionListener<SearchResponse> listener) {
        // Validate cluster health first
        if (!clusterService.state().routingTable().hasIndex(EXPERIMENT.getIndexName())) {
            listener.onFailure(new ResourceNotFoundException("Index [" + EXPERIMENT.getIndexName() + "] not found"));
            return;
        }

        try {
            if (request.getId() != null) {
                // Handle single query set request
                experimentDao.getExperiment(request.getId(), listener);
            } else {
                // Handle list request
                experimentDao.listExperiment(request.getSearchSourceBuilder(), listener);
            }
        } catch (Exception e) {
            listener.onFailure(new SearchRelevanceException("Failed to get/list Experiment", e, RestStatus.INTERNAL_SERVER_ERROR));
        }
    }
}
