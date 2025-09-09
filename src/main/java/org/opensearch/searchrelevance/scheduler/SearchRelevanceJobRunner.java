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
import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.opensearch.common.annotation.ExperimentalApi;
import org.opensearch.common.util.concurrent.FutureUtils;
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
        AtomicBoolean hasStarted = new AtomicBoolean(false);
        String scheduledExperimentResultId = UUID.randomUUID().toString();
        SearchRelevanceJobParameters parameter = (SearchRelevanceJobParameters) jobParameter;

        Runnable runnable = () -> {
            if (jobParameter.getLockDurationSeconds() != null) {
                lockService.acquireLock(jobParameter, context, ActionListener.wrap(lock -> {
                    if (lock == null) {
                        return;
                    }
                    manager.runScheduledExperiment(parameter, hasStarted, scheduledExperimentResultId);
                    lockService.release(
                        lock,
                        ActionListener.wrap(released -> { log.info("Released lock for job {}", jobParameter.getName()); }, exception -> {
                            throw new IllegalStateException("Failed to release lock.");
                        })
                    );
                }, exception -> { throw new IllegalStateException("Failed to acquire lock."); }));
            }
        };

        CompletableFuture<Void> searchEvaluationTask = null;
        try {
            // Schedule the experiment to run with a timeout.
            searchEvaluationTask = ConcurrencyUtil.withTimeout(
                CompletableFuture.runAsync(runnable, threadPool.generic()),
                settingsAccessor.getScheduledExperimentsTimeout().getSeconds(),
                threadPool
            );
        } catch (CancellationException e) {
            log.error("Timeout for scheduled experiment has occured!");
        } finally {
            if (searchEvaluationTask != null && !searchEvaluationTask.isDone()) {
                FutureUtils.cancel(searchEvaluationTask);
            }
            manager.cleanupResources(parameter.getExperimentId(), scheduledExperimentResultId, hasStarted);
        }
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
