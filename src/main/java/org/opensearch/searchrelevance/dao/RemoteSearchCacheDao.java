/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */
package org.opensearch.searchrelevance.dao;

import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.opensearch.action.delete.DeleteResponse;
import org.opensearch.action.index.IndexResponse;
import org.opensearch.action.search.SearchResponse;
import org.opensearch.common.unit.TimeValue;
import org.opensearch.common.xcontent.XContentFactory;
import org.opensearch.core.action.ActionListener;
import org.opensearch.core.xcontent.ToXContent;
import org.opensearch.core.xcontent.XContentBuilder;
import org.opensearch.index.query.BoolQueryBuilder;
import org.opensearch.index.query.QueryBuilders;
import org.opensearch.index.query.RangeQueryBuilder;
import org.opensearch.search.SearchHit;
import org.opensearch.search.builder.SearchSourceBuilder;
import org.opensearch.searchrelevance.indices.SearchRelevanceIndices;
import org.opensearch.searchrelevance.indices.SearchRelevanceIndicesManager;
import org.opensearch.searchrelevance.model.RemoteSearchCache;

/**
 * Data Access Object for RemoteSearchCache operations.
 * Handles CRUD operations and TTL-based cache management.
 */
public class RemoteSearchCacheDao {
    private static final Logger logger = LogManager.getLogger(RemoteSearchCacheDao.class);

    private final SearchRelevanceIndicesManager indicesManager;

    public RemoteSearchCacheDao(SearchRelevanceIndicesManager indicesManager) {
        this.indicesManager = indicesManager;
    }

    /**
     * Store a cache entry with TTL-based expiration. Upserts the document.
     *
     * @param cache the cache entry to store
     * @param listener callback for the operation result
     */
    public void storeCache(RemoteSearchCache cache, ActionListener<IndexResponse> listener) {
        try {
            XContentBuilder builder = XContentFactory.jsonBuilder();
            cache.toXContent(builder, ToXContent.EMPTY_PARAMS);

            // Use manager so index is created if absent; upsert behavior
            indicesManager.updateDocEfficient(cache.getId(), builder, SearchRelevanceIndices.REMOTE_SEARCH_CACHE, listener);
            logger.debug("Storing cache entry with ID: {}", cache.getId());
        } catch (IOException e) {
            logger.error("Failed to store cache entry: {}", e.getMessage(), e);
            listener.onFailure(e);
        }
    }

    /**
     * Retrieve a cache entry by cache key, checking TTL expiration.
     * Includes index readiness check to prevent shard failures.
     *
     * @param cacheKey the cache key to retrieve
     * @param listener callback with the cache entry or null if not found/expired
     */
    public void getCache(String cacheKey, ActionListener<RemoteSearchCache> listener) {
        // Direct cache lookup; index readiness issues are treated as expected errors in onFailure
        indicesManager.getDocByDocId(cacheKey, SearchRelevanceIndices.REMOTE_SEARCH_CACHE, new ActionListener<SearchResponse>() {
            @Override
            public void onResponse(SearchResponse response) {
                long total = Objects.requireNonNull(response.getHits().getTotalHits()).value();
                if (total == 0) {
                    logger.debug("Cache miss for key: {}", cacheKey);
                    listener.onResponse(null);
                    return;
                }

                try {
                    SearchHit hit = response.getHits().getAt(0);
                    RemoteSearchCache cache = RemoteSearchCache.fromSourceMap(hit.getSourceAsMap());

                    // Check if cache entry has expired
                    if (cache.isExpired()) {
                        logger.debug("Cache entry expired for key: {}", cacheKey);
                        // Asynchronously delete expired entry
                        deleteCache(
                            cacheKey,
                            ActionListener.wrap(
                                deleteResponse -> logger.debug("Deleted expired cache entry: {}", cacheKey),
                                deleteError -> logger.warn("Failed to delete expired cache entry: {}", deleteError.getMessage())
                            )
                        );
                        listener.onResponse(null);
                        return;
                    }

                    logger.debug("Cache hit for key: {}", cacheKey);
                    listener.onResponse(cache);
                } catch (Exception e) {
                    logger.error("Failed to parse cache entry for key {}: {}", cacheKey, e.getMessage(), e);
                    listener.onFailure(e);
                }
            }

            @Override
            public void onFailure(Exception e) {
                // Categorize cache errors vs normal cache misses
                if (isExpectedCacheError(e)) {
                    logger.debug("Cache lookup failed for key {} (expected - treating as cache miss): {}", cacheKey, e.getMessage());
                    listener.onResponse(null); // Treat as cache miss, don't fail the request
                } else {
                    logger.error("Failed to retrieve cache entry for key {}: {}", cacheKey, e.getMessage(), e);
                    listener.onFailure(e);
                }
            }
        });
    }

    /**
     * Delete a cache entry by cache key.
     *
     * @param cacheKey the cache key to delete
     * @param listener callback for the operation result
     */
    public void deleteCache(String cacheKey, ActionListener<DeleteResponse> listener) {
        indicesManager.deleteDocByDocId(cacheKey, SearchRelevanceIndices.REMOTE_SEARCH_CACHE, listener);
    }

    /**
     * Delete all cache entries for a specific configuration.
     *
     * @param configurationId the configuration ID to clear cache for
     * @param listener callback for the operation result
     */
    public void clearCacheForConfiguration(String configurationId, ActionListener<Void> listener) {
        BoolQueryBuilder queryBuilder = QueryBuilders.boolQuery()
            .must(QueryBuilders.termQuery(RemoteSearchCache.CONFIGURATION_ID_FIELD, configurationId));

        SearchSourceBuilder sourceBuilder = new SearchSourceBuilder().query(queryBuilder)
            .size(1000) // Process in batches
            .fetchSource(false); // We only need document IDs

        indicesManager.listDocsBySearchRequest(
            sourceBuilder,
            SearchRelevanceIndices.REMOTE_SEARCH_CACHE,
            ActionListener.wrap(searchResponse -> {
                List<String> cacheKeysToDelete = new ArrayList<>();
                searchResponse.getHits().forEach(hit -> cacheKeysToDelete.add(hit.getId()));

                if (cacheKeysToDelete.isEmpty()) {
                    logger.debug("No cache entries found for configuration: {}", configurationId);
                    listener.onResponse(null);
                    return;
                }

                // Delete cache entries in parallel (sequential loop)
                deleteCacheEntries(cacheKeysToDelete, 0, listener);
            }, error -> {
                logger.error("Failed to search cache entries for configuration {}: {}", configurationId, error.getMessage(), error);
                listener.onFailure(error);
            })
        );
    }

    /**
     * Clean up expired cache entries across all configurations.
     *
     * @param listener callback with the number of deleted entries
     */
    public void cleanupExpiredEntries(ActionListener<Integer> listener) {
        // Search for expired entries
        RangeQueryBuilder expiredQuery = QueryBuilders.rangeQuery(RemoteSearchCache.TIMESTAMP_FIELD).lt(Instant.now().toEpochMilli());

        SearchSourceBuilder sourceBuilder = new SearchSourceBuilder().query(expiredQuery)
            .size(1000) // Process in batches
            .fetchSource(false); // We only need document IDs

        indicesManager.listDocsBySearchRequest(
            sourceBuilder,
            SearchRelevanceIndices.REMOTE_SEARCH_CACHE,
            ActionListener.wrap(searchResponse -> {
                List<String> expiredKeys = new ArrayList<>();
                searchResponse.getHits().forEach(hit -> expiredKeys.add(hit.getId()));

                if (expiredKeys.isEmpty()) {
                    logger.debug("No expired cache entries found");
                    listener.onResponse(0);
                    return;
                }

                logger.info("Found {} expired cache entries to clean up", expiredKeys.size());
                deleteCacheEntries(expiredKeys, 0, ActionListener.wrap(v -> listener.onResponse(expiredKeys.size()), listener::onFailure));
            }, error -> {
                logger.error("Failed to search for expired cache entries: {}", error.getMessage(), error);
                listener.onFailure(error);
            })
        );
    }

    /**
     * Recursively delete cache entries from a list.
     */
    private void deleteCacheEntries(List<String> cacheKeys, int index, ActionListener<Void> listener) {
        if (index >= cacheKeys.size()) {
            listener.onResponse(null);
            return;
        }

        String cacheKey = cacheKeys.get(index);
        deleteCache(cacheKey, ActionListener.wrap(deleteResponse -> {
            // Continue with next entry
            deleteCacheEntries(cacheKeys, index + 1, listener);
        }, error -> {
            logger.warn("Failed to delete cache entry {}: {}", cacheKey, error.getMessage());
            // Continue with next entry even if this one failed
            deleteCacheEntries(cacheKeys, index + 1, listener);
        }));
    }

    /**
     * Alias for getCache() for compatibility with RemoteSearchExecutor
     */
    public void getCachedResponse(String cacheKey, ActionListener<RemoteSearchCache> listener) {
        getCache(cacheKey, listener);
    }

    /**
     * Alias for storeCache() for compatibility with RemoteSearchExecutor
     */
    public void cacheResponse(RemoteSearchCache cache, ActionListener<IndexResponse> listener) {
        storeCache(cache, listener);
    }

    /**
     * Check if the cache index is ready for operations.
     * This prevents shard failures when the index is still initializing.
     *
     * @param listener callback with readiness status
     */
    private void checkCacheIndexReadiness(ActionListener<Boolean> listener) {
        // Use a simple search to test if the index is ready
        // This is more reliable than just checking if index exists
        try {
            SearchSourceBuilder sourceBuilder = new SearchSourceBuilder().query(QueryBuilders.matchAllQuery())
                .size(0) // We don't need results, just want to test if index is ready
                .timeout(TimeValue.timeValueSeconds(1)); // Quick timeout

            indicesManager.listDocsBySearchRequest(
                sourceBuilder,
                SearchRelevanceIndices.REMOTE_SEARCH_CACHE,
                ActionListener.wrap(searchResponse -> {
                    logger.debug("Cache index readiness check successful");
                    listener.onResponse(true);
                }, error -> {
                    logger.debug("Cache index not ready: {}", error.getMessage());
                    listener.onResponse(false);
                })
            );
        } catch (Exception e) {
            logger.debug("Cache index readiness check failed with exception: {}", e.getMessage());
            listener.onResponse(false);
        }
    }

    /**
     * Determine if a cache operation error is expected (e.g., index not ready, shard failures)
     * vs unexpected errors that should be propagated.
     *
     * @param error the exception to categorize
     * @return true if this is an expected cache error that should be treated as cache miss
     */
    private boolean isExpectedCacheError(Exception error) {
        String errorMessage = error.getMessage();
        if (errorMessage == null) {
            return false;
        }

        // Check exception type first - ResourceNotFoundException is always a normal cache miss
        if (error instanceof org.opensearch.ResourceNotFoundException) {
            return true;
        }

        // Check for SearchRelevanceException with specific cache-related causes
        if (error instanceof org.opensearch.searchrelevance.exception.SearchRelevanceException) {
            // These are typically index readiness issues, treat as expected
            return errorMessage.contains("Failed to get document")
                || errorMessage.contains("all shards failed")
                || errorMessage.contains("SearchPhaseExecutionException");
        }

        // Common patterns for expected cache errors (fallback string matching)
        return errorMessage.contains("Document not found")
            || errorMessage.contains("all shards failed")
            || errorMessage.contains("SearchPhaseExecutionException")
            || errorMessage.contains("index_not_found_exception")
            || errorMessage.contains("Failed to get document")
            || errorMessage.contains("no such index")
            || errorMessage.contains("IndexNotFoundException");
    }

    /**
     * Get cache statistics for monitoring.
     *
     * @param listener callback with cache statistics
     */
    public void getCacheStats(ActionListener<Map<String, Object>> listener) {
        SearchSourceBuilder sourceBuilder = new SearchSourceBuilder().size(0) // We only want aggregations
            .aggregation(
                org.opensearch.search.aggregations.AggregationBuilders.terms("by_configuration")
                    .field(RemoteSearchCache.CONFIGURATION_ID_FIELD + ".keyword")
                    .size(100)
            )
            .aggregation(
                org.opensearch.search.aggregations.AggregationBuilders.dateHistogram("by_hour")
                    .field(RemoteSearchCache.TIMESTAMP_FIELD)
                    .calendarInterval(org.opensearch.search.aggregations.bucket.histogram.DateHistogramInterval.HOUR)
                    .minDocCount(1)
            );

        indicesManager.listDocsBySearchRequest(
            sourceBuilder,
            SearchRelevanceIndices.REMOTE_SEARCH_CACHE,
            ActionListener.wrap(searchResponse -> {
                Map<String, Object> stats = new java.util.HashMap<>();
                stats.put("total_entries", Objects.requireNonNull(searchResponse.getHits().getTotalHits()).value());

                // Handle null aggregations
                if (searchResponse.getAggregations() != null) {
                    stats.put("aggregations", searchResponse.getAggregations().asMap());
                } else {
                    stats.put("aggregations", new java.util.HashMap<>());
                }
                listener.onResponse(stats);
            }, error -> {
                logger.error("Failed to get cache statistics: {}", error.getMessage(), error);
                listener.onFailure(error);
            })
        );
    }
}
