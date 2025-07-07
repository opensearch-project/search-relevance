/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */
package org.opensearch.searchrelevance.executors;

import static org.mockito.Mockito.mock;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.opensearch.core.action.ActionListener;
import org.opensearch.searchrelevance.model.JudgmentBatchStatus;
import org.opensearch.searchrelevance.model.SearchConfiguration;
import org.opensearch.test.OpenSearchTestCase;

public class JudgmentTaskContextTests extends OpenSearchTestCase {

    public void testTaskContextInitialization() {
        // Arrange
        String queryTextWithReference = "laptop#Professional laptop for business";
        String modelId = "test-model-id";
        List<String> contextFields = List.of("name", "description");
        List<SearchConfiguration> searchConfigurations = List.of(mock(SearchConfiguration.class));
        boolean ignoreFailure = true;
        ActionListener<Map<String, String>> listener = mock(ActionListener.class);

        // Act
        JudgmentTaskContext context = new JudgmentTaskContext(
            queryTextWithReference,
            modelId,
            contextFields,
            searchConfigurations,
            ignoreFailure,
            listener
        );

        // Assert
        assertEquals(queryTextWithReference, context.getQueryTextWithReference());
        assertEquals(modelId, context.getModelId());
        assertEquals(contextFields, context.getContextFields());
        assertEquals(searchConfigurations, context.getSearchConfigurations());
        assertEquals(ignoreFailure, context.isIgnoreFailure());
        assertEquals(searchConfigurations.size(), context.getPendingSearchTasks().get());
        assertEquals(0, context.getPendingCacheTasks().get());
        assertEquals(0, context.getSuccessfulTasks().get());
        assertEquals(0, context.getFailedTasks().get());
        assertNotNull(context.getDocIdToScore());
        assertTrue(context.getDocIdToScore().isEmpty());
    }

    public void testCompleteSearchTaskSuccess() {
        // Arrange
        JudgmentTaskContext context = createTestContext(2);

        // Act
        context.completeSearchTask(true);

        // Assert
        assertEquals(1, context.getSuccessfulTasks().get());
        assertEquals(0, context.getFailedTasks().get());
        assertEquals(1, context.getPendingSearchTasks().get());
        assertFalse(context.isAllTasksCompleted());
    }

    public void testCompleteSearchTaskFailure() {
        // Arrange
        JudgmentTaskContext context = createTestContext(2);

        // Act
        context.completeSearchTask(false);

        // Assert
        assertEquals(0, context.getSuccessfulTasks().get());
        assertEquals(1, context.getFailedTasks().get());
        assertEquals(1, context.getPendingSearchTasks().get());
        assertFalse(context.isAllTasksCompleted());
    }

    public void testCompleteCacheTaskSuccess() {
        // Arrange
        JudgmentTaskContext context = createTestContext(1);
        context.setPendingCacheTasks(3);

        // Act
        context.completeCacheTask(true);

        // Assert
        assertEquals(1, context.getSuccessfulTasks().get());
        assertEquals(0, context.getFailedTasks().get());
        assertEquals(2, context.getPendingCacheTasks().get());
        assertFalse(context.isAllTasksCompleted());
    }

    public void testCompleteCacheTaskFailure() {
        // Arrange
        JudgmentTaskContext context = createTestContext(1);
        context.setPendingCacheTasks(3);

        // Act
        context.completeCacheTask(false);

        // Assert
        assertEquals(0, context.getSuccessfulTasks().get());
        assertEquals(1, context.getFailedTasks().get());
        assertEquals(2, context.getPendingCacheTasks().get());
        assertFalse(context.isAllTasksCompleted());
    }

    public void testIsAllTasksCompleted() {
        // Arrange
        JudgmentTaskContext context = createTestContext(2);
        context.setPendingCacheTasks(2);

        // Act & Assert - initially not completed
        assertFalse(context.isAllTasksCompleted());

        // Complete search tasks
        context.completeSearchTask(true);
        context.completeSearchTask(true);
        assertFalse(context.isAllTasksCompleted()); // Cache tasks still pending

        // Complete cache tasks
        context.completeCacheTask(true);
        context.completeCacheTask(false);
        assertTrue(context.isAllTasksCompleted()); // All tasks completed
    }

    public void testCompleteJudgmentSuccess() throws Exception {
        // Arrange
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<Map<String, String>> result = new AtomicReference<>();

        ActionListener<Map<String, String>> listener = new ActionListener<Map<String, String>>() {
            @Override
            public void onResponse(Map<String, String> response) {
                result.set(response);
                latch.countDown();
            }

            @Override
            public void onFailure(Exception e) {
                fail("Should not fail: " + e.getMessage());
            }
        };

        JudgmentTaskContext context = new JudgmentTaskContext(
            "test query",
            "model-id",
            List.of("field1"),
            List.of(mock(SearchConfiguration.class)),
            false,
            listener
        );

        // Add some test data
        context.getDocIdToScore().put("doc1", "0.8");
        context.getDocIdToScore().put("doc2", "0.6");

        // Act
        context.completeJudgment();

        // Assert
        assertTrue("Judgment should complete within timeout", latch.await(5, TimeUnit.SECONDS));
        assertNotNull(result.get());
        assertEquals(context.getDocIdToScore(), result.get());
    }

    public void testFailJudgment() throws Exception {
        // Arrange
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<Exception> error = new AtomicReference<>();

        ActionListener<Map<String, String>> listener = new ActionListener<Map<String, String>>() {
            @Override
            public void onResponse(Map<String, String> response) {
                fail("Should not succeed");
            }

            @Override
            public void onFailure(Exception e) {
                error.set(e);
                latch.countDown();
            }
        };

        JudgmentTaskContext context = new JudgmentTaskContext(
            "test query",
            "model-id",
            List.of("field1"),
            List.of(mock(SearchConfiguration.class)),
            false,
            listener
        );

        Exception testException = new RuntimeException("Test failure");

        // Act
        context.failJudgment(testException);

        // Assert
        assertTrue("Judgment should fail within timeout", latch.await(5, TimeUnit.SECONDS));
        assertEquals(testException, error.get());
    }

    public void testGetStatusAllSuccess() {
        // Arrange
        JudgmentTaskContext context = createTestContext(2);
        context.setPendingCacheTasks(2);

        // Complete all tasks successfully
        context.completeSearchTask(true);
        context.completeSearchTask(true);
        context.completeCacheTask(true);
        context.completeCacheTask(true);

        // Act & Assert
        assertEquals(JudgmentBatchStatus.SUCCESS, context.getStatus());
    }

    public void testGetStatusPartialSuccess() {
        // Arrange
        JudgmentTaskContext context = createTestContext(2);
        context.setPendingCacheTasks(2);

        // Complete with mixed results
        context.completeSearchTask(true);
        context.completeSearchTask(false);
        context.completeCacheTask(true);
        context.completeCacheTask(true);

        // Act & Assert
        assertEquals(JudgmentBatchStatus.PARTIAL_SUCCESS, context.getStatus());
    }

    public void testGetStatusAllFailed() {
        // Arrange
        JudgmentTaskContext context = createTestContext(2);
        context.setPendingCacheTasks(2);

        // Complete all tasks with failures
        context.completeSearchTask(false);
        context.completeSearchTask(false);
        context.completeCacheTask(false);
        context.completeCacheTask(false);

        // Act & Assert
        assertEquals(JudgmentBatchStatus.ALL_FAILED, context.getStatus());
    }

    public void testConcurrentTaskCompletions() throws Exception {
        // Arrange
        int searchTasks = 50;
        int cacheTasks = 50;
        JudgmentTaskContext context = createTestContext(searchTasks);
        context.setPendingCacheTasks(cacheTasks);

        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch completionLatch = new CountDownLatch(searchTasks + cacheTasks);

        // Act - simulate concurrent task completions
        for (int i = 0; i < searchTasks; i++) {
            final boolean isSuccess = i % 3 != 0; // Every 3rd task fails
            new Thread(() -> {
                try {
                    startLatch.await();
                    context.completeSearchTask(isSuccess);
                    completionLatch.countDown();
                } catch (InterruptedException e) {
                    fail("Thread interrupted");
                }
            }).start();
        }

        for (int i = 0; i < cacheTasks; i++) {
            final boolean isSuccess = i % 4 != 0; // Every 4th task fails
            new Thread(() -> {
                try {
                    startLatch.await();
                    context.completeCacheTask(isSuccess);
                    completionLatch.countDown();
                } catch (InterruptedException e) {
                    fail("Thread interrupted");
                }
            }).start();
        }

        // Start all threads simultaneously
        startLatch.countDown();

        // Wait for all completions
        assertTrue("All tasks should complete within timeout", completionLatch.await(10, TimeUnit.SECONDS));

        // Assert
        assertTrue(context.isAllTasksCompleted());
        assertEquals(0, context.getPendingSearchTasks().get());
        assertEquals(0, context.getPendingCacheTasks().get());

        int totalTasks = searchTasks + cacheTasks;
        int successCount = context.getSuccessfulTasks().get();
        int failureCount = context.getFailedTasks().get();

        assertEquals(totalTasks, successCount + failureCount);
        assertTrue("Should have some successes", successCount > 0);
        assertTrue("Should have some failures", failureCount > 0);
        assertEquals(JudgmentBatchStatus.PARTIAL_SUCCESS, context.getStatus());
    }

    public void testDocIdToScoreThreadSafety() throws Exception {
        // Arrange
        JudgmentTaskContext context = createTestContext(1);
        int threadCount = 100;
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch completionLatch = new CountDownLatch(threadCount);

        // Act - concurrent writes to docIdToScore
        for (int i = 0; i < threadCount; i++) {
            final int docIndex = i;
            new Thread(() -> {
                try {
                    startLatch.await();
                    context.getDocIdToScore().put("doc" + docIndex, "0." + docIndex);
                    completionLatch.countDown();
                } catch (InterruptedException e) {
                    fail("Thread interrupted");
                }
            }).start();
        }

        startLatch.countDown();
        assertTrue("All writes should complete within timeout", completionLatch.await(5, TimeUnit.SECONDS));

        // Assert
        assertEquals(threadCount, context.getDocIdToScore().size());
        for (int i = 0; i < threadCount; i++) {
            assertTrue("Should contain doc" + i, context.getDocIdToScore().containsKey("doc" + i));
        }
    }

    private JudgmentTaskContext createTestContext(int searchConfigCount) {
        List<SearchConfiguration> searchConfigurations = new java.util.ArrayList<>();
        for (int i = 0; i < searchConfigCount; i++) {
            searchConfigurations.add(mock(SearchConfiguration.class));
        }

        return new JudgmentTaskContext(
            "test query#reference answer",
            "test-model-id",
            List.of("name", "description"),
            searchConfigurations,
            false,
            mock(ActionListener.class)
        );
    }
}
