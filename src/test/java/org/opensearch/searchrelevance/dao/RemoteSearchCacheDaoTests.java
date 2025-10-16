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
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.apache.lucene.search.TotalHits;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.opensearch.action.delete.DeleteResponse;
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
import org.opensearch.searchrelevance.model.RemoteSearchCache;

public class RemoteSearchCacheDaoTests extends org.apache.lucene.tests.util.LuceneTestCase {

    @Mock
    private SearchRelevanceIndicesManager indicesManager;

    private RemoteSearchCacheDao cacheDao;

    // @Before
    public void setUp() throws Exception {
        super.setUp();
        MockitoAnnotations.openMocks(this);
        cacheDao = new RemoteSearchCacheDao(indicesManager);
    }

    public void testStoreCache() throws InterruptedException {
        // Create test cache entry
        RemoteSearchCache cache = new RemoteSearchCache(
            "test-cache-id",
            "config-1",
            "test-query-hash",
            "test query",
            "{\"response\": \"data\"}",
            "{\"mapped\": \"response\"}",
            Instant.now().toEpochMilli(),
            Instant.now().toEpochMilli() + (60L * 60 * 1000) // 60 minutes from now
        );

        // Mock successful index response
        IndexResponse mockResponse = mock(IndexResponse.class);
        when(mockResponse.getId()).thenReturn("test-cache-id");

        // Stub indices manager update call
        doAnswer(invocation -> {
            @SuppressWarnings("unchecked")
            ActionListener<IndexResponse> listener = invocation.getArgument(3);
            listener.onResponse(mockResponse);
            return null;
        }).when(indicesManager)
            .updateDocEfficient(
                eq("test-cache-id"),
                any(XContentBuilder.class),
                eq(SearchRelevanceIndices.REMOTE_SEARCH_CACHE),
                any(ActionListener.class)
            );

        // Test store operation
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<IndexResponse> result = new AtomicReference<>();
        AtomicReference<Exception> error = new AtomicReference<>();

        cacheDao.storeCache(cache, new ActionListener<IndexResponse>() {
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

        // Verify indices manager was called with correct arguments
        verify(indicesManager, times(1)).updateDocEfficient(
            eq("test-cache-id"),
            any(XContentBuilder.class),
            eq(SearchRelevanceIndices.REMOTE_SEARCH_CACHE),
            any(ActionListener.class)
        );
    }

    public void testGetCacheHit() throws InterruptedException {
        String cacheKey = "test-cache-key";

        // Create test cache data
        Map<String, Object> sourceMap = Map.of(
            RemoteSearchCache.ID_FIELD,
            cacheKey,
            RemoteSearchCache.CONFIGURATION_ID_FIELD,
            "config-1",
            RemoteSearchCache.QUERY_HASH_FIELD,
            "query-hash",
            RemoteSearchCache.QUERY_TEXT_FIELD,
            "test query",
            RemoteSearchCache.RAW_RESPONSE_FIELD,
            "{\"response\": \"data\"}",
            RemoteSearchCache.MAPPED_RESPONSE_FIELD,
            "{\"mapped\": \"response\"}",
            RemoteSearchCache.TIMESTAMP_FIELD,
            Instant.now().toEpochMilli(),
            RemoteSearchCache.EXPIRATION_TIMESTAMP,
            Instant.now().toEpochMilli() + (60L * 60 * 1000)
        );

        // Prepare SearchResponse with one real hit
        String json = "{\"cacheKey\":\""
            + cacheKey
            + "\",\"remoteConfigId\":\"config-1\",\"query\":\"query-hash\",\"queryText\":\"test query\",\"cachedResponse\":\"{\\\"response\\\": \\\"data\\\"}\",\"mappedResponse\":\"{\\\"mapped\\\": \\\"response\\\"}\",\"cacheTimestamp\":"
            + sourceMap.get(RemoteSearchCache.TIMESTAMP_FIELD)
            + ",\"expirationTimestamp\":"
            + sourceMap.get(RemoteSearchCache.EXPIRATION_TIMESTAMP)
            + "}";
        SearchHit hit = new SearchHit(1, cacheKey, Map.of(), Map.of());
        hit.sourceRef(new BytesArray(json));
        SearchHits searchHits = new SearchHits(new SearchHit[] { hit }, new TotalHits(1, TotalHits.Relation.EQUAL_TO), 1.0f);
        SearchResponse mockResponse = mock(SearchResponse.class);
        when(mockResponse.getHits()).thenReturn(searchHits);

        // Stub indices manager get by id
        doAnswer(invocation -> {
            @SuppressWarnings("unchecked")
            ActionListener<SearchResponse> listener = invocation.getArgument(2);
            listener.onResponse(mockResponse);
            return null;
        }).when(indicesManager).getDocByDocId(eq(cacheKey), eq(SearchRelevanceIndices.REMOTE_SEARCH_CACHE), any(ActionListener.class));

        // Test get operation
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<RemoteSearchCache> result = new AtomicReference<>();
        AtomicReference<Exception> error = new AtomicReference<>();

        cacheDao.getCache(cacheKey, new ActionListener<RemoteSearchCache>() {
            @Override
            public void onResponse(RemoteSearchCache cache) {
                result.set(cache);
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
        assertEquals(cacheKey, result.get().getId());
        assertEquals("config-1", result.get().getConfigurationId());
    }

    public void testGetCacheMiss() throws InterruptedException {
        String cacheKey = "non-existent-key";

        // Prepare mocked SearchResponse with zero hits
        SearchHits searchHits = new SearchHits(new SearchHit[0], new TotalHits(0, TotalHits.Relation.EQUAL_TO), 1.0f);
        SearchResponse mockResponse = mock(SearchResponse.class);
        when(mockResponse.getHits()).thenReturn(searchHits);

        // Stub indices manager get by id
        doAnswer(invocation -> {
            @SuppressWarnings("unchecked")
            ActionListener<SearchResponse> listener = invocation.getArgument(2);
            listener.onResponse(mockResponse);
            return null;
        }).when(indicesManager).getDocByDocId(eq(cacheKey), eq(SearchRelevanceIndices.REMOTE_SEARCH_CACHE), any(ActionListener.class));

        // Test get operation
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<RemoteSearchCache> result = new AtomicReference<>();
        AtomicReference<Exception> error = new AtomicReference<>();

        cacheDao.getCache(cacheKey, new ActionListener<RemoteSearchCache>() {
            @Override
            public void onResponse(RemoteSearchCache cache) {
                result.set(cache);
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
        assertNull(result.get()); // Should be null for cache miss
    }

    public void testGetExpiredCache() throws InterruptedException {
        String cacheKey = "expired-cache-key";

        // Create expired cache data (timestamp from 2 hours ago, expiration 1 hour ago)
        Instant expiredTime = Instant.now().minus(2, ChronoUnit.HOURS);
        Instant expirationTime = Instant.now().minus(1, ChronoUnit.HOURS);
        Map<String, Object> sourceMap = Map.of(
            RemoteSearchCache.ID_FIELD,
            cacheKey,
            RemoteSearchCache.CONFIGURATION_ID_FIELD,
            "config-1",
            RemoteSearchCache.QUERY_HASH_FIELD,
            "query-hash",
            RemoteSearchCache.QUERY_TEXT_FIELD,
            "test query",
            RemoteSearchCache.RAW_RESPONSE_FIELD,
            "{\"response\": \"data\"}",
            RemoteSearchCache.MAPPED_RESPONSE_FIELD,
            "{\"mapped\": \"response\"}",
            RemoteSearchCache.TIMESTAMP_FIELD,
            expiredTime.toEpochMilli(),
            RemoteSearchCache.EXPIRATION_TIMESTAMP,
            expirationTime.toEpochMilli()
        );

        // Search response with one expired real hit
        String json = "{\"cacheKey\":\""
            + cacheKey
            + "\",\"remoteConfigId\":\"config-1\",\"query\":\"query-hash\",\"queryText\":\"test query\",\"cachedResponse\":\"{\\\"response\\\": \\\"data\\\"}\",\"mappedResponse\":\"{\\\"mapped\\\": \\\"response\\\"}\",\"cacheTimestamp\":"
            + expiredTime.toEpochMilli()
            + ",\"expirationTimestamp\":"
            + expirationTime.toEpochMilli()
            + "}";
        SearchHit hit = new SearchHit(1, cacheKey, Map.of(), Map.of());
        hit.sourceRef(new BytesArray(json));
        SearchHits searchHits = new SearchHits(new SearchHit[] { hit }, new TotalHits(1, TotalHits.Relation.EQUAL_TO), 1.0f);
        SearchResponse mockGetResponse = mock(SearchResponse.class);
        when(mockGetResponse.getHits()).thenReturn(searchHits);

        // Stub indices manager get by id
        doAnswer(invocation -> {
            @SuppressWarnings("unchecked")
            ActionListener<SearchResponse> listener = invocation.getArgument(2);
            listener.onResponse(mockGetResponse);
            return null;
        }).when(indicesManager).getDocByDocId(eq(cacheKey), eq(SearchRelevanceIndices.REMOTE_SEARCH_CACHE), any(ActionListener.class));

        // Stub delete call
        DeleteResponse mockDeleteResponse = mock(DeleteResponse.class);
        doAnswer(invocation -> {
            @SuppressWarnings("unchecked")
            ActionListener<DeleteResponse> listener = invocation.getArgument(2);
            listener.onResponse(mockDeleteResponse);
            return null;
        }).when(indicesManager).deleteDocByDocId(eq(cacheKey), eq(SearchRelevanceIndices.REMOTE_SEARCH_CACHE), any(ActionListener.class));

        // Test get operation
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<RemoteSearchCache> result = new AtomicReference<>();
        AtomicReference<Exception> error = new AtomicReference<>();

        cacheDao.getCache(cacheKey, new ActionListener<RemoteSearchCache>() {
            @Override
            public void onResponse(RemoteSearchCache cache) {
                result.set(cache);
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
        assertNull(result.get()); // Should be null for expired cache

        // Verify delete was called for cleanup
        verify(indicesManager, times(1)).deleteDocByDocId(
            eq(cacheKey),
            eq(SearchRelevanceIndices.REMOTE_SEARCH_CACHE),
            any(ActionListener.class)
        );
    }

    public void testDeleteCache() throws InterruptedException {
        String cacheKey = "cache-to-delete";

        // Mock successful delete response
        DeleteResponse mockResponse = mock(DeleteResponse.class);

        // Stub indices manager delete call
        doAnswer(invocation -> {
            @SuppressWarnings("unchecked")
            ActionListener<DeleteResponse> listener = invocation.getArgument(2);
            listener.onResponse(mockResponse);
            return null;
        }).when(indicesManager).deleteDocByDocId(eq(cacheKey), eq(SearchRelevanceIndices.REMOTE_SEARCH_CACHE), any(ActionListener.class));

        // Test delete operation
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<DeleteResponse> result = new AtomicReference<>();
        AtomicReference<Exception> error = new AtomicReference<>();

        cacheDao.deleteCache(cacheKey, new ActionListener<DeleteResponse>() {
            @Override
            public void onResponse(DeleteResponse response) {
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

        // Verify delete call
        verify(indicesManager, times(1)).deleteDocByDocId(
            eq(cacheKey),
            eq(SearchRelevanceIndices.REMOTE_SEARCH_CACHE),
            any(ActionListener.class)
        );
    }

    public void testClearCacheForConfiguration() throws InterruptedException {
        String configurationId = "config-to-clear";

        // Create search response with cache entries
        SearchHit hit1 = new SearchHit(1, "cache-1", Map.of(), Map.of());
        SearchHit hit2 = new SearchHit(2, "cache-2", Map.of(), Map.of());
        SearchHits searchHits = new SearchHits(new SearchHit[] { hit1, hit2 }, new TotalHits(2, TotalHits.Relation.EQUAL_TO), 1.0f);

        SearchResponse mockSearchResponse = mock(SearchResponse.class);
        when(mockSearchResponse.getHits()).thenReturn(searchHits);

        // Stub list docs call
        doAnswer(invocation -> {
            @SuppressWarnings("unchecked")
            ActionListener<SearchResponse> listener = invocation.getArgument(2);
            listener.onResponse(mockSearchResponse);
            return null;
        }).when(indicesManager)
            .listDocsBySearchRequest(
                any(SearchSourceBuilder.class),
                eq(SearchRelevanceIndices.REMOTE_SEARCH_CACHE),
                any(ActionListener.class)
            );

        // Stub delete responses
        DeleteResponse mockDeleteResponse = mock(DeleteResponse.class);
        doAnswer(invocation -> {
            @SuppressWarnings("unchecked")
            ActionListener<DeleteResponse> listener = invocation.getArgument(2);
            listener.onResponse(mockDeleteResponse);
            return null;
        }).when(indicesManager).deleteDocByDocId(anyString(), eq(SearchRelevanceIndices.REMOTE_SEARCH_CACHE), any(ActionListener.class));

        // Test clear operation
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<Exception> error = new AtomicReference<>();

        cacheDao.clearCacheForConfiguration(configurationId, new ActionListener<Void>() {
            @Override
            public void onResponse(Void response) {
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

        // Verify search and delete calls
        verify(indicesManager, times(1)).listDocsBySearchRequest(
            any(SearchSourceBuilder.class),
            eq(SearchRelevanceIndices.REMOTE_SEARCH_CACHE),
            any(ActionListener.class)
        );
        verify(indicesManager, times(2)).deleteDocByDocId(
            anyString(),
            eq(SearchRelevanceIndices.REMOTE_SEARCH_CACHE),
            any(ActionListener.class)
        );
    }

    public void testGetCacheStats() throws InterruptedException {
        // Create search response with aggregations
        SearchHits searchHits = new SearchHits(new SearchHit[0], new TotalHits(100L, TotalHits.Relation.EQUAL_TO), 1.0f);
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
                eq(SearchRelevanceIndices.REMOTE_SEARCH_CACHE),
                any(ActionListener.class)
            );

        // Test stats operation
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<Map<String, Object>> result = new AtomicReference<>();
        AtomicReference<Exception> error = new AtomicReference<>();

        cacheDao.getCacheStats(new ActionListener<Map<String, Object>>() {
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
        assertTrue(result.get().containsKey("total_entries"));
        assertTrue(result.get().containsKey("aggregations"));
    }
}
