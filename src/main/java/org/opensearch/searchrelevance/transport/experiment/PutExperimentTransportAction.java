/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */
package org.opensearch.searchrelevance.transport.experiment;

import static org.opensearch.searchrelevance.common.MetricsConstants.METRICS_INDEX_AND_QUERIES_FIELD_NAME;
import static org.opensearch.searchrelevance.common.MetricsConstants.METRICS_QUERY_TEXT_FIELD_NAME;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.opensearch.action.StepListener;
import org.opensearch.action.index.IndexResponse;
import org.opensearch.action.support.ActionFilters;
import org.opensearch.action.support.HandledTransportAction;
import org.opensearch.cluster.service.ClusterService;
import org.opensearch.common.inject.Inject;
import org.opensearch.core.action.ActionListener;
import org.opensearch.core.rest.RestStatus;
import org.opensearch.searchrelevance.dao.ExperimentDao;
import org.opensearch.searchrelevance.dao.JudgmentDao;
import org.opensearch.searchrelevance.dao.QuerySetDao;
import org.opensearch.searchrelevance.dao.SearchConfigurationDao;
import org.opensearch.searchrelevance.exception.SearchRelevanceException;
import org.opensearch.searchrelevance.experiment.ExperimentOptionsFactory;
import org.opensearch.searchrelevance.experiment.ExperimentOptionsForHybridSearch;
import org.opensearch.searchrelevance.judgments.BaseJudgmentsProcessor;
import org.opensearch.searchrelevance.judgments.JudgmentsProcessorFactory;
import org.opensearch.searchrelevance.metrics.MetricsHelper;
import org.opensearch.searchrelevance.model.AsyncStatus;
import org.opensearch.searchrelevance.model.Experiment;
import org.opensearch.searchrelevance.model.ExperimentType;
import org.opensearch.searchrelevance.model.Judgment;
import org.opensearch.searchrelevance.model.JudgmentType;
import org.opensearch.searchrelevance.utils.TimeUtils;
import org.opensearch.tasks.Task;
import org.opensearch.transport.TransportService;

/**
 * Handles transport actions for creating experiments in the system.
 */
public class PutExperimentTransportAction extends HandledTransportAction<PutExperimentRequest, IndexResponse> {

    private final ClusterService clusterService;
    private final ExperimentDao experimentDao;
    private final QuerySetDao querySetDao;
    private final SearchConfigurationDao searchConfigurationDao;
    private final JudgmentDao judgmentDao;

    private final MetricsHelper metricsHelper;
    private final JudgmentsProcessorFactory judgmentsProcessorFactory;

    private static final Logger LOGGER = LogManager.getLogger(PutExperimentTransportAction.class);

    @Inject
    public PutExperimentTransportAction(
        ClusterService clusterService,
        TransportService transportService,
        ActionFilters actionFilters,
        ExperimentDao experimentDao,
        QuerySetDao querySetDao,
        SearchConfigurationDao searchConfigurationDao,
        JudgmentDao judgmentDao,
        MetricsHelper metricsHelper,
        JudgmentsProcessorFactory judgmentsProcessorFactory
    ) {
        super(PutExperimentAction.NAME, transportService, actionFilters, PutExperimentRequest::new);
        this.clusterService = clusterService;
        this.experimentDao = experimentDao;
        this.querySetDao = querySetDao;
        this.searchConfigurationDao = searchConfigurationDao;
        this.judgmentDao = judgmentDao;
        this.metricsHelper = metricsHelper;
        this.judgmentsProcessorFactory = judgmentsProcessorFactory;
    }

    @Override
    protected void doExecute(Task task, PutExperimentRequest request, ActionListener<IndexResponse> listener) {
        if (request == null) {
            listener.onFailure(new SearchRelevanceException("Request cannot be null", RestStatus.BAD_REQUEST));
            return;
        }

        String id = UUID.randomUUID().toString();
        String timestamp = TimeUtils.getTimestamp();
        Map<String, Object> results = new HashMap<>();

        // step 1: Create Index if not exists
        StepListener<Void> createIndexStep = new StepListener<>();
        experimentDao.createIndexIfAbsent(createIndexStep);

        // step 2: Get QuerySet
        StepListener<Map<String, Object>> getQuerySetStep = new StepListener<>();
        createIndexStep.whenComplete(
            v -> { querySetDao.getQuerySetWithStepListener(request.getQuerySetId(), results, getQuerySetStep); },
            listener::onFailure
        );

        // Step 3: Create initial experiment record with status "PROCESSING"
        getQuerySetStep.whenComplete(v -> {
            Experiment initialExperiment = new Experiment(
                id,
                timestamp,
                request.getType(),
                AsyncStatus.PROCESSING,
                request.getQuerySetId(),
                request.getSearchConfigurationList(),
                request.getJudgmentList(),
                request.getSize(),
                new HashMap<>()
            );
            experimentDao.putExperiment(initialExperiment, ActionListener.wrap(response -> {
                triggerAsyncProcessing(id, request, results);
                listener.onResponse((IndexResponse) response);
            }, e -> { listener.onFailure(e); }));
        }, listener::onFailure);
    }

    private void triggerAsyncProcessing(String experimentId, PutExperimentRequest request, Map<String, Object> results) {
        searchConfigurationDao.getSearchConfigsWithStepListener(
            request.getSearchConfigurationList(),
            results,
            ActionListener.wrap(searchConfigResults -> {
                calculateMetricsAsync(experimentId, request, searchConfigResults);
            }, error -> { handleAsyncFailure(experimentId, request, "Failed at async step 1: Get Search Configurations", error); })
        );
    }

    private void calculateMetricsAsync(String experimentId, PutExperimentRequest request, Map<String, Object> results) {
        Map<String, List<String>> indexAndQueries = (Map<String, List<String>>) results.get(METRICS_INDEX_AND_QUERIES_FIELD_NAME);
        List<String> queryTexts = (List<String>) results.get(METRICS_QUERY_TEXT_FIELD_NAME);

        if (queryTexts == null || indexAndQueries == null) {
            handleAsyncFailure(
                experimentId,
                request,
                "Failed to calculate metrics: Missing required data",
                new IllegalStateException("Missing required data for metrics calculation")
            );
            return;
        }

        processQueryTextMetrics(experimentId, request, indexAndQueries, queryTexts);
    }

    private void processQueryTextMetrics(
        String experimentId,
        PutExperimentRequest request,
        Map<String, List<String>> indexAndQueries,
        List<String> queryTexts
    ) {
        Map<String, Object> finalResults = Collections.synchronizedMap(new HashMap<>());
        AtomicInteger pendingQueries = new AtomicInteger(queryTexts.size());
        AtomicBoolean hasFailure = new AtomicBoolean(false);

        Map<String, Object> metadata = new HashMap<>();
        if (request.getType() == ExperimentType.LLM_EVALUATION) {
            PutLlmExperimentRequest llmRequest = (PutLlmExperimentRequest) request;
            // generate llm judgment only if modelId is provided
            if (llmRequest.getModelId() != null) {
                metadata.put("querySetId", request.getQuerySetId());
                metadata.put("searchConfigurationList", request.getSearchConfigurationList());
                metadata.put("size", request.getSize());
                metadata.put("modelId", llmRequest.getModelId());
                metadata.put("tokenLimit", llmRequest.getTokenLimit());
                metadata.put("contextFields", llmRequest.getContextFields());

                // Step 1: Generate Judgment Scores
                StepListener<Map<String, Map<String, String>>> processJudgmentScoresStep = new StepListener<>();
                BaseJudgmentsProcessor llmJudgmentsProcessor = judgmentsProcessorFactory.getProcessor(JudgmentType.LLM_JUDGMENT);
                llmJudgmentsProcessor.generateJudgmentScore(metadata, processJudgmentScoresStep);

                // Step 2: Store Judgment
                StepListener<String> storeJudgmentStep = new StepListener<>();
                processJudgmentScoresStep.whenComplete(
                    llmJudgments -> { createAndStoreLlmJudgment(metadata, llmJudgments, storeJudgmentStep); },
                    error -> {
                        handleFailure(error, hasFailure, experimentId, request);
                    }
                );

                // Step 3: Execute Experiment Evaluation
                storeJudgmentStep.whenComplete(llmJudgmentId -> {
                    List<String> updatedJudgmentList = new ArrayList<>(request.getJudgmentList());
                    updatedJudgmentList.add(llmJudgmentId);

                    executeExperimentEvaluation(
                        experimentId,
                        request,
                        indexAndQueries,
                        queryTexts,
                        finalResults,
                        pendingQueries,
                        hasFailure,
                        updatedJudgmentList
                    );
                }, error -> { handleFailure(error, hasFailure, experimentId, request); });
            }
        } else if (ExperimentType.HYBRID_SEARCH == request.getType()) {
            executeExperimentEvaluation(
                experimentId,
                request,
                indexAndQueries,
                queryTexts,
                finalResults,
                pendingQueries,
                hasFailure,
                request.getJudgmentList()
            );
        } else {
            executeExperimentEvaluation(
                experimentId,
                request,
                indexAndQueries,
                queryTexts,
                finalResults,
                pendingQueries,
                hasFailure,
                request.getJudgmentList()
            );
        }
    }

    private void executeExperimentEvaluation(
        String experimentId,
        PutExperimentRequest request,
        Map<String, List<String>> indexAndQueries,
        List<String> queryTexts,
        Map<String, Object> finalResults,
        AtomicInteger pendingQueries,
        AtomicBoolean hasFailure,
        List<String> judgmentList
    ) {
        for (String queryText : queryTexts) {
            if (request.getType() == ExperimentType.PAIRWISE_COMPARISON) {
                metricsHelper.processPairwiseMetrics(
                    queryText,
                    indexAndQueries,
                    request.getSize(),
                    ActionListener.wrap(
                        queryResults -> handleQueryResults(
                            queryText,
                            queryResults,
                            finalResults,
                            pendingQueries,
                            experimentId,
                            request,
                            hasFailure,
                            judgmentList
                        ),
                        error -> handleFailure(error, hasFailure, experimentId, request)
                    )
                );
            } else if (request.getType() == ExperimentType.HYBRID_SEARCH) {
                Map<String, Object> defaultParametersForHybridSearch = ExperimentOptionsFactory
                    .createDefaultExperimentParametersForHybridSearch();
                ExperimentOptionsForHybridSearch experimentOptionForHybridSearch =
                    (ExperimentOptionsForHybridSearch) ExperimentOptionsFactory.createExperimentOptions(
                        ExperimentOptionsFactory.HYBRID_SEARCH_EXPERIMENT_OPTIONS,
                        defaultParametersForHybridSearch
                    );
                metricsHelper.processEvaluationMetrics(
                    queryText,
                    indexAndQueries,
                    request.getSize(),
                    judgmentList,
                    ActionListener.wrap(queryResults -> {
                        Map<String, Object> convertedResults = new HashMap<>(queryResults);
                        handleQueryResults(
                            queryText,
                            convertedResults,
                            finalResults,
                            pendingQueries,
                            experimentId,
                            request,
                            hasFailure,
                            judgmentList
                        );
                    }, error -> handleFailure(error, hasFailure, experimentId, request)),
                    experimentOptionForHybridSearch
                );
            } else {
                metricsHelper.processEvaluationMetrics(
                    queryText,
                    indexAndQueries,
                    request.getSize(),
                    judgmentList,
                    ActionListener.wrap(queryResults -> {
                        Map<String, Object> convertedResults = new HashMap<>(queryResults);
                        handleQueryResults(
                            queryText,
                            convertedResults,
                            finalResults,
                            pendingQueries,
                            experimentId,
                            request,
                            hasFailure,
                            judgmentList
                        );
                    }, error -> handleFailure(error, hasFailure, experimentId, request))
                );
            }
        }
    }

    private void handleQueryResults(
        String queryText,
        Map<String, Object> queryResults,
        Map<String, Object> finalResults,
        AtomicInteger pendingQueries,
        String experimentId,
        PutExperimentRequest request,
        AtomicBoolean hasFailure,
        List<String> judgmentList
    ) {
        if (hasFailure.get()) return;

        try {
            synchronized (finalResults) {
                finalResults.put(queryText, queryResults);
                if (pendingQueries.decrementAndGet() == 0) {
                    updateFinalExperiment(experimentId, request, finalResults, judgmentList);
                }
            }
        } catch (Exception e) {
            handleFailure(e, hasFailure, experimentId, request);
        }
    }

    private void handleFailure(Exception error, AtomicBoolean hasFailure, String experimentId, PutExperimentRequest request) {
        if (hasFailure.compareAndSet(false, true)) {
            handleAsyncFailure(experimentId, request, "Failed to process metrics", error);
        }
    }

    private void updateFinalExperiment(
        String experimentId,
        PutExperimentRequest request,
        Map<String, Object> finalResults,
        List<String> judgmentList
    ) {
        Experiment finalExperiment = new Experiment(
            experimentId,
            TimeUtils.getTimestamp(),
            request.getType(),
            AsyncStatus.COMPLETED,
            request.getQuerySetId(),
            request.getSearchConfigurationList(),
            judgmentList,
            request.getSize(),
            finalResults
        );

        experimentDao.updateExperiment(
            finalExperiment,
            ActionListener.wrap(
                response -> LOGGER.debug("Updated final experiment: {}", experimentId),
                error -> handleAsyncFailure(experimentId, request, "Failed to update final experiment", error)
            )
        );
    }

    private void handleAsyncFailure(String experimentId, PutExperimentRequest request, String message, Exception error) {
        LOGGER.error(message + " for experiment: " + experimentId, error);

        Experiment errorExperiment = new Experiment(
            experimentId,
            TimeUtils.getTimestamp(),
            request.getType(),
            AsyncStatus.ERROR,
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

    private void createAndStoreLlmJudgment(
        Map<String, Object> metadata,
        Map<String, Map<String, String>> llmJudgments,
        ActionListener<String> listener
    ) {
        String judgmentId = UUID.randomUUID().toString();

        Judgment llmJudgment = new Judgment(
            judgmentId,
            TimeUtils.getTimestamp(),
            "LLM Generated Judgment" + new Date(),
            AsyncStatus.COMPLETED,
            JudgmentType.LLM_JUDGMENT,
            metadata,
            llmJudgments
        );

        judgmentDao.putJudgement(llmJudgment, ActionListener.wrap(response -> { listener.onResponse(judgmentId); }, error -> {
            listener.onFailure(error);
        }));
    }
}
