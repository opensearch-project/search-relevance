/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */
package org.opensearch.searchrelevance.transport.experiment;

import java.util.ArrayList;
import java.util.UUID;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CountDownLatch;

import org.opensearch.action.index.IndexResponse;
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
import org.opensearch.searchrelevance.executors.ExperimentRunningManager;
import org.opensearch.searchrelevance.executors.ExperimentTaskManager;
import org.opensearch.searchrelevance.experiment.HybridOptimizerExperimentProcessor;
import org.opensearch.searchrelevance.experiment.PointwiseExperimentProcessor;
import org.opensearch.searchrelevance.metrics.MetricsHelper;
import org.opensearch.searchrelevance.model.AsyncStatus;
import org.opensearch.searchrelevance.model.Experiment;
import org.opensearch.searchrelevance.scheduler.AbstractCancellationToken;
import org.opensearch.searchrelevance.scheduler.ExperimentCancellationToken;
import org.opensearch.searchrelevance.settings.SearchRelevanceSettingsAccessor;
import org.opensearch.searchrelevance.utils.ConcurrencyUtil;
import org.opensearch.searchrelevance.utils.TimeUtils;
import org.opensearch.tasks.Task;
import org.opensearch.threadpool.ThreadPool;
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
    private final ExperimentRunningManager experimentRunningManager;
    private final ThreadPool threadPool;
    private final SearchRelevanceSettingsAccessor settingsAccessor;

    @Inject
    public PutExperimentTransportAction(
        TransportService transportService,
        ActionFilters actionFilters,
        ExperimentDao experimentDao,
        QuerySetDao querySetDao,
        SearchConfigurationDao searchConfigurationDao,
        MetricsHelper metricsHelper,
        JudgmentDao judgmentDao,
        ExperimentTaskManager experimentTaskManager,
        ExperimentRunningManager experimentRunningManager,
        ThreadPool threadPool,
        SearchRelevanceSettingsAccessor settingsAccessor
    ) {
        super(PutExperimentAction.NAME, transportService, actionFilters, PutExperimentRequest::new);
        this.experimentDao = experimentDao;
        this.querySetDao = querySetDao;
        this.searchConfigurationDao = searchConfigurationDao;
        this.metricsHelper = metricsHelper;
        this.hybridOptimizerExperimentProcessor = new HybridOptimizerExperimentProcessor(judgmentDao, experimentTaskManager);
        this.pointwiseExperimentProcessor = new PointwiseExperimentProcessor(judgmentDao, experimentTaskManager);
        this.experimentRunningManager = experimentRunningManager;
        this.threadPool = threadPool;
        this.settingsAccessor = settingsAccessor;
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

                ExperimentCancellationToken cancellationToken = new ExperimentCancellationToken(id);
                CountDownLatch actuallyFinished = new CountDownLatch(1);
                Runnable experimentRunTask = () -> {
                    // Start experiment with async processing
                    experimentRunningManager.startExperimentRun(id, request, cancellationToken, actuallyFinished);
                    log.info("Experiment {} is finished.", id);
                };

                Runnable timeoutJobWithCleanup = () -> {
                    CompletableFuture<Void> searchEvaluationTask = null;
                    try {
                        // Schedule the experiment to run then also schedule a timeout to cancel experiment after some time.
                        long timeoutAmount = settingsAccessor.getExperimentsTimeout().getSeconds();
                        CompletableFuture<Void> originalExperimentStart;
                        try {
                            originalExperimentStart = CompletableFuture.runAsync(experimentRunTask, threadPool.generic());
                            searchEvaluationTask = ConcurrencyUtil.withTimeout(
                                originalExperimentStart,
                                timeoutAmount,
                                cancellationToken,
                                actuallyFinished,
                                threadPool
                            );
                        } catch (Exception e) {
                            actuallyFinished.countDown();
                            log.error("Experiment never started " + e.getMessage());
                        }

                        // Wait until all asynchronous operations or timeout complete before cleanup
                        searchEvaluationTask.join();
                    } catch (CancellationException e) {
                        log.error("Timeout for experiment has occured!");
                    } catch (CompletionException e) {
                        log.error("Experiment has timed out. Moving onto cleanup");
                    } finally {
                        // All threads except this current running one should be released if we got to this point.
                        // This is if join somehow failed, but the thread should be waiting at the join call and only
                        // be released when the actuallyFinished latch is counted down.
                        while (actuallyFinished.getCount() > 0) {
                            actuallyFinished.countDown();
                        }
                        if (cancellationToken.isCancelled()) {
                            log.info("Search evaluation task has concluded through cancellation.");
                        } else {
                            log.info("Search evaluation task has concluded without cancellation");
                        }
                        cleanupResources(id, request, cancellationToken);
                        // This will clean up the future map in ExperimentRunningManager
                        cancellationToken.cancel();
                    }
                };

                // The logic of the experiment run should not block this calling thread, so it will be scheduled
                // into a threadpool.
                threadPool.generic().execute(timeoutJobWithCleanup);
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

    /**
     *
     * @param experimentId Id of experiment that is scheduled to run
     * @param cancellationToken The token to indicate whether this scheduled experiment run has been cancelled
     */
    public void cleanupResources(String experimentId, PutExperimentRequest request, AbstractCancellationToken cancellationToken) {
        log.info("Cleaning up all resources for {}", experimentId);
        Experiment finalExperiment = new Experiment(
            experimentId,
            TimeUtils.getTimestamp(),
            request.getType(),
            AsyncStatus.TIMEOUT,
            request.getQuerySetId(),
            request.getSearchConfigurationList(),
            request.getJudgmentList(),
            request.getSize(),
            null
        );

        if (cancellationToken.isCancelled()) {
            experimentDao.updateExperiment(
                finalExperiment,
                ActionListener.wrap(
                    response -> log.info("Updated experiment {} status to TIMEOUT", experimentId),
                    e -> log.error("Failed to update error status for experiment: {}", experimentId, e)
                )
            );
        }
    }
}
