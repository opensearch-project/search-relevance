/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */
package org.opensearch.searchrelevance.transport.experiment;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.opensearch.action.StepListener;
import org.opensearch.action.index.IndexResponse;
import org.opensearch.action.support.ActionFilters;
import org.opensearch.action.support.HandledTransportAction;
import org.opensearch.cluster.service.ClusterService;
import org.opensearch.common.inject.Inject;
import org.opensearch.core.action.ActionListener;
import org.opensearch.searchrelevance.dao.ExperimentDao;
import org.opensearch.searchrelevance.dao.QuerySetDao;
import org.opensearch.searchrelevance.dao.SearchConfigurationDao;
import org.opensearch.searchrelevance.model.Experiment;
import org.opensearch.searchrelevance.utils.TimeUtils;
import org.opensearch.tasks.Task;
import org.opensearch.transport.TransportService;

public class PutExperimentTransportAction extends HandledTransportAction<PutExperimentRequest, IndexResponse> {
    private final ClusterService clusterService;
    private final ExperimentDao experimentDao;
    private final QuerySetDao querySetDao;
    private final SearchConfigurationDao searchConfigurationDao;

    @Inject
    public PutExperimentTransportAction(
        ClusterService clusterService,
        TransportService transportService,
        ActionFilters actionFilters,
        ExperimentDao experimentDao,
        QuerySetDao querySetDao,
        SearchConfigurationDao searchConfigurationDao
    ) {
        super(PutExperimentAction.NAME, transportService, actionFilters, PutExperimentRequest::new);
        this.clusterService = clusterService;
        this.experimentDao = experimentDao;
        this.querySetDao = querySetDao;
        this.searchConfigurationDao = searchConfigurationDao;
    }

    @Override
    protected void doExecute(Task task, PutExperimentRequest request, ActionListener<IndexResponse> listener) {
        if (request == null) {
            listener.onFailure(new IllegalArgumentException("Request cannot be null"));
            return;
        }
        String id = UUID.randomUUID().toString();
        String timestamp = TimeUtils.getTimestamp();

        String index = request.getIndex();
        String querySetId = request.getQuerySetId();
        List<String> searchConfigurationList = request.getSearchConfigurationList();
        int k = request.getK();

        Map<String, Object> results = new HashMap<>();
        // step1: Create Index
        StepListener<Void> createIndexStep = new StepListener<>();
        experimentDao.createIndexIfAbsent(createIndexStep);

        // step2: Get QuerySet
        StepListener<Map<String, Object>> getQuerySetStep = new StepListener<>();
        createIndexStep.whenComplete(
            v -> { querySetDao.getQuerySetWithStepListener(querySetId, results, getQuerySetStep); },
            listener::onFailure
        );

        // step3: Get SearchConfigurations
        StepListener<Map<String, Object>> getSearchConfigsStep = new StepListener<>();
        getQuerySetStep.whenComplete(v -> {
            searchConfigurationDao.getSearchConfigsWithStepListener(searchConfigurationList, results, getSearchConfigsStep);
        }, listener::onFailure);

        // Step4: Put Experiment
        getSearchConfigsStep.whenComplete(v -> {
            Experiment experiment = new Experiment(id, timestamp, index, querySetId, searchConfigurationList, k, results);
            experimentDao.putExperiment(experiment, listener);
        }, listener::onFailure);
    }
}
