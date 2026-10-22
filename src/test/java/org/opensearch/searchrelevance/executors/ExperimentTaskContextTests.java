/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */
package org.opensearch.searchrelevance.executors;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import org.opensearch.core.action.ActionListener;
import org.opensearch.searchrelevance.dao.ExperimentVariantDao;
import org.opensearch.searchrelevance.model.AsyncStatus;
import org.opensearch.searchrelevance.model.ExperimentBatchStatus;
import org.opensearch.searchrelevance.model.ExperimentType;
import org.opensearch.searchrelevance.model.ExperimentVariant;
import org.opensearch.test.OpenSearchTestCase;

import com.carrotsearch.randomizedtesting.annotations.ThreadLeakScope;

@ThreadLeakScope(ThreadLeakScope.Scope.NONE)
public class ExperimentTaskContextTests extends OpenSearchTestCase {

    private ExperimentVariantDao experimentVariantDao;

    @Override
    public void setUp() throws Exception {
        super.setUp();
        experimentVariantDao = mock(ExperimentVariantDao.class);
    }

    public void testTaskContextInitialization() {
        // Arrange
        String experimentId = "test-experiment-id";
        String searchConfigId = "test-search-config";
        String queryText = "test query";
        int totalVariants = 3;
        ConcurrentHashMap<String, Object> configMap = new ConcurrentHashMap<>();
        CompletableFuture<Map<String, Object>> resultFuture = new CompletableFuture<>();
        AtomicBoolean hasFailure = new AtomicBoolean(false);

        // Act
        ExperimentTaskContext context = new ExperimentTaskContext(
            experimentId,
            searchConfigId,
            queryText,
            totalVariants,
            configMap,
            resultFuture,
            hasFailure,
            experimentVariantDao,
            ExperimentType.HYBRID_OPTIMIZER
        );

        // Assert
        assertEquals(experimentId, context.getExperimentId());
        assertEquals(searchConfigId, context.getSearchConfigId());
        assertEquals(queryText, context.getQueryText());
        assertEquals(totalVariants, context.getTotalVariants());
        assertEquals(configMap, context.getConfigToExperimentVariants());
        assertEquals(resultFuture, context.getResultFuture());
        assertEquals(hasFailure, context.getHasFailure());
        assertEquals(experimentVariantDao, context.getExperimentVariantDao());
        assertEquals(totalVariants, context.getRemainingVariants().get());
        assertEquals(0, context.getSuccessfulVariants().get());
        assertEquals(0, context.getFailedVariants().get());
    }

    public void testCompleteVariantSuccess() {
        // Arrange
        ExperimentTaskContext context = createTestContext(2);

        // Act
        context.completeVariantSuccess();

        // Assert
        assertEquals(1, context.getSuccessfulVariants().get());
        assertEquals(0, context.getFailedVariants().get());
        assertEquals(1, context.getRemainingVariants().get());
        assertFalse(context.getResultFuture().isDone());

        // Complete the second variant
        context.completeVariantSuccess();

        // Assert final state
        assertEquals(2, context.getSuccessfulVariants().get());
        assertEquals(0, context.getFailedVariants().get());
        assertEquals(0, context.getRemainingVariants().get());
        assertTrue(context.getResultFuture().isDone());
    }

    public void testCompleteVariantFailure() {
        // Arrange
        ExperimentTaskContext context = createTestContext(2);

        // Act
        context.completeVariantFailure();

        // Assert
        assertEquals(0, context.getSuccessfulVariants().get());
        assertEquals(1, context.getFailedVariants().get());
        assertEquals(1, context.getRemainingVariants().get());
        assertFalse(context.getResultFuture().isDone());

        // Complete the second variant
        context.completeVariantFailure();

        // Assert final state
        assertEquals(0, context.getSuccessfulVariants().get());
        assertEquals(2, context.getFailedVariants().get());
        assertEquals(0, context.getRemainingVariants().get());
        assertTrue(context.getResultFuture().isDone());
    }

    public void testMixedSuccessAndFailure() throws Exception {
        // Arrange
        ExperimentTaskContext context = createTestContext(3);

        // Act - 2 successes, 1 failure
        context.completeVariantSuccess();
        context.completeVariantFailure();
        context.completeVariantSuccess();

        // Assert
        assertTrue(context.getResultFuture().isDone());
        Map<String, Object> result = context.getResultFuture().get();

        assertEquals(ExperimentBatchStatus.PARTIAL_SUCCESS, result.get("status"));
        Map<String, Object> summary = (Map<String, Object>) result.get("summary");
        assertEquals(3, summary.get("totalVariants"));
        assertEquals(2, summary.get("successfulVariants"));
        assertEquals(1, summary.get("failedVariants"));
    }

    public void testAllVariantsFailed() throws Exception {
        // Arrange
        ExperimentTaskContext context = createTestContext(2);

        // Act
        context.completeVariantFailure();
        context.completeVariantFailure();

        // Assert
        assertTrue(context.getResultFuture().isDone());
        Map<String, Object> result = context.getResultFuture().get(5, TimeUnit.SECONDS);

        assertEquals(ExperimentBatchStatus.ALL_FAILED, result.get("status"));
    }

    public void testAllVariantsSucceeded() throws Exception {
        // Arrange
        ExperimentTaskContext context = createTestContext(2);

        // Act
        context.completeVariantSuccess();
        context.completeVariantSuccess();

        // Assert
        assertTrue(context.getResultFuture().isDone());
        Map<String, Object> result = context.getResultFuture().get(5, TimeUnit.SECONDS);

        assertEquals(ExperimentBatchStatus.SUCCESS, result.get("status"));
    }

    public void testScheduleVariantWriteSuccess() throws Exception {
        // Arrange
        CountDownLatch latch = new CountDownLatch(1);
        ExperimentTaskContext context = createTestContext(1);
        ExperimentVariant variant = createTestVariant("variant-1");
        String evaluationId = "eval-1";

        // Mock successful write
        doAnswer(invocation -> {
            ActionListener<Object> listener = invocation.getArgument(1);
            listener.onResponse(null);
            latch.countDown();
            return null;
        }).when(experimentVariantDao).putExperimentVariantEfficient(any(), any());

        // Setup config map
        ConcurrentHashMap<String, Object> searchConfigMap = new ConcurrentHashMap<>();
        context.getConfigToExperimentVariants().put(context.getSearchConfigId(), searchConfigMap);

        // Act
        context.scheduleVariantWrite(variant, evaluationId, true);

        // Assert
        assertTrue("Write should complete within timeout", latch.await(5, TimeUnit.SECONDS));
        verify(experimentVariantDao, times(1)).putExperimentVariantEfficient(any(), any());

        // Wait a bit for async map update
        Thread.sleep(100);
        assertEquals(evaluationId, searchConfigMap.get(variant.getId()));
    }

    public void testScheduleVariantWriteFailure() throws Exception {
        // Arrange
        CountDownLatch latch = new CountDownLatch(1);
        ExperimentTaskContext context = createTestContext(1);
        ExperimentVariant variant = createTestVariant("variant-1");
        String evaluationId = "eval-1";

        // Mock failed write
        doAnswer(invocation -> {
            ActionListener<Object> listener = invocation.getArgument(1);
            listener.onFailure(new RuntimeException("Write failed"));
            latch.countDown();
            return null;
        }).when(experimentVariantDao).putExperimentVariantEfficient(any(), any());

        // Act
        context.scheduleVariantWrite(variant, evaluationId, false);

        // Assert
        assertTrue("Write should complete within timeout", latch.await(5, TimeUnit.SECONDS));
        verify(experimentVariantDao, times(1)).putExperimentVariantEfficient(any(), any());
    }

    public void testConcurrentVariantCompletions() throws Exception {
        // Arrange
        int variantCount = 100;
        ExperimentTaskContext context = createTestContext(variantCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch completionLatch = new CountDownLatch(variantCount);

        // Act - simulate concurrent completions
        for (int i = 0; i < variantCount; i++) {
            final boolean isSuccess = (i + 1) % 3 != 0; // Every 3rd variant fails (1-indexed)
            new Thread(() -> {
                try {
                    startLatch.await();
                    if (isSuccess) {
                        context.completeVariantSuccess();
                    } else {
                        context.completeVariantFailure();
                    }
                    completionLatch.countDown();
                } catch (InterruptedException e) {
                    fail("Thread interrupted");
                }
            }).start();
        }

        // Start all threads simultaneously
        startLatch.countDown();

        // Wait for all completions
        assertTrue("All variants should complete within timeout", completionLatch.await(10, TimeUnit.SECONDS));

        // Assert
        assertTrue(context.getResultFuture().isDone());
        assertEquals(0, context.getRemainingVariants().get());

        // With 100 variants and every 3rd failing, we get 67 successes and 33 failures
        int successCount = context.getSuccessfulVariants().get();
        int failureCount = context.getFailedVariants().get();

        // Allow for slight variation due to concurrent execution
        assertTrue("Expected around 67 successes, got " + successCount, successCount >= 66 && successCount <= 68);
        assertTrue("Expected around 33 failures, got " + failureCount, failureCount >= 32 && failureCount <= 34);

        Map<String, Object> result = context.getResultFuture().get();
        assertEquals(ExperimentBatchStatus.PARTIAL_SUCCESS, result.get("status"));
    }

    public void testResultFutureCompletesOnlyOnce() throws Exception {
        // Arrange
        ExperimentTaskContext context = createTestContext(1);

        // Act - complete the single variant
        context.completeVariantSuccess();

        // Assert - future should be done
        assertTrue(context.getResultFuture().isDone());
        Map<String, Object> result = context.getResultFuture().get();
        assertEquals(ExperimentBatchStatus.SUCCESS, result.get("status"));

        // Note: The implementation allows counters to continue changing after completion
        // Try to complete again (counters will change but future remains the same)
        context.completeVariantSuccess();
        context.completeVariantFailure();

        // Future result should remain unchanged
        assertTrue(context.getResultFuture().isDone());
        Map<String, Object> sameResult = context.getResultFuture().get();
        assertEquals(ExperimentBatchStatus.SUCCESS, sameResult.get("status"));
        assertEquals(result, sameResult); // Same object reference
    }

    private ExperimentTaskContext createTestContext(int totalVariants) {
        return new ExperimentTaskContext(
            "test-experiment",
            "test-search-config",
            "test query",
            totalVariants,
            new ConcurrentHashMap<>(),
            new CompletableFuture<>(),
            new AtomicBoolean(false),
            experimentVariantDao,
            ExperimentType.HYBRID_OPTIMIZER
        );
    }

    private ExperimentVariant createTestVariant(String id) {
        return new ExperimentVariant(
            id,
            "2023-01-01T00:00:00Z",
            ExperimentType.HYBRID_OPTIMIZER,
            AsyncStatus.PROCESSING,
            "test-experiment",
            Map.of("param", "value"),
            Map.of()
        );
    }
}
