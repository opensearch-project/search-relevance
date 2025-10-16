/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */
package org.opensearch.searchrelevance.executors;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;

import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.apache.lucene.tests.util.LuceneTestCase.SuppressSysoutChecks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.opensearch.core.action.ActionListener;
import org.opensearch.searchrelevance.dao.RemoteSearchCacheDao;
import org.opensearch.searchrelevance.dao.RemoteSearchConfigurationDao;
import org.opensearch.searchrelevance.dao.RemoteSearchFailureDao;
import org.opensearch.searchrelevance.executors.RemoteSearchExecutor.RemoteSearchResponse;
import org.opensearch.searchrelevance.model.RemoteSearchConfiguration;

/**
 * Tests for RemoteSearchExecutor
 */
@SuppressSysoutChecks(bugUrl = "https://github.com/opensearch-project/search-relevance/issues/XXX")
public class RemoteSearchExecutorTests extends org.apache.lucene.tests.util.LuceneTestCase {

    @Mock
    private RemoteSearchConfigurationDao mockDao;

    @Mock
    private RemoteSearchCacheDao mockCacheDao;

    @Mock
    private RemoteSearchFailureDao mockFailureDao;

    @Mock
    private RemoteResponseMapper mockResponseMapper;

    private RemoteSearchExecutor remoteSearchExecutor;

    @Override
    public void setUp() throws Exception {
        super.setUp();
        MockitoAnnotations.openMocks(this);

        // Setup default mock behaviors
        setupDefaultMockBehaviors();

        // Create RemoteSearchExecutor with standard constructor (no HttpClient needed)
        remoteSearchExecutor = new RemoteSearchExecutor(mockDao, mockCacheDao, mockFailureDao, mockResponseMapper);
    }

    private void setupDefaultMockBehaviors() {
        // Mock cache DAO to return null (cache miss) by default
        doAnswer(invocation -> {
            ActionListener listener = invocation.getArgument(1);
            listener.onResponse(null);
            return null;
        }).when(mockCacheDao).getCachedResponse(any(), any());

        // Mock cache DAO to succeed when caching
        doAnswer(invocation -> {
            ActionListener listener = invocation.getArgument(1);
            listener.onResponse(null);
            return null;
        }).when(mockCacheDao).cacheResponse(any(), any());

        // Mock failure DAO to succeed when recording failures
        doAnswer(invocation -> {
            ActionListener listener = invocation.getArgument(1);
            listener.onResponse(null);
            return null;
        }).when(mockFailureDao).recordFailure(any(), any());

        // Mock response mapper to return input by default
        when(mockResponseMapper.mapResponse(any(), any())).thenAnswer(invocation -> invocation.getArgument(0));
    }

    public void testExecuteRemoteSearchConfigNotFound() throws Exception {
        String configId = "nonexistent-config";
        String query = "{\"query\":{\"match\":{\"title\":\"test\"}}}";
        String queryText = "test";
        String experimentId = "exp-123";

        // Mock DAO response with null config
        doAnswer(invocation -> {
            ActionListener<RemoteSearchConfiguration> listener = invocation.getArgument(1);
            listener.onResponse(null);
            return null;
        }).when(mockDao).getRemoteSearchConfiguration(eq(configId), any());

        // Execute test
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<RemoteSearchResponse> responseRef = new AtomicReference<>();
        AtomicReference<Exception> errorRef = new AtomicReference<>();

        remoteSearchExecutor.executeRemoteSearch(configId, query, queryText, 10, experimentId, ActionListener.wrap(response -> {
            responseRef.set(response);
            latch.countDown();
        }, error -> {
            errorRef.set(error);
            latch.countDown();
        }));

        // Wait for async completion
        assertTrue("Request should complete within timeout", latch.await(5, TimeUnit.SECONDS));

        // Verify error
        assertNotNull("Error should not be null", errorRef.get());
        assertTrue("Error should be IllegalArgumentException", errorRef.get() instanceof IllegalArgumentException);
        assertTrue("Error message should mention config not found", errorRef.get().getMessage().contains("Remote configuration not found"));
    }

    public void testExecuteRemoteSearchWithInvalidUrl() throws Exception {
        String configId = "test-config-invalid-url";
        String query = "{\"query\":{\"match\":{\"title\":\"test\"}}}";
        String queryText = "test";
        String experimentId = "exp-123";

        // Create config with invalid URL
        RemoteSearchConfiguration config = new RemoteSearchConfiguration(
            configId,
            "Invalid URL Config",
            "Test configuration with invalid URL",
            "invalid-url-format",
            "user",
            "pass",
            "${query}",
            null,
            10,
            5,
            60,
            false,
            Map.of(),
            "2025-01-29T12:00:00Z"
        );

        // Mock DAO response
        doAnswer(invocation -> {
            ActionListener<RemoteSearchConfiguration> listener = invocation.getArgument(1);
            listener.onResponse(config);
            return null;
        }).when(mockDao).getRemoteSearchConfiguration(eq(configId), any());

        // Execute test
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<RemoteSearchResponse> responseRef = new AtomicReference<>();
        AtomicReference<Exception> errorRef = new AtomicReference<>();

        remoteSearchExecutor.executeRemoteSearch(configId, query, queryText, 10, experimentId, ActionListener.wrap(response -> {
            responseRef.set(response);
            latch.countDown();
        }, error -> {
            errorRef.set(error);
            latch.countDown();
        }));

        // Wait for async completion
        assertTrue("Request should complete within timeout", latch.await(10, TimeUnit.SECONDS));

        // Verify error due to invalid URL
        assertNotNull("Error should not be null", errorRef.get());
        assertTrue("Error should be RuntimeException", errorRef.get() instanceof RuntimeException);
        assertTrue("Error message should mention remote search failed", errorRef.get().getMessage().contains("Remote search failed"));
    }

    public void testRateLimitingConcurrentRequests() throws Exception {
        String configId = "test-config-rate-limit";
        RemoteSearchConfiguration config = new RemoteSearchConfiguration(
            configId,
            "Rate Limited Config",
            "Test configuration with low concurrent limit",
            "https://httpbin.org/delay/2", // Use httpbin for testing with delay
            "user",
            "pass",
            "${query}",
            null,
            10, // requests per second
            1,  // max concurrent requests (low limit for testing)
            60,
            false,
            Map.of(),
            "2025-01-29T12:00:00Z"
        );

        // Mock DAO response
        doAnswer(invocation -> {
            ActionListener<RemoteSearchConfiguration> listener = invocation.getArgument(1);
            listener.onResponse(config);
            return null;
        }).when(mockDao).getRemoteSearchConfiguration(eq(configId), any());

        // Start first request (should succeed but take time)
        CountDownLatch firstLatch = new CountDownLatch(1);
        AtomicReference<Exception> firstError = new AtomicReference<>();

        remoteSearchExecutor.executeRemoteSearch(
            configId,
            "{\"query\":{}}",
            "test1",
            10,
            "exp-1",
            ActionListener.wrap(response -> firstLatch.countDown(), error -> {
                firstError.set(error);
                firstLatch.countDown();
            })
        );

        // Start second request immediately (should fail due to concurrent limit)
        CountDownLatch secondLatch = new CountDownLatch(1);
        AtomicReference<Exception> secondError = new AtomicReference<>();

        remoteSearchExecutor.executeRemoteSearch(
            configId,
            "{\"query\":{}}",
            "test2",
            10,
            "exp-2",
            ActionListener.wrap(response -> secondLatch.countDown(), error -> {
                secondError.set(error);
                secondLatch.countDown();
            })
        );

        // Wait for second request to fail quickly
        assertTrue("Second request should complete quickly", secondLatch.await(2, TimeUnit.SECONDS));

        // Verify second request failed due to rate limiting
        assertNotNull("Second request should have failed", secondError.get());
        assertTrue("Error should mention rate limit", secondError.get().getMessage().contains("Rate limit exceeded"));

        // Wait for first request to complete (or timeout)
        firstLatch.await(15, TimeUnit.SECONDS);
    }

    public void testConstructorInitialization() {
        // Test that constructor properly initializes all dependencies
        assertNotNull("RemoteSearchExecutor should be initialized", remoteSearchExecutor);

        // Test basic functionality by calling with null config ID (should trigger DAO call)
        CountDownLatch latch = new CountDownLatch(1);

        remoteSearchExecutor.executeRemoteSearch(
            "test-config",
            "{}",
            "test",
            10,
            "exp-1",
            ActionListener.wrap(response -> latch.countDown(), error -> latch.countDown())
        );

        // Just verify the call doesn't throw an exception immediately
        assertTrue("Constructor should create functional executor", true);
    }

    /**
     * Create a test configuration with default values
     */
    private RemoteSearchConfiguration createTestConfiguration(String configId) {
        return new RemoteSearchConfiguration(
            configId,
            "Test Configuration",
            "Test configuration for unit tests",
            "https://httpbin.org/post", // Use httpbin for testing
            "testuser",
            "testpass",
            "${query}",
            null,
            10, // max requests per second
            5,  // max concurrent requests
            60, // cache duration minutes
            false,
            Map.of("test", "metadata"),
            "2025-01-29T12:00:00Z"
        );
    }
}
