/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */
package org.opensearch.searchrelevance.scheduler;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.opensearch.core.action.ActionListener;
import org.opensearch.jobscheduler.spi.JobExecutionContext;
import org.opensearch.jobscheduler.spi.ScheduledJobParameter;
import org.opensearch.jobscheduler.spi.ScheduledJobRunner;
import org.opensearch.jobscheduler.spi.utils.LockService;
import org.opensearch.searchrelevance.settings.SearchRelevanceSettingsAccessor;
import org.opensearch.threadpool.ThreadPool;
import org.opensearch.transport.client.Client;

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

        Runnable runnable = () -> {
            if (jobParameter.getLockDurationSeconds() != null) {
                lockService.acquireLock(jobParameter, context, ActionListener.wrap(lock -> {
                    if (lock == null) {
                        return;
                    }
                    SearchRelevanceJobParameters parameter = (SearchRelevanceJobParameters) jobParameter;
                    manager.runScheduledExperiment(parameter);
                    lockService.release(
                        lock,
                        ActionListener.wrap(released -> { log.info("Released lock for job {}", jobParameter.getName()); }, exception -> {
                            throw new IllegalStateException("Failed to release lock.");
                        })
                    );
                }, exception -> { throw new IllegalStateException("Failed to acquire lock."); }));
            }
        };

        Future<?> searchEvaluationTask = threadPool.generic().submit(runnable);

        try {
            // Attempt to get the result with a timeout seconds
            searchEvaluationTask.get(settingsAccessor.getScheduledExperimentsTimeout().getSeconds(), TimeUnit.SECONDS);
        } catch (TimeoutException e) {
            log.error("timeout for scheduled experiment has occured!");
            searchEvaluationTask.cancel(true); // Attempt to interrupt the running task
        } catch (InterruptedException | ExecutionException e) {
            log.error(" for scheduled experiment has occured!");
        } finally {}
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
