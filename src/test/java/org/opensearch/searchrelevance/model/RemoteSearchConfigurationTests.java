/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */
package org.opensearch.searchrelevance.model;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import org.opensearch.common.xcontent.XContentType;
import org.opensearch.core.xcontent.XContentBuilder;

/**
 * Tests for RemoteSearchConfiguration model
 */
public class RemoteSearchConfigurationTests extends org.apache.lucene.tests.util.LuceneTestCase {

    public void testRemoteSearchConfigurationCreation() {
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("environment", "test");

        RemoteSearchConfiguration config = new RemoteSearchConfiguration(
            "test-config-1",
            "Test Remote Config",
            "Test configuration for remote OpenSearch cluster",
            "https://remote-cluster.example.com:9200",
            "testuser",
            "testpass",
            "{\"query\": {\"match\": {\"content\": \"%SearchText%\"}}}",
            "{\"response_structure\": {\"hits\": {\"total_path\": \"$.hits.total.value\"}}}",
            10,
            5,
            60,
            false,
            metadata,
            "2025-01-29T10:00:00Z"
        );

        assertEquals("test-config-1", config.getId());
        assertEquals("Test Remote Config", config.getName());
        assertEquals("Test configuration for remote OpenSearch cluster", config.getDescription());
        assertEquals("https://remote-cluster.example.com:9200", config.getConnectionUrl());
        assertEquals("testuser", config.getUsername());
        assertEquals("testpass", config.getPassword());
        assertEquals("{\"query\": {\"match\": {\"content\": \"%SearchText%\"}}}", config.getQueryTemplate());
        assertEquals("{\"response_structure\": {\"hits\": {\"total_path\": \"$.hits.total.value\"}}}", config.getResponseTemplate());
        assertEquals(10, config.getMaxRequestsPerSecond());
        assertEquals(5, config.getMaxConcurrentRequests());
        assertEquals(60, config.getCacheDurationMinutes());
        assertEquals(false, config.isRefreshCache());
        assertEquals(metadata, config.getMetadata());
        assertEquals("2025-01-29T10:00:00Z", config.getTimestamp());
    }

    public void testRemoteSearchConfigurationToXContent() throws IOException {
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("environment", "test");

        RemoteSearchConfiguration config = new RemoteSearchConfiguration(
            "test-config-1",
            "Test Remote Config",
            "Test configuration",
            "https://remote-cluster.example.com:9200",
            "testuser",
            "testpass",
            "{\"query\": {\"match\": {\"content\": \"%SearchText%\"}}}",
            "{\"response_structure\": {\"hits\": {\"total_path\": \"$.hits.total.value\"}}}",
            RemoteSearchConfiguration.DEFAULT_MAX_REQUESTS_PER_SECOND,
            RemoteSearchConfiguration.DEFAULT_MAX_CONCURRENT_REQUESTS,
            RemoteSearchConfiguration.DEFAULT_CACHE_DURATION_MINUTES,
            false,
            metadata,
            "2025-01-29T10:00:00Z"
        );

        XContentBuilder builder = config.toXContent(XContentBuilder.builder(XContentType.JSON.xContent()), null);
        assertNotNull(builder);

        String jsonString = builder.toString();
        assertNotNull(jsonString);

        // Verify key fields are present in JSON
        assert (jsonString.contains("test-config-1"));
        assert (jsonString.contains("Test Remote Config"));
        assert (jsonString.contains("https://remote-cluster.example.com:9200"));
        assert (jsonString.contains("testuser"));
        assert (jsonString.contains("%SearchText%"));
    }

    public void testRemoteSearchConfigurationDefaults() {
        RemoteSearchConfiguration config = new RemoteSearchConfiguration(
            "test-config-2",
            "Test Config 2",
            null, // null description
            "https://example.com",
            null, // null username
            null, // null password
            null, // null query template
            null, // null response template
            RemoteSearchConfiguration.DEFAULT_MAX_REQUESTS_PER_SECOND,
            RemoteSearchConfiguration.DEFAULT_MAX_CONCURRENT_REQUESTS,
            RemoteSearchConfiguration.DEFAULT_CACHE_DURATION_MINUTES,
            false,
            null, // null metadata
            null  // null timestamp
        );

        assertEquals("test-config-2", config.getId());
        assertEquals("Test Config 2", config.getName());
        assertEquals(null, config.getDescription());
        assertEquals("https://example.com", config.getConnectionUrl());
        assertEquals(null, config.getUsername());
        assertEquals(null, config.getPassword());
        assertEquals(null, config.getQueryTemplate());
        assertEquals(null, config.getResponseTemplate());
        assertEquals(10, config.getMaxRequestsPerSecond());
        assertEquals(5, config.getMaxConcurrentRequests());
        assertEquals(60, config.getCacheDurationMinutes());
        assertEquals(false, config.isRefreshCache());
        assertEquals(null, config.getMetadata());
        assertEquals(null, config.getTimestamp());
    }

    public void testRemoteSearchConfigurationConstants() {
        assertEquals(10, RemoteSearchConfiguration.DEFAULT_MAX_REQUESTS_PER_SECOND);
        assertEquals(5, RemoteSearchConfiguration.DEFAULT_MAX_CONCURRENT_REQUESTS);
        assertEquals(60, RemoteSearchConfiguration.DEFAULT_CACHE_DURATION_MINUTES);

        // Verify field name constants
        assertEquals("id", RemoteSearchConfiguration.ID);
        assertEquals("name", RemoteSearchConfiguration.NAME);
        assertEquals("connectionUrl", RemoteSearchConfiguration.CONNECTION_URL);
        assertEquals("queryTemplate", RemoteSearchConfiguration.QUERY_TEMPLATE);
        assertEquals("responseTemplate", RemoteSearchConfiguration.RESPONSE_TEMPLATE);
    }
}
