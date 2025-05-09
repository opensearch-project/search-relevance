/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */
package org.opensearch.searchrelevance.transport.experiment;

import static org.opensearch.searchrelevance.common.MetricsConstants.METRICS_INDEX_AND_QUERY_BODY_FIELD_NAME;
import static org.opensearch.searchrelevance.common.MetricsConstants.METRICS_QUERY_TEXT_FIELD_NAME;
import static org.opensearch.searchrelevance.common.MetricsConstants.MODEL_ID;
import static org.opensearch.searchrelevance.model.EvaluationResult.JUDGMENT_IDS;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import java.util.concurrent.atomic.AtomicInteger;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.opensearch.action.StepListener;
import org.opensearch.action.index.IndexResponse;
import org.opensearch.action.search.SearchResponse;
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
import org.opensearch.searchrelevance.model.EvaluationResult;
import org.opensearch.searchrelevance.model.Experiment;
import org.opensearch.searchrelevance.model.ExperimentStatus;
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

    private static final Logger LOGGER = LogManager.getLogger(PutExperimentTransportAction.class);

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

        int size = request.getSize();
        ExperimentType type = request.getType();

        Map<String, Object> results = new HashMap<>();
        // step 1: Create Index
        StepListener<Void> createIndexStep = new StepListener<>();
        experimentDao.createIndexIfAbsent(createIndexStep);

        // step 2: Get QuerySet
        StepListener<Map<String, Object>> getQuerySetStep = new StepListener<>();
        createIndexStep.whenComplete(
            v -> { querySetDao.getQuerySetWithStepListener(querySetId, results, getQuerySetStep); },
            listener::onFailure
        );

        // Step 3: Create initial experiment record with status "PROCESSING"
        getQuerySetStep.whenComplete(v -> {
            Experiment initialExperiment = new Experiment(
                id,
                timestamp,
                type,
                ExperimentStatus.PROCESSING,
                querySetId,
                searchConfigurationList,
                request.getJudgmentList(),
                size,
                new HashMap<>()
            );
            experimentDao.putExperiment(initialExperiment, ActionListener.wrap(
                response -> {
                    LOGGER.info("Initial experiment {} created successfully. Triggering async processing.", id);
                    triggerAsyncProcessing(id, request, results);
                    listener.onResponse((IndexResponse) response);  // Respond to the client that the experiment has been created
                },
                e -> {
                    LOGGER.error("Failed to create initial experiment: {}", id, e);
                    listener.onFailure(e);
                }
            ));
            // trigger asynchronous steps
            triggerAsyncProcessing(id, request, results);
        }, listener::onFailure);
    }

    private void triggerAsyncProcessing(String experimentId, PutExperimentRequest request, Map<String, Object> results) {
        // Async Step 1: Get Search Configurations
        searchConfigurationDao.getSearchConfigsWithStepListener(
            request.getSearchConfigurationList(),
            results,
            ActionListener.wrap(
                // Async Step 2: Calculate Metrics
                searchConfigResults -> {
                    calculateMetricsAsync(experimentId, request, searchConfigResults);
                },
                error -> {
                    handleAsyncFailure(experimentId, request, "Failed at async step 1: Get Search Configurations", error);
                }
            )
        );
    }

    private void calculateMetricsAsync(String experimentId, PutExperimentRequest request, Map<String, Object> results) {
        LOGGER.debug("Starting calculateMetricsAsync for experiment: {}", experimentId);
        Map<String, List<String>> indexAndQueryBodies = (Map<String, List<String>>) results.get(METRICS_INDEX_AND_QUERY_BODY_FIELD_NAME);
        List<String> queryTexts = (List<String>) results.get(METRICS_QUERY_TEXT_FIELD_NAME);
        Map<String, Object> metadata = createMetadataForRequest(request);

        LOGGER.debug("IndexAndQueryBodies: {}, QueryTexts: {}, Metadata: {}", indexAndQueryBodies, queryTexts, metadata);

        if (queryTexts == null || indexAndQueryBodies == null) {
            LOGGER.error("Missing required data for metrics calculation");
            handleAsyncFailure(experimentId, request,
                "Failed to calculate metrics: Missing required data",
                new IllegalStateException("Missing required data for metrics calculation"));
            return;
        }

        Map<String, Object> finalResults = new HashMap<>();
        AtomicInteger pendingQueries = new AtomicInteger(queryTexts.size());

        for (String queryText : queryTexts) {
            LOGGER.debug("Processing query text: {}", queryText);
            metricsHelper.evaluateQueryTextAsync(
                queryText,
                indexAndQueryBodies,
                request.getSize(),
                metadata,
                ActionListener.wrap(
                    queryResults -> {
                        synchronized (finalResults) {
                            LOGGER.debug("Query results for {}: {}", queryText, queryResults);
                            finalResults.put(queryText, queryResults);
                            if (pendingQueries.decrementAndGet() == 0) {
                                LOGGER.debug("All queries processed, updating final experiment");
                                updateFinalExperiment(experimentId, request, finalResults);
                            }
                        }
                    },
                    error -> {
                        LOGGER.error("Failed to evaluate query: " + queryText, error);
                        handleAsyncFailure(experimentId, request, "Failed to evaluate query: " + queryText, error);
                    }
                )
            );
        }
    }

    private void updateFinalExperiment(String experimentId, PutExperimentRequest request, Map<String, Object> finalResults) {
        Experiment finalExperiment = new Experiment(
            experimentId,
            TimeUtils.getTimestamp(),
            request.getType(),
            ExperimentStatus.COMPLETED,
            request.getQuerySetId(),
            request.getSearchConfigurationList(),
            request.getJudgmentList(),
            request.getSize(),
            finalResults
        );

        experimentDao.updateExperiment(
            finalExperiment,
            ActionListener.wrap(
                indexResponse -> LOGGER.info("Completed async step 3: Experiment {} completed successfully", experimentId),
                error -> handleAsyncFailure(experimentId, request, "Failed at async step 3: Update Final Experiment", error)
            )
        );
    }

    private void handleAsyncFailure(String experimentId, PutExperimentRequest request, String message, Exception error) {
        LOGGER.error(message + " for experiment: " + experimentId, error);

        Experiment errorExperiment = new Experiment(
            experimentId,
            TimeUtils.getTimestamp(),
            request.getType(),
            ExperimentStatus.ERROR,
            request.getQuerySetId(),
            request.getSearchConfigurationList(),
            request.getJudgmentList(),
            request.getSize(),
            Map.of("error", error.getMessage())
        );

        experimentDao.updateExperiment(
            errorExperiment,
            ActionListener.wrap(
                response -> LOGGER.info("Updated experiment {} status to ERROR", experimentId),
                e -> LOGGER.error("Failed to update error status for experiment: " + experimentId, e)
            )
        );
    }

    private Map<String, Object> createMetadataForRequest(PutExperimentRequest request) {
        Map<String, Object> metadata = new HashMap<>();

        if (request instanceof PutLlmExperimentRequest) {
            metadata.put(MODEL_ID, ((PutLlmExperimentRequest) request).getModelId());
            metadata.put(JUDGMENT_IDS, request.getJudgmentList());
        } else if (request instanceof PutUbiExperimentRequest) {
            metadata.put(JUDGMENT_IDS, request.getJudgmentList());
        }
        return metadata;
    }
}
