/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */
package org.opensearch.searchrelevance.executors;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Semaphore;

import org.opensearch.common.SuppressForbidden;
import org.opensearch.core.action.ActionListener;
import org.opensearch.searchrelevance.dao.RemoteSearchCacheDao;
import org.opensearch.searchrelevance.dao.RemoteSearchConfigurationDao;
import org.opensearch.searchrelevance.dao.RemoteSearchFailureDao;
import org.opensearch.searchrelevance.model.RemoteSearchCache;
import org.opensearch.searchrelevance.model.RemoteSearchConfiguration;
import org.opensearch.searchrelevance.model.RemoteSearchFailure;
import org.opensearch.searchrelevance.stats.events.EventStatName;
import org.opensearch.searchrelevance.stats.events.EventStatsManager;
import org.opensearch.searchrelevance.utils.TimeUtils;

import lombok.extern.log4j.Log4j2;

/**
 * RemoteSearchExecutor handles HTTP requests to remote search engines with rate limiting,
 * caching, and comprehensive error handling. This enables experiments to run against
 * remote OpenSearch clusters or other search engines via HTTPS.
 */
@Log4j2
public class RemoteSearchExecutor {

    private final RemoteSearchConfigurationDao remoteSearchConfigurationDao;
    private final RemoteSearchCacheDao remoteSearchCacheDao;
    private final RemoteSearchFailureDao remoteSearchFailureDao;
    private final RemoteResponseMapper remoteResponseMapper;

    // Rate limiting: Map of config ID to semaphore for concurrent request limiting
    private final Map<String, Semaphore> concurrentRequestLimiters = new ConcurrentHashMap<>();

    // Rate limiting: Map of config ID to last request timestamp for requests per second limiting
    private final Map<String, Long> lastRequestTimestamps = new ConcurrentHashMap<>();

    // Lock objects for pacing RPS per config
    private final Map<String, Object> rateLimitLocks = new ConcurrentHashMap<>();

    /**
     * Constructor with all dependencies
     */
    public RemoteSearchExecutor(
        RemoteSearchConfigurationDao remoteSearchConfigurationDao,
        RemoteSearchCacheDao remoteSearchCacheDao,
        RemoteSearchFailureDao remoteSearchFailureDao,
        RemoteResponseMapper remoteResponseMapper
    ) {
        this.remoteSearchConfigurationDao = remoteSearchConfigurationDao;
        this.remoteSearchCacheDao = remoteSearchCacheDao;
        this.remoteSearchFailureDao = remoteSearchFailureDao;
        this.remoteResponseMapper = remoteResponseMapper;
    }

    /**
     * Execute a remote search request with rate limiting, caching, and error handling
     *
     * @param remoteConfigId The remote configuration ID
     * @param query The search query (JSON string)
     * @param queryText The original query text for caching
     * @param experimentId The experiment ID for failure tracking
     * @param listener ActionListener for async response handling
     */
    public void executeRemoteSearch(
        String remoteConfigId,
        String query,
        String queryText,
        int size,
        String experimentId,
        ActionListener<RemoteSearchResponse> listener
    ) {
        // Metrics: count remote search request
        EventStatsManager.increment(EventStatName.REMOTE_SEARCH_REQUESTS);
        // First, get the remote configuration
        remoteSearchConfigurationDao.getRemoteSearchConfiguration(remoteConfigId, ActionListener.wrap(config -> {
            if (config == null) {
                listener.onFailure(new IllegalArgumentException("Remote configuration not found: " + remoteConfigId));
                return;
            }

            // Check cache first
            String cacheKey = RemoteSearchCache.generateCacheKey(remoteConfigId, query, queryText);
            checkCacheAndExecute(config, query, queryText, size, experimentId, cacheKey, listener);
        }, error -> {
            log.error("Failed to retrieve remote configuration {}: {}", remoteConfigId, error.getMessage());
            listener.onFailure(error);
        }));
    }

    /**
     * Check cache for existing response, execute remote request if not cached
     */
    private void checkCacheAndExecute(
        RemoteSearchConfiguration config,
        String query,
        String queryText,
        int size,
        String experimentId,
        String cacheKey,
        ActionListener<RemoteSearchResponse> listener
    ) {
        // Check cache first if caching is enabled
        if (config.getCacheTtlMinutes() > 0) {
            remoteSearchCacheDao.getCachedResponse(cacheKey, ActionListener.wrap(cachedResponse -> {
                if (cachedResponse != null && !cachedResponse.isExpired()) {
                    // Cache hit - return cached response
                    EventStatsManager.increment(EventStatName.REMOTE_SEARCH_CACHE_HITS);
                    log.debug("Cache hit for config: {}, key: {}", config.getId(), cacheKey);

                    // Apply response mapping to cached response
                    String mappedResponse = applyResponseMapping(config, cachedResponse.getResponse());

                    RemoteSearchResponse response = new RemoteSearchResponse(
                        cachedResponse.getResponse(),
                        mappedResponse,
                        200, // Assume success for cached responses
                        true
                    );

                    listener.onResponse(response);
                } else {
                    // Cache miss or expired - execute remote request
                    EventStatsManager.increment(EventStatName.REMOTE_SEARCH_CACHE_MISSES);
                    log.debug("Cache miss for config: {}, key: {}", config.getId(), cacheKey);
                    executeRemoteRequest(config, query, queryText, size, experimentId, cacheKey, listener);
                }
            }, error -> {
                // Cache lookup failed - proceed with remote execution
                log.warn("Cache lookup failed for config: {}, proceeding with remote execution: {}", config.getId(), error.getMessage());
                executeRemoteRequest(config, query, queryText, size, experimentId, cacheKey, listener);
            }));
        } else {
            // Caching disabled - proceed directly to remote execution
            executeRemoteRequest(config, query, queryText, size, experimentId, cacheKey, listener);
        }
    }

    /**
     * Execute the actual remote HTTP request with rate limiting
     */
    @SuppressForbidden(reason = "External HTTP I/O is required to call remote search engines")
    private void executeRemoteRequest(
        RemoteSearchConfiguration config,
        String query,
        String queryText,
        int size,
        String experimentId,
        String cacheKey,
        ActionListener<RemoteSearchResponse> listener
    ) {
        try {
            // Apply rate limiting
            if (!applyRateLimit(config)) {
                EventStatsManager.increment(EventStatName.REMOTE_SEARCH_RATE_LIMIT_HITS);
                listener.onFailure(new RuntimeException("Rate limit exceeded for configuration: " + config.getId()));
                return;
            }

            // Process query template
            String processedQuery = processQueryTemplate(config.getQueryTemplate(), query, queryText, size);

            // Execute HTTP request using HttpURLConnection (similar to build.gradle approach)
            executeWithHttpURLConnection(config, processedQuery, query, queryText, experimentId, cacheKey, listener);

        } catch (Exception e) {
            releaseConcurrentRequestLimit(config.getId());
            handleRequestFailure(config, query, queryText, experimentId, e, listener);
        }
    }

    /**
     * Execute HTTP request using HttpURLConnection (avoiding forbidden getInputStream)
     */
    @SuppressForbidden(reason = "External HTTP I/O is required to call remote search engines")
    private void executeWithHttpURLConnection(
        RemoteSearchConfiguration config,
        String processedQuery,
        String originalQuery,
        String queryText,
        String experimentId,
        String cacheKey,
        ActionListener<RemoteSearchResponse> listener
    ) {
        // Execute in a separate thread to avoid blocking
        Thread requestThread = new Thread(() -> {
            try {
                URI uri = URI.create(config.getConnectionUrl());

                // Detect Solr endpoint and build appropriate request
                boolean isSolr = uri.getPath().contains("/solr/") && uri.getPath().endsWith("/select");

                URL url;
                String method;
                String requestBody = null;
                String contentType = null;

                // For Solr param-style requests, URL-encode only the inserted queryText to avoid illegal characters (e.g., spaces)
                String safeProcessedQuery = processedQuery;
                if (isSolr && queryText != null && !queryText.isEmpty()) {
                    try {
                        String encodedQueryText = URLEncoder.encode(queryText, StandardCharsets.UTF_8.name());
                        safeProcessedQuery = processedQuery.replace(queryText, encodedQueryText);
                    } catch (Exception e) {
                        log.warn("Failed to URL-encode queryText for Solr: {}", e.getMessage());
                    }
                }

                if (isSolr && safeProcessedQuery.contains("=") && safeProcessedQuery.contains("&")) {
                    // Solr with URL parameters - use GET request
                    url = new URL(config.getConnectionUrl() + "?" + safeProcessedQuery);
                    method = "GET";
                } else if (isSolr) {
                    // Solr with form data - use POST with form encoding
                    url = new URL(config.getConnectionUrl());
                    method = "POST";
                    requestBody = safeProcessedQuery;
                    contentType = "application/x-www-form-urlencoded";
                } else {
                    // Non-Solr endpoint - use JSON POST
                    url = new URL(config.getConnectionUrl());
                    method = "POST";
                    requestBody = processedQuery;
                    contentType = "application/json";
                }

                log.debug("Executing remote request to: {} with method: {}", url, method);

                HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod(method);
                connection.setConnectTimeout(30000); // 30 seconds
                connection.setReadTimeout(30000); // 30 seconds
                connection.setRequestProperty("Accept", "application/json");

                // Add authentication if provided
                if (config.getUsername() != null
                    && !config.getUsername().trim().isEmpty()
                    && config.getPassword() != null
                    && !config.getPassword().trim().isEmpty()) {

                    String credentials = config.getUsername() + ":" + config.getPassword();
                    String encodedCredentials = Base64.getEncoder().encodeToString(credentials.getBytes(StandardCharsets.UTF_8));
                    connection.setRequestProperty("Authorization", "Basic " + encodedCredentials);
                }

                // Set content type and write request body if needed
                if (requestBody != null) {
                    connection.setDoOutput(true);
                    connection.setRequestProperty("Content-Type", contentType);

                    byte[] requestBytes = requestBody.getBytes(StandardCharsets.UTF_8);
                    connection.getOutputStream().write(requestBytes);
                    connection.getOutputStream().flush();
                    connection.getOutputStream().close();
                }

                // Get response code first (this triggers the request)
                int statusCode = connection.getResponseCode();
                String responseMessage = connection.getResponseMessage();

                releaseConcurrentRequestLimit(config.getId());

                if (statusCode >= 200 && statusCode < 300) {
                    // Read the actual response body
                    String responseBody = readResponseBody(connection);

                    handleHttpSuccess(config, originalQuery, queryText, experimentId, cacheKey, statusCode, responseBody, listener);
                } else {
                    // Try to read error response body for better error reporting
                    String errorBody = readErrorResponseBody(connection);
                    String errorMessage = String.format(Locale.ROOT, "HTTP %d: %s", statusCode, responseMessage);
                    if (errorBody != null && !errorBody.trim().isEmpty()) {
                        errorMessage += " - Response: " + errorBody;
                    }
                    Exception httpError = new IOException(errorMessage);
                    handleRequestFailure(config, originalQuery, queryText, experimentId, httpError, listener);
                }

            } catch (Exception e) {
                releaseConcurrentRequestLimit(config.getId());
                handleRequestFailure(config, originalQuery, queryText, experimentId, e, listener);
            }
        });

        requestThread.setDaemon(true);
        requestThread.start();
    }

    /**
     * Handle successful HTTP response
     */
    private void handleHttpSuccess(
        RemoteSearchConfiguration config,
        String query,
        String queryText,
        String experimentId,
        String cacheKey,
        int statusCode,
        String responseBody,
        ActionListener<RemoteSearchResponse> listener
    ) {
        try {
            // Debug logging to capture what we're actually receiving
            log.debug("DEBUG: Response status: {}", statusCode);
            log.debug("DEBUG: Response body: {}", responseBody);

            // Metrics: count success
            EventStatsManager.increment(EventStatName.REMOTE_SEARCH_SUCCESSES);
            // Apply response mapping
            String mappedResponse = applyResponseMapping(config, responseBody);

            // Cache the response if caching is enabled
            if (config.getCacheTtlMinutes() > 0) {
                long currentTimestamp = System.currentTimeMillis();
                long expirationTimestamp = currentTimestamp + (config.getCacheTtlMinutes() * 60 * 1000);

                RemoteSearchCache cacheEntry = new RemoteSearchCache(
                    cacheKey,
                    config.getId(),
                    query,
                    queryText,
                    responseBody,
                    mappedResponse,
                    currentTimestamp,
                    expirationTimestamp
                );

                remoteSearchCacheDao.cacheResponse(
                    cacheEntry,
                    ActionListener.wrap(
                        success -> log.debug("Response cached for config: {}, key: {}", config.getId(), cacheKey),
                        error -> log.warn("Failed to cache response for config: {}: {}", config.getId(), error.getMessage())
                    )
                );
            }

            RemoteSearchResponse remoteResponse = new RemoteSearchResponse(responseBody, mappedResponse, statusCode, true);
            listener.onResponse(remoteResponse);

            log.debug("Remote search successful for config: {}, status: {}", config.getId(), statusCode);

        } catch (Exception e) {
            handleRequestFailure(config, query, queryText, experimentId, e, listener);
        }
    }

    /**
     * Apply rate limiting based on configuration settings
     */
    private boolean applyRateLimit(RemoteSearchConfiguration config) {
        String configId = config.getId();

        // Acquire concurrency permit (block until available)
        Semaphore concurrentLimiter = concurrentRequestLimiters.computeIfAbsent(
            configId,
            k -> new Semaphore(Math.max(1, config.getMaxConcurrentRequests()))
        );

        if (!concurrentLimiter.tryAcquire()) {
            log.debug("Concurrent request limit exceeded for config: {}", configId);
            return false;
        }

        // Pace requests per second by sleeping if needed
        Object lock = rateLimitLocks.computeIfAbsent(configId, k -> new Object());
        synchronized (lock) {
            long now = System.currentTimeMillis();
            long last = lastRequestTimestamps.getOrDefault(configId, 0L);
            int maxRps = Math.max(1, config.getMaxRequestsPerSecond());
            long minIntervalMs = 1000L / maxRps;
            long elapsed = now - last;

            if (elapsed < minIntervalMs) {
                long waitMs = minIntervalMs - elapsed;
                try {
                    Thread.sleep(waitMs);
                    now = System.currentTimeMillis();
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    // Release permit before failing
                    releaseConcurrentRequestLimit(configId);
                    log.warn("Interrupted while pacing RPS for config: {}", configId);
                    return false;
                }
            }
            lastRequestTimestamps.put(configId, now);
        }

        return true;
    }

    /**
     * Release concurrent request limit
     */
    private void releaseConcurrentRequestLimit(String configId) {
        Semaphore limiter = concurrentRequestLimiters.get(configId);
        if (limiter != null) {
            limiter.release();
        }
    }

    /**
     * Process query template by substituting placeholders
     */
    private String processQueryTemplate(String queryTemplate, String query, String queryText, int size) {
        if (queryTemplate == null || queryTemplate.trim().isEmpty()) {
            return query; // Use query as-is if no template
        }

        // Replace common placeholders
        String processed = queryTemplate.replace("${query}", query)
            .replace("${queryText}", queryText)
            .replace("{{query}}", query)
            .replace("{{queryText}}", queryText)
            .replace("${size}", String.valueOf(size))
            .replace("{{size}}", String.valueOf(size));

        return processed;
    }

    /**
     * Handle request failure with proper error categorization and logging
     */
    private void handleRequestFailure(
        RemoteSearchConfiguration config,
        String query,
        String queryText,
        String experimentId,
        Throwable error,
        ActionListener<RemoteSearchResponse> listener
    ) {
        EventStatsManager.increment(EventStatName.REMOTE_SEARCH_FAILURES);
        log.error("Remote search failed for config: {}, error: {}", config.getId(), error.getMessage());
        log.error("Remote search exception stack trace for config: {}", config.getId(), error);

        // Create failure record for tracking
        String failureId = "failure_" + System.currentTimeMillis() + "_" + config.getId().hashCode();
        RemoteSearchFailure failure = RemoteSearchFailure.fromException(
            failureId,
            config.getId(),
            experimentId,
            query,
            queryText,
            error instanceof Exception ? (Exception) error : new RuntimeException(error),
            TimeUtils.getTimestamp()
        );

        // Store failure record for analysis and circuit breaker logic
        remoteSearchFailureDao.recordFailure(
            failure,
            ActionListener.wrap(
                success -> log.debug("Failure recorded for config: {}, failure ID: {}", config.getId(), failureId),
                storeError -> log.warn("Failed to store failure record for config: {}: {}", config.getId(), storeError.getMessage())
            )
        );

        listener.onFailure(new RuntimeException("Remote search failed: " + error.getMessage(), error));
    }

    /**
     * Read response body from successful HTTP connection
     */
    @SuppressForbidden(reason = "External HTTP I/O is required to call remote search engines")
    private String readResponseBody(HttpURLConnection connection) throws IOException {
        try (InputStream inputStream = connection.getInputStream()) {
            if (inputStream == null) {
                log.warn("No input stream available from connection");
                return "{}";
            }

            try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
                StringBuilder responseBuilder = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    responseBuilder.append(line).append('\n');
                }

                String responseBody = responseBuilder.toString();
                log.debug("Read response body, length: {}", responseBody.length());
                return responseBody;
            }
        } catch (IOException e) {
            log.error("Failed to read response body: {}", e.getMessage());
            throw e;
        }
    }

    /**
     * Read error response body from failed HTTP connection
     */
    @SuppressForbidden(reason = "External HTTP I/O is required to call remote search engines")
    private String readErrorResponseBody(HttpURLConnection connection) {
        try (InputStream errorStream = connection.getErrorStream()) {
            if (errorStream == null) {
                return null;
            }

            try (BufferedReader reader = new BufferedReader(new InputStreamReader(errorStream, StandardCharsets.UTF_8))) {
                StringBuilder errorBuilder = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    errorBuilder.append(line).append('\n');
                }

                String errorResponse = errorBuilder.toString();
                log.debug("Read error response body, length: {}", errorResponse.length());
                return errorResponse;
            }
        } catch (IOException e) {
            log.warn("Failed to read error response body: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Apply response mapping using the RemoteResponseMapper
     */
    private String applyResponseMapping(RemoteSearchConfiguration config, String rawResponse) {
        try {
            String responseTemplate = config.getResponseTemplate();
            log.debug("Applying response mapping for config: {}", config.getId());
            log.debug(
                "Raw response length: {}, starts with: {}",
                rawResponse != null ? rawResponse.length() : 0,
                rawResponse != null && rawResponse.length() > 50 ? rawResponse.substring(0, 50) : rawResponse
            );

            if (responseTemplate != null && !responseTemplate.trim().isEmpty()) {
                log.debug(
                    "Response template length: {}, starts with: {}",
                    responseTemplate.length(),
                    responseTemplate.length() > 50 ? responseTemplate.substring(0, 50) : responseTemplate
                );

                log.debug("Calling response mapper for config: {}", config.getId());
                String mappedResponse = remoteResponseMapper.mapResponse(rawResponse, responseTemplate);

                log.debug(
                    "Response mapping completed for config: {}, mapped response length: {}",
                    config.getId(),
                    mappedResponse != null ? mappedResponse.length() : 0
                );

                // Validate mapped response is not null or "null"
                if (mappedResponse == null || "null".equals(mappedResponse.trim())) {
                    log.warn("Response mapping returned null/empty for config: {}, falling back to raw response", config.getId());
                    return rawResponse;
                }

                return mappedResponse;
            } else {
                // No template or blank -> use default mapping behavior in RemoteResponseMapper
                if (responseTemplate == null || responseTemplate.trim().isEmpty()) {
                    log.debug("No/empty response template for config: {}, applying default mapping", config.getId());
                }
                try {
                    String mappedResponse = remoteResponseMapper.mapResponse(rawResponse, responseTemplate);
                    if (mappedResponse == null || "null".equals(mappedResponse.trim())) {
                        log.warn(
                            "Default response mapping returned null/empty for config: {}, falling back to raw response",
                            config.getId()
                        );
                        return rawResponse;
                    }
                    return mappedResponse;
                } catch (Exception e) {
                    log.error("Default response mapping failed for config: {}: {}", config.getId(), e.getMessage(), e);
                    log.warn("Falling back to raw response due to default mapping failure");
                    return rawResponse;
                }
            }
        } catch (Exception e) {
            log.error("Response mapping failed for config: {}: {}", config.getId(), e.getMessage(), e);
            log.warn("Falling back to raw response due to mapping failure");
            return rawResponse;
        }
    }

    /**
     * Response wrapper for remote search results
     */
    public static class RemoteSearchResponse {
        private final String rawResponse;
        private final String mappedResponse;
        private final int statusCode;
        private final boolean success;

        public RemoteSearchResponse(String rawResponse, String mappedResponse, int statusCode, boolean success) {
            this.rawResponse = rawResponse;
            this.mappedResponse = mappedResponse;
            this.statusCode = statusCode;
            this.success = success;
        }

        public String getRawResponse() {
            return rawResponse;
        }

        public String getMappedResponse() {
            return mappedResponse;
        }

        public int getStatusCode() {
            return statusCode;
        }

        public boolean isSuccess() {
            return success;
        }
    }
}
