/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */
package org.opensearch.searchrelevance.executors;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import org.opensearch.core.action.ActionListener;
import org.opensearch.searchrelevance.model.JudgmentBatchStatus;
import org.opensearch.searchrelevance.model.SearchConfiguration;

import lombok.Getter;
import lombok.extern.log4j.Log4j2;

/**
 * Context for tracking LLM judgment generation tasks for a specific query
 */
@Log4j2
@Getter
public class JudgmentTaskContext {
    private final String queryTextWithReference;
    private final String modelId;
    private final List<String> contextFields;
    private final List<SearchConfiguration> searchConfigurations;
    private final boolean ignoreFailure;

    private final ConcurrentMap<String, String> docIdToScore;
    private final AtomicInteger pendingSearchTasks;
    private final AtomicInteger pendingCacheTasks;
    private final AtomicInteger successfulTasks;
    private final AtomicInteger failedTasks;
    private final AtomicBoolean hasTerminated;
    private final List<Future<?>> pendingTasks;
    private volatile boolean cancelled;

    private ActionListener<Map<String, String>> completionListener;

    public JudgmentTaskContext(
        String queryTextWithReference,
        String modelId,
        List<String> contextFields,
        List<SearchConfiguration> searchConfigurations,
        boolean ignoreFailure,
        ActionListener<Map<String, String>> completionListener
    ) {
        this.queryTextWithReference = queryTextWithReference;
        this.modelId = modelId;
        this.contextFields = contextFields;
        this.searchConfigurations = searchConfigurations;
        this.ignoreFailure = ignoreFailure;
        this.completionListener = completionListener;

        this.docIdToScore = new ConcurrentHashMap<>();
        this.pendingSearchTasks = new AtomicInteger(searchConfigurations.size());
        this.pendingCacheTasks = new AtomicInteger(0);
        this.successfulTasks = new AtomicInteger(0);
        this.failedTasks = new AtomicInteger(0);
        this.hasTerminated = new AtomicBoolean(false);
        this.pendingTasks = new CopyOnWriteArrayList<>();
        this.cancelled = false;

        log.info(
            "JudgmentTaskContext initialized for query: {} with {} search configurations",
            queryTextWithReference,
            searchConfigurations.size()
        );
    }

    public void setPendingCacheTasks(int count) {
        this.pendingCacheTasks.set(count);
    }

    public void completeSearchTask(boolean success) {
        if (hasTerminated.get()) return;

        if (success) {
            successfulTasks.incrementAndGet();
        } else {
            failedTasks.incrementAndGet();
            log.warn("Search task failed for query: {} (ignoreFailure={})", queryTextWithReference, ignoreFailure);
        }

        if (pendingSearchTasks.decrementAndGet() == 0) {
            log.debug("All search tasks completed for query: {}", queryTextWithReference);
        }
    }

    public void completeCacheTask(boolean success) {
        if (hasTerminated.get()) return;

        if (success) {
            successfulTasks.incrementAndGet();
        } else {
            failedTasks.incrementAndGet();
            log.warn("Cache task failed for query: {} (ignoreFailure={})", queryTextWithReference, ignoreFailure);
        }

        if (pendingCacheTasks.decrementAndGet() == 0) {
            log.debug("All cache tasks completed for query: {}", queryTextWithReference);
        }
    }

    public boolean isAllTasksCompleted() {
        return hasTerminated.get() || (pendingSearchTasks.get() == 0 && pendingCacheTasks.get() == 0);
    }

    public void completeJudgment() {
        if (hasTerminated.get()) return;

        JudgmentBatchStatus status = determineStatus();

        log.info(
            "Judgment completed for query: {} with {} ratings (success: {}, failed: {}, status: {})",
            queryTextWithReference,
            docIdToScore.size(),
            successfulTasks.get(),
            failedTasks.get(),
            status
        );

        if (completionListener != null) {
            completionListener.onResponse(docIdToScore);
        }
    }

    private JudgmentBatchStatus determineStatus() {
        if (hasTerminated.get() && !ignoreFailure) {
            return JudgmentBatchStatus.TERMINATED;
        }

        int completedTasks = successfulTasks.get() + failedTasks.get();

        if (completedTasks == 0) {
            return JudgmentBatchStatus.SUCCESS;
        }

        if (failedTasks.get() == completedTasks) {
            return JudgmentBatchStatus.ALL_FAILED;
        } else if (failedTasks.get() > 0) {
            return JudgmentBatchStatus.PARTIAL_SUCCESS;
        } else {
            return JudgmentBatchStatus.SUCCESS;
        }
    }

    public JudgmentBatchStatus getStatus() {
        return determineStatus();
    }

    public void failJudgment(Exception e) {
        if (hasTerminated.getAndSet(true)) return;

        log.error("Judgment failed for query: {} (ignoreFailure={})", queryTextWithReference, ignoreFailure, e);
        if (completionListener != null) {
            completionListener.onFailure(e);
        }
    }

}
