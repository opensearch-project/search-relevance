/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */
package org.opensearch.searchrelevance.scheduler;

import java.util.concurrent.CountDownLatch;

import org.opensearch.test.OpenSearchTestCase;

/**
 * Unit tests for {@link ExperimentCancellationToken}
 */
public class ExperimentCancellationTokenTests extends OpenSearchTestCase {
    public void testCancellationToken() {
        String scheduledExperimentResultId = "test-experiment-result-id";
        ExperimentCancellationToken token = new ExperimentCancellationToken(scheduledExperimentResultId);

        // Verify initial state
        assertEquals(scheduledExperimentResultId, token.getScheduledExperimentResultId());
        assertFalse(token.isCancelled());

        CountDownLatch latch = new CountDownLatch(3);

        // Register 2 cancellation callbacks and verify it is not called immediately
        Runnable callback1 = () -> { latch.countDown(); };

        Runnable callback2 = () -> { latch.countDown(); };

        token.onCancel(callback1);
        token.onCancel(callback2);

        assertEquals(3, latch.getCount());

        // Cancel the token and verify state
        token.cancel();
        assertTrue(token.isCancelled());
        assertEquals(1, latch.getCount());

        Runnable callback3 = () -> { latch.countDown(); };

        // Since the token is already cancelled, this callback should be called immediately
        token.onCancel(callback3);
        assertEquals(0, latch.getCount());
    }
}
