/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */
package org.opensearch.searchrelevance.ml;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import org.opensearch.searchrelevance.ml.connector.ConnectorType;
import org.opensearch.test.OpenSearchTestCase;

public class AdaptiveRateLimiterTests extends OpenSearchTestCase {

    private AdaptiveRateLimiter rateLimiter;

    @Override
    public void setUp() throws Exception {
        super.setUp();
        rateLimiter = new AdaptiveRateLimiter();
    }

    @Override
    public void tearDown() throws Exception {
        if (rateLimiter != null) {
            rateLimiter.shutdown();
        }
        super.tearDown();
    }

    public void testNoRateLimitWhenZero() throws Exception {
        long startTime = System.currentTimeMillis();

        CompletableFuture<Void> future = rateLimiter.applyRateLimit("test-model", ConnectorType.OPENAI, 0L);
        future.get(1, TimeUnit.SECONDS);

        long elapsed = System.currentTimeMillis() - startTime;
        assertTrue("Should complete immediately with 0 rate limit", elapsed < 100);
    }

    public void testRateLimitApplied() throws Exception {
        // Test that non-zero rate limit creates a delay future (don't wait for it to avoid thread leaks)
        CompletableFuture<Void> future = rateLimiter.applyRateLimit("test-model", ConnectorType.CLAUDE, 100L);
        assertNotNull("Should return a future for rate limiting", future);
        assertFalse("Future should not be completed immediately for non-zero rate limit", future.isDone());
    }

    public void testSuccessRecording() {
        // Should not throw exception
        rateLimiter.recordResult("test-model", ConnectorType.OPENAI, true, null);
    }

    public void testRateLimitErrorRecording() {
        Exception rateLimitError = new RuntimeException("Rate limit exceeded");
        rateLimiter.recordResult("test-model", ConnectorType.CLAUDE, false, rateLimitError);
    }

    public void testModelUnavailableError() {
        Exception unavailableError = new RuntimeException("Model not found");
        rateLimiter.recordResult("test-model", ConnectorType.COHERE, false, unavailableError);
    }

    public void testCircuitBreakerAfterManyFailures() {
        String modelId = "failing-model";
        ConnectorType connectorType = ConnectorType.CLAUDE;

        // Record many failures to trigger circuit breaker
        for (int i = 0; i < 15; i++) {
            rateLimiter.recordResult(modelId, connectorType, false, new RuntimeException("Service unavailable"));
        }

        // Test that circuit breaker creates a future (don't wait to avoid thread leaks)
        CompletableFuture<Void> future = rateLimiter.applyRateLimit(modelId, connectorType, 100L);
        assertNotNull("Should return a future even with circuit breaker", future);
    }
}
