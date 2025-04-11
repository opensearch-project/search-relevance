/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */
package org.opensearch.searchrelevance.transport.experiment;

import static org.opensearch.searchrelevance.indices.SearchRelevanceIndices.EXPERIMENT;

import org.opensearch.ResourceNotFoundException;
import org.opensearch.action.delete.DeleteResponse;
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

public class DeleteExperimentTransportAction extends HandledTransportAction<OpenSearchDocRequest, DeleteResponse> {
    private final ClusterService clusterService;
    private final ExperimentDao experimentDao;

    @Inject
    public DeleteExperimentTransportAction(
        ClusterService clusterService,
        TransportService transportService,
        ActionFilters actionFilters,
        ExperimentDao experimentDao
    ) {
        super(DeleteExperimentAction.NAME, transportService, actionFilters, OpenSearchDocRequest::new);
        this.clusterService = clusterService;
        this.experimentDao = experimentDao;
    }

    @Override
    protected void doExecute(Task task, OpenSearchDocRequest request, ActionListener<DeleteResponse> listener) {
        // Validate cluster health first
        if (!clusterService.state().routingTable().hasIndex(EXPERIMENT.getIndexName())) {
            listener.onFailure(new ResourceNotFoundException("Index [" + EXPERIMENT.getIndexName() + "] not found"));
            return;
        }

        try {
            String experimentId = request.getId();
            if (experimentId == null || experimentId.trim().isEmpty()) {
                listener.onFailure(new SearchRelevanceException("Experiment ID cannot be null or empty", RestStatus.BAD_REQUEST));
                return;
            }
            experimentDao.deleteExperiment(experimentId, listener);
        } catch (Exception e) {
            listener.onFailure(e);
        }
    }
}
