/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */
package org.opensearch.searchrelevance.dao;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.apache.lucene.search.TotalHits;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.opensearch.action.index.IndexResponse;
import org.opensearch.action.search.SearchResponse;
import org.opensearch.core.action.ActionListener;
import org.opensearch.core.common.bytes.BytesArray;
import org.opensearch.core.xcontent.XContentBuilder;
import org.opensearch.search.SearchHit;
import org.opensearch.search.SearchHits;
import org.opensearch.search.builder.SearchSourceBuilder;
import org.opensearch.searchrelevance.indices.SearchRelevanceIndices;
import org.opensearch.searchrelevance.indices.SearchRelevanceIndicesManager;
import org.opensearch.searchrelevance.model.RemoteSearchFailure;

public class RemoteSearchFailureDaoTests extends org.apache.lucene.tests.util.LuceneTestCase {

    @Mock
    private SearchRelevanceIndicesManager indicesManager;

    private RemoteSearchFailureDao failureDao;

    // @Before
    public void setUp() throws Exception {
        super.setUp();
        MockitoAnnotations.openMocks(this);
        failureDao = new RemoteSearchFailureDao(indicesManager);
    }

    public void testRecordFailure() throws InterruptedException {
        // Create test failure
        RemoteSearchFailure failure = new RemoteSearchFailure(
            "failure-1",
            "config-1",
            "experiment-1",
            "test query",
            "test query text",
            "NETWORK_ERROR",
            "Connection timeout",
            Instant.now().toString(),
            "FAILED"
        );

        // Mock successful index response
        IndexResponse mockResponse = mock(IndexResponse.class);
        when(mockResponse.getId()).thenReturn("failure-1");

        // Stub indices manager upsert call
        doAnswer(invocation -> {
            @SuppressWarnings("unchecked")
            ActionListener<IndexResponse> listener = invocation.getArgument(3);
            listener.onResponse(mockResponse);
            return null;
        }).when(indicesManager)
            .updateDoc(
                eq("failure-1"),
                any(XContentBuilder.class),
                eq(SearchRelevanceIndices.REMOTE_SEARCH_FAILURE),
                any(ActionListener.class)
            );

        // Test record operation
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<IndexResponse> result = new AtomicReference<>();
        AtomicReference<Exception> error = new AtomicReference<>();

        failureDao.recordFailure(failure, new ActionListener<IndexResponse>() {
            @Override
            public void onResponse(IndexResponse response) {
                result.set(response);
                latch.countDown();
            }

            @Override
            public void onFailure(Exception e) {
                error.set(e);
                latch.countDown();
            }
        });

        assertTrue(latch.await(5, TimeUnit.SECONDS));
        assertNull(error.get());
        assertNotNull(result.get());

        // Verify indices manager call
        verify(indicesManager, times(1)).updateDoc(
            eq("failure-1"),
            any(XContentBuilder.class),
            eq(SearchRelevanceIndices.REMOTE_SEARCH_FAILURE),
            any(ActionListener.class)
        );
    }

    public void testGetRecentFailures() throws InterruptedException {
        String configurationId = "config-1";

        // Prepare two real hits with source
        SearchHit hit1 = new SearchHit(1, "failure-1", Map.of(), Map.of());
        hit1.sourceRef(
            new BytesArray(
                "{\"id\":\"failure-1\",\"remoteConfigId\":\"config-1\",\"errorType\":\"CONNECTION_TIMEOUT\",\"errorMessage\":\"Timeout\",\"timestamp\":\"2023-01-01T00:00:00Z\",\"status\":\"FAILED\"}"
            )
        );
        SearchHit hit2 = new SearchHit(2, "failure-2", Map.of(), Map.of());
        hit2.sourceRef(
            new BytesArray(
                "{\"id\":\"failure-2\",\"remoteConfigId\":\"config-1\",\"errorType\":\"AUTH_FAILURE\",\"errorMessage\":\"Unauthorized\",\"timestamp\":\"2023-01-01T01:00:00Z\",\"status\":\"FAILED\"}"
            )
        );

        SearchHits searchHits = new SearchHits(new SearchHit[] { hit1, hit2 }, new TotalHits(2, TotalHits.Relation.EQUAL_TO), 1.0f);

        SearchResponse mockResponse = mock(SearchResponse.class);
        when(mockResponse.getHits()).thenReturn(searchHits);

        // Stub list docs call
        doAnswer(invocation -> {
            @SuppressWarnings("unchecked")
            ActionListener<SearchResponse> listener = invocation.getArgument(2);
            listener.onResponse(mockResponse);
            return null;
        }).when(indicesManager)
            .listDocsBySearchRequest(
                any(SearchSourceBuilder.class),
                eq(SearchRelevanceIndices.REMOTE_SEARCH_FAILURE),
                any(ActionListener.class)
            );

        // Test get recent failures
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<List<RemoteSearchFailure>> result = new AtomicReference<>();
        AtomicReference<Exception> error = new AtomicReference<>();

        failureDao.getRecentFailures(configurationId, 10, new ActionListener<List<RemoteSearchFailure>>() {
            @Override
            public void onResponse(List<RemoteSearchFailure> failures) {
                result.set(failures);
                latch.countDown();
            }

            @Override
            public void onFailure(Exception e) {
                error.set(e);
                latch.countDown();
            }
        });

        assertTrue(latch.await(5, TimeUnit.SECONDS));
        assertNull(error.get());
        assertNotNull(result.get());
        assertEquals(2, result.get().size());

        // Verify call
        verify(indicesManager, times(1)).listDocsBySearchRequest(
            any(SearchSourceBuilder.class),
            eq(SearchRelevanceIndices.REMOTE_SEARCH_FAILURE),
            any(ActionListener.class)
        );
    }

    public void testGetFailureStats() throws InterruptedException {
        String configurationId = "config-1";
        int hours = 24;

        // Create search response with total hits = 10
        SearchHits searchHits = new SearchHits(new SearchHit[0], new TotalHits(10L, TotalHits.Relation.EQUAL_TO), 1.0f);
        SearchResponse mockResponse = mock(SearchResponse.class);
        when(mockResponse.getHits()).thenReturn(searchHits);
        when(mockResponse.getAggregations()).thenReturn(null);

        // Stub list docs call
        doAnswer(invocation -> {
            @SuppressWarnings("unchecked")
            ActionListener<SearchResponse> listener = invocation.getArgument(2);
            listener.onResponse(mockResponse);
            return null;
        }).when(indicesManager)
            .listDocsBySearchRequest(
                any(SearchSourceBuilder.class),
                eq(SearchRelevanceIndices.REMOTE_SEARCH_FAILURE),
                any(ActionListener.class)
            );

        // Test get failure stats
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<Map<String, Object>> result = new AtomicReference<>();
        AtomicReference<Exception> error = new AtomicReference<>();

        failureDao.getFailureStats(configurationId, hours, new ActionListener<Map<String, Object>>() {
            @Override
            public void onResponse(Map<String, Object> stats) {
                result.set(stats);
                latch.countDown();
            }

            @Override
            public void onFailure(Exception e) {
                error.set(e);
                latch.countDown();
            }
        });

        assertTrue(latch.await(5, TimeUnit.SECONDS));
        assertNull(error.get());
        assertNotNull(result.get());
        assertTrue(result.get().containsKey("total_failures"));
        assertTrue(result.get().containsKey("time_range_hours"));
        assertTrue(result.get().containsKey("configuration_id"));
        assertTrue(result.get().containsKey("aggregations"));
        assertEquals(10L, result.get().get("total_failures"));
        assertEquals(hours, result.get().get("time_range_hours"));
        assertEquals(configurationId, result.get().get("configuration_id"));
    }

    public void testHasExcessiveFailures() throws InterruptedException {
        String configurationId = "config-1";
        int maxFailures = 5;
        int timeWindowMinutes = 30;

        // Create search response indicating excessive failures
        SearchHits searchHits = new SearchHits(new SearchHit[0], new TotalHits(7L, TotalHits.Relation.EQUAL_TO), 1.0f);
        SearchResponse mockResponse = mock(SearchResponse.class);
        when(mockResponse.getHits()).thenReturn(searchHits);

        // Stub list docs call
        doAnswer(invocation -> {
            @SuppressWarnings("unchecked")
            ActionListener<SearchResponse> listener = invocation.getArgument(2);
            listener.onResponse(mockResponse);
            return null;
        }).when(indicesManager)
            .listDocsBySearchRequest(
                any(SearchSourceBuilder.class),
                eq(SearchRelevanceIndices.REMOTE_SEARCH_FAILURE),
                any(ActionListener.class)
            );

        // Test excessive failures check
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<Boolean> result = new AtomicReference<>();
        AtomicReference<Exception> error = new AtomicReference<>();

        failureDao.hasExcessiveFailures(configurationId, maxFailures, timeWindowMinutes, new ActionListener<Boolean>() {
            @Override
            public void onResponse(Boolean hasExcessive) {
                result.set(hasExcessive);
                latch.countDown();
            }

            @Override
            public void onFailure(Exception e) {
                error.set(e);
                latch.countDown();
            }
        });

        assertTrue(latch.await(5, TimeUnit.SECONDS));
        assertNull(error.get());
        assertNotNull(result.get());
        assertTrue(result.get()); // Should be true since 7 > 5
    }

    public void testHasExcessiveFailuresWithinLimit() throws InterruptedException {
        String configurationId = "config-1";
        int maxFailures = 5;
        int timeWindowMinutes = 30;

        // Create search response indicating failures within limit
        SearchHits searchHits = new SearchHits(new SearchHit[0], new TotalHits(3L, TotalHits.Relation.EQUAL_TO), 1.0f);
        SearchResponse mockResponse = mock(SearchResponse.class);
        when(mockResponse.getHits()).thenReturn(searchHits);

        // Stub list docs call
        doAnswer(invocation -> {
            @SuppressWarnings("unchecked")
            ActionListener<SearchResponse> listener = invocation.getArgument(2);
            listener.onResponse(mockResponse);
            return null;
        }).when(indicesManager)
            .listDocsBySearchRequest(
                any(SearchSourceBuilder.class),
                eq(SearchRelevanceIndices.REMOTE_SEARCH_FAILURE),
                any(ActionListener.class)
            );

        // Test excessive failures check
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<Boolean> result = new AtomicReference<>();
        AtomicReference<Exception> error = new AtomicReference<>();

        failureDao.hasExcessiveFailures(configurationId, maxFailures, timeWindowMinutes, new ActionListener<Boolean>() {
            @Override
            public void onResponse(Boolean hasExcessive) {
                result.set(hasExcessive);
                latch.countDown();
            }

            @Override
            public void onFailure(Exception e) {
                error.set(e);
                latch.countDown();
            }
        });

        assertTrue(latch.await(5, TimeUnit.SECONDS));
        assertNull(error.get());
        assertNotNull(result.get());
        assertFalse(result.get()); // Should be false since 3 < 5
    }

    public void testCleanupOldFailures() throws InterruptedException {
        int retentionDays = 30;

        // Create search response with old failures
        SearchHit hit1 = new SearchHit(1, "old-failure-1", Map.of(), Map.of());
        SearchHit hit2 = new SearchHit(2, "old-failure-2", Map.of(), Map.of());
        SearchHits searchHits = new SearchHits(new SearchHit[] { hit1, hit2 }, new TotalHits(2, TotalHits.Relation.EQUAL_TO), 1.0f);

        SearchResponse mockResponse = mock(SearchResponse.class);
        when(mockResponse.getHits()).thenReturn(searchHits);

        // Stub list docs call
        doAnswer(invocation -> {
            @SuppressWarnings("unchecked")
            ActionListener<SearchResponse> listener = invocation.getArgument(2);
            listener.onResponse(mockResponse);
            return null;
        }).when(indicesManager)
            .listDocsBySearchRequest(
                any(SearchSourceBuilder.class),
                eq(SearchRelevanceIndices.REMOTE_SEARCH_FAILURE),
                any(ActionListener.class)
            );

        // Test cleanup operation
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<Integer> result = new AtomicReference<>();
        AtomicReference<Exception> error = new AtomicReference<>();

        failureDao.cleanupOldFailures(retentionDays, new ActionListener<Integer>() {
            @Override
            public void onResponse(Integer deletedCount) {
                result.set(deletedCount);
                latch.countDown();
            }

            @Override
            public void onFailure(Exception e) {
                error.set(e);
                latch.countDown();
            }
        });

        assertTrue(latch.await(5, TimeUnit.SECONDS));
        assertNull(error.get());
        assertNotNull(result.get());
        assertEquals(Integer.valueOf(2), result.get()); // Should find 2 old failures
    }

    public void testGetErrorPatterns() throws InterruptedException {
        String configurationId = "config-1";
        int days = 7;

        // Create search response with aggregations
        SearchHits searchHits = new SearchHits(new SearchHit[0], new TotalHits(15L, TotalHits.Relation.EQUAL_TO), 1.0f);
        SearchResponse mockResponse = mock(SearchResponse.class);
        when(mockResponse.getHits()).thenReturn(searchHits);
        when(mockResponse.getAggregations()).thenReturn(null);

        // Stub list docs call
        doAnswer(invocation -> {
            @SuppressWarnings("unchecked")
            ActionListener<SearchResponse> listener = invocation.getArgument(2);
            listener.onResponse(mockResponse);
            return null;
        }).when(indicesManager)
            .listDocsBySearchRequest(
                any(SearchSourceBuilder.class),
                eq(SearchRelevanceIndices.REMOTE_SEARCH_FAILURE),
                any(ActionListener.class)
            );

        // Test get error patterns
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<Map<String, Object>> result = new AtomicReference<>();
        AtomicReference<Exception> error = new AtomicReference<>();

        failureDao.getErrorPatterns(configurationId, days, new ActionListener<Map<String, Object>>() {
            @Override
            public void onResponse(Map<String, Object> patterns) {
                result.set(patterns);
                latch.countDown();
            }

            @Override
            public void onFailure(Exception e) {
                error.set(e);
                latch.countDown();
            }
        });

        assertTrue(latch.await(5, TimeUnit.SECONDS));
        assertNull(error.get());
        assertNotNull(result.get());
        assertTrue(result.get().containsKey("total_failures"));
        assertTrue(result.get().containsKey("analysis_period_days"));
        assertTrue(result.get().containsKey("configuration_id"));
        assertTrue(result.get().containsKey("error_analysis"));
        assertEquals(15L, result.get().get("total_failures"));
        assertEquals(days, result.get().get("analysis_period_days"));
        assertEquals(configurationId, result.get().get("configuration_id"));
    }

    public void testGetErrorPatternsAllConfigurations() throws InterruptedException {
        int days = 7;

        // Create search response with aggregations
        SearchHits searchHits = new SearchHits(new SearchHit[0], new TotalHits(25L, TotalHits.Relation.EQUAL_TO), 1.0f);
        SearchResponse mockResponse = mock(SearchResponse.class);
        when(mockResponse.getHits()).thenReturn(searchHits);
        when(mockResponse.getAggregations()).thenReturn(null);

        // Stub list docs call
        doAnswer(invocation -> {
            @SuppressWarnings("unchecked")
            ActionListener<SearchResponse> listener = invocation.getArgument(2);
            listener.onResponse(mockResponse);
            return null;
        }).when(indicesManager)
            .listDocsBySearchRequest(
                any(SearchSourceBuilder.class),
                eq(SearchRelevanceIndices.REMOTE_SEARCH_FAILURE),
                any(ActionListener.class)
            );

        // Test get error patterns for all configurations (null configurationId)
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<Map<String, Object>> result = new AtomicReference<>();
        AtomicReference<Exception> error = new AtomicReference<>();

        failureDao.getErrorPatterns(null, days, new ActionListener<Map<String, Object>>() {
            @Override
            public void onResponse(Map<String, Object> patterns) {
                result.set(patterns);
                latch.countDown();
            }

            @Override
            public void onFailure(Exception e) {
                error.set(e);
                latch.countDown();
            }
        });

        assertTrue(latch.await(5, TimeUnit.SECONDS));
        assertNull(error.get());
        assertNotNull(result.get());
        assertTrue(result.get().containsKey("total_failures"));
        assertTrue(result.get().containsKey("analysis_period_days"));
        assertTrue(result.get().containsKey("configuration_id"));
        assertTrue(result.get().containsKey("error_analysis"));
        assertEquals(25L, result.get().get("total_failures"));
        assertEquals(days, result.get().get("analysis_period_days"));
        assertNull(result.get().get("configuration_id")); // Should be null for all configurations
    }
}
