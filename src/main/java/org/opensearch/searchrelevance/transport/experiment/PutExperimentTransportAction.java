/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */
package org.opensearch.searchrelevance.transport.experiment;

import static org.opensearch.searchrelevance.common.MetricsConstants.QUERY_TEXT;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import org.opensearch.action.index.IndexResponse;
import org.opensearch.action.search.SearchResponse;
import org.opensearch.action.support.ActionFilters;
import org.opensearch.action.support.HandledTransportAction;
import org.opensearch.common.inject.Inject;
import org.opensearch.core.action.ActionListener;
import org.opensearch.core.rest.RestStatus;
import org.opensearch.searchrelevance.dao.ExperimentDao;
import org.opensearch.searchrelevance.dao.JudgmentDao;
import org.opensearch.searchrelevance.dao.QuerySetDao;
import org.opensearch.searchrelevance.dao.SearchConfigurationDao;
import org.opensearch.searchrelevance.exception.SearchRelevanceException;
import org.opensearch.searchrelevance.executors.ExperimentTaskManager;
import org.opensearch.searchrelevance.experiment.HybridOptimizerExperimentProcessor;
import org.opensearch.searchrelevance.experiment.PointwiseExperimentProcessor;
import org.opensearch.searchrelevance.metrics.MetricsHelper;
import org.opensearch.searchrelevance.model.AsyncStatus;
import org.opensearch.searchrelevance.model.Experiment;
import org.opensearch.searchrelevance.model.ExperimentType;
import org.opensearch.searchrelevance.model.QuerySet;
import org.opensearch.searchrelevance.model.SearchConfiguration;
import org.opensearch.searchrelevance.model.SearchConfigurationDetails;
import org.opensearch.searchrelevance.utils.TimeUtils;
import org.opensearch.tasks.Task;
import org.opensearch.transport.TransportService;

import lombok.extern.log4j.Log4j2;

/**
 * Handles transport actions for creating experiments in the system.
 */
@Log4j2
public class PutExperimentTransportAction extends HandledTransportAction<PutExperimentRequest, IndexResponse> {

    private final ExperimentDao experimentDao;
    private final QuerySetDao querySetDao;
    private final SearchConfigurationDao searchConfigurationDao;
    private final MetricsHelper metricsHelper;
    private final HybridOptimizerExperimentProcessor hybridOptimizerExperimentProcessor;
    private final PointwiseExperimentProcessor pointwiseExperimentProcessor;

    @Inject
    public PutExperimentTransportAction(
        TransportService transportService,
        ActionFilters actionFilters,
        ExperimentDao experimentDao,
        QuerySetDao querySetDao,
        SearchConfigurationDao searchConfigurationDao,
        MetricsHelper metricsHelper,
        JudgmentDao judgmentDao,
        ExperimentTaskManager experimentTaskManager
    ) {
        super(PutExperimentAction.NAME, transportService, actionFilters, PutExperimentRequest::new);
        this.experimentDao = experimentDao;
        this.querySetDao = querySetDao;
        this.searchConfigurationDao = searchConfigurationDao;
        this.metricsHelper = metricsHelper;
        this.hybridOptimizerExperimentProcessor = new HybridOptimizerExperimentProcessor(judgmentDao, experimentTaskManager);
        this.pointwiseExperimentProcessor = new PointwiseExperimentProcessor(judgmentDao, experimentTaskManager);
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
                log.error("Failed to create initial experiment", e);
                listener.onFailure(
                    new SearchRelevanceException("Failed to create initial experiment", e, RestStatus.INTERNAL_SERVER_ERROR)
                );
            }));

        } catch (Exception e) {
            log.error("Failed to process experiment request", e);
            listener.onFailure(new SearchRelevanceException("Failed to process experiment request", e, RestStatus.INTERNAL_SERVER_ERROR));
        }
    }

    private void triggerAsyncProcessing(String experimentId, PutExperimentRequest request) {
        // First, get QuerySet asynchronously
        querySetDao.getQuerySet(request.getQuerySetId(), ActionListener.wrap(querySetResponse -> {
            try {
                QuerySet querySet = convertToQuerySet(querySetResponse);
                List<String> queryTextWithReferences = querySet.querySetQueries()
                    .stream()
                    .map(e -> e.queryText())
                    .collect(Collectors.toList());

                // Check if queryTexts is empty and complete experiment immediately
                if (queryTextWithReferences.isEmpty()) {
                    log.info("Experiment {} completed with 0 query texts", experimentId);
                    updateFinalExperiment(experimentId, request, new ArrayList<>(), request.getJudgmentList());
                    return;
                }

                // Then get SearchConfigurations asynchronously
                fetchSearchConfigurationsAsync(experimentId, request, queryTextWithReferences);
            } catch (Exception e) {
                handleAsyncFailure(experimentId, request, "Failed to process QuerySet", e);
            }
        }, e -> { handleAsyncFailure(experimentId, request, "Failed to fetch QuerySet", e); }));
    }

    private void fetchSearchConfigurationsAsync(String experimentId, PutExperimentRequest request, List<String> queryTextWithReferences) {
        Map<String, SearchConfigurationDetails> searchConfigurations = new HashMap<>();
        AtomicInteger pendingConfigs = new AtomicInteger(request.getSearchConfigurationList().size());
        AtomicBoolean hasFailure = new AtomicBoolean(false);

        for (String configId : request.getSearchConfigurationList()) {
            searchConfigurationDao.getSearchConfiguration(configId, ActionListener.wrap(searchConfigResponse -> {
                try {
                    if (hasFailure.get()) return;

                    SearchConfiguration config = convertToSearchConfiguration(searchConfigResponse);
                    synchronized (searchConfigurations) {
                        searchConfigurations.put(
                            config.id(),
                            SearchConfigurationDetails.builder()
                                .index(config.index())
                                .query(config.query())
                                .pipeline(config.searchPipeline())
                                .build()
                        );
                    }

                    // Check if all configurations are fetched
                    if (pendingConfigs.decrementAndGet() == 0) {
                        calculateMetricsAsync(experimentId, request, searchConfigurations, queryTextWithReferences);
                    }
                } catch (Exception e) {
                    if (hasFailure.compareAndSet(false, true)) {
                        handleAsyncFailure(experimentId, request, "Failed to process SearchConfiguration", e);
                    }
                }
            }, e -> {
                if (hasFailure.compareAndSet(false, true)) {
                    handleAsyncFailure(experimentId, request, "Failed to fetch SearchConfiguration: " + configId, e);
                }
            }));
        }
    }

    private QuerySet convertToQuerySet(SearchResponse response) {
        if (response.getHits().getTotalHits().value() == 0) {
            throw new SearchRelevanceException("QuerySet not found", RestStatus.NOT_FOUND);
        }

        Map<String, Object> sourceMap = response.getHits().getHits()[0].getSourceAsMap();

        // Convert querySetQueries from list of maps to List<QuerySetEntry>
        List<org.opensearch.searchrelevance.model.QuerySetEntry> querySetEntries = new ArrayList<>();
        Object querySetQueriesObj = sourceMap.get("querySetQueries");
        if (querySetQueriesObj instanceof List) {
            List<Map<String, Object>> querySetQueriesList = (List<Map<String, Object>>) querySetQueriesObj;
            querySetEntries = querySetQueriesList.stream()
                .map(
                    entryMap -> org.opensearch.searchrelevance.model.QuerySetEntry.Builder.builder()
                        .queryText((String) entryMap.get("queryText"))
                        .build()
                )
                .collect(Collectors.toList());
        }

        return org.opensearch.searchrelevance.model.QuerySet.Builder.builder()
            .id((String) sourceMap.get("id"))
            .name((String) sourceMap.get("name"))
            .description((String) sourceMap.get("description"))
            .timestamp((String) sourceMap.get("timestamp"))
            .sampling((String) sourceMap.get("sampling"))
            .querySetQueries(querySetEntries)
            .build();
    }

    private SearchConfiguration convertToSearchConfiguration(SearchResponse response) {
        if (response.getHits().getTotalHits().value() == 0) {
            throw new SearchRelevanceException("SearchConfiguration not found", RestStatus.NOT_FOUND);
        }

        Map<String, Object> source = response.getHits().getHits()[0].getSourceAsMap();
        return new SearchConfiguration(
            (String) source.get("id"),
            (String) source.get("name"),
            (String) source.get("timestamp"),
            (String) source.get("index"),
            (String) source.get("query"),
            (String) source.get("searchPipeline")
        );
    }

    private void calculateMetricsAsync(
        String experimentId,
        PutExperimentRequest request,
        Map<String, SearchConfigurationDetails> searchConfigurations,
        List<String> queryTextWithReferences
    ) {
        if (queryTextWithReferences == null || searchConfigurations == null) {
            throw new IllegalStateException("Missing required data for metrics calculation");
        }

        processQueryTextMetrics(experimentId, request, searchConfigurations, queryTextWithReferences);
    }

    private void processQueryTextMetrics(
        String experimentId,
        PutExperimentRequest request,
        Map<String, SearchConfigurationDetails> searchConfigurations,
        List<String> queryTexts
    ) {
        List<Map<String, Object>> finalResults = Collections.synchronizedList(new ArrayList<>());
        AtomicInteger pendingQueries = new AtomicInteger(queryTexts.size());
        AtomicBoolean hasFailure = new AtomicBoolean(false);

        executeExperimentEvaluation(
            experimentId,
            request,
            searchConfigurations,
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
        Map<String, SearchConfigurationDetails> searchConfigurations,
        List<String> queryTexts,
        List<Map<String, Object>> finalResults,
        AtomicInteger pendingQueries,
        AtomicBoolean hasFailure,
        List<String> judgmentList
    ) {
        for (String queryText : queryTexts) {
            if (hasFailure.get()) {
                return;
            }

            if (request.getType() == ExperimentType.PAIRWISE_COMPARISON) {
                metricsHelper.processPairwiseMetrics(
                    queryText,
                    searchConfigurations,
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
                // Use our task manager implementation for hybrid optimizer
                hybridOptimizerExperimentProcessor.processHybridOptimizerExperiment(
                    experimentId,
                    queryText,
                    searchConfigurations,
                    judgmentList,
                    request.getSize(),
                    hasFailure,
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
            } else if (request.getType() == ExperimentType.POINTWISE_EVALUATION) {
                pointwiseExperimentProcessor.processPointwiseExperiment(
                    experimentId,
                    queryText,
                    searchConfigurations,
                    judgmentList,
                    request.getSize(),
                    hasFailure,
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
                // Handle different response formats based on experiment type
                if (request.getType() == ExperimentType.HYBRID_OPTIMIZER) {
                    // For HYBRID_OPTIMIZER, the response contains searchConfigurationResults
                    List<Map<String, Object>> searchConfigResults = (List<Map<String, Object>>) queryResults.get(
                        "searchConfigurationResults"
                    );
                    if (searchConfigResults != null) {
                        for (Map<String, Object> configResult : searchConfigResults) {
                            Map<String, Object> resultWithQuery = new HashMap<>(configResult);
                            resultWithQuery.put(QUERY_TEXT, queryText);
                            finalResults.add(resultWithQuery);
                        }
                    }
                } else if (request.getType() == ExperimentType.POINTWISE_EVALUATION) {
                    // For POINTWISE_EVALUATION, the response contains results array
                    List<Map<String, Object>> pointwiseResults = (List<Map<String, Object>>) queryResults.get("results");
                    if (pointwiseResults != null) {
                        // Results already contain the proper format with evaluationId, searchConfigurationId, queryText
                        finalResults.addAll(pointwiseResults);
                    }
                } else {
                    // For other experiment types, use generic format
                    queryResults.put(QUERY_TEXT, queryText);
                    finalResults.add(queryResults);
                }

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
                response -> log.debug("Updated final experiment: {}", experimentId),
                error -> handleAsyncFailure(experimentId, request, "Failed to update final experiment", error)
            )
        );
    }

    private void handleAsyncFailure(String experimentId, PutExperimentRequest request, String message, Exception error) {
        log.error(message + " for experiment: " + experimentId, error);

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
                response -> log.info("Updated experiment {} status to ERROR", experimentId),
                e -> log.error("Failed to update error status for experiment: " + experimentId, e)
            )
        );
    }
}
