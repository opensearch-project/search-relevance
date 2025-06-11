/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */
package org.opensearch.searchrelevance.transport.experiment;

import static org.opensearch.searchrelevance.common.MetricsConstants.PAIRWISE_FIELD_NAME_QUERY_TEXT;
import static org.opensearch.searchrelevance.experiment.ExperimentOptionsForHybridSearch.EXPERIMENT_OPTION_COMBINATION_TECHNIQUE;
import static org.opensearch.searchrelevance.experiment.ExperimentOptionsForHybridSearch.EXPERIMENT_OPTION_NORMALIZATION_TECHNIQUE;
import static org.opensearch.searchrelevance.experiment.ExperimentOptionsForHybridSearch.EXPERIMENT_OPTION_WEIGHTS_FOR_COMBINATION;

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
import org.opensearch.searchrelevance.dao.ExperimentDao;
import org.opensearch.searchrelevance.dao.ExperimentVariantDao;
import org.opensearch.searchrelevance.dao.QuerySetDao;
import org.opensearch.searchrelevance.dao.SearchConfigurationDao;
import org.opensearch.searchrelevance.exception.SearchRelevanceException;
import org.opensearch.searchrelevance.experiment.ExperimentOptionsFactory;
import org.opensearch.searchrelevance.experiment.ExperimentOptionsForHybridSearch;
import org.opensearch.searchrelevance.experiment.ExperimentVariantHybridSearchDTO;
import org.opensearch.searchrelevance.metrics.MetricsHelper;
import org.opensearch.searchrelevance.model.AsyncStatus;
import org.opensearch.searchrelevance.model.Experiment;
import org.opensearch.searchrelevance.model.ExperimentType;
import org.opensearch.searchrelevance.model.ExperimentVariant;
import org.opensearch.searchrelevance.model.QuerySet;
import org.opensearch.searchrelevance.model.SearchConfiguration;
import org.opensearch.searchrelevance.utils.TimeUtils;
import org.opensearch.tasks.Task;
import org.opensearch.transport.TransportService;

/**
 * Handles transport actions for creating experiments in the system.
 */
public class PutExperimentTransportAction extends HandledTransportAction<PutExperimentRequest, IndexResponse> {

    private final ClusterService clusterService;
    private final ExperimentDao experimentDao;
    private final ExperimentVariantDao experimentVariantDao;
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
        ExperimentVariantDao experimentVariantDao,
        QuerySetDao querySetDao,
        SearchConfigurationDao searchConfigurationDao,
        MetricsHelper metricsHelper
    ) {
        super(PutExperimentAction.NAME, transportService, actionFilters, PutExperimentRequest::new);
        this.clusterService = clusterService;
        this.experimentDao = experimentDao;
        this.experimentVariantDao = experimentVariantDao;
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

        try {
            String id = UUID.randomUUID().toString();
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

    private void triggerAsyncProcessing(String experimentId, PutExperimentRequest request) {
        try {
            QuerySet querySet = querySetDao.getQuerySetSync(request.getQuerySetId());
            List<String> queryTextWithReferences = querySet.querySetQueries().stream().map(e -> e.queryText()).collect(Collectors.toList());

            List<SearchConfiguration> searchConfigurations = request.getSearchConfigurationList()
                .stream()
                .map(id -> searchConfigurationDao.getSearchConfigurationSync(id))
                .collect(Collectors.toList());
            Map<String, List<String>> indexAndQueries = new HashMap<>();
            for (SearchConfiguration config : searchConfigurations) {
                indexAndQueries.put(config.id(), Arrays.asList(config.index(), config.query(), config.searchPipeline()));
            }
            calculateMetricsAsync(experimentId, request, indexAndQueries, queryTextWithReferences);
        } catch (Exception e) {
            handleAsyncFailure(experimentId, request, "Failed to start async processing", e);
        }
    }

    private void calculateMetricsAsync(
        String experimentId,
        PutExperimentRequest request,
        Map<String, List<String>> indexAndQueries,
        List<String> queryTextWithReferences
    ) {
        if (queryTextWithReferences == null || indexAndQueries == null) {
            throw new IllegalStateException("Missing required data for metrics calculation");
        }

        processQueryTextMetrics(experimentId, request, indexAndQueries, queryTextWithReferences);
    }

    private void processQueryTextMetrics(
        String experimentId,
        PutExperimentRequest request,
        Map<String, List<String>> indexAndQueries,
        List<String> queryTexts
    ) {
        List<Map<String, Object>> finalResults = Collections.synchronizedList(new ArrayList<>());
        AtomicInteger pendingQueries = new AtomicInteger(queryTexts.size());
        AtomicBoolean hasFailure = new AtomicBoolean(false);

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

    private void executeExperimentEvaluation(
        String experimentId,
        PutExperimentRequest request,
        Map<String, List<String>> indexAndQueries,
        List<String> queryTexts,
        List<Map<String, Object>> finalResults,
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
            } else if (request.getType() == ExperimentType.HYBRID_OPTIMIZER) {
                Map<String, Object> defaultParametersForHybridSearch = ExperimentOptionsFactory
                    .createDefaultExperimentParametersForHybridSearch();
                ExperimentOptionsForHybridSearch experimentOptionForHybridSearch =
                    (ExperimentOptionsForHybridSearch) ExperimentOptionsFactory.createExperimentOptions(
                        ExperimentOptionsFactory.HYBRID_SEARCH_EXPERIMENT_OPTIONS,
                        defaultParametersForHybridSearch
                    );
                List<ExperimentVariantHybridSearchDTO> experimentVariantDTOs = experimentOptionForHybridSearch.getParameterCombinations(
                    true
                );
                List<ExperimentVariant> experimentVariants = new ArrayList<>();
                for (ExperimentVariantHybridSearchDTO experimentVariantDTO : experimentVariantDTOs) {
                    Map<String, Object> parameters = new HashMap<>(
                        Map.of(
                            EXPERIMENT_OPTION_NORMALIZATION_TECHNIQUE,
                            experimentVariantDTO.getNormalizationTechnique(),
                            EXPERIMENT_OPTION_COMBINATION_TECHNIQUE,
                            experimentVariantDTO.getCombinationTechnique(),
                            EXPERIMENT_OPTION_WEIGHTS_FOR_COMBINATION,
                            experimentVariantDTO.getQueryWeightsForCombination()
                        )
                    );
                    String experimentVariantId = UUID.randomUUID().toString();
                    ExperimentVariant experimentVariant = new ExperimentVariant(
                        experimentVariantId,
                        TimeUtils.getTimestamp(),
                        ExperimentType.HYBRID_OPTIMIZER,
                        AsyncStatus.PROCESSING,
                        experimentId,
                        convertMapToNestedList(parameters),
                        Map.of()
                    );
                    experimentVariants.add(experimentVariant);
                    experimentVariantDao.putExperimentVariant(experimentVariant, ActionListener.wrap(response -> {}, e -> {}));
                }
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
                    experimentVariants
                );
            } else if (request.getType() == ExperimentType.POINTWISE_EVALUATION) {
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
            } else {
                throw new SearchRelevanceException("Unknown experimentType" + request.getType(), RestStatus.BAD_REQUEST);
            }
        }
    }

    private void handleQueryResults(
        String queryText,
        Map<String, Object> queryResults,
        List<Map<String, Object>> finalResults,
        AtomicInteger pendingQueries,
        String experimentId,
        PutExperimentRequest request,
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

    private void handleFailure(Exception error, AtomicBoolean hasFailure, String experimentId, PutExperimentRequest request) {
        if (hasFailure.compareAndSet(false, true)) {
            handleAsyncFailure(experimentId, request, "Failed to process metrics", error);
        }
    }

    private void updateFinalExperiment(
        String experimentId,
        PutExperimentRequest request,
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

    /**
     * Convert flat Map<String, Object> to nested List<Map<String, Object>> format
     */
    private List<Map<String, Object>> convertMapToNestedList(Map<String, Object> flatMap) {
        return flatMap.entrySet().stream().map(entry -> Map.of("name", entry.getKey(), "value", entry.getValue())).toList();
    }
}
