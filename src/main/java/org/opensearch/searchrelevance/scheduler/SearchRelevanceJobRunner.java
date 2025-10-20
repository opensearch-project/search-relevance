/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */
package org.opensearch.searchrelevance.scheduler;

import java.util.UUID;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CountDownLatch;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.opensearch.common.annotation.ExperimentalApi;
import org.opensearch.core.action.ActionListener;
import org.opensearch.jobscheduler.spi.JobExecutionContext;
import org.opensearch.jobscheduler.spi.ScheduledJobParameter;
import org.opensearch.jobscheduler.spi.ScheduledJobRunner;
import org.opensearch.jobscheduler.spi.utils.LockService;
import org.opensearch.searchrelevance.settings.SearchRelevanceSettingsAccessor;
import org.opensearch.searchrelevance.utils.ConcurrencyUtil;
import org.opensearch.threadpool.ThreadPool;
import org.opensearch.transport.client.Client;

@ExperimentalApi
public enum SearchRelevanceJobRunner implements ScheduledJobRunner {
    INSTANCE;

    private static final Logger log = LogManager.getLogger(SearchRelevanceJobRunner.class);
    private ThreadPool threadPool;
    private Client client;
    private SearchRelevanceSettingsAccessor settingsAccessor;
    private ScheduledExperimentRunnerManager manager;

    public synchronized void setThreadPool(ThreadPool threadPool) {
        this.threadPool = threadPool;
    }

    public synchronized void setClient(Client client) {
        this.client = client;
    }

    public synchronized void setSettingsAccessor(SearchRelevanceSettingsAccessor settingsAccessor) {
        this.settingsAccessor = settingsAccessor;
    }

    public synchronized void setManager(ScheduledExperimentRunnerManager manager) {
        this.manager = manager;
    }

    @Override
    public void runJob(ScheduledJobParameter jobParameter, JobExecutionContext context) {
        if (!(jobParameter instanceof SearchRelevanceJobParameters)) {
            throw new IllegalStateException(
                "Job parameter is not instance of SearchRelevanceJobParameters, type: " + jobParameter.getClass().getCanonicalName()
            );
        }

        checkComponents();

        final LockService lockService = context.getLockService();
        // Helps track whether the experiment has started or not and applied to how cleanups
        // should be handled.
        String scheduledExperimentResultId = UUID.randomUUID().toString();
        SearchRelevanceJobParameters parameter = (SearchRelevanceJobParameters) jobParameter;

        ExperimentCancellationToken cancellationToken = new ExperimentCancellationToken(scheduledExperimentResultId);
        CountDownLatch actuallyFinished = new CountDownLatch(1);

        Runnable jobRunTask = () -> {
            if (jobParameter.getLockDurationSeconds() != null) {
                lockService.acquireLock(jobParameter, context, ActionListener.wrap(lock -> {
                    if (lock == null) {
                        return;
                    }
                    manager.runScheduledExperiment(parameter, scheduledExperimentResultId, cancellationToken, actuallyFinished);
                    lockService.release(
                        lock,
                        ActionListener.wrap(released -> { log.info("Released lock for job {}", jobParameter.getName()); }, exception -> {
                            throw new IllegalStateException("Failed to release lock.");
                        })
                    );
                }, exception -> { throw new IllegalStateException("Failed to acquire lock."); }));
            }
        };

        Runnable timeoutJobWithCleanup = () -> {
            CompletableFuture<Void> searchEvaluationTask = null;
            try {
                // Schedule the experiment to run then also schedule a timeout to cancel experiment after some time.
                long timeoutAmount = settingsAccessor.getScheduledExperimentsTimeout().getSeconds();
                searchEvaluationTask = ConcurrencyUtil.withTimeout(
                    CompletableFuture.runAsync(jobRunTask, threadPool.generic()),
                    timeoutAmount,
                    cancellationToken,
                    actuallyFinished,
                    threadPool
                );

                // Wait until all asynchronous operations or timeout complete before cleanup
                searchEvaluationTask.join();
            } catch (CancellationException e) {
                log.error("Timeout for scheduled experiment has occured!");
            } catch (CompletionException e) {
                log.error("Scheduled experiment has timed out. Moving onto cleanup");
            } finally {
                if (cancellationToken.isCancelled()) {
                    log.info("Search evaluation task has concluded through cancellation.");
                } else {
                    log.info("Search evaluation task has concluded without cancellation");
                }
                manager.cleanupResources(parameter.getExperimentId(), scheduledExperimentResultId, cancellationToken);
                // This will clean up the future map in ExperimentRunningManager
                cancellationToken.cancel();
            }
        };
        threadPool.generic().execute(timeoutJobWithCleanup);
    }

    private void checkComponents() {
        if (this.threadPool == null) {
            throw new IllegalStateException("ThreadPool is not initialized.");
        }

        if (this.client == null) {
            throw new IllegalStateException("Client is not initialized.");
        }

        if (this.settingsAccessor == null) {
            throw new IllegalStateException("Settings accessor is not initialized.");
        }

        if (this.manager == null) {
            throw new IllegalStateException("Manager is not initialized.");
        }
    }
}
