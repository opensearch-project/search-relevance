/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */
package org.opensearch.searchrelevance.transport.experiment;

import static org.opensearch.searchrelevance.common.MetricsConstants.PAIRWISE_FIELD_NAME_QUERY_TEXT;
import static org.opensearch.searchrelevance.common.MetricsConstants.POINTWISE_FIELD_NAME_EVALUATION_ID;
import static org.opensearch.searchrelevance.common.MetricsConstants.POINTWISE_FIELD_NAME_SEARCH_CONFIGURATION_ID;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.opensearch.action.index.IndexResponse;
import org.opensearch.action.support.ActionFilters;
import org.opensearch.action.support.HandledTransportAction;
import org.opensearch.cluster.service.ClusterService;
import org.opensearch.common.inject.Inject;
import org.opensearch.core.action.ActionListener;
import org.opensearch.core.rest.RestStatus;
import org.opensearch.searchrelevance.dao.EvaluationResultDao;
import org.opensearch.searchrelevance.dao.ExperimentDao;
import org.opensearch.searchrelevance.dao.ExperimentVariantDao;
import org.opensearch.searchrelevance.dao.QuerySetDao;
import org.opensearch.searchrelevance.dao.SearchConfigurationDao;
import org.opensearch.searchrelevance.exception.SearchRelevanceException;
import org.opensearch.searchrelevance.metrics.MetricsHelper;
import org.opensearch.searchrelevance.model.AsyncStatus;
import org.opensearch.searchrelevance.model.EvaluationResult;
import org.opensearch.searchrelevance.model.Experiment;
import org.opensearch.searchrelevance.model.ExperimentType;
import org.opensearch.searchrelevance.model.SearchConfiguration;
import org.opensearch.searchrelevance.utils.TimeUtils;
import org.opensearch.tasks.Task;
import org.opensearch.transport.TransportService;

/**
 * Handles transport actions for importing experiments in the system.
 */
public class PostExperimentTransportAction extends HandledTransportAction<PostExperimentRequest, IndexResponse> {

    private final ClusterService clusterService;
    private final ExperimentDao experimentDao;
    private final EvaluationResultDao evaluationResultDao;
    private final ExperimentVariantDao experimentVariantDao;
    private final QuerySetDao querySetDao;
    private final SearchConfigurationDao searchConfigurationDao;
    private final MetricsHelper metricsHelper;

    private static final Logger LOGGER = LogManager.getLogger(PostExperimentTransportAction.class);

    @Inject
    public PostExperimentTransportAction(
        ClusterService clusterService,
        TransportService transportService,
        ActionFilters actionFilters,
        ExperimentDao experimentDao,
        ExperimentVariantDao experimentVariantDao,
        QuerySetDao querySetDao,
        SearchConfigurationDao searchConfigurationDao,
        EvaluationResultDao evaluationResultDao,
        MetricsHelper metricsHelper
    ) {
        super(PostExperimentAction.NAME, transportService, actionFilters, PostExperimentRequest::new);
        this.clusterService = clusterService;
        this.experimentDao = experimentDao;
        this.experimentVariantDao = experimentVariantDao;
        this.querySetDao = querySetDao;
        this.searchConfigurationDao = searchConfigurationDao;
        this.evaluationResultDao = evaluationResultDao;
        this.metricsHelper = metricsHelper;
    }

    @Override
    protected void doExecute(Task task, PostExperimentRequest request, ActionListener<IndexResponse> listener) {
        if (request == null) {
            listener.onFailure(new SearchRelevanceException("Request cannot be null", RestStatus.BAD_REQUEST));
            return;
        }

        try {
            String id = UUID.randomUUID().toString();
            LOGGER.warn("Experiment ID: " + id);
            Experiment initialExperiment = new Experiment(
                id,
                TimeUtils.getTimestamp(),
                request.getType(),
                AsyncStatus.PROCESSING,
                request.getQuerySetId(),
                request.getSearchConfigurationList(),
                request.getJudgmentList(),
                request.getSize(),
                new ArrayList<>()
            );

            // Store initial experiment and return ID immediately
            experimentDao.putExperiment(initialExperiment, ActionListener.wrap(response -> {
                // Return response immediately
                listener.onResponse((IndexResponse) response);

                // Start async processing
                triggerAsyncProcessing(id, request);
            }, e -> {
                LOGGER.error("Failed to create initial experiment", e);
                listener.onFailure(
                    new SearchRelevanceException("Failed to create initial experiment", e, RestStatus.INTERNAL_SERVER_ERROR)
                );
            }));

        } catch (Exception e) {
            LOGGER.error("Failed to process experiment request", e);
            listener.onFailure(new SearchRelevanceException("Failed to process experiment request", e, RestStatus.INTERNAL_SERVER_ERROR));
        }
    }

    private void triggerAsyncProcessing(String experimentId, PostExperimentRequest request) {
        try {

            List<SearchConfiguration> searchConfigurations = request.getSearchConfigurationList()
                .stream()
                .map(id -> searchConfigurationDao.getSearchConfigurationSync(id))
                .collect(Collectors.toList());

            if (searchConfigurations.size() != 1) {
                throw new Exception("Must have exactly one search configuration. Had " + searchConfigurations.size() + " size.");
            }
            String searchConfigurationId = searchConfigurations.get(0).id();

            if (request.getJudgmentList().size() != 1) {
                throw new Exception("Must have exactly one judgment list. Had " + request.getJudgmentList().size() + " size.");
            }

            processExperiment(experimentId, request, searchConfigurationId);
        } catch (Exception e) {
            handleAsyncFailure(experimentId, request, "Failed to start async processing", e);
        }
    }

    private void processExperiment(String experimentId, PostExperimentRequest request, String searchConfigurationId) {
        List<Map<String, Object>> finalResults = Collections.synchronizedList(new ArrayList<>());
        AtomicInteger pendingQueries = new AtomicInteger(request.getEvaluationResultList().size());
        AtomicBoolean hasFailure = new AtomicBoolean(false);

        importExperiment(experimentId, request, searchConfigurationId, finalResults, pendingQueries, hasFailure);
    }

    private void importExperiment(
        String experimentId,
        PostExperimentRequest request,
        String searchConfigurationId,
        List<Map<String, Object>> finalResults,
        AtomicInteger pendingQueries,
        AtomicBoolean hasFailure
    ) {
        if (request.getType() == ExperimentType.POINTWISE_EVALUATION) {

            List judgmentList = request.getJudgmentList();
            for (Map<String, Object> evalResultMap : request.getEvaluationResultList()) {
                final String evaluationId = UUID.randomUUID().toString();

                String queryText = (String) evalResultMap.get("searchText");
                List metrics = (List) evalResultMap.get("metrics");
                List documentIds = (List) evalResultMap.get("documentIds");

                EvaluationResult evaluationResult = new EvaluationResult(
                    evaluationId,
                    TimeUtils.getTimestamp(),
                    searchConfigurationId,
                    queryText,
                    judgmentList,
                    documentIds,
                    metrics
                );

                evaluationResultDao.putEvaluationResult(evaluationResult, ActionListener.wrap(success -> {
                    Map<String, Object> evalResults = Collections.synchronizedMap(new HashMap<>());
                    evalResults.put(POINTWISE_FIELD_NAME_SEARCH_CONFIGURATION_ID, searchConfigurationId);
                    evalResults.put(POINTWISE_FIELD_NAME_EVALUATION_ID, evaluationId);
                    evalResults.put(PAIRWISE_FIELD_NAME_QUERY_TEXT, queryText);
                    finalResults.add(evalResults);

                    if (pendingQueries.decrementAndGet() == 0) {
                        updateFinalExperiment(experimentId, request, finalResults, judgmentList);
                    }
                }, error -> {
                    hasFailure.set(true);
                    LOGGER.error(error);
                }));
            }
        } else {
            throw new SearchRelevanceException(
                "Importing experimentType" + request.getType() + " is not supported",
                RestStatus.BAD_REQUEST
            );
        }
    }

    private void handleQueryResults(
        String queryText,
        Map<String, Object> queryResults,
        List<Map<String, Object>> finalResults,
        AtomicInteger pendingQueries,
        String experimentId,
        PostExperimentRequest request,
        AtomicBoolean hasFailure,
        List<String> judgmentList
    ) {
        if (hasFailure.get()) return;

        try {
            synchronized (finalResults) {
                queryResults.put(PAIRWISE_FIELD_NAME_QUERY_TEXT, queryText);
                finalResults.add(queryResults);
                if (pendingQueries.decrementAndGet() == 0) {
                    updateFinalExperiment(experimentId, request, finalResults, judgmentList);
                }
            }
        } catch (Exception e) {
            handleFailure(e, hasFailure, experimentId, request);
        }
    }

    private void handleFailure(Exception error, AtomicBoolean hasFailure, String experimentId, PostExperimentRequest request) {
        if (hasFailure.compareAndSet(false, true)) {
            handleAsyncFailure(experimentId, request, "Failed to process metrics", error);
        }
    }

    private void updateFinalExperiment(
        String experimentId,
        PostExperimentRequest request,
        List<Map<String, Object>> finalResults,
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

    private void handleAsyncFailure(String experimentId, PostExperimentRequest request, String message, Exception error) {
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
            List.of(Map.of("error", error.getMessage()))
        );

        experimentDao.updateExperiment(
            errorExperiment,
            ActionListener.wrap(
                response -> LOGGER.info("Updated experiment {} status to ERROR", experimentId),
                e -> LOGGER.error("Failed to update error status for experiment: " + experimentId, e)
            )
        );
    }
}
