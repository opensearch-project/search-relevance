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
import java.util.function.Function;

import org.opensearch.common.inject.Inject;
import org.opensearch.core.action.ActionListener;
import org.opensearch.threadpool.ThreadPool;

import lombok.extern.log4j.Log4j2;

/**
 * Manages concurrent execution of LLM judgment tasks at the query text level.
 *
 * <p>Delegates to {@link BatchedAsyncExecutor} for batched submission to prevent
 * thread pool starvation. Only one batch of queries is submitted to the thread pool
 * at a time. Each batch contains at most {@code batchSize} queries. The next batch
 * is submitted only after the current batch completes.
 *
 * <p>This ensures that at most {@code batchSize} threads from the GENERIC pool are
 * occupied by query processing tasks, leaving the remaining pool capacity available
 * for nested operations (search, cache lookup, LLM calls) that those tasks depend on.
 */
@Log4j2
public class LlmJudgmentTaskManager {
    private static final String THREAD_POOL_EXECUTOR_NAME = ThreadPool.Names.GENERIC;

    private final BatchedAsyncExecutor<String, Map<String, Object>> batchedExecutor;

    @Inject
    public LlmJudgmentTaskManager(ThreadPool threadPool) {
        this.batchedExecutor = new BatchedAsyncExecutor<>(threadPool, THREAD_POOL_EXECUTOR_NAME);
        log.info(
            "LlmJudgmentTaskManager initialized with {} max concurrent tasks (processors: {})",
            batchedExecutor.getBatchSize(),
            Runtime.getRuntime().availableProcessors()
        );
    }

    /**
     * Schedules query processing tasks in batches to prevent thread pool starvation.
     *
     * <p>Instead of submitting all queries at once (which would exhaust the GENERIC thread pool
     * when queries exceed pool capacity), this method delegates to {@link BatchedAsyncExecutor}
     * which processes queries in sequential batches. Each batch submits at most {@code batchSize}
     * tasks to the thread pool concurrently. The next batch begins only after the current batch
     * completes.
     *
     * @param queryTextsWithCustomInput List of query texts to process
     * @param queryProcessor Function that processes a single query and returns results
     * @param ignoreFailure If true, individual query failures don't fail the entire operation
     * @param listener Callback with the aggregated results from all batches
     */
    public void scheduleTasksAsync(
        List<String> queryTextsWithCustomInput,
        Function<String, Map<String, Object>> queryProcessor,
        boolean ignoreFailure,
        ActionListener<List<Map<String, Object>>> listener
    ) {
        int totalQueries = queryTextsWithCustomInput.size();
        log.info("Scheduling {} query text tasks for batched processing (batch size: {})", totalQueries, batchedExecutor.getBatchSize());

        batchedExecutor.execute(queryTextsWithCustomInput, queryProcessor, new ActionListener<>() {
            @Override
            public void onResponse(List<Map<String, Object>> allResults) {
                int processedQueries = allResults.size();
                int successQueries = countSuccessful(allResults);
                int failureQueries = totalQueries - processedQueries;

                log.info(
                    "Task manager completed - Total: {}, Processed: {}, Success: {}, Failure: {}",
                    totalQueries,
                    processedQueries,
                    successQueries,
                    failureQueries
                );
                log.info("Task manager calling listener.onResponse with {} results", allResults.size());
                listener.onResponse(allResults);
            }

            @Override
            public void onFailure(Exception e) {
                listener.onFailure(e);
            }
        });
    }

    /**
     * Counts queries with non-empty ratings (successful LLM judgments).
     */
    @SuppressWarnings("unchecked")
    private static int countSuccessful(List<Map<String, Object>> results) {
        return (int) results.stream().mapToLong(result -> {
            List<Map<String, String>> ratings = (List<Map<String, String>>) result.get("ratings");
            return ratings != null && !ratings.isEmpty() ? 1 : 0;
        }).sum();
    }
}
