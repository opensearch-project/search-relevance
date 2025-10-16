/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */
package org.opensearch.searchrelevance.model;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.IOException;

import org.opensearch.common.xcontent.XContentType;
import org.opensearch.core.xcontent.XContentBuilder;

/**
 * Tests for RemoteSearchCache model
 */
public class RemoteSearchCacheTests extends org.apache.lucene.tests.util.LuceneTestCase {

    public void testRemoteSearchCacheCreation() {
        long currentTime = System.currentTimeMillis();
        long expirationTime = currentTime + 3600000; // 1 hour later

        RemoteSearchCache cache = new RemoteSearchCache(
            "cache-key-123",
            "remote-config-1",
            "{\"query\": {\"match\": {\"content\": \"test query\"}}}",
            "test query",
            "{\"hits\": {\"total\": {\"value\": 10}}}",
            "{\"took\": 5, \"hits\": {\"total\": {\"value\": 10}}}",
            currentTime,
            expirationTime
        );

        assertEquals("cache-key-123", cache.getCacheKey());
        assertEquals("remote-config-1", cache.getRemoteConfigId());
        assertEquals("{\"query\": {\"match\": {\"content\": \"test query\"}}}", cache.getQuery());
        assertEquals("test query", cache.getQueryText());
        assertEquals("{\"hits\": {\"total\": {\"value\": 10}}}", cache.getCachedResponse());
        assertEquals("{\"took\": 5, \"hits\": {\"total\": {\"value\": 10}}}", cache.getMappedResponse());
        assertEquals(currentTime, cache.getCacheTimestamp());
        assertEquals(expirationTime, cache.getExpirationTimestamp());
    }

    public void testRemoteSearchCacheToXContent() throws IOException {
        long currentTime = System.currentTimeMillis();
        long expirationTime = currentTime + 3600000;

        RemoteSearchCache cache = new RemoteSearchCache(
            "cache-key-123",
            "remote-config-1",
            "{\"query\": {\"match\": {\"content\": \"test query\"}}}",
            "test query",
            "{\"hits\": {\"total\": {\"value\": 10}}}",
            "{\"took\": 5, \"hits\": {\"total\": {\"value\": 10}}}",
            currentTime,
            expirationTime
        );

        XContentBuilder builder = cache.toXContent(XContentBuilder.builder(XContentType.JSON.xContent()), null);
        assertNotNull(builder);

        String jsonString = builder.toString();
        assertNotNull(jsonString);

        // Verify key fields are present in JSON
        assertTrue(jsonString.contains("cache-key-123"));
        assertTrue(jsonString.contains("remote-config-1"));
        assertTrue(jsonString.contains("test query"));
    }

    public void testCacheExpiration() {
        long currentTime = System.currentTimeMillis();

        // Create expired cache entry
        RemoteSearchCache expiredCache = new RemoteSearchCache(
            "expired-key",
            "config-1",
            "{}",
            "query",
            "{}",
            "{}",
            currentTime - 7200000, // 2 hours ago
            currentTime - 3600000   // 1 hour ago (expired)
        );

        assertTrue("Cache should be expired", expiredCache.isExpired());

        // Create non-expired cache entry
        RemoteSearchCache validCache = new RemoteSearchCache(
            "valid-key",
            "config-1",
            "{}",
            "query",
            "{}",
            "{}",
            currentTime,
            currentTime + 3600000 // 1 hour from now
        );

        assertFalse("Cache should not be expired", validCache.isExpired());
    }

    public void testGenerateCacheKey() {
        String cacheKey1 = RemoteSearchCache.generateCacheKey("config-1", "{\"query\": \"test\"}", "test");
        String cacheKey2 = RemoteSearchCache.generateCacheKey("config-1", "{\"query\": \"test\"}", "test");
        String cacheKey3 = RemoteSearchCache.generateCacheKey("config-2", "{\"query\": \"test\"}", "test");
        String cacheKey4 = RemoteSearchCache.generateCacheKey("config-1", "{\"query\": \"different\"}", "test");

        // Same inputs should generate same cache key
        assertEquals("Same inputs should generate same cache key", cacheKey1, cacheKey2);

        // Different config ID should generate different cache key
        assertFalse("Different config should generate different cache key", cacheKey1.equals(cacheKey3));

        // Different query should generate different cache key
        assertFalse("Different query should generate different cache key", cacheKey1.equals(cacheKey4));

        // Cache keys should be non-null and non-empty
        assertNotNull(cacheKey1);
        assertFalse(cacheKey1.isEmpty());
    }

    public void testRemoteSearchCacheConstants() {
        // Verify field name constants
        assertEquals("cacheKey", RemoteSearchCache.CACHE_KEY);
        assertEquals("remoteConfigId", RemoteSearchCache.REMOTE_CONFIG_ID);
        assertEquals("query", RemoteSearchCache.QUERY);
        assertEquals("queryText", RemoteSearchCache.QUERY_TEXT);
        assertEquals("cachedResponse", RemoteSearchCache.CACHED_RESPONSE);
        assertEquals("mappedResponse", RemoteSearchCache.MAPPED_RESPONSE);
        assertEquals("cacheTimestamp", RemoteSearchCache.CACHE_TIMESTAMP);
        assertEquals("expirationTimestamp", RemoteSearchCache.EXPIRATION_TIMESTAMP);
    }

    public void testRemoteSearchCacheWithNullValues() throws IOException {
        RemoteSearchCache cache = new RemoteSearchCache(
            null, // null cache key
            null, // null remote config ID
            null, // null query
            null, // null query text
            null, // null cached response
            null, // null mapped response
            0,    // zero timestamp
            0     // zero expiration
        );

        // Should handle null values gracefully
        XContentBuilder builder = cache.toXContent(XContentBuilder.builder(XContentType.JSON.xContent()), null);
        assertNotNull(builder);

        String jsonString = builder.toString();
        assertNotNull(jsonString);

        // Should contain empty strings for null values
        assertTrue(jsonString.contains("\"cacheKey\":\"\""));
        assertTrue(jsonString.contains("\"remoteConfigId\":\"\""));
    }
}
