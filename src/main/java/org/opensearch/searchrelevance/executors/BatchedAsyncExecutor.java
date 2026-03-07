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
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import java.util.function.Supplier;

import org.opensearch.core.action.ActionListener;
import org.opensearch.core.rest.RestStatus;
import org.opensearch.searchrelevance.exception.SearchRelevanceException;
import org.opensearch.threadpool.ThreadPool;

import lombok.Getter;
import lombok.extern.log4j.Log4j2;

/**
 * Executes work items in sequential batches on an OpenSearch thread pool.
 *
 * <p>Each batch submits at most {@code batchSize} tasks concurrently to the specified
 * thread pool. The next batch starts only after the current batch completes. This
 * prevents thread pool starvation when work items have nested dependencies on the
 * same pool (e.g., search callbacks, cache lookups, ML predict calls).
 *
 * <p>Two execution modes are supported:
 * <ul>
 *   <li>{@link #execute} — for synchronous processors ({@code Function<T, R>}), tasks
 *       are wrapped in {@code CompletableFuture.supplyAsync()} and submitted to the pool</li>
 *   <li>{@link #executeAsync} — for natively async processors ({@code Function<T, CompletableFuture<R>>}),
 *       the processor itself returns a future and manages its own thread pool submission</li>
 * </ul>
 *
 * <p>This utility was extracted from {@code LlmJudgmentTaskManager} after a thread
 * starvation bug (#386) caused by submitting all N queries simultaneously to the
 * GENERIC pool. The batched approach ensures at most {@code batchSize} threads are
 * occupied, leaving remaining pool capacity for nested operations.
 *
 * <p>Usage examples:
 * <pre>{@code
 * // Synchronous processor (LLM judgments)
 * BatchedAsyncExecutor<String, Map<String, Object>> executor =
 *     new BatchedAsyncExecutor<>(threadPool, ThreadPool.Names.GENERIC);
 * executor.execute(queryTexts, query -> processQuery(query), listener);
 *
 * // Async processor (experiment variants with ActionListener callbacks)
 * executor.executeAsync(variants, variant -> {
 *     CompletableFuture<Result> future = new CompletableFuture<>();
 *     client.search(request, ActionListener.wrap(future::complete, future::completeExceptionally));
 *     return future;
 * }, listener);
 * }</pre>
 *
 * @param <T> Input item type
 * @param <R> Result type per item
 */
@Log4j2
public class BatchedAsyncExecutor<T, R> {

    private static final int DEFAULT_MIN_CONCURRENT_THREADS = 24;
    private static final int PROCESSOR_NUMBER_DIVISOR = 2;
    private static final int MIN_BATCH_SIZE = 2;

    private final ThreadPool threadPool;
    private final String poolName;
    @Getter
    private final int batchSize;

    /**
     * Creates a BatchedAsyncExecutor with an explicit batch size.
     *
     * @param threadPool OpenSearch thread pool
     * @param poolName   Name of the thread pool to submit tasks to
     * @param batchSize  Maximum number of concurrent tasks per batch
     */
    public BatchedAsyncExecutor(ThreadPool threadPool, String poolName, int batchSize) {
        if (threadPool == null) {
            throw new IllegalArgumentException("threadPool must not be null");
        }
        if (poolName == null || poolName.isEmpty()) {
            throw new IllegalArgumentException("poolName must not be null or empty");
        }
        if (batchSize < 1) {
            throw new IllegalArgumentException("batchSize must be at least 1");
        }
        this.threadPool = threadPool;
        this.poolName = poolName;
        this.batchSize = batchSize;
    }

    /**
     * Creates a BatchedAsyncExecutor with batch size auto-calculated from available processors.
     * Formula: {@code max(2, min(24, processors / 2))}
     *
     * @param threadPool OpenSearch thread pool
     * @param poolName   Name of the thread pool to submit tasks to
     */
    public BatchedAsyncExecutor(ThreadPool threadPool, String poolName) {
        this(
            threadPool,
            poolName,
            Math.max(
                MIN_BATCH_SIZE,
                Math.min(DEFAULT_MIN_CONCURRENT_THREADS, Runtime.getRuntime().availableProcessors() / PROCESSOR_NUMBER_DIVISOR)
            )
        );
    }

    /**
     * Executes all items using a synchronous processor function.
     *
     * <p>Each item is wrapped in {@code CompletableFuture.supplyAsync()} and submitted
     * to the configured thread pool. Items are partitioned into batches; batches run
     * sequentially, items within a batch run concurrently.
     *
     * @param items     List of input items to process
     * @param processor Synchronous function that processes a single item and returns a result
     * @param listener  Callback with the aggregated results from all batches (non-null results only)
     */
    public void execute(List<T> items, Function<T, R> processor, ActionListener<List<R>> listener) {
        execute(items, processor, () -> false, listener);
    }

    /**
     * Executes all items using a synchronous processor with cancellation support.
     *
     * @param items       List of input items to process
     * @param processor   Synchronous function that processes a single item and returns a result
     * @param isCancelled Supplier that returns true if processing should be cancelled
     * @param listener    Callback with the aggregated results from all batches
     */
    public void execute(List<T> items, Function<T, R> processor, Supplier<Boolean> isCancelled, ActionListener<List<R>> listener) {
        // Wrap synchronous processor into async by submitting to thread pool
        Function<T, CompletableFuture<R>> asyncProcessor = item -> CompletableFuture.supplyAsync(() -> {
            try {
                return processor.apply(item);
            } catch (Exception e) {
                log.warn("Item processing failed: {}", e.getMessage(), e);
                return null;
            }
        }, threadPool.executor(poolName));

        executeAsync(items, asyncProcessor, isCancelled, listener);
    }

    /**
     * Executes all items using a natively async processor function.
     *
     * <p>The processor returns a {@code CompletableFuture<R>} for each item and is
     * responsible for its own thread pool submission. This is suitable for processors
     * that use {@code ActionListener}-based APIs (e.g., {@code client.search()}).
     *
     * <p>Items are partitioned into batches; batches run sequentially, items within a
     * batch run concurrently. The next batch starts only when all futures in the current
     * batch complete.
     *
     * @param items          List of input items to process
     * @param asyncProcessor Function that processes a single item and returns a CompletableFuture
     * @param listener       Callback with the aggregated results from all batches (non-null results only)
     */
    public void executeAsync(List<T> items, Function<T, CompletableFuture<R>> asyncProcessor, ActionListener<List<R>> listener) {
        executeAsync(items, asyncProcessor, () -> false, listener);
    }

    /**
     * Executes all items using a natively async processor with cancellation support.
     *
     * @param items          List of input items to process
     * @param asyncProcessor Function that processes a single item and returns a CompletableFuture
     * @param isCancelled    Supplier that returns true if processing should be cancelled
     * @param listener       Callback with the aggregated results from all batches
     */
    public void executeAsync(
        List<T> items,
        Function<T, CompletableFuture<R>> asyncProcessor,
        Supplier<Boolean> isCancelled,
        ActionListener<List<R>> listener
    ) {
        if (items == null || items.isEmpty()) {
            log.debug("No items to process, returning empty results");
            listener.onResponse(Collections.emptyList());
            return;
        }

        int totalItems = items.size();
        log.info("Scheduling {} items for batched processing (batch size: {}, pool: {})", totalItems, batchSize, poolName);

        List<List<T>> batches = partition(items, batchSize);
        List<R> allResults = Collections.synchronizedList(new ArrayList<>());

        log.debug("Created {} batches for {} total items", batches.size(), totalItems);

        processBatchAsync(batches, 0, asyncProcessor, isCancelled, allResults, totalItems, listener);
    }

    /**
     * Recursively processes batches: runs the current batch concurrently, then proceeds to the next.
     */
    private void processBatchAsync(
        List<List<T>> batches,
        int batchIndex,
        Function<T, CompletableFuture<R>> asyncProcessor,
        Supplier<Boolean> isCancelled,
        List<R> allResults,
        int totalItems,
        ActionListener<List<R>> listener
    ) {
        // Base case: all batches processed
        if (batchIndex >= batches.size()) {
            log.info("Batched execution completed - Total: {}, Results collected: {}", totalItems, allResults.size());
            listener.onResponse(allResults);
            return;
        }

        // Check cancellation before starting next batch
        if (isCancelled.get()) {
            log.info(
                "Batched execution cancelled at batch {}/{}. Returning {} partial results.",
                batchIndex + 1,
                batches.size(),
                allResults.size()
            );
            listener.onResponse(allResults);
            return;
        }

        List<T> batch = batches.get(batchIndex);
        log.debug("Processing batch {}/{} with {} items", batchIndex + 1, batches.size(), batch.size());

        try {
            // Create futures for all items in this batch
            List<CompletableFuture<R>> futures = batch.stream().map(item -> {
                try {
                    return asyncProcessor.apply(item);
                } catch (Exception e) {
                    log.warn("Failed to create future for item: {}", e.getMessage(), e);
                    CompletableFuture<R> failed = new CompletableFuture<>();
                    failed.complete(null);
                    return failed;
                }
            }).toList();

            // When all futures in this batch complete, collect results and proceed to next batch
            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).whenComplete((v, ex) -> {
                for (CompletableFuture<R> future : futures) {
                    try {
                        R result = future.join();
                        if (result != null) {
                            allResults.add(result);
                        }
                    } catch (Exception e) {
                        log.warn("Failed to collect result from future: {}", e.getMessage());
                    }
                }

                log.debug(
                    "Batch {}/{} completed. Cumulative results: {}/{}",
                    batchIndex + 1,
                    batches.size(),
                    allResults.size(),
                    totalItems
                );

                // Process next batch
                processBatchAsync(batches, batchIndex + 1, asyncProcessor, isCancelled, allResults, totalItems, listener);
            });
        } catch (Exception e) {
            log.error("Failed to schedule batch {}/{}", batchIndex + 1, batches.size(), e);
            listener.onFailure(new SearchRelevanceException("Failed to schedule batched tasks", e, RestStatus.INTERNAL_SERVER_ERROR));
        }
    }

    /**
     * Partitions a list into sublists of at most the given size.
     */
    static <E> List<List<E>> partition(List<E> list, int size) {
        List<List<E>> partitions = new ArrayList<>();
        for (int i = 0; i < list.size(); i += size) {
            partitions.add(list.subList(i, Math.min(i + size, list.size())));
        }
        return partitions;
    }
}
