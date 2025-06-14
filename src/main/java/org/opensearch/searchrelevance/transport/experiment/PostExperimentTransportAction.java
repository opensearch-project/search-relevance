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
import java.util.Arrays;
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
import org.opensearch.searchrelevance.model.QuerySet;
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
            QuerySet querySet = querySetDao.getQuerySetSync(request.getQuerySetId());
            List<String> queryTextWithReferences = querySet.querySetQueries().stream().map(e -> e.queryText()).collect(Collectors.toList());

            List<String> queries = request.getEvaluationResultList()
                .stream()
                .map(e -> (String) e.get("searchText"))
                .collect(Collectors.toList());

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
            String judgmentListId = request.getJudgmentList().get(0);

            Map<String, List<String>> indexAndQueries = new HashMap<>();
            for (SearchConfiguration config : searchConfigurations) {
                indexAndQueries.put(config.id(), Arrays.asList(config.index(), config.query(), config.searchPipeline()));
            }
            processExperiment(experimentId, request, searchConfigurationId, judgmentListId, indexAndQueries, queries);
        } catch (Exception e) {
            handleAsyncFailure(experimentId, request, "Failed to start async processing", e);
        }
    }

    private void processExperiment(
        String experimentId,
        PostExperimentRequest request,
        String searchConfigurationId,
        String judgmentListId,
        Map<String, List<String>> indexAndQueries,
        List<String> queryTexts
    ) {
        List<Map<String, Object>> finalResults = Collections.synchronizedList(new ArrayList<>());
        AtomicInteger pendingQueries = new AtomicInteger(queryTexts.size());
        AtomicBoolean hasFailure = new AtomicBoolean(false);

        importExperiment(
            experimentId,
            request,
            searchConfigurationId,
            judgmentListId,
            indexAndQueries,
            queryTexts,
            finalResults,
            pendingQueries,
            hasFailure,
            request.getJudgmentList()
        );
    }

    private void importExperiment(
        String experimentId,
        PostExperimentRequest request,
        String searchConfigurationId,
        String judgmentListId,
        Map<String, List<String>> indexAndQueries,
        List<String> queryTexts,
        List<Map<String, Object>> finalResults,
        AtomicInteger pendingQueries,
        AtomicBoolean hasFailure,
        List<String> judgmentList
    ) {
        if (request.getType() == ExperimentType.POINTWISE_EVALUATION) {
            if (request.getJudgmentList().size() != 1) {
                // throw new Exception("Must have exactly one judgment list. Had " + request.getJudgmentList().size() + " size.");
            }
            List localJudgmentListId = request.getJudgmentList();
            for (Map<String, Object> evalResultMap : request.getEvaluationResultList()) {
                final String evaluationId = UUID.randomUUID().toString();
                LOGGER.warn("evaluation id " + evaluationId);
                String localQueryText = (String) evalResultMap.get("searchText");
                // List localJudgmentIds = (List) evalResultMap.get("judgmentIds");

                List localMetrics = (List) evalResultMap.get("metrics");
                List localDocIds = (List) evalResultMap.get("documentIds");

                List<Map<String, Object>> metrics = List.of(Map.of("dcg@10", 0.8, "ndcg@10", 0.75));

                EvaluationResult evaluationResult = new EvaluationResult(
                    evaluationId,
                    TimeUtils.getTimestamp(),
                    searchConfigurationId,
                    localQueryText,
                    localJudgmentListId,
                    localDocIds,
                    localMetrics.subList(0, 4)
                );

                evaluationResultDao.putEvaluationResult(evaluationResult, ActionListener.wrap(success -> {
                    Map<String, Object> evalResults = Collections.synchronizedMap(new HashMap<>());
                    evalResults.put(POINTWISE_FIELD_NAME_SEARCH_CONFIGURATION_ID, searchConfigurationId);
                    evalResults.put(POINTWISE_FIELD_NAME_EVALUATION_ID, evaluationId);
                    evalResults.put(PAIRWISE_FIELD_NAME_QUERY_TEXT, localQueryText);
                    finalResults.add(evalResults);
                    // configToEvalIds.put(POINTWISE_FIELD_NAME_SEARCH_CONFIGURATION_ID, searchConfigurationId);
                    // configToEvalIds.put(POINTWISE_FIELD_NAME_EVALUATION_ID, evaluationId);

                    if (pendingQueries.decrementAndGet() == 0) {
                        updateFinalExperiment(experimentId, request, finalResults, judgmentList);
                    }
                }, error -> {
                    hasFailure.set(true);
                    LOGGER.error(error);
                    // listener.onFailure(error);
                }));
            }
        } else {
            throw new SearchRelevanceException(
                "experimentType" + request.getType() + " not supported for import scenario",
                RestStatus.BAD_REQUEST
            );
        }

        if (false) {
            for (String queryText : queryTexts) {
                if (request.getType() == ExperimentType.POINTWISE_EVALUATION) {
                    final String evaluationId = UUID.randomUUID().toString();
                    LOGGER.warn("evaluation id " + evaluationId);

                    List<String> judgmentIds = List.of(judgmentListId);
                    List<String> docIds = List.of("doca", "docb");
                    List<Map<String, Object>> metrics = List.of(Map.of("dcg@10", 0.8, "ndcg@10", 0.75));
                    EvaluationResult evaluationResult = new EvaluationResult(
                        evaluationId,
                        TimeUtils.getTimestamp(),
                        searchConfigurationId,
                        queryText,
                        judgmentIds,
                        docIds,
                        metrics
                    );

                    Map<String, Object> queryResults = new HashMap<String, Object>();
                    queryResults.put(PAIRWISE_FIELD_NAME_QUERY_TEXT, queryText);
                    queryResults.put(POINTWISE_FIELD_NAME_SEARCH_CONFIGURATION_ID, searchConfigurationId);
                    queryResults.put(POINTWISE_FIELD_NAME_EVALUATION_ID, evaluationId);
                    evaluationResultDao.putEvaluationResult(evaluationResult, ActionListener.wrap(success -> {
                        finalResults.add(queryResults);
                        // configToEvalIds.put(POINTWISE_FIELD_NAME_SEARCH_CONFIGURATION_ID, searchConfigurationId);
                        // configToEvalIds.put(POINTWISE_FIELD_NAME_EVALUATION_ID, evaluationId);
                        if (pendingQueries.decrementAndGet() == 0) {
                            updateFinalExperiment(experimentId, request, finalResults, judgmentList);
                        }
                    }, error -> {
                        hasFailure.set(true);
                        LOGGER.error(error);
                        // listener.onFailure(error);
                    }));

                } else {
                    throw new SearchRelevanceException(
                        "experimentType" + request.getType() + " not supported for import scenario",
                        RestStatus.BAD_REQUEST
                    );
                }
            }
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
