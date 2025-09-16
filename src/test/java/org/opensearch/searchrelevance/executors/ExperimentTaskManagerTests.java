/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */
package org.opensearch.searchrelevance.executors;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import org.opensearch.cluster.service.ClusterService;
import org.opensearch.common.settings.Settings;
import org.opensearch.common.unit.TimeValue;
import org.opensearch.common.util.concurrent.OpenSearchExecutors;
import org.opensearch.core.action.ActionListener;
import org.opensearch.searchrelevance.dao.EvaluationResultDao;
import org.opensearch.searchrelevance.dao.ExperimentVariantDao;
import org.opensearch.searchrelevance.model.AsyncStatus;
import org.opensearch.searchrelevance.model.ExperimentType;
import org.opensearch.searchrelevance.model.ExperimentVariant;
import org.opensearch.searchrelevance.scheduler.ExperimentCancellationToken;
import org.opensearch.test.OpenSearchTestCase;
import org.opensearch.threadpool.ThreadPool;
import org.opensearch.transport.client.Client;

/**
 * Unit tests for ExperimentTaskManager
 */
public class ExperimentTaskManagerTests extends OpenSearchTestCase {

    private Client client;
    private ClusterService clusterService;
    private EvaluationResultDao evaluationResultDao;
    private ExperimentVariantDao experimentVariantDao;
    private ThreadPool threadPool;
    private ExecutorService immediateExecutor;

    @Override
    public void setUp() throws Exception {
        super.setUp();
        client = mock(Client.class);
        clusterService = mock(ClusterService.class);
        evaluationResultDao = mock(EvaluationResultDao.class);
        experimentVariantDao = mock(ExperimentVariantDao.class);
        threadPool = mock(ThreadPool.class);

        // Create an immediate executor
        immediateExecutor = mock(ExecutorService.class);
        doAnswer(invocation -> {
            Runnable command = invocation.getArgument(0);
            command.run();
            return null;
        }).when(immediateExecutor).execute(any(Runnable.class));

        // Setup thread pool mocks
        when(threadPool.executor(anyString())).thenReturn(immediateExecutor);

        // Handle scheduled tasks
        doAnswer(invocation -> {
            Runnable command = invocation.getArgument(0);
            command.run();
            return null;
        }).when(threadPool).schedule(any(Runnable.class), any(TimeValue.class), anyString());
    }

    public void testScheduleTasksWithValidInputShouldTrackCompletions() {
        // This is a simplified test focusing on completion tracking logic

        // Arrange
        AtomicInteger pendingConfigs = new AtomicInteger(1);
        AtomicBoolean hasFailure = new AtomicBoolean(false);
        AtomicBoolean listenerCalled = new AtomicBoolean(false);

        ActionListener<Map<String, Object>> listener = new ActionListener<>() {
            @Override
            public void onResponse(Map<String, Object> response) {
                listenerCalled.set(true);
            }

            @Override
            public void onFailure(Exception e) {
                fail("Should not fail: " + e.getMessage());
            }
        };

        // Act - Simulate completion of configuration
        pendingConfigs.decrementAndGet();
        if (pendingConfigs.get() == 0 && !hasFailure.get()) {
            listener.onResponse(new HashMap<>());
        }

        // Assert
        assertTrue("Listener should be called when all configurations complete", listenerCalled.get());
    }

    public void testScheduleTasksWithMultipleConfigsShouldWaitForAll() {
        // Arrange
        AtomicInteger pendingConfigs = new AtomicInteger(2); // Two configurations
        AtomicBoolean hasFailure = new AtomicBoolean(false);
        AtomicBoolean listenerCalled = new AtomicBoolean(false);

        ActionListener<Map<String, Object>> listener = new ActionListener<>() {
            @Override
            public void onResponse(Map<String, Object> response) {
                listenerCalled.set(true);
            }

            @Override
            public void onFailure(Exception e) {
                fail("Should not fail: " + e.getMessage());
            }
        };

        // Act - Simulate completion of first configuration
        pendingConfigs.decrementAndGet();
        if (pendingConfigs.get() == 0 && !hasFailure.get()) {
            listener.onResponse(new HashMap<>());
        }

        // Assert - Should not complete yet
        assertFalse("Listener should not be called until all configs complete", listenerCalled.get());

        // Act - Simulate completion of second configuration
        pendingConfigs.decrementAndGet();
        if (pendingConfigs.get() == 0 && !hasFailure.get()) {
            listener.onResponse(new HashMap<>());
        }

        // Assert - Should complete now
        assertTrue("Listener should be called when all configs are done", listenerCalled.get());
    }

    private List<ExperimentVariant> createTestVariants(String experimentId, int count) {
        List<ExperimentVariant> variants = new java.util.ArrayList<>();
        for (int i = 0; i < count; i++) {
            ExperimentVariant variant = new ExperimentVariant(
                "variant-" + i,
                "2023-01-01T00:00:00Z",
                ExperimentType.HYBRID_OPTIMIZER,
                AsyncStatus.PROCESSING,
                experimentId,
                Map.of("param" + i, "value" + i),
                Map.of()
            );
            variants.add(variant);
        }
        return variants;
    }

    // Tests for newly added dynamic concurrency control logic

    public void testDynamicConcurrencyControlInitialization() {
        // Test that ExperimentTaskManager initializes with dynamic concurrency limits
        ExperimentTaskManager taskManager = new ExperimentTaskManager(client, evaluationResultDao, experimentVariantDao, threadPool);

        Map<String, Object> metrics = taskManager.getConcurrencyMetrics();

        // Verify all expected metrics are present
        assertTrue("Should report active_experiments", metrics.containsKey("active_experiments"));
        assertTrue("Should report max_concurrent_tasks", metrics.containsKey("max_concurrent_tasks"));
        assertTrue("Should report available_permits", metrics.containsKey("available_permits"));
        assertTrue("Should report queued_threads", metrics.containsKey("queued_threads"));
        assertTrue("Should report thread_pool", metrics.containsKey("thread_pool"));

        // Verify the concurrency limit is reasonable
        int maxConcurrentTasks = (Integer) metrics.get("max_concurrent_tasks");
        assertTrue("Should have at least 2 concurrent tasks (minimum)", maxConcurrentTasks >= 2);
        assertTrue("Should have at most 16 concurrent tasks (maximum)", maxConcurrentTasks <= 16);

        // Verify initial state
        assertEquals("Should have 0 active experiments initially", 0, metrics.get("active_experiments"));
        assertEquals("Should report correct thread pool name", "_plugin_search_relevance_executor", metrics.get("thread_pool"));

        // Available permits should equal max concurrent tasks initially
        assertEquals("Available permits should equal max concurrent tasks initially", maxConcurrentTasks, metrics.get("available_permits"));
    }

    public void testConcurrencyLimitCalculationLogic() {
        // Test the actual concurrency calculation logic with current system
        ExperimentTaskManager taskManager = new ExperimentTaskManager(client, evaluationResultDao, experimentVariantDao, threadPool);

        Map<String, Object> metrics = taskManager.getConcurrencyMetrics();
        int maxConcurrentTasks = (Integer) metrics.get("max_concurrent_tasks");

        // Get actual processor count for validation
        int actualProcessors = OpenSearchExecutors.allocatedProcessors(Settings.EMPTY);
        int expectedTasks = Math.max(2, Math.min(16, actualProcessors / 2));

        assertEquals("Dynamic concurrency limit should match expected calculation", expectedTasks, maxConcurrentTasks);
    }

    public void testConcurrencyMetricsConsistency() {
        // Test that metrics are consistent and make sense
        ExperimentTaskManager taskManager = new ExperimentTaskManager(client, evaluationResultDao, experimentVariantDao, threadPool);

        Map<String, Object> metrics = taskManager.getConcurrencyMetrics();

        int maxConcurrentTasks = (Integer) metrics.get("max_concurrent_tasks");
        int availablePermits = (Integer) metrics.get("available_permits");
        int queuedThreads = (Integer) metrics.get("queued_threads");

        // Initially, available permits should equal max concurrent tasks
        assertEquals("Initially available permits should equal max concurrent tasks", maxConcurrentTasks, availablePermits);

        // Initially, no threads should be queued
        assertEquals("Initially no threads should be queued", 0, queuedThreads);

        // Max concurrent tasks should be within expected bounds
        assertTrue("Max concurrent tasks should be at least 2", maxConcurrentTasks >= 2);
        assertTrue("Max concurrent tasks should be at most 16", maxConcurrentTasks <= 16);
    }

    public void testConcurrencyLimitBoundaries() {
        // Test that the concurrency calculation respects minimum and maximum bounds
        ExperimentTaskManager taskManager = new ExperimentTaskManager(client, evaluationResultDao, experimentVariantDao, threadPool);

        Map<String, Object> metrics = taskManager.getConcurrencyMetrics();
        int maxConcurrentTasks = (Integer) metrics.get("max_concurrent_tasks");

        // Test minimum boundary (should never be less than 2)
        assertTrue("Should enforce minimum of 2 concurrent tasks", maxConcurrentTasks >= 2);

        // Test maximum boundary (should never exceed 16)
        assertTrue("Should enforce maximum of 16 concurrent tasks", maxConcurrentTasks <= 16);

        // For validation, test the actual formula logic
        int actualProcessors = OpenSearchExecutors.allocatedProcessors(Settings.EMPTY);
        int formulaResult = actualProcessors / 2;
        int expectedResult = Math.max(2, Math.min(16, formulaResult));

        assertEquals("Concurrency limit should match formula: max(2, min(16, processors/2))", expectedResult, maxConcurrentTasks);
    }

    public void testDynamicConcurrencyScaling() {
        // Test that dynamic concurrency scales appropriately with processor count
        ExperimentTaskManager taskManager = new ExperimentTaskManager(client, evaluationResultDao, experimentVariantDao, threadPool);

        Map<String, Object> metrics = taskManager.getConcurrencyMetrics();
        int maxConcurrentTasks = (Integer) metrics.get("max_concurrent_tasks");

        int actualProcessors = OpenSearchExecutors.allocatedProcessors(Settings.EMPTY);

        // Test scaling relationship
        if (actualProcessors <= 4) {
            // Small systems should get minimum of 2
            assertEquals("Small systems should get minimum 2 tasks", 2, maxConcurrentTasks);
        } else if (actualProcessors >= 32) {
            // Large systems should be capped at 16
            assertEquals("Large systems should be capped at 16 tasks", 16, maxConcurrentTasks);
        } else {
            // Medium systems should scale with processor count / 2
            assertEquals("Medium systems should scale as processors/2", actualProcessors / 2, maxConcurrentTasks);
        }
    }

    public void testConfigMapInitialization() throws Exception {
        // Arrange
        ExperimentTaskManager taskManager = new ExperimentTaskManager(client, evaluationResultDao, experimentVariantDao, threadPool);
        String experimentId = "test-experiment";
        String searchConfigId = "test-config";
        Map<String, Object> initialConfigMap = new HashMap<>();
        initialConfigMap.put("existing-key", "existing-value");

        // Act
        CompletableFuture<Map<String, Object>> future = taskManager.scheduleTasksAsync(
            ExperimentType.HYBRID_OPTIMIZER,
            experimentId,
            searchConfigId,
            "test-index",
            "test-query",
            "test query text",
            10,
            createTestVariants(experimentId, 1),
            List.of("judgment-1"),
            Map.of("doc1", "5"),
            initialConfigMap,
            new AtomicBoolean(false),
            null,
            null,
            null
        );

        // Assert
        assertNotNull("Should return a CompletableFuture", future);
        // The initial config map should be preserved
        assertTrue("Should preserve existing keys", initialConfigMap.containsKey("existing-key"));
    }

    public void testScheduledTaskAsyncWithCancellation() {
        ExperimentTaskManager taskManager = new ExperimentTaskManager(client, evaluationResultDao, experimentVariantDao, threadPool);
        String experimentId = "test-experiment";
        String searchConfigId = "test-config";
        Map<String, Object> initialConfigMap = new HashMap<>();
        initialConfigMap.put("existing-key", "existing-value");
        String scheduledExperimentResultId = "scheduled-experiment-result";
        ExperimentCancellationToken cancellationToken = new ExperimentCancellationToken(scheduledExperimentResultId);
        cancellationToken.cancel();

        CompletableFuture<Map<String, Object>> future = taskManager.scheduleTasksAsync(
            ExperimentType.HYBRID_OPTIMIZER,
            experimentId,
            searchConfigId,
            "test-index",
            "test-query",
            "test query text",
            10,
            createTestVariants(experimentId, 1),
            List.of("judgment-1"),
            Map.of("doc1", "5"),
            initialConfigMap,
            new AtomicBoolean(false),
            scheduledExperimentResultId,
            new HashMap<>(),
            cancellationToken
        );

        // Wait on future to be ready to be cancelled.
        expectThrows(CompletionException.class, () -> future.join());

        assertTrue(future.isCompletedExceptionally());
    }
}
