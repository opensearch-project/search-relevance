/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */
package org.opensearch.searchrelevance.executors;

import static org.opensearch.searchrelevance.common.PluginConstants.QUERY_TEXT;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import org.opensearch.action.search.SearchResponse;
import org.opensearch.core.action.ActionListener;
import org.opensearch.core.rest.RestStatus;
import org.opensearch.searchrelevance.dao.ExperimentDao;
import org.opensearch.searchrelevance.dao.QuerySetDao;
import org.opensearch.searchrelevance.dao.ScheduledExperimentHistoryDao;
import org.opensearch.searchrelevance.dao.SearchConfigurationDao;
import org.opensearch.searchrelevance.exception.SearchRelevanceException;
import org.opensearch.searchrelevance.experiment.HybridOptimizerExperimentProcessor;
import org.opensearch.searchrelevance.experiment.PointwiseExperimentProcessor;
import org.opensearch.searchrelevance.metrics.MetricsHelper;
import org.opensearch.searchrelevance.model.AsyncStatus;
import org.opensearch.searchrelevance.model.Experiment;
import org.opensearch.searchrelevance.model.ExperimentType;
import org.opensearch.searchrelevance.model.QuerySet;
import org.opensearch.searchrelevance.model.ScheduledExperimentResult;
import org.opensearch.searchrelevance.model.SearchConfiguration;
import org.opensearch.searchrelevance.model.SearchConfigurationDetails;
import org.opensearch.searchrelevance.settings.SearchRelevanceSettingsAccessor;
import org.opensearch.searchrelevance.transport.experiment.PutExperimentRequest;
import org.opensearch.searchrelevance.utils.ConcurrencyUtil;
import org.opensearch.searchrelevance.utils.TimeUtils;
import org.opensearch.threadpool.ThreadPool;

import lombok.AllArgsConstructor;
import lombok.extern.log4j.Log4j2;

/**
 * ExperimentRunningManager helps isolate the logic for running the logic in
 * PutExperimentTransportAction and ScheduledExperimentRunnerManager.
 */
@Log4j2
@AllArgsConstructor
public class ExperimentRunningManager {
    private ExperimentDao experimentDao;
    private QuerySetDao querySetDao;
    private SearchConfigurationDao searchConfigurationDao;
    private ScheduledExperimentHistoryDao scheduledExperimentHistoryDao;
    private MetricsHelper metricsHelper;
    private HybridOptimizerExperimentProcessor hybridOptimizerExperimentProcessor;
    private PointwiseExperimentProcessor pointwiseExperimentProcessor;
    private ThreadPool threadPool;
    private SearchRelevanceSettingsAccessor settingsAccessor;

    public void startExperimentRun(String experimentId, PutExperimentRequest request) {
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
        AtomicBoolean hasFailure = new AtomicBoolean(false);
        List<CompletableFuture<Entry<String, Object>>> configFutures = new ArrayList<>();

        for (String configId : request.getSearchConfigurationList()) {
            configFutures.add(
                ConcurrencyUtil.withTimeout(
                    fetchSingleSearchConfigurationAsync(experimentId, request, queryTextWithReferences, hasFailure, configId),
                    settingsAccessor.getScheduledExperimentsTimeout().getSeconds(),
                    threadPool
                )
            );
        }

        // Wait for all configurations to complete
        // If any of the futures fails, the exception would be handled
        // in the logic of that future. Therefore, no action for failure
        // is necessary here.
        CompletableFuture.allOf(configFutures.toArray(new CompletableFuture[0])).join();

        for (CompletableFuture<Entry<String, Object>> configFuture : configFutures) {
            Entry<String, Object> configEntry;
            try {
                configEntry = configFuture.get();
            } catch (InterruptedException e) {
                handleAsyncFailure(experimentId, request, "Failed to fetch SearchConfiguration", e);
                return;
            } catch (ExecutionException e) {
                handleAsyncFailure(experimentId, request, "Failed to fetch SearchConfiguration", e);
                return;
            }
            searchConfigurations.put(configEntry.getKey(), (SearchConfigurationDetails) configEntry.getValue());
        }

        if (queryTextWithReferences == null || searchConfigurations == null) {
            throw new IllegalStateException("Missing required data for metrics calculation");
        }

        List<Map<String, Object>> finalResults = Collections.synchronizedList(new ArrayList<>());
        AtomicInteger pendingQueries = new AtomicInteger(queryTextWithReferences.size());

        executeExperimentEvaluation(
            experimentId,
            request,
            searchConfigurations,
            queryTextWithReferences,
            finalResults,
            pendingQueries,
            hasFailure,
            request.getJudgmentList()
        );
    }

    private CompletableFuture<Entry<String, Object>> fetchSingleSearchConfigurationAsync(
        String experimentId,
        PutExperimentRequest request,
        List<String> queryTextWithReferences,
        AtomicBoolean hasFailure,
        String configId
    ) {
        CompletableFuture<Entry<String, Object>> future = new CompletableFuture<>();
        searchConfigurationDao.getSearchConfiguration(configId, ActionListener.wrap(searchConfigResponse -> {
            try {
                if (hasFailure.get()) {
                    future.complete(null);
                    return;
                }

                SearchConfiguration config = convertToSearchConfiguration(searchConfigResponse);

                future.complete(
                    Map.entry(
                        config.id(),
                        SearchConfigurationDetails.builder()
                            .index(config.index())
                            .query(config.query())
                            .pipeline(config.searchPipeline())
                            .build()
                    )
                );
            } catch (Exception e) {
                future.completeExceptionally(e);
                if (hasFailure.compareAndSet(false, true)) {
                    handleAsyncFailure(experimentId, request, "Failed to process SearchConfiguration", e);
                }
            }
        }, e -> {
            future.completeExceptionally(e);
            if (hasFailure.compareAndSet(false, true)) {
                handleAsyncFailure(experimentId, request, "Failed to fetch SearchConfiguration: " + configId, e);
            }
        }));
        return future;
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
                    request.getScheduledExperimentResultId(),
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
                    request.getScheduledExperimentResultId(),
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
        if (request.getScheduledExperimentResultId() != null) {
            ScheduledExperimentResult finalExperiment = new ScheduledExperimentResult(
                request.getScheduledExperimentResultId(),
                experimentId,
                TimeUtils.getTimestamp(),
                AsyncStatus.COMPLETED,
                finalResults
            );

            scheduledExperimentHistoryDao.updateScheduledExperimentResult(
                finalExperiment,
                ActionListener.wrap(
                    response -> log.debug("Updated completed scheduled experiment: {}", experimentId),
                    error -> handleAsyncFailure(experimentId, request, "Failed to update final experiment", error)
                )
            );
            return;
        }
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
        log.error(message + " for scheduled experiment: " + experimentId, error);
        if (request.getScheduledExperimentResultId() != null) {
            ScheduledExperimentResult finalExperiment = new ScheduledExperimentResult(
                request.getScheduledExperimentResultId(),
                experimentId,
                TimeUtils.getTimestamp(),
                AsyncStatus.ERROR,
                null
            );

            scheduledExperimentHistoryDao.updateScheduledExperimentResult(
                finalExperiment,
                ActionListener.wrap(
                    response -> log.info("Updated scheduled experiment {} status to ERROR", experimentId),
                    e -> log.error("Failed to update error status for scheduled experiment: " + experimentId, e)
                )
            );
            return;
        }

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
