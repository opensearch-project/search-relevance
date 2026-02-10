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
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.opensearch.core.action.ActionListener;
import org.opensearch.test.OpenSearchTestCase;
import org.opensearch.threadpool.TestThreadPool;
import org.opensearch.threadpool.ThreadPool;

/**
 * Tests that verify the batched submission fix in {@link LlmJudgmentTaskManager} prevents
 * thread pool starvation when processing large numbers of queries.
 *
 * Original implementation submitted ALL queries as CompletableFuture.supplyAsync() to the
 * GENERIC thread pool at once. Each query's processor blocks (via .join() or .actionGet()) waiting
 * for sub-operations (search, cache lookup, LLM calls) whose callbacks also need GENERIC pool
 * threads. When the number of queries exceeded available threads, all threads became blocked
 * waiting for callbacks that could never execute — classic thread starvation / deadlock.
 *
 * Fix uses batched submission: only {@code batchSize} queries are submitted concurrently.
 * The next batch starts only after the current one completes. This ensures that at most
 * {@code batchSize} GENERIC threads are occupied by query tasks, leaving remaining pool
 * capacity for the nested operations those tasks depend on.
 *
 * @see <a href="https://github.com/opensearch-project/search-relevance/issues/386">GitHub issue #386</a>
 */
public class LlmJudgmentTaskManagerThreadStarvationTests extends OpenSearchTestCase {

    /**
     * Calculates the number of queries needed to guarantee GENERIC pool saturation on any machine.
     *
     * The GENERIC pool max threads = max(128, 4 * availableProcessors) (from OpenSearch ThreadPool).
     * To reliably trigger thread starvation with the old unbounded submission, we need more queries
     * than the pool can hold. Using 2x the pool max ensures saturation regardless of machine size:
     *
     * - 2-core CI:     max(128, 8) = 128  → test uses 256 queries
     * - 12-core laptop: max(128, 48) = 128 → test uses 256 queries
     * - 32-core server: max(128, 128) = 128 → test uses 256 queries
     * - 96-core server: max(128, 384) = 384 → test uses 768 queries
     * - 128-core:       max(128, 512) = 512 → test uses 1024 queries
     */
    private static int calculateQueryCountForSaturation() {
        int processors = Runtime.getRuntime().availableProcessors();
        int genericPoolMax = Math.max(128, 4 * processors);
        return genericPoolMax * 2;
    }

    /**
     * Verifies that batched submission prevents thread pool starvation when processing
     * a large number of queries with nested blocking operations on the same GENERIC pool.
     *
     * Submits enough queries to exceed the GENERIC pool max (calculated dynamically based on
     * machine processors) where each query performs 3 nested blocking operations that submit
     * work to the GENERIC pool and block waiting for results. This mirrors the production
     * pattern in processQueryTextAsync():
     * - processSearchConfigurationsAsync() → CompletableFuture.allOf(...).join()
     * - deduplicateFromCache() → CompletableFuture.allOf(...).join()
     * - processWithLLM() → PlainActionFuture.actionGet()
     *
     * With batched submission, all queries should complete successfully because only
     * batchSize threads are occupied at a time, leaving pool capacity for inner operations.
     */
    public void testThreadPoolStarvationWithNestedBlockingOnSamePool() throws Exception {
        TestThreadPool threadPool = new TestThreadPool("starvation-test");

        try {
            LlmJudgmentTaskManager taskManager = new LlmJudgmentTaskManager(threadPool);

            int numQueries = calculateQueryCountForSaturation();
            List<String> queries = IntStream.range(0, numQueries).mapToObj(i -> "query_" + i).collect(Collectors.toList());

            logger.info(
                "Running starvation test with {} queries (processors: {}, estimated GENERIC pool max: {})",
                numQueries,
                Runtime.getRuntime().availableProcessors(),
                Math.max(128, 4 * Runtime.getRuntime().availableProcessors())
            );

            CountDownLatch completionLatch = new CountDownLatch(1);
            AtomicReference<List<Map<String, Object>>> resultRef = new AtomicReference<>();
            AtomicReference<Exception> errorRef = new AtomicReference<>();
            AtomicInteger starvationTimeouts = new AtomicInteger(0);

            taskManager.scheduleTasksAsync(queries, queryText -> {
                // Simulate 3 nested blocking operations on the GENERIC pool
                for (int step = 0; step < 3; step++) {
                    CompletableFuture<String> innerWork = CompletableFuture.supplyAsync(() -> {
                        try {
                            Thread.sleep(5);
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                        }
                        return "inner_result";
                    }, threadPool.executor(ThreadPool.Names.GENERIC));

                    try {
                        innerWork.get(3, TimeUnit.SECONDS);
                    } catch (TimeoutException e) {
                        starvationTimeouts.incrementAndGet();
                        return Map.of("query", (Object) queryText, "ratings", List.of());
                    } catch (Exception e) {
                        return Map.of("query", (Object) queryText, "ratings", List.of());
                    }
                }

                return Map.of("query", (Object) queryText, "ratings", List.of(Map.of("docId", "doc1", "rating", "0.8")));
            }, true, ActionListener.wrap(results -> {
                resultRef.set(results);
                completionLatch.countDown();
            }, error -> {
                errorRef.set(error);
                completionLatch.countDown();
            }));

            boolean completed = completionLatch.await(180, TimeUnit.SECONDS);

            if (!completed) {
                fail(
                    "DEADLOCK DETECTED: LlmJudgmentTaskManager.scheduleTasksAsync() did not complete "
                        + "within 180 seconds with "
                        + numQueries
                        + " queries."
                );
            }

            if (errorRef.get() != null) {
                fail("Unexpected error: " + errorRef.get().getMessage());
            }

            assertNotNull("Results should not be null", resultRef.get());
            List<Map<String, Object>> results = resultRef.get();

            long successfulQueries = results.stream().filter(r -> {
                List<?> ratings = (List<?>) r.get("ratings");
                return ratings != null && !ratings.isEmpty();
            }).count();

            long failedQueries = results.size() - successfulQueries;

            logger.info(
                "Thread starvation test results: total={}, successful={}, failed={}, starvationTimeouts={}",
                numQueries,
                successfulQueries,
                failedQueries,
                starvationTimeouts.get()
            );

            // With batched submission, all queries should succeed with zero starvation timeouts
            assertEquals(
                "All "
                    + numQueries
                    + " queries should succeed with batched submission. "
                    + "Starvation timeouts: "
                    + starvationTimeouts.get()
                    + ", failed: "
                    + failedQueries,
                0,
                starvationTimeouts.get()
            );
            assertEquals("All queries should produce results", numQueries, results.size());
            assertEquals("All queries should have non-empty ratings", (long) numQueries, successfulQueries);

        } finally {
            ThreadPool.terminate(threadPool, 60, TimeUnit.SECONDS);
        }
    }

    /**
     * Verifies that a small number of queries (well within thread pool capacity)
     * completes successfully. This serves as a basic sanity check for the batched
     * submission logic with minimal load.
     */
    public void testSmallQuerySetSucceedsWithoutStarvation() throws Exception {
        TestThreadPool threadPool = new TestThreadPool("small-set-test");

        try {
            LlmJudgmentTaskManager taskManager = new LlmJudgmentTaskManager(threadPool);

            int numQueries = 3;
            List<String> queries = IntStream.range(0, numQueries).mapToObj(i -> "query_" + i).collect(Collectors.toList());

            CountDownLatch completionLatch = new CountDownLatch(1);
            AtomicReference<List<Map<String, Object>>> resultRef = new AtomicReference<>();
            AtomicReference<Exception> errorRef = new AtomicReference<>();

            taskManager.scheduleTasksAsync(queries, queryText -> {
                CompletableFuture<String> innerWork = CompletableFuture.supplyAsync(() -> {
                    try {
                        Thread.sleep(10);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                    return "inner_result";
                }, threadPool.executor(ThreadPool.Names.GENERIC));

                try {
                    innerWork.get(5, TimeUnit.SECONDS);
                } catch (Exception e) {
                    return Map.of("query", (Object) queryText, "ratings", List.of());
                }

                return Map.of("query", (Object) queryText, "ratings", List.of(Map.of("docId", "doc1", "rating", "0.8")));
            }, false, ActionListener.wrap(results -> {
                resultRef.set(results);
                completionLatch.countDown();
            }, error -> {
                errorRef.set(error);
                completionLatch.countDown();
            }));

            boolean completed = completionLatch.await(30, TimeUnit.SECONDS);
            assertTrue("Small query set should complete without deadlock", completed);
            assertNull("Should not have errors with small query set", errorRef.get());
            assertNotNull("Should have results", resultRef.get());

            long successfulQueries = resultRef.get().stream().filter(r -> {
                List<?> ratings = (List<?>) r.get("ratings");
                return ratings != null && !ratings.isEmpty();
            }).count();

            assertEquals("All queries in small set should succeed", numQueries, successfulQueries);

        } finally {
            ThreadPool.terminate(threadPool, 30, TimeUnit.SECONDS);
        }
    }
}
