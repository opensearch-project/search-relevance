/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */
package org.opensearch.searchrelevance.scheduler;

import static org.opensearch.searchrelevance.common.MetricsConstants.QUERY_TEXT;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.opensearch.action.search.SearchResponse;
import org.opensearch.core.action.ActionListener;
import org.opensearch.core.rest.RestStatus;
import org.opensearch.jobscheduler.spi.JobExecutionContext;
import org.opensearch.jobscheduler.spi.ScheduledJobParameter;
import org.opensearch.jobscheduler.spi.ScheduledJobRunner;
import org.opensearch.jobscheduler.spi.utils.LockService;
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
import org.opensearch.searchrelevance.model.SearchConfiguration;
import org.opensearch.searchrelevance.model.SearchConfigurationDetails;
import org.opensearch.searchrelevance.utils.TimeUtils;
import org.opensearch.threadpool.ThreadPool;
import org.opensearch.transport.client.Client;

public class SearchRelevanceJobRunner implements ScheduledJobRunner {
    private static final Logger log = LogManager.getLogger(ScheduledJobRunner.class);

    private static SearchRelevanceJobRunner INSTANCE;

    public static SearchRelevanceJobRunner getJobRunnerInstance() {
        if (INSTANCE != null) {
            return INSTANCE;
        }
        synchronized (SearchRelevanceJobRunner.class) {
            if (INSTANCE != null) {
                return INSTANCE;
            }
            INSTANCE = new SearchRelevanceJobRunner();
            return INSTANCE;
        }
    }

    private ThreadPool threadPool;
    private Client client;
    private ExperimentDao experimentDao;
    private QuerySetDao querySetDao;
    private SearchConfigurationDao searchConfigurationDao;
    private ScheduledExperimentHistoryDao scheduledExperimentHistoryDao;
    private MetricsHelper metricsHelper;
    private HybridOptimizerExperimentProcessor hybridOptimizerExperimentProcessor;
    private PointwiseExperimentProcessor pointwiseExperimentProcessor;

    private SearchRelevanceJobRunner() {
        // Singleton class, use getJobRunner method instead of constructor
    }

    public void setThreadPool(ThreadPool threadPool) {
        this.threadPool = threadPool;
    }

    public void setClient(Client client) {
        this.client = client;
    }

    public void setExperimentDao(ExperimentDao experimentDao) {
        this.experimentDao = experimentDao;
    }

    public void setQuerySetDao(QuerySetDao querySetDao) {
        this.querySetDao = querySetDao;
    }

    public void setSearchConfigurationDao(SearchConfigurationDao searchConfigurationDao) {
        this.searchConfigurationDao = searchConfigurationDao;
    }

    public void setScheduledExperimentHistoryDao(ScheduledExperimentHistoryDao scheduledExperimentHistoryDao) {
        this.scheduledExperimentHistoryDao = scheduledExperimentHistoryDao;
    }

    public void setMetricsHelper(MetricsHelper metricsHelper) {
        this.metricsHelper = metricsHelper;
    }

    public void setHybridOptimizerExperimentProcessor(HybridOptimizerExperimentProcessor hybridOptimizerExperimentProcessor) {
        this.hybridOptimizerExperimentProcessor = hybridOptimizerExperimentProcessor;
    }

    public void setPointwiseExperimentProcessor(PointwiseExperimentProcessor pointwiseExperimentProcessor) {
        this.pointwiseExperimentProcessor = pointwiseExperimentProcessor;
    }

    @Override
    public void runJob(ScheduledJobParameter jobParameter, JobExecutionContext context) {
        if (!(jobParameter instanceof SearchRelevanceJobParameters)) {
            throw new IllegalStateException(
                "Job parameter is not instance of SearchRelevanceJobParameters, type: " + jobParameter.getClass().getCanonicalName()
            );
        }

        if (this.threadPool == null) {
            throw new IllegalStateException("ThreadPool is not initialized.");
        }

        if (this.experimentDao == null) {
            throw new IllegalStateException("Experiment dao is not initialized.");
        }

        if (this.metricsHelper == null) {
            throw new IllegalStateException("Metrics helper is not initialized.");
        }

        if (this.hybridOptimizerExperimentProcessor == null) {
            throw new IllegalStateException("Hybrid optimizer experiment processor is not initialized.");
        }

        if (this.pointwiseExperimentProcessor == null) {
            throw new IllegalStateException("Pointwise processor experiment is not initialized.");
        }

        final LockService lockService = context.getLockService();

        Runnable runnable = () -> {
            if (jobParameter.getLockDurationSeconds() != null) {
                lockService.acquireLock(jobParameter, context, ActionListener.wrap(lock -> {
                    if (lock == null) {
                        return;
                    }
                    SearchRelevanceJobParameters parameter = (SearchRelevanceJobParameters) jobParameter;
                    log.info(parameter.getExperimentId());
                    String experimentId = parameter.getExperimentId();
                    try {
                        // Retrieve experiment
                        experimentDao.getExperiment(experimentId, ActionListener.wrap(experimentResponse -> {
                            try {
                                Experiment experiment = convertToExperiment(experimentResponse);
                                String startTime = TimeUtils.getTimestamp();
                                // What I will do here is add a new request parameter to replace the Experiment object so I can store the id of the running experiment to record the end time when finished.
                                
                                // First, get QuerySet asynchronously
                                querySetDao.getQuerySet(experiment.querySetId(), ActionListener.wrap(querySetResponse -> {
                                    try {
                                        QuerySet querySet = convertToQuerySet(querySetResponse);
                                        List<String> queryTextWithReferences = querySet.querySetQueries()
                                            .stream()
                                            .map(e -> e.queryText())
                                            .collect(Collectors.toList());

                                        // Then get SearchConfigurations asynchronously
                                        fetchSearchConfigurationsAsync(experimentId, experiment, queryTextWithReferences);
                                    } catch (Exception e) {
                                        handleAsyncFailure(experimentId, experiment, "Failed to process QuerySet", e);
                                    }
                                }, e -> { handleAsyncFailure(experimentId, experiment, "Failed to fetch QuerySet", e); }));

                            } catch (Exception e) {}
                        }, e -> {}));
                    } catch (Exception e) {
                        throw new IllegalStateException("Experiment not found.");
                    }
                    lockService.release(
                        lock,
                        ActionListener.wrap(released -> { log.info("Released lock for job {}", jobParameter.getName()); }, exception -> {
                            throw new IllegalStateException("Failed to release lock.");
                        })
                    );
                }, exception -> { throw new IllegalStateException("Failed to acquire lock."); }));
            }
        };

        threadPool.generic().submit(runnable);
    }

    private void fetchSearchConfigurationsAsync(String experimentId, Experiment experiment, List<String> queryTextWithReferences) {
        Map<String, SearchConfigurationDetails> searchConfigurations = new HashMap<>();
        AtomicInteger pendingConfigs = new AtomicInteger(experiment.searchConfigurationList().size());
        AtomicBoolean hasFailure = new AtomicBoolean(false);

        for (String configId : experiment.searchConfigurationList()) {
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
                        if (queryTextWithReferences == null || searchConfigurations == null) {
                            throw new IllegalStateException("Missing required data for metrics calculation");
                        }

                        List<Map<String, Object>> finalResults = Collections.synchronizedList(new ArrayList<>());
                        AtomicInteger pendingQueries = new AtomicInteger(queryTextWithReferences.size());

                        executeExperimentEvaluation(
                            experimentId,
                            experiment,
                            searchConfigurations,
                            queryTextWithReferences,
                            finalResults,
                            pendingQueries,
                            hasFailure,
                            experiment.judgmentList()
                        );
                    }
                } catch (Exception e) {
                    if (hasFailure.compareAndSet(false, true)) {
                        handleAsyncFailure(experimentId, experiment, "Failed to process SearchConfiguration", e);
                    }
                }
            }, e -> {
                if (hasFailure.compareAndSet(false, true)) {
                    handleAsyncFailure(experimentId, experiment, "Failed to fetch SearchConfiguration: " + configId, e);
                }
            }));
        }
    }

    private void executeExperimentEvaluation(
        String experimentId,
        Experiment experiment,
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

            if (experiment.type() == ExperimentType.PAIRWISE_COMPARISON) {
                metricsHelper.processPairwiseMetrics(
                    queryText,
                    searchConfigurations,
                    experiment.size(),
                    ActionListener.wrap(
                        queryResults -> handleQueryResults(
                            queryText,
                            queryResults,
                            finalResults,
                            pendingQueries,
                            experimentId,
                            experiment,
                            hasFailure,
                            judgmentList
                        ),
                        error -> handleFailure(error, hasFailure, experimentId, experiment)
                    )
                );
            } else if (experiment.type() == ExperimentType.HYBRID_OPTIMIZER) {
                // Use our task manager implementation for hybrid optimizer
                hybridOptimizerExperimentProcessor.processHybridOptimizerExperiment(
                    experimentId,
                    queryText,
                    searchConfigurations,
                    judgmentList,
                    experiment.size(),
                    hasFailure,
                    ActionListener.wrap(
                        queryResults -> handleQueryResults(
                            queryText,
                            queryResults,
                            finalResults,
                            pendingQueries,
                            experimentId,
                            experiment,
                            hasFailure,
                            judgmentList
                        ),
                        error -> handleFailure(error, hasFailure, experimentId, experiment)
                    )
                );
            } else if (experiment.type() == ExperimentType.POINTWISE_EVALUATION) {
                pointwiseExperimentProcessor.processPointwiseExperiment(
                    experimentId,
                    queryText,
                    searchConfigurations,
                    judgmentList,
                    experiment.size(),
                    hasFailure,
                    ActionListener.wrap(
                        queryResults -> handleQueryResults(
                            queryText,
                            queryResults,
                            finalResults,
                            pendingQueries,
                            experimentId,
                            experiment,
                            hasFailure,
                            judgmentList
                        ),
                        error -> handleFailure(error, hasFailure, experimentId, experiment)
                    )
                );
            } else {
                throw new SearchRelevanceException("Unknown experimentType" + experiment.type(), RestStatus.BAD_REQUEST);
            }
        }
    }

    private void handleQueryResults(
        String queryText,
        Map<String, Object> queryResults,
        List<Map<String, Object>> finalResults,
        AtomicInteger pendingQueries,
        String experimentId,
        Experiment experiment,
        AtomicBoolean hasFailure,
        List<String> judgmentList
    ) {
        if (hasFailure.get()) return;

        try {
            synchronized (finalResults) {
                // Handle different response formats based on experiment type
                if (experiment.type() == ExperimentType.HYBRID_OPTIMIZER) {
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
                } else if (experiment.type() == ExperimentType.POINTWISE_EVALUATION) {
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
            }
        } catch (Exception e) {
            handleFailure(e, hasFailure, experimentId, experiment);
        }
    }

    private Experiment convertToExperiment(SearchResponse response) {
        if (response.getHits().getTotalHits().value() == 0) {
            throw new SearchRelevanceException("QuerySet not found", RestStatus.NOT_FOUND);
        }

        Map<String, Object> sourceMap = response.getHits().getHits()[0].getSourceAsMap();

        return new Experiment(
            "",
            "",
            ExperimentType.valueOf((String) sourceMap.get("type")),
            AsyncStatus.valueOf((String) sourceMap.get("status")),
            (String) sourceMap.get("querySetId"),
            (List<String>) sourceMap.get("searchConfigurationList"),
            (List<String>) sourceMap.get("judgmentList"),
            (int) sourceMap.get("size"),
            List.of()
        );
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

    private void handleFailure(Exception error, AtomicBoolean hasFailure, String experimentId, Experiment experiment) {
        if (hasFailure.compareAndSet(false, true)) {
            handleAsyncFailure(experimentId, experiment, "Failed to process metrics", error);
        }
    }

    private void handleAsyncFailure(String experimentId, Experiment experiment, String message, Exception error) {
        log.error(message + " for experiment: " + experimentId, error);

        Experiment errorExperiment = new Experiment(
            experimentId,
            TimeUtils.getTimestamp(),
            experiment.type(),
            AsyncStatus.ERROR,
            experiment.querySetId(),
            experiment.searchConfigurationList(),
            experiment.judgmentList(),
            experiment.size(),
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
