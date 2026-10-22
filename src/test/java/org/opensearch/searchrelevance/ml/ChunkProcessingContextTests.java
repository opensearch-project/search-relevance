/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */
package org.opensearch.searchrelevance.ml;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.opensearch.core.action.ActionListener;
import org.opensearch.test.OpenSearchTestCase;

public class ChunkProcessingContextTests extends OpenSearchTestCase {

    public void testHandleSuccessFirstChunk() throws Exception {
        // Arrange
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<ChunkResult> result = new AtomicReference<>();

        ActionListener<ChunkResult> listener = new ActionListener<ChunkResult>() {
            @Override
            public void onResponse(ChunkResult chunkResult) {
                result.set(chunkResult);
                latch.countDown();
            }

            @Override
            public void onFailure(Exception e) {
                fail("Should not fail: " + e.getMessage());
            }
        };

        ChunkProcessingContext context = new ChunkProcessingContext(3, listener);

        // Act
        context.handleSuccess(0, "response0");

        // Assert
        assertTrue("Should complete within timeout", latch.await(5, TimeUnit.SECONDS));
        ChunkResult chunkResult = result.get();
        assertNotNull(chunkResult);
        assertEquals(0, chunkResult.getChunkIndex());
        assertEquals(3, chunkResult.getTotalChunks());
        assertFalse(chunkResult.isLastChunk());
        assertEquals(1, chunkResult.getSucceededChunks().size());
        assertEquals("response0", chunkResult.getSucceededChunks().get(0));
        assertTrue(chunkResult.getFailedChunks().isEmpty());
    }

    public void testHandleSuccessLastChunk() throws Exception {
        // Arrange
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<ChunkResult> result = new AtomicReference<>();

        ActionListener<ChunkResult> listener = new ActionListener<ChunkResult>() {
            @Override
            public void onResponse(ChunkResult chunkResult) {
                if (chunkResult.isLastChunk()) {
                    result.set(chunkResult);
                    latch.countDown();
                }
            }

            @Override
            public void onFailure(Exception e) {
                fail("Should not fail: " + e.getMessage());
            }
        };

        ChunkProcessingContext context = new ChunkProcessingContext(2, listener);

        // Act
        context.handleSuccess(0, "response0");
        context.handleSuccess(1, "response1");

        // Assert
        assertTrue("Should complete within timeout", latch.await(5, TimeUnit.SECONDS));
        ChunkResult chunkResult = result.get();
        assertNotNull(chunkResult);
        assertTrue(chunkResult.isLastChunk());
        assertEquals(2, chunkResult.getSucceededChunks().size());
        assertEquals("response0", chunkResult.getSucceededChunks().get(0));
        assertEquals("response1", chunkResult.getSucceededChunks().get(1));
    }

    public void testHandleFailure() throws Exception {
        // Arrange
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<ChunkResult> result = new AtomicReference<>();

        ActionListener<ChunkResult> listener = new ActionListener<ChunkResult>() {
            @Override
            public void onResponse(ChunkResult chunkResult) {
                result.set(chunkResult);
                latch.countDown();
            }

            @Override
            public void onFailure(Exception e) {
                fail("Should not fail: " + e.getMessage());
            }
        };

        ChunkProcessingContext context = new ChunkProcessingContext(2, listener);

        // Act
        context.handleFailure(0, new RuntimeException("Test error"));

        // Assert
        assertTrue("Should complete within timeout", latch.await(5, TimeUnit.SECONDS));
        ChunkResult chunkResult = result.get();
        assertNotNull(chunkResult);
        assertEquals(1, chunkResult.getFailedChunks().size());
        assertEquals("Test error", chunkResult.getFailedChunks().get(0));
        assertTrue(chunkResult.getSucceededChunks().isEmpty());
    }

    public void testMixedSuccessAndFailure() throws Exception {
        // Arrange
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<ChunkResult> result = new AtomicReference<>();

        ActionListener<ChunkResult> listener = new ActionListener<ChunkResult>() {
            @Override
            public void onResponse(ChunkResult chunkResult) {
                if (chunkResult.isLastChunk()) {
                    result.set(chunkResult);
                    latch.countDown();
                }
            }

            @Override
            public void onFailure(Exception e) {
                fail("Should not fail: " + e.getMessage());
            }
        };

        ChunkProcessingContext context = new ChunkProcessingContext(3, listener);

        // Act
        context.handleSuccess(0, "response0");
        context.handleFailure(1, new RuntimeException("Error1"));
        context.handleSuccess(2, "response2");

        // Assert
        assertTrue("Should complete within timeout", latch.await(5, TimeUnit.SECONDS));
        ChunkResult chunkResult = result.get();
        assertNotNull(chunkResult);
        assertTrue(chunkResult.isLastChunk());
        assertEquals(2, chunkResult.getSucceededChunks().size());
        assertEquals(1, chunkResult.getFailedChunks().size());
        assertEquals("response0", chunkResult.getSucceededChunks().get(0));
        assertEquals("response2", chunkResult.getSucceededChunks().get(2));
        assertEquals("Error1", chunkResult.getFailedChunks().get(1));
    }

    public void testConcurrentChunkProcessing() throws Exception {
        // Arrange
        int totalChunks = 10;
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch completionLatch = new CountDownLatch(1);
        AtomicReference<ChunkResult> finalResult = new AtomicReference<>();

        ActionListener<ChunkResult> listener = new ActionListener<ChunkResult>() {
            @Override
            public void onResponse(ChunkResult chunkResult) {
                if (chunkResult.isLastChunk()) {
                    finalResult.set(chunkResult);
                    completionLatch.countDown();
                }
            }

            @Override
            public void onFailure(Exception e) {
                fail("Should not fail: " + e.getMessage());
            }
        };

        ChunkProcessingContext context = new ChunkProcessingContext(totalChunks, listener);

        // Act - simulate concurrent chunk processing
        for (int i = 0; i < totalChunks; i++) {
            final int chunkIndex = i;
            new Thread(() -> {
                try {
                    startLatch.await();
                    if (chunkIndex % 3 == 0) {
                        context.handleFailure(chunkIndex, new RuntimeException("Error" + chunkIndex));
                    } else {
                        context.handleSuccess(chunkIndex, "response" + chunkIndex);
                    }
                } catch (InterruptedException e) {
                    fail("Thread interrupted");
                }
            }).start();
        }

        // Start all threads
        startLatch.countDown();

        // Assert
        assertTrue("Should complete within timeout", completionLatch.await(10, TimeUnit.SECONDS));
        ChunkResult result = finalResult.get();
        assertNotNull(result);
        assertTrue(result.isLastChunk());
        assertEquals(totalChunks, result.getTotalChunks());

        // Should have some successes and some failures
        assertTrue("Should have successful chunks", result.getSuccessfulChunksCount() > 0);
        assertTrue("Should have failed chunks", result.getFailedChunksCount() > 0);
        assertEquals(totalChunks, result.getSuccessfulChunksCount() + result.getFailedChunksCount());
    }

    public void testSingleChunkCompletion() throws Exception {
        // Arrange
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<ChunkResult> result = new AtomicReference<>();

        ActionListener<ChunkResult> listener = new ActionListener<ChunkResult>() {
            @Override
            public void onResponse(ChunkResult chunkResult) {
                result.set(chunkResult);
                latch.countDown();
            }

            @Override
            public void onFailure(Exception e) {
                fail("Should not fail: " + e.getMessage());
            }
        };

        ChunkProcessingContext context = new ChunkProcessingContext(1, listener);

        // Act
        context.handleSuccess(0, "single response");

        // Assert
        assertTrue("Should complete within timeout", latch.await(5, TimeUnit.SECONDS));
        ChunkResult chunkResult = result.get();
        assertNotNull(chunkResult);
        assertTrue(chunkResult.isLastChunk());
        assertEquals(1, chunkResult.getTotalChunks());
        assertEquals(1, chunkResult.getSuccessfulChunksCount());
        assertEquals(0, chunkResult.getFailedChunksCount());
    }

    public void testExceptionInCompleteChunk() throws Exception {
        // Arrange
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<Exception> error = new AtomicReference<>();

        ActionListener<ChunkResult> listener = new ActionListener<ChunkResult>() {
            @Override
            public void onResponse(ChunkResult chunkResult) {
                throw new RuntimeException("Listener exception");
            }

            @Override
            public void onFailure(Exception e) {
                error.set(e);
                latch.countDown();
            }
        };

        ChunkProcessingContext context = new ChunkProcessingContext(1, listener);

        // Act
        context.handleSuccess(0, "response");

        // Assert
        assertTrue("Should complete within timeout", latch.await(5, TimeUnit.SECONDS));
        assertNotNull(error.get());
        assertTrue(error.get().getMessage().contains("Listener exception"));
    }
}
