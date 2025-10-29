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
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import org.opensearch.action.search.SearchResponse;
import org.opensearch.common.util.concurrent.FutureUtils;
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
import org.opensearch.searchrelevance.scheduler.ExperimentCancellationToken;
import org.opensearch.searchrelevance.scheduler.ScheduledExperimentRunnerManager;
import org.opensearch.searchrelevance.settings.SearchRelevanceSettingsAccessor;
import org.opensearch.searchrelevance.transport.experiment.PutExperimentRequest;
import org.opensearch.searchrelevance.transport.experiment.PutExperimentTransportAction;
import org.opensearch.searchrelevance.utils.TimeUtils;
import org.opensearch.threadpool.ThreadPool;

import com.google.common.annotations.VisibleForTesting;

import lombok.AllArgsConstructor;
import lombok.extern.log4j.Log4j2;

/**
 * ExperimentRunningManager helps isolate the logic for running the steps that was
 * in PutExperimentTransportAction and ScheduledExperimentRunnerManager. There are
 * 2 paths where the code will use {@link ExperimentRunningManager}: The experiment
 * was scheduled to run, or the user manually triggered through
 * {@link PutExperimentTransportAction}.
 *
 * <p>
 * When running an experiment, we need a {@link QuerySet} and a
 * list of {@link SearchConfiguration}. We then need to pass that into either
 * {@link MetricsHelper} for pairwise experiments,
 * {@link HybridOptimizerExperimentProcessor} for hybrid experiments,
 * or {@link PointwiseExperimentProcessor}
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
    private static final double MEMORY_WARNING_THRESHOLD = 0.75; // 75% usage
    private static final double MEMORY_CRITICAL_THRESHOLD = 0.90; // 90% usage
    private static final int RESULTS_SIZE_WARNING = 50000; // Warn at 10k results
    /**
     * There is only one instance of {@link ScheduledExperimentRunnerManager}.
     * While there may be multiple instances of {@link ExperimentRunningManager}
     * since this is also used for {@link PutExperimentTransportAction},
     * the only time runningFutures is used is when this class is run through
     * the unique instance of {@link ScheduledExperimentRunnerManager}.
     */
    private final Map<String, List<Future<?>>> runningFutures = new ConcurrentHashMap<>();

    /**
     * Starts the experiment by setting up cancellation callback for the cancellation token and also retrieves the queryset.
     *
     * One thing to be careful about is that only at most scheduled run for a given scheduled experiment run id 
     * should be in the system at all times.
     * @param experimentId - the id of the experiment to be run
     * @param request - required parameters for placing a request to start an experiment
     * @param cancellationToken - reference to cancellation state of scheduled experiment and cancels all futures tied to it
     * @param actuallyFinished - tracks when the task and all asynchronous processes are completed
     */
    public void startExperimentRun(
        String experimentId,
        PutExperimentRequest request,
        ExperimentCancellationToken cancellationToken,
        CountDownLatch actuallyFinished
    ) {
        List<Future<?>> futures = new ArrayList<>();
        if (request.getScheduledExperimentResultId() != null) {
            if (runningFutures.containsKey(request.getScheduledExperimentResultId())) {
                handleAsyncFailure(
                    experimentId,
                    request,
                    "There is a running scheduled run with the same scheduled experiment id",
                    new Exception("Cannot run experiment!"),
                    actuallyFinished
                );
                log.error("Cannot run experiment as there is a running scheduled run with the same experiment id, {}", experimentId);
                return;
            }
            runningFutures.compute(request.getScheduledExperimentResultId(), (key, existingList) -> {
                List<Future<?>> list = existingList != null ? existingList : new CopyOnWriteArrayList<>();
                return list;
            });

            // register cancellation callback
            // If the
            cancellationToken.onCancel(() -> {
                runningFutures.get(request.getScheduledExperimentResultId()).forEach(f -> FutureUtils.cancel(f));
                runningFutures.remove(request.getScheduledExperimentResultId());
            });
        }
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
                    updateFinalExperiment(experimentId, request, new ArrayList<>(), request.getJudgmentList(), actuallyFinished);
                    return;
                }

                // Then get SearchConfigurations asynchronously (this will also start the experiment)
                fetchSearchConfigurationsAsync(experimentId, request, queryTextWithReferences, cancellationToken, actuallyFinished);
            } catch (Exception e) {
                handleAsyncFailure(experimentId, request, "Failed to process QuerySet", e, actuallyFinished);
            }
        }, e -> { handleAsyncFailure(experimentId, request, "Failed to fetch QuerySet", e, actuallyFinished); }));
    }

    @VisibleForTesting
    void fetchSearchConfigurationsAsync(
        String experimentId,
        PutExperimentRequest request,
        List<String> queryTextWithReferences,
        ExperimentCancellationToken cancellationToken,
        CountDownLatch actuallyFinished
    ) {
        Map<String, SearchConfigurationDetails> searchConfigurations = new HashMap<>();
        AtomicBoolean hasFailure = new AtomicBoolean(false);
        List<CompletableFuture<Entry<String, Object>>> configFutures = new ArrayList<>();

        for (String configId : request.getSearchConfigurationList()) {
            CompletableFuture<Entry<String, Object>> singleSearchConfigurationFuture = fetchSingleSearchConfigurationAsync(
                experimentId,
                request,
                queryTextWithReferences,
                hasFailure,
                configId,
                cancellationToken,
                actuallyFinished
            );
            // We will have to set futures to be cancelled in case of timeout.
            if (request.getScheduledExperimentResultId() != null && checkIfCancelled(cancellationToken) == false) {
                // This should be synchronized.
                try {
                    runningFutures.get(request.getScheduledExperimentResultId()).add(singleSearchConfigurationFuture);
                } catch (Exception e) {
                    log.info(
                        "Fetching search configuration, {} for scheduled experiment with underlying experiment {} cannot be completed",
                        configId,
                        experimentId
                    );
                }
            }
            configFutures.add(singleSearchConfigurationFuture);
        }

        for (CompletableFuture<Entry<String, Object>> configFuture : configFutures) {
            Entry<String, Object> configEntry;
            try {
                // The config future will be waited on, but there is a chance the future might be null.
                configEntry = configFuture.get();
            } catch (InterruptedException e) {
                handleFailure(e, hasFailure, experimentId, request, actuallyFinished);
                return;
            } catch (ExecutionException e) {
                handleFailure(e, hasFailure, experimentId, request, actuallyFinished);
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
            request.getJudgmentList(),
            cancellationToken,
            actuallyFinished
        );
    }

    private boolean checkIfCancelled(ExperimentCancellationToken cancellationToken) {
        if (cancellationToken != null && cancellationToken.isCancelled()) {
            return true;
        }
        return false;
    }

    private CompletableFuture<Entry<String, Object>> fetchSingleSearchConfigurationAsync(
        String experimentId,
        PutExperimentRequest request,
        List<String> queryTextWithReferences,
        AtomicBoolean hasFailure,
        String configId,
        ExperimentCancellationToken cancellationToken,
        CountDownLatch actuallyFinished
    ) {
        CompletableFuture<Entry<String, Object>> future = new CompletableFuture<>();
        searchConfigurationDao.getSearchConfiguration(configId, ActionListener.wrap(searchConfigResponse -> {
            try {
                if (hasFailure.get() || checkIfCancelled(cancellationToken)) {
                    log.info("Experiment {} has been timed out while search configuration fetching id {}", experimentId, configId);
                    future.completeExceptionally(new Exception("Experiment Cancelled"));
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
                    handleAsyncFailure(experimentId, request, "Failed to process SearchConfiguration", e, actuallyFinished);
                }
            }
        }, e -> {
            future.completeExceptionally(e);
            if (hasFailure.compareAndSet(false, true)) {
                handleAsyncFailure(experimentId, request, "Failed to fetch SearchConfiguration: " + configId, e, actuallyFinished);
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
        // TODO: finalResults can incur a lot of memory, so we need to make sure to monitor and log
        // if/when it goes over thresholds. https://github.com/opensearch-project/search-relevance/issues/283
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
            request.getJudgmentList(),
            null,
            null
        );
    }

    @VisibleForTesting
    void executeExperimentEvaluation(
        String experimentId,
        PutExperimentRequest request,
        Map<String, SearchConfigurationDetails> searchConfigurations,
        List<String> queryTexts,
        List<Map<String, Object>> finalResults,
        AtomicInteger pendingQueries,
        AtomicBoolean hasFailure,
        List<String> judgmentList,
        ExperimentCancellationToken cancellationToken,
        CountDownLatch actuallyFinished
    ) {
        int completedQueries = 0;
        int totalQueries = queryTexts.size();
        for (String queryText : queryTexts) {
            // We need to process metrics for every query text, therefore, we have to keep track of
            // Any previous failures or tiimeout cancellations.
            if (hasFailure.get() || checkIfCancelled(cancellationToken)) {
                log.info(
                    "Scheduled experiment based on underlying experiment {} has been timed out while executing experiments for each queryText on queryText, {}. Completed {} queries out of {} queries",
                    experimentId,
                    queryText,
                    completedQueries,
                    totalQueries
                );
                handleFailure(new Exception("Experiment cancelled"), hasFailure, experimentId, request, actuallyFinished);
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
                            judgmentList,
                            cancellationToken,
                            actuallyFinished
                        ),
                        error -> handleFailure(error, hasFailure, experimentId, request, actuallyFinished)
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
                    cancellationToken,
                    runningFutures,
                    ActionListener.wrap(
                        queryResults -> handleQueryResults(
                            queryText,
                            queryResults,
                            finalResults,
                            pendingQueries,
                            experimentId,
                            request,
                            hasFailure,
                            judgmentList,
                            cancellationToken,
                            actuallyFinished
                        ),
                        error -> handleFailure(error, hasFailure, experimentId, request, actuallyFinished)
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
                    cancellationToken,
                    ActionListener.wrap(
                        queryResults -> handleQueryResults(
                            queryText,
                            queryResults,
                            finalResults,
                            pendingQueries,
                            experimentId,
                            request,
                            hasFailure,
                            judgmentList,
                            cancellationToken,
                            actuallyFinished
                        ),
                        error -> handleFailure(error, hasFailure, experimentId, request, actuallyFinished)
                    )
                );
            } else {
                throw new SearchRelevanceException("Unknown experimentType" + request.getType(), RestStatus.BAD_REQUEST);
            }
            completedQueries++;
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
        List<String> judgmentList,
        ExperimentCancellationToken cancellationToken,
        CountDownLatch actuallyFinished
    ) {
        if (hasFailure.get() || checkIfCancelled(cancellationToken)) {
            log.info(
                "Experiment with underlying id {} has been timed out or failed before handling query results, therefore we should not update results",
                experimentId
            );
            handleFailure(null, hasFailure, experimentId, request, actuallyFinished);
            return;
        }

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
                    updateFinalExperiment(experimentId, request, finalResults, judgmentList, actuallyFinished);
                }
            }
        } catch (Exception e) {
            handleFailure(e, hasFailure, experimentId, request, actuallyFinished);
        }
    }

    private void handleFailure(
        Exception error,
        AtomicBoolean hasFailure,
        String experimentId,
        PutExperimentRequest request,
        CountDownLatch actuallyFinished
    ) {
        if (hasFailure.compareAndSet(false, true)) {
            handleAsyncFailure(experimentId, request, "Failed to process metrics", error, actuallyFinished);
        }
    }

    private void updateFinalExperiment(
        String experimentId,
        PutExperimentRequest request,
        List<Map<String, Object>> finalResults,
        List<String> judgmentList,
        CountDownLatch actuallyFinished
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
                    error -> handleAsyncFailure(experimentId, request, "Failed to update final experiment", error, actuallyFinished)
                )
            );
            actuallyFinished.countDown();
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
                error -> handleAsyncFailure(experimentId, request, "Failed to update final experiment", error, actuallyFinished)
            )
        );
    }

    private void handleAsyncFailure(
        String experimentId,
        PutExperimentRequest request,
        String message,
        Exception error,
        CountDownLatch actuallyFinished
    ) {
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
            actuallyFinished.countDown();
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
