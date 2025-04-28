/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */
package org.opensearch.searchrelevance.transport.experiment;

import static org.opensearch.searchrelevance.common.MetricsConstants.JUDGMENT_IDS;
import static org.opensearch.searchrelevance.common.MetricsConstants.METRICS_INDEX_AND_QUERY_BODY_FIELD_NAME;
import static org.opensearch.searchrelevance.common.MetricsConstants.METRICS_QUERY_TEXT_FIELD_NAME;
import static org.opensearch.searchrelevance.common.MetricsConstants.MODEL_ID;
import static org.opensearch.searchrelevance.model.ExperimentType.LLM_EVALUATION;
import static org.opensearch.searchrelevance.model.ExperimentType.PAIRWISE_COMPARISON;
import static org.opensearch.searchrelevance.model.ExperimentType.UBI_EVALUATION;

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
import org.opensearch.core.rest.RestStatus;
import org.opensearch.searchrelevance.dao.ExperimentDao;
import org.opensearch.searchrelevance.dao.QuerySetDao;
import org.opensearch.searchrelevance.dao.SearchConfigurationDao;
import org.opensearch.searchrelevance.exception.SearchRelevanceException;
import org.opensearch.searchrelevance.metrics.MetricsHelper;
import org.opensearch.searchrelevance.model.Experiment;
import org.opensearch.searchrelevance.model.ExperimentType;
import org.opensearch.searchrelevance.utils.TimeUtils;
import org.opensearch.tasks.Task;
import org.opensearch.transport.TransportService;

public class PutExperimentTransportAction extends HandledTransportAction<PutExperimentRequest, IndexResponse> {
    private final ClusterService clusterService;
    private final ExperimentDao experimentDao;
    private final QuerySetDao querySetDao;
    private final SearchConfigurationDao searchConfigurationDao;

    private final MetricsHelper metricsHelper;

    @Inject
    public PutExperimentTransportAction(
        ClusterService clusterService,
        TransportService transportService,
        ActionFilters actionFilters,
        ExperimentDao experimentDao,
        QuerySetDao querySetDao,
        SearchConfigurationDao searchConfigurationDao,
        MetricsHelper metricsHelper
    ) {
        super(PutExperimentAction.NAME, transportService, actionFilters, PutExperimentRequest::new);
        this.clusterService = clusterService;
        this.experimentDao = experimentDao;
        this.querySetDao = querySetDao;
        this.searchConfigurationDao = searchConfigurationDao;
        this.metricsHelper = metricsHelper;
    }

    @Override
    protected void doExecute(Task task, PutExperimentRequest request, ActionListener<IndexResponse> listener) {
        if (request == null) {
            listener.onFailure(new SearchRelevanceException("Request cannot be null", RestStatus.BAD_REQUEST));
            return;
        }
        String id = UUID.randomUUID().toString();
        String timestamp = TimeUtils.getTimestamp();

        String querySetId = request.getQuerySetId();
        List<String> searchConfigurationList = request.getSearchConfigurationList();
        int k = request.getK();

        ExperimentType type = request.getType();

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

        // step4: Search and Calculate Metrics
        StepListener<Map<String, Object>> searchAndMetricsCalcStep = new StepListener<>();
        getSearchConfigsStep.whenComplete(v -> {
            List<List<String>> indexAndQueryBodies = (List<List<String>>) results.get(METRICS_INDEX_AND_QUERY_BODY_FIELD_NAME);
            List<String> queryTexts = (List<String>) results.get(METRICS_QUERY_TEXT_FIELD_NAME);
            Map<String, Object> metadata = new HashMap<>();
            switch (type) {
                case PAIRWISE_COMPARISON:
                    metricsHelper.getMetricsAsync(
                        results,
                        queryTexts,
                        indexAndQueryBodies,
                        k,
                        PAIRWISE_COMPARISON,
                        metadata,
                        searchAndMetricsCalcStep
                    );
                    break;
                case LLM_EVALUATION:
                    PutLlmExperimentRequest llmRequest = (PutLlmExperimentRequest) request;
                    String modelId = llmRequest.getModelId();
                    metadata.put(MODEL_ID, modelId);
                    metricsHelper.getMetricsAsync(
                        results,
                        queryTexts,
                        indexAndQueryBodies,
                        k,
                        LLM_EVALUATION,
                        metadata,
                        searchAndMetricsCalcStep
                    );
                    break;
                case UBI_EVALUATION:
                    PutUbiExperimentRequest ubiRequest = (PutUbiExperimentRequest) request;
                    List<String> judgmentIds = ubiRequest.getJudgmentIds();
                    metadata.put(JUDGMENT_IDS, judgmentIds);
                    metricsHelper.getMetricsAsync(
                        results,
                        queryTexts,
                        indexAndQueryBodies,
                        k,
                        UBI_EVALUATION,
                        metadata,
                        searchAndMetricsCalcStep
                    );
                    break;
            }

        }, listener::onFailure);

        // Step5: Put Experiment
        searchAndMetricsCalcStep.whenComplete(v -> {
            Experiment experiment = new Experiment(id, timestamp, querySetId, searchConfigurationList, k, results);
            experimentDao.putExperiment(experiment, listener);
        }, listener::onFailure);
    }
}
