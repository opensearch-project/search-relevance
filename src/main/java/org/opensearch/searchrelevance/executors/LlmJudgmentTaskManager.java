/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */
package org.opensearch.searchrelevance.executors;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
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
 * Manages concurrent execution of LLM judgment tasks at the query text level.
 *
 * Uses batched submission to prevent thread pool starvation: only one batch of queries
 * is submitted to the thread pool at a time. Each batch contains at most {@code batchSize}
 * queries. The next batch is submitted only after the current batch completes.
 *
 * This ensures that at most {@code batchSize} threads from the GENERIC pool are occupied
 * by query processing tasks, leaving the remaining pool capacity available for nested
 * operations (search, cache lookup, LLM calls) that those tasks depend on.
 *
 */
@Log4j2
public class LlmJudgmentTaskManager {
    private static final String THREAD_POOL_EXECUTOR_NAME = ThreadPool.Names.GENERIC;
    private static final int DEFAULT_MIN_CONCURRENT_THREADS = 24;
    private static final int PROCESSOR_NUMBER_DIVISOR = 2;
    private static final int ALLOCATED_PROCESSORS = Runtime.getRuntime().availableProcessors();

    private final ThreadPool threadPool;
    private final int batchSize;

    @Inject
    public LlmJudgmentTaskManager(ThreadPool threadPool) {
        this.threadPool = threadPool;
        this.batchSize = Math.max(2, Math.min(DEFAULT_MIN_CONCURRENT_THREADS, ALLOCATED_PROCESSORS / PROCESSOR_NUMBER_DIVISOR));
        log.info("LlmJudgmentTaskManager initialized with {} max concurrent tasks (processors: {})", batchSize, ALLOCATED_PROCESSORS);
    }

    /**
     * Schedules query processing tasks in batches to prevent thread pool starvation.
     *
     * Instead of submitting all queries at once (which would exhaust the GENERIC thread pool
     * when queries exceed pool capacity), this method processes queries in sequential batches.
     * Each batch submits at most {@code batchSize} tasks to the thread pool concurrently.
     * The next batch begins only after the current batch completes.
     *
     * This approach ensures that:
     * 1. At most {@code batchSize} GENERIC threads are occupied by query tasks
     * 2. Remaining GENERIC threads are available for nested operations (search, cache, LLM)
     * 3. No thread starvation or deadlock regardless of total query count
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
        log.info("Scheduling {} query text tasks for batched processing (batch size: {})", totalQueries, batchSize);

        // Partition queries into batches
        List<List<String>> batches = partition(queryTextsWithCustomInput, batchSize);
        List<Map<String, Object>> allResults = Collections.synchronizedList(new ArrayList<>());

        log.info("Created {} batches for {} total queries", batches.size(), totalQueries);

        // Process batches sequentially — each batch runs its queries concurrently
        processBatch(batches, 0, queryProcessor, ignoreFailure, allResults, totalQueries, listener);
    }

    /**
     * Recursively processes batches: runs the current batch concurrently, then proceeds to the next.
     */
    private void processBatch(
        List<List<String>> batches,
        int batchIndex,
        Function<String, Map<String, Object>> queryProcessor,
        boolean ignoreFailure,
        List<Map<String, Object>> allResults,
        int totalQueries,
        ActionListener<List<Map<String, Object>>> listener
    ) {
        // Base case: all batches processed
        if (batchIndex >= batches.size()) {
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
            return;
        }

        List<String> batch = batches.get(batchIndex);
        log.debug("Processing batch {}/{} with {} queries", batchIndex + 1, batches.size(), batch.size());

        try {
            // Submit all queries in this batch concurrently
            List<CompletableFuture<Map<String, Object>>> futures = batch.stream()
                .map(queryTextWithCustomInput -> CompletableFuture.supplyAsync(() -> {
                    try {
                        return queryProcessor.apply(queryTextWithCustomInput);
                    } catch (Exception e) {
                        log.warn("Query processing failed, returning empty result for: {}", queryTextWithCustomInput, e);
                        return JudgmentDataTransformer.createJudgmentResult(queryTextWithCustomInput, Map.of());
                    }
                }, threadPool.executor(THREAD_POOL_EXECUTOR_NAME)))
                .collect(Collectors.toList());

            // When all futures in this batch complete, collect results and proceed to next batch
            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).whenComplete((v, ex) -> {
                // Collect results from this batch
                for (CompletableFuture<Map<String, Object>> future : futures) {
                    try {
                        Map<String, Object> result = future.join();
                        if (result != null) {
                            allResults.add(result);
                        }
                    } catch (Exception e) {
                        log.warn("Individual query future failed, skipping", e);
                    }
                }

                log.debug(
                    "Batch {}/{} completed. Cumulative results: {}/{}",
                    batchIndex + 1,
                    batches.size(),
                    allResults.size(),
                    totalQueries
                );

                // Process next batch
                processBatch(batches, batchIndex + 1, queryProcessor, ignoreFailure, allResults, totalQueries, listener);
            });
        } catch (Exception e) {
            log.error("Failed to schedule batch {}/{}", batchIndex + 1, batches.size(), e);
            if (!ignoreFailure) {
                listener.onFailure(new SearchRelevanceException("Failed to schedule judgment tasks", e, RestStatus.INTERNAL_SERVER_ERROR));
            } else {
                // Skip failed batch, continue with next
                processBatch(batches, batchIndex + 1, queryProcessor, ignoreFailure, allResults, totalQueries, listener);
            }
        }
    }

    /**
     * Partitions a list into sublists of at most the given size.
     */
    private static <T> List<List<T>> partition(List<T> list, int size) {
        List<List<T>> partitions = new ArrayList<>();
        for (int i = 0; i < list.size(); i += size) {
            partitions.add(list.subList(i, Math.min(i + size, list.size())));
        }
        return partitions;
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
