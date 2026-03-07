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
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;

import org.junit.After;
import org.junit.Before;
import org.opensearch.core.action.ActionListener;
import org.opensearch.test.OpenSearchTestCase;
import org.opensearch.threadpool.TestThreadPool;
import org.opensearch.threadpool.ThreadPool;

import lombok.SneakyThrows;
import lombok.extern.log4j.Log4j2;

/**
 * Unit tests for {@link BatchedAsyncExecutor}.
 */
@Log4j2
public class BatchedAsyncExecutorTests extends OpenSearchTestCase {

    private static final int TEST_TIMEOUT_SECONDS = 60;

    private TestThreadPool threadPool;

    @Before
    public void setUp() throws Exception {
        super.setUp();
        threadPool = new TestThreadPool(getTestName());
        log.info("[{}] before test", getTestName());
    }

    @After
    public void tearDown() throws Exception {
        log.info("[{}] after test", getTestName());
        ThreadPool.terminate(threadPool, 30, TimeUnit.SECONDS);
        super.tearDown();
    }

    @SneakyThrows
    public void testExecuteAllItemsSuccessfully() {
        BatchedAsyncExecutor<Integer, String> executor = new BatchedAsyncExecutor<>(threadPool, ThreadPool.Names.GENERIC, 3);

        List<Integer> items = List.of(1, 2, 3, 4, 5);
        CountDownLatch latch = new CountDownLatch(1);
        List<String> results = Collections.synchronizedList(new ArrayList<>());

        executor.execute(items, i -> "result-" + i, ActionListener.wrap(r -> {
            results.addAll(r);
            latch.countDown();
        }, e -> {
            fail("Should not fail: " + e.getMessage());
            latch.countDown();
        }));

        assertTrue("Timed out waiting for results", latch.await(TEST_TIMEOUT_SECONDS, TimeUnit.SECONDS));
        assertEquals("All 5 items should produce results", 5, results.size());
        for (int i = 1; i <= 5; i++) {
            assertTrue("Should contain result-" + i, results.contains("result-" + i));
        }
    }

    @SneakyThrows
    public void testExecuteWithSingleBatch() {
        BatchedAsyncExecutor<Integer, Integer> executor = new BatchedAsyncExecutor<>(threadPool, ThreadPool.Names.GENERIC, 10);

        List<Integer> items = List.of(1, 2, 3); // fewer than batch size
        CountDownLatch latch = new CountDownLatch(1);
        List<Integer> results = Collections.synchronizedList(new ArrayList<>());

        executor.execute(items, i -> i * 2, ActionListener.wrap(r -> {
            results.addAll(r);
            latch.countDown();
        }, e -> {
            fail("Should not fail: " + e.getMessage());
            latch.countDown();
        }));

        assertTrue(latch.await(TEST_TIMEOUT_SECONDS, TimeUnit.SECONDS));
        assertEquals(3, results.size());
        assertTrue(results.containsAll(List.of(2, 4, 6)));
    }

    @SneakyThrows
    public void testExecuteWithSingleItem() {
        BatchedAsyncExecutor<String, String> executor = new BatchedAsyncExecutor<>(threadPool, ThreadPool.Names.GENERIC, 5);

        CountDownLatch latch = new CountDownLatch(1);
        List<String> results = Collections.synchronizedList(new ArrayList<>());

        executor.execute(List.of("only"), s -> s.toUpperCase(Locale.ROOT), ActionListener.wrap(r -> {
            results.addAll(r);
            latch.countDown();
        }, e -> {
            fail("Should not fail: " + e.getMessage());
            latch.countDown();
        }));

        assertTrue(latch.await(TEST_TIMEOUT_SECONDS, TimeUnit.SECONDS));
        assertEquals(1, results.size());
        assertEquals("ONLY", results.get(0));
    }

    @SneakyThrows
    public void testExecuteWithEmptyList() {
        BatchedAsyncExecutor<String, String> executor = new BatchedAsyncExecutor<>(threadPool, ThreadPool.Names.GENERIC, 5);

        CountDownLatch latch = new CountDownLatch(1);
        AtomicBoolean called = new AtomicBoolean(false);

        executor.execute(Collections.emptyList(), s -> s, ActionListener.wrap(r -> {
            called.set(true);
            assertTrue("Results should be empty", r.isEmpty());
            latch.countDown();
        }, e -> {
            fail("Should not fail: " + e.getMessage());
            latch.countDown();
        }));

        assertTrue(latch.await(TEST_TIMEOUT_SECONDS, TimeUnit.SECONDS));
        assertTrue("Listener should have been called", called.get());
    }

    @SneakyThrows
    public void testExecuteWithNullList() {
        BatchedAsyncExecutor<String, String> executor = new BatchedAsyncExecutor<>(threadPool, ThreadPool.Names.GENERIC, 5);

        CountDownLatch latch = new CountDownLatch(1);

        executor.execute(null, s -> s, ActionListener.wrap(r -> {
            assertTrue("Results should be empty for null input", r.isEmpty());
            latch.countDown();
        }, e -> {
            fail("Should not fail: " + e.getMessage());
            latch.countDown();
        }));

        assertTrue(latch.await(TEST_TIMEOUT_SECONDS, TimeUnit.SECONDS));
    }

    @SneakyThrows
    public void testErrorIsolationFailingItemsDoNotBlockOthers() {
        BatchedAsyncExecutor<Integer, String> executor = new BatchedAsyncExecutor<>(threadPool, ThreadPool.Names.GENERIC, 3);

        // Items 2 and 5 will throw exceptions
        List<Integer> items = List.of(1, 2, 3, 4, 5);
        CountDownLatch latch = new CountDownLatch(1);
        List<String> results = Collections.synchronizedList(new ArrayList<>());

        executor.execute(items, i -> {
            if (i == 2 || i == 5) {
                throw new RuntimeException("Simulated failure for item " + i);
            }
            return "ok-" + i;
        }, ActionListener.wrap(r -> {
            results.addAll(r);
            latch.countDown();
        }, e -> {
            fail("Executor should not fail when individual items fail: " + e.getMessage());
            latch.countDown();
        }));

        assertTrue(latch.await(TEST_TIMEOUT_SECONDS, TimeUnit.SECONDS));
        // Items 1, 3, 4 succeed; items 2, 5 return null (filtered out)
        assertEquals("3 successful items should produce results", 3, results.size());
        assertTrue(results.containsAll(List.of("ok-1", "ok-3", "ok-4")));
    }

    @SneakyThrows
    public void testAllItemsFailReturnsEmptyResults() {
        BatchedAsyncExecutor<Integer, String> executor = new BatchedAsyncExecutor<>(threadPool, ThreadPool.Names.GENERIC, 2);

        List<Integer> items = List.of(1, 2, 3);
        CountDownLatch latch = new CountDownLatch(1);

        executor.execute(items, i -> { throw new RuntimeException("All items fail"); }, ActionListener.wrap(r -> {
            assertTrue("All items failed, results should be empty", r.isEmpty());
            latch.countDown();
        }, e -> {
            fail("Executor should not fail: " + e.getMessage());
            latch.countDown();
        }));

        assertTrue(latch.await(TEST_TIMEOUT_SECONDS, TimeUnit.SECONDS));
    }

    @SneakyThrows
    public void testCancellationStopsProcessing() {
        BatchedAsyncExecutor<Integer, Integer> executor = new BatchedAsyncExecutor<>(threadPool, ThreadPool.Names.GENERIC, 2);

        // 10 items, batch size 2 → 5 batches
        List<Integer> items = IntStream.rangeClosed(1, 10).boxed().toList();
        AtomicBoolean cancelled = new AtomicBoolean(false);
        AtomicInteger processedCount = new AtomicInteger(0);
        CountDownLatch latch = new CountDownLatch(1);

        executor.execute(items, i -> {
            int count = processedCount.incrementAndGet();
            // Cancel after processing 4 items (2 batches)
            if (count >= 4) {
                cancelled.set(true);
            }
            return i;
        }, cancelled::get, ActionListener.wrap(r -> {
            log.info("Cancellation test: received {} results, processed {} items", r.size(), processedCount.get());
            // Should have fewer results than total items
            assertTrue("Should have partial results due to cancellation", r.size() < items.size());
            latch.countDown();
        }, e -> {
            fail("Should not fail: " + e.getMessage());
            latch.countDown();
        }));

        assertTrue(latch.await(TEST_TIMEOUT_SECONDS, TimeUnit.SECONDS));
    }

    @SneakyThrows
    public void testImmediateCancellationReturnsEmptyResults() {
        BatchedAsyncExecutor<Integer, Integer> executor = new BatchedAsyncExecutor<>(threadPool, ThreadPool.Names.GENERIC, 2);

        List<Integer> items = List.of(1, 2, 3, 4, 5);
        CountDownLatch latch = new CountDownLatch(1);

        // Cancelled from the start
        executor.execute(items, i -> i, () -> true, ActionListener.wrap(r -> {
            assertTrue("No items should be processed when cancelled immediately", r.isEmpty());
            latch.countDown();
        }, e -> {
            fail("Should not fail: " + e.getMessage());
            latch.countDown();
        }));

        assertTrue(latch.await(TEST_TIMEOUT_SECONDS, TimeUnit.SECONDS));
    }

    @SneakyThrows
    public void testThreadStarvationPrevention() {
        // This is the core test proving the BatchedAsyncExecutor prevents starvation.
        // We simulate nested blocking operations on the GENERIC pool — the same pattern
        // that caused #386 in LlmJudgmentTaskManager.

        int processors = Runtime.getRuntime().availableProcessors();
        int genericPoolMax = Math.max(128, 4 * processors);
        // Submit more items than the pool can handle — would cause starvation without batching
        int numItems = genericPoolMax * 2;
        int batchSize = Math.max(2, Math.min(24, processors / 2));

        log.info("Thread starvation test: {} items, batch size {}, estimated GENERIC pool max {}", numItems, batchSize, genericPoolMax);

        BatchedAsyncExecutor<Integer, String> executor = new BatchedAsyncExecutor<>(threadPool, ThreadPool.Names.GENERIC, batchSize);

        List<Integer> items = IntStream.rangeClosed(1, numItems).boxed().toList();
        CountDownLatch latch = new CountDownLatch(1);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger timeoutCount = new AtomicInteger(0);

        executor.execute(items, item -> {
            // Simulate nested blocking operation on the GENERIC pool
            // (mimics search → cache → LLM pattern)
            try {
                CompletableFuture<String> nestedOp = CompletableFuture.supplyAsync(() -> {
                    try {
                        Thread.sleep(10); // simulate I/O latency
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                    return "nested-result";
                }, threadPool.executor(ThreadPool.Names.GENERIC));

                String nestedResult = nestedOp.get(5, TimeUnit.SECONDS);
                successCount.incrementAndGet();
                return "item-" + item + "-" + nestedResult;
            } catch (Exception e) {
                timeoutCount.incrementAndGet();
                return null;
            }
        }, ActionListener.wrap(results -> {
            log.info(
                "Starvation test results: total={}, results={}, successes={}, timeouts={}",
                numItems,
                results.size(),
                successCount.get(),
                timeoutCount.get()
            );
            latch.countDown();
        }, e -> {
            fail("Executor should not fail: " + e.getMessage());
            latch.countDown();
        }));

        assertTrue("Timed out waiting for batched execution", latch.await(180, TimeUnit.SECONDS));
        assertEquals("All items should succeed with batched execution (no starvation timeouts)", 0, timeoutCount.get());
        assertEquals("All items should complete successfully", numItems, successCount.get());
    }

    public void testConstructorValidationNullThreadPool() {
        expectThrows(IllegalArgumentException.class, () -> new BatchedAsyncExecutor<>(null, ThreadPool.Names.GENERIC, 5));
    }

    public void testConstructorValidationNullPoolName() {
        expectThrows(IllegalArgumentException.class, () -> new BatchedAsyncExecutor<>(threadPool, null, 5));
    }

    public void testConstructorValidationEmptyPoolName() {
        expectThrows(IllegalArgumentException.class, () -> new BatchedAsyncExecutor<>(threadPool, "", 5));
    }

    public void testConstructorValidationZeroBatchSize() {
        expectThrows(IllegalArgumentException.class, () -> new BatchedAsyncExecutor<>(threadPool, ThreadPool.Names.GENERIC, 0));
    }

    public void testConstructorValidationNegativeBatchSize() {
        expectThrows(IllegalArgumentException.class, () -> new BatchedAsyncExecutor<>(threadPool, ThreadPool.Names.GENERIC, -1));
    }

    public void testAutoCalculatedBatchSize() {
        BatchedAsyncExecutor<String, String> executor = new BatchedAsyncExecutor<>(threadPool, ThreadPool.Names.GENERIC);
        int expected = Math.max(2, Math.min(24, Runtime.getRuntime().availableProcessors() / 2));
        assertEquals("Auto-calculated batch size should match formula", expected, executor.getBatchSize());
    }

    @SneakyThrows
    public void testExecuteAsyncAllItemsSuccessfully() {
        BatchedAsyncExecutor<Integer, String> executor = new BatchedAsyncExecutor<>(threadPool, ThreadPool.Names.GENERIC, 3);

        List<Integer> items = List.of(1, 2, 3, 4, 5);
        CountDownLatch latch = new CountDownLatch(1);
        List<String> results = Collections.synchronizedList(new ArrayList<>());

        executor.executeAsync(
            items,
            i -> CompletableFuture.supplyAsync(() -> "async-" + i, threadPool.executor(ThreadPool.Names.GENERIC)),
            ActionListener.wrap(r -> {
                results.addAll(r);
                latch.countDown();
            }, e -> {
                fail("Should not fail: " + e.getMessage());
                latch.countDown();
            })
        );

        assertTrue("Timed out waiting for results", latch.await(TEST_TIMEOUT_SECONDS, TimeUnit.SECONDS));
        assertEquals("All 5 items should produce results", 5, results.size());
        for (int i = 1; i <= 5; i++) {
            assertTrue("Should contain async-" + i, results.contains("async-" + i));
        }
    }

    @SneakyThrows
    public void testExecuteAsyncWithErrorIsolation() {
        BatchedAsyncExecutor<Integer, String> executor = new BatchedAsyncExecutor<>(threadPool, ThreadPool.Names.GENERIC, 3);

        List<Integer> items = List.of(1, 2, 3, 4, 5);
        CountDownLatch latch = new CountDownLatch(1);
        List<String> results = Collections.synchronizedList(new ArrayList<>());

        executor.executeAsync(items, i -> {
            if (i == 2 || i == 4) {
                CompletableFuture<String> failed = new CompletableFuture<>();
                failed.completeExceptionally(new RuntimeException("Simulated async failure for " + i));
                return failed;
            }
            return CompletableFuture.supplyAsync(() -> "ok-" + i, threadPool.executor(ThreadPool.Names.GENERIC));
        }, ActionListener.wrap(r -> {
            results.addAll(r);
            latch.countDown();
        }, e -> {
            fail("Executor should not fail: " + e.getMessage());
            latch.countDown();
        }));

        assertTrue(latch.await(TEST_TIMEOUT_SECONDS, TimeUnit.SECONDS));
        assertEquals("3 successful items should produce results", 3, results.size());
        assertTrue(results.containsAll(List.of("ok-1", "ok-3", "ok-5")));
    }

    @SneakyThrows
    public void testExecuteAsyncWithCancellation() {
        BatchedAsyncExecutor<Integer, Integer> executor = new BatchedAsyncExecutor<>(threadPool, ThreadPool.Names.GENERIC, 2);

        List<Integer> items = IntStream.rangeClosed(1, 10).boxed().toList();
        AtomicBoolean cancelled = new AtomicBoolean(false);
        AtomicInteger processedCount = new AtomicInteger(0);
        CountDownLatch latch = new CountDownLatch(1);

        executor.executeAsync(items, i -> CompletableFuture.supplyAsync(() -> {
            int count = processedCount.incrementAndGet();
            if (count >= 4) {
                cancelled.set(true);
            }
            return i;
        }, threadPool.executor(ThreadPool.Names.GENERIC)), cancelled::get, ActionListener.wrap(r -> {
            assertTrue("Should have partial results", r.size() < items.size());
            latch.countDown();
        }, e -> {
            fail("Should not fail: " + e.getMessage());
            latch.countDown();
        }));

        assertTrue(latch.await(TEST_TIMEOUT_SECONDS, TimeUnit.SECONDS));
    }

    @SneakyThrows
    public void testExecuteAsyncWithEmptyList() {
        BatchedAsyncExecutor<String, String> executor = new BatchedAsyncExecutor<>(threadPool, ThreadPool.Names.GENERIC, 5);

        CountDownLatch latch = new CountDownLatch(1);

        executor.executeAsync(Collections.emptyList(), s -> CompletableFuture.completedFuture(s), ActionListener.wrap(r -> {
            assertTrue("Results should be empty", r.isEmpty());
            latch.countDown();
        }, e -> {
            fail("Should not fail: " + e.getMessage());
            latch.countDown();
        }));

        assertTrue(latch.await(TEST_TIMEOUT_SECONDS, TimeUnit.SECONDS));
    }

    @SneakyThrows
    public void testExecuteAsyncThreadStarvationPrevention() {
        int processors = Runtime.getRuntime().availableProcessors();
        int genericPoolMax = Math.max(128, 4 * processors);
        int numItems = genericPoolMax * 2;
        int batchSize = Math.max(2, Math.min(24, processors / 2));

        BatchedAsyncExecutor<Integer, String> executor = new BatchedAsyncExecutor<>(threadPool, ThreadPool.Names.GENERIC, batchSize);

        List<Integer> items = IntStream.rangeClosed(1, numItems).boxed().toList();
        CountDownLatch latch = new CountDownLatch(1);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger timeoutCount = new AtomicInteger(0);

        executor.executeAsync(items, item -> {
            // The async processor itself submits work to GENERIC and returns a future
            CompletableFuture<String> outerFuture = CompletableFuture.supplyAsync(() -> {
                // Nested blocking on the same pool
                try {
                    CompletableFuture<String> nestedOp = CompletableFuture.supplyAsync(() -> {
                        try {
                            Thread.sleep(10);
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                        }
                        return "nested";
                    }, threadPool.executor(ThreadPool.Names.GENERIC));

                    String result = nestedOp.get(5, TimeUnit.SECONDS);
                    successCount.incrementAndGet();
                    return "item-" + item + "-" + result;
                } catch (Exception e) {
                    timeoutCount.incrementAndGet();
                    return null;
                }
            }, threadPool.executor(ThreadPool.Names.GENERIC));
            return outerFuture;
        }, ActionListener.wrap(results -> {
            log.info("Async starvation test: total={}, successes={}, timeouts={}", numItems, successCount.get(), timeoutCount.get());
            latch.countDown();
        }, e -> {
            fail("Should not fail: " + e.getMessage());
            latch.countDown();
        }));

        assertTrue("Timed out", latch.await(180, TimeUnit.SECONDS));
        assertEquals("No starvation timeouts with batched async execution", 0, timeoutCount.get());
        assertEquals("All items should succeed", numItems, successCount.get());
    }

    public void testPartitionEvenSplit() {
        List<Integer> list = List.of(1, 2, 3, 4, 5, 6);
        List<List<Integer>> partitions = BatchedAsyncExecutor.partition(list, 3);
        assertEquals(2, partitions.size());
        assertEquals(List.of(1, 2, 3), partitions.get(0));
        assertEquals(List.of(4, 5, 6), partitions.get(1));
    }

    public void testPartitionUnevenSplit() {
        List<Integer> list = List.of(1, 2, 3, 4, 5);
        List<List<Integer>> partitions = BatchedAsyncExecutor.partition(list, 3);
        assertEquals(2, partitions.size());
        assertEquals(List.of(1, 2, 3), partitions.get(0));
        assertEquals(List.of(4, 5), partitions.get(1));
    }

    public void testPartitionSingleElement() {
        List<Integer> list = List.of(1);
        List<List<Integer>> partitions = BatchedAsyncExecutor.partition(list, 5);
        assertEquals(1, partitions.size());
        assertEquals(List.of(1), partitions.get(0));
    }

    public void testPartitionBatchSizeOne() {
        List<Integer> list = List.of(1, 2, 3);
        List<List<Integer>> partitions = BatchedAsyncExecutor.partition(list, 1);
        assertEquals(3, partitions.size());
    }
}
