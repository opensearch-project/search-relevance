/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */
package org.opensearch.searchrelevance.transport.experiment;

import java.util.UUID;

import org.opensearch.action.StepListener;
import org.opensearch.action.index.IndexResponse;
import org.opensearch.action.support.ActionFilters;
import org.opensearch.action.support.HandledTransportAction;
import org.opensearch.cluster.service.ClusterService;
import org.opensearch.common.inject.Inject;
import org.opensearch.core.action.ActionListener;
import org.opensearch.searchrelevance.dao.ExperimentDao;
import org.opensearch.searchrelevance.model.Experiment;
import org.opensearch.searchrelevance.utils.TimeUtils;
import org.opensearch.tasks.Task;
import org.opensearch.transport.TransportService;
import org.opensearch.transport.client.Client;

public class CreateExperimentTransportAction extends HandledTransportAction<CreateExperimentRequest, IndexResponse> {
    private final Client client;
    private final ClusterService clusterService;

    private final ExperimentDao experimentDao;

    @Inject
    public CreateExperimentTransportAction(
        ClusterService clusterService,
        TransportService transportService,
        ActionFilters actionFilters,
        Client client
    ) {
        super(CreateExperimentAction.NAME, transportService, actionFilters, CreateExperimentRequest::new);
        this.client = client;
        this.clusterService = clusterService;
        this.experimentDao = new ExperimentDao(client, clusterService);
    }

    @Override
    protected void doExecute(Task task, CreateExperimentRequest request, ActionListener<IndexResponse> listener) {
        if (request == null) {
            listener.onFailure(new IllegalArgumentException("Request cannot be null"));
            return;
        }
        String id = UUID.randomUUID().toString();
        String timestamp = TimeUtils.getTimestamp();

        StepListener<Void> createIndexStep = new StepListener<>();
        experimentDao.createIndexIfAbsent(createIndexStep);
        createIndexStep.whenComplete(v -> {
            Experiment experiment = new Experiment(id, timestamp);
            experimentDao.putExperiment(experiment, listener);
        }, listener::onFailure);
    }
}
