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
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Semaphore;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.opensearch.common.inject.Inject;
import org.opensearch.core.action.ActionListener;
import org.opensearch.core.rest.RestStatus;
import org.opensearch.searchrelevance.exception.SearchRelevanceException;
import org.opensearch.searchrelevance.judgments.JudgmentDataTransformer;
import org.opensearch.threadpool.ThreadPool;

import lombok.extern.log4j.Log4j2;

/**
 * Manages concurrent execution of LLM judgment tasks at the query text level
 */
@Log4j2
public class LlmJudgmentTaskManager {
    private static final String THREAD_POOL_EXECUTOR_NAME = ThreadPool.Names.GENERIC;
    private static final int DEFAULT_MIN_CONCURRENT_THREADS = 24;
    private static final int PROCESSOR_NUMBER_DIVISOR = 2;
    private static final int ALLOCATED_PROCESSORS = Runtime.getRuntime().availableProcessors();

    private final ThreadPool threadPool;
    private final Semaphore rateLimiter;
    private final int maxConcurrentTasks;

    @Inject
    public LlmJudgmentTaskManager(ThreadPool threadPool) {
        this.threadPool = threadPool;
        this.maxConcurrentTasks = Math.max(2, Math.min(DEFAULT_MIN_CONCURRENT_THREADS, ALLOCATED_PROCESSORS / PROCESSOR_NUMBER_DIVISOR));
        this.rateLimiter = new Semaphore(maxConcurrentTasks);
        log.info(
            "LlmJudgmentTaskManager initialized with {} max concurrent tasks (processors: {})",
            maxConcurrentTasks,
            ALLOCATED_PROCESSORS
        );
    }

    public void scheduleTasksAsync(
        List<String> queryTextWithReferences,
        Function<String, Map<String, Object>> queryProcessor,
        boolean ignoreFailure,
        ActionListener<List<Map<String, Object>>> listener
    ) {
        int totalQueries = queryTextWithReferences.size();
        log.info("Scheduling {} query text tasks for concurrent processing", totalQueries);

        try {
            List<CompletableFuture<Map<String, Object>>> futures = queryTextWithReferences.stream()
                .map(queryTextWithReference -> CompletableFuture.supplyAsync(() -> {
                    try {
                        rateLimiter.acquire();
                        try {
                            return queryProcessor.apply(queryTextWithReference);
                        } finally {
                            rateLimiter.release();
                        }
                    } catch (Exception e) {
                        log.warn("Query processing failed, returning empty result for: {}", queryTextWithReference, e);
                        return JudgmentDataTransformer.createJudgmentResult(queryTextWithReference, Map.of());
                    }
                }, threadPool.executor(THREAD_POOL_EXECUTOR_NAME)))
                .collect(Collectors.toList());

            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).whenComplete((v, ex) -> {
                List<Map<String, Object>> results = futures.stream().map(future -> {
                    try {
                        return future.join();
                    } catch (Exception e) {
                        log.warn("Individual query future failed, skipping", e);
                        return null;
                    }
                }).filter(result -> result != null).collect(Collectors.toList());

                int processedQueries = results.size();
                int successQueries = (int) results.stream().mapToLong(result -> {
                    List<Map<String, String>> ratings = (List<Map<String, String>>) result.get("ratings");
                    return ratings != null && !ratings.isEmpty() ? 1 : 0;
                }).sum();
                int failureQueries = totalQueries - processedQueries;

                if (results.isEmpty() && ex != null) {
                    log.error("Task manager failed - Total: {}, Processed: 0, Success: 0, Failure: {}", totalQueries, totalQueries, ex);
                    listener.onFailure(
                        new SearchRelevanceException("All query text judgments failed", ex, RestStatus.INTERNAL_SERVER_ERROR)
                    );
                } else {
                    log.info(
                        "Task manager completed - Total: {}, Processed: {}, Success: {}, Failure: {}",
                        totalQueries,
                        processedQueries,
                        successQueries,
                        failureQueries
                    );
                    log.info("Task manager calling listener.onResponse with {} results", results.size());
                    listener.onResponse(results);
                }
            });
        } catch (Exception e) {
            log.error("Failed to schedule tasks - Total: {}", totalQueries, e);
            if (!ignoreFailure) {
                listener.onFailure(new SearchRelevanceException("Failed to schedule judgment tasks", e, RestStatus.INTERNAL_SERVER_ERROR));
            } else {
                listener.onResponse(List.of());
            }
        }
    }
}
