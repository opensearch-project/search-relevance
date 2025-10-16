/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */
package org.opensearch.searchrelevance.model;

import java.io.IOException;
import java.util.Map;

import org.opensearch.core.xcontent.ToXContentObject;
import org.opensearch.core.xcontent.XContentBuilder;

/**
 * RemoteSearchConfiguration represents connection details and settings for remote search engines.
 * This enables experiments to run against remote OpenSearch clusters or other search engines via HTTPS.
 */
public class RemoteSearchConfiguration implements ToXContentObject {
    public static final String ID = "id";
    public static final String NAME = "name";
    public static final String DESCRIPTION = "description";
    public static final String CONNECTION_URL = "connectionUrl";
    public static final String USERNAME = "username";
    public static final String PASSWORD = "password";
    public static final String QUERY_TEMPLATE = "queryTemplate";
    public static final String RESPONSE_TEMPLATE = "responseTemplate";
    public static final String MAX_REQUESTS_PER_SECOND = "maxRequestsPerSecond";
    public static final String MAX_CONCURRENT_REQUESTS = "maxConcurrentRequests";
    public static final String CACHE_DURATION_MINUTES = "cacheDurationMinutes";
    public static final String REFRESH_CACHE = "refreshCache";
    public static final String METADATA = "metadata";
    public static final String TIMESTAMP = "timestamp";

    // Default values
    public static final int DEFAULT_MAX_REQUESTS_PER_SECOND = 10;
    public static final int DEFAULT_MAX_CONCURRENT_REQUESTS = 5;
    public static final long DEFAULT_CACHE_DURATION_MINUTES = 60;

    private final String id;
    private final String name;
    private final String description;
    private final String connectionUrl;
    private final String username;
    private final String password; // Will be encrypted in storage
    private final String queryTemplate;
    private final String responseTemplate;
    private final int maxRequestsPerSecond;
    private final int maxConcurrentRequests;
    private final long cacheDurationMinutes;
    private final boolean refreshCache;
    private final Map<String, Object> metadata;
    private final String timestamp;

    public RemoteSearchConfiguration(
        String id,
        String name,
        String description,
        String connectionUrl,
        String username,
        String password,
        String queryTemplate,
        String responseTemplate,
        int maxRequestsPerSecond,
        int maxConcurrentRequests,
        long cacheDurationMinutes,
        boolean refreshCache,
        Map<String, Object> metadata,
        String timestamp
    ) {
        this.id = id;
        this.name = name;
        this.description = description;
        this.connectionUrl = connectionUrl;
        this.username = username;
        this.password = password;
        this.queryTemplate = queryTemplate;
        this.responseTemplate = responseTemplate;
        this.maxRequestsPerSecond = maxRequestsPerSecond;
        this.maxConcurrentRequests = maxConcurrentRequests;
        this.cacheDurationMinutes = cacheDurationMinutes;
        this.refreshCache = refreshCache;
        this.metadata = metadata;
        this.timestamp = timestamp;
    }

    @Override
    public XContentBuilder toXContent(XContentBuilder builder, Params params) throws IOException {
        XContentBuilder xContentBuilder = builder.startObject();
        xContentBuilder.field(ID, this.id);
        xContentBuilder.field(NAME, this.name != null ? this.name.trim() : "");
        xContentBuilder.field(DESCRIPTION, this.description != null ? this.description.trim() : "");
        xContentBuilder.field(CONNECTION_URL, this.connectionUrl != null ? this.connectionUrl.trim() : "");
        xContentBuilder.field(USERNAME, this.username != null ? this.username.trim() : "");
        xContentBuilder.field(PASSWORD, this.password != null ? this.password : ""); // Password will be encrypted
        xContentBuilder.field(QUERY_TEMPLATE, this.queryTemplate != null ? this.queryTemplate.trim() : "");
        xContentBuilder.field(RESPONSE_TEMPLATE, this.responseTemplate != null ? this.responseTemplate.trim() : "");
        xContentBuilder.field(MAX_REQUESTS_PER_SECOND, this.maxRequestsPerSecond);
        xContentBuilder.field(MAX_CONCURRENT_REQUESTS, this.maxConcurrentRequests);
        xContentBuilder.field(CACHE_DURATION_MINUTES, this.cacheDurationMinutes);
        xContentBuilder.field(REFRESH_CACHE, this.refreshCache);
        xContentBuilder.field(METADATA, this.metadata);
        xContentBuilder.field(TIMESTAMP, this.timestamp != null ? this.timestamp.trim() : "");
        return xContentBuilder.endObject();
    }

    // Getters
    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public String getConnectionUrl() {
        return connectionUrl;
    }

    public String getUsername() {
        return username;
    }

    public String getPassword() {
        return password;
    }

    public String getQueryTemplate() {
        return queryTemplate;
    }

    public String getResponseTemplate() {
        return responseTemplate;
    }

    public int getMaxRequestsPerSecond() {
        return maxRequestsPerSecond;
    }

    public int getMaxConcurrentRequests() {
        return maxConcurrentRequests;
    }

    public long getCacheDurationMinutes() {
        return cacheDurationMinutes;
    }

    public boolean isRefreshCache() {
        return refreshCache;
    }

    public Map<String, Object> getMetadata() {
        return metadata;
    }

    public String getTimestamp() {
        return timestamp;
    }

    /**
     * Alias for getCacheDurationMinutes() for DAO compatibility
     */
    public long getCacheTtlMinutes() {
        return cacheDurationMinutes;
    }
}
