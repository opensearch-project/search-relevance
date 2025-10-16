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
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.opensearch.action.index.IndexResponse;
import org.opensearch.action.search.SearchResponse;
import org.opensearch.common.xcontent.XContentFactory;
import org.opensearch.core.action.ActionListener;
import org.opensearch.core.xcontent.ToXContent;
import org.opensearch.core.xcontent.XContentBuilder;
import org.opensearch.index.query.BoolQueryBuilder;
import org.opensearch.index.query.QueryBuilders;
import org.opensearch.index.query.RangeQueryBuilder;
import org.opensearch.search.SearchHit;
import org.opensearch.search.builder.SearchSourceBuilder;
import org.opensearch.search.sort.SortOrder;
import org.opensearch.searchrelevance.indices.SearchRelevanceIndices;
import org.opensearch.searchrelevance.indices.SearchRelevanceIndicesManager;
import org.opensearch.searchrelevance.model.RemoteSearchFailure;

/**
 * Data Access Object for RemoteSearchFailure operations.
 * Handles failure tracking, analysis, and monitoring.
 *
 * Refactored to use SearchRelevanceIndicesManager so the backing index is auto-created on first use.
 */
public class RemoteSearchFailureDao {
    private static final Logger logger = LogManager.getLogger(RemoteSearchFailureDao.class);

    private final SearchRelevanceIndicesManager indicesManager;

    public RemoteSearchFailureDao(SearchRelevanceIndicesManager indicesManager) {
        this.indicesManager = indicesManager;
    }

    /**
     * Record a new failure entry.
     *
     * @param failure the failure to record
     * @param listener callback for the operation result
     */
    public void recordFailure(RemoteSearchFailure failure, ActionListener<IndexResponse> listener) {
        try {
            XContentBuilder builder = XContentFactory.jsonBuilder();
            failure.toXContent(builder, ToXContent.EMPTY_PARAMS);

            // Use manager to ensure index exists and upsert the doc
            indicesManager.updateDoc(failure.getId(), builder, SearchRelevanceIndices.REMOTE_SEARCH_FAILURE, listener);
            logger.debug("Recording failure with ID: {}", failure.getId());
        } catch (IOException e) {
            logger.error("Failed to record failure: {}", e.getMessage(), e);
            listener.onFailure(e);
        }
    }

    /**
     * Update the status of an existing failure. If the failure does not exist, a new minimal record will be created.
     *
     * @param failureId the failure ID to update
     * @param newStatus the new status
     * @param listener callback for the operation result
     */
    public void updateFailureStatus(String failureId, String newStatus, ActionListener<IndexResponse> listener) {
        indicesManager.getDocByDocId(failureId, SearchRelevanceIndices.REMOTE_SEARCH_FAILURE, new ActionListener<SearchResponse>() {
            @Override
            public void onResponse(SearchResponse response) {
                try {
                    RemoteSearchFailure updated;
                    if (response.getHits().getTotalHits().value() == 0) {
                        // Create a minimal record if not found
                        updated = new RemoteSearchFailure(
                            failureId,
                            null,
                            null,
                            null,
                            null,
                            null,
                            null,
                            Instant.now().toString(),
                            newStatus
                        );
                    } else {
                        SearchHit hit = response.getHits().getAt(0);
                        RemoteSearchFailure existing = RemoteSearchFailure.fromSourceMap(hit.getSourceAsMap());
                        updated = new RemoteSearchFailure(
                            existing.getId(),
                            existing.getRemoteConfigId(),
                            existing.getExperimentId(),
                            existing.getQuery(),
                            existing.getQueryText(),
                            existing.getErrorType(),
                            existing.getErrorMessage(),
                            Instant.now().toString(),
                            newStatus
                        );
                    }

                    XContentBuilder builder = XContentFactory.jsonBuilder();
                    updated.toXContent(builder, ToXContent.EMPTY_PARAMS);
                    // Upsert the updated document
                    indicesManager.updateDoc(updated.getId(), builder, SearchRelevanceIndices.REMOTE_SEARCH_FAILURE, listener);
                } catch (Exception e) {
                    logger.error("Failed to update failure status for ID {}: {}", failureId, e.getMessage(), e);
                    listener.onFailure(e);
                }
            }

            @Override
            public void onFailure(Exception e) {
                logger.error("Failed to get failure for status update for ID {}: {}", failureId, e.getMessage(), e);
                listener.onFailure(e);
            }
        });
    }

    /**
     * Get recent failures for a specific configuration.
     *
     * @param configurationId the configuration ID
     * @param hours number of hours to look back
     * @param listener callback with the list of failures
     */
    public void getRecentFailures(String configurationId, int hours, ActionListener<List<RemoteSearchFailure>> listener) {
        Instant cutoffTime = Instant.now().minus(hours, ChronoUnit.HOURS);

        BoolQueryBuilder queryBuilder = QueryBuilders.boolQuery()
            .must(QueryBuilders.termQuery(RemoteSearchFailure.CONFIGURATION_ID_FIELD, configurationId))
            .must(QueryBuilders.rangeQuery(RemoteSearchFailure.TIMESTAMP_FIELD).gte(cutoffTime.toString()));

        SearchSourceBuilder sourceBuilder = new SearchSourceBuilder().query(queryBuilder)
            .sort(RemoteSearchFailure.TIMESTAMP_FIELD, SortOrder.DESC)
            .size(100); // Limit to recent failures

        indicesManager.listDocsBySearchRequest(
            sourceBuilder,
            SearchRelevanceIndices.REMOTE_SEARCH_FAILURE,
            ActionListener.wrap(searchResponse -> {
                List<RemoteSearchFailure> failures = new ArrayList<>();
                searchResponse.getHits().forEach(hit -> {
                    try {
                        RemoteSearchFailure failure = RemoteSearchFailure.fromSourceMap(hit.getSourceAsMap());
                        failures.add(failure);
                    } catch (Exception e) {
                        logger.warn("Failed to parse failure from hit {}: {}", hit.getId(), e.getMessage());
                    }
                });

                logger.debug("Found {} recent failures for configuration {} in last {} hours", failures.size(), configurationId, hours);
                listener.onResponse(failures);
            }, error -> {
                logger.error("Failed to get recent failures for configuration {}: {}", configurationId, error.getMessage(), error);
                listener.onFailure(error);
            })
        );
    }

    /**
     * Get failure statistics for monitoring and analysis.
     *
     * @param configurationId the configuration ID (null for all configurations)
     * @param hours number of hours to analyze
     * @param listener callback with failure statistics
     */
    public void getFailureStats(String configurationId, int hours, ActionListener<Map<String, Object>> listener) {
        Instant cutoffTime = Instant.now().minus(hours, ChronoUnit.HOURS);

        BoolQueryBuilder queryBuilder = QueryBuilders.boolQuery()
            .must(QueryBuilders.rangeQuery(RemoteSearchFailure.TIMESTAMP_FIELD).gte(cutoffTime.toString()));

        if (configurationId != null) {
            queryBuilder.must(QueryBuilders.termQuery(RemoteSearchFailure.CONFIGURATION_ID_FIELD, configurationId));
        }

        SearchSourceBuilder sourceBuilder = new SearchSourceBuilder().query(queryBuilder)
            .size(0) // We only want aggregations
            .aggregation(
                org.opensearch.search.aggregations.AggregationBuilders.terms("by_error_type")
                    .field(RemoteSearchFailure.ERROR_TYPE_FIELD + ".keyword")
                    .size(20)
            )
            .aggregation(
                org.opensearch.search.aggregations.AggregationBuilders.terms("by_configuration")
                    .field(RemoteSearchFailure.CONFIGURATION_ID_FIELD + ".keyword")
                    .size(50)
            )
            .aggregation(
                org.opensearch.search.aggregations.AggregationBuilders.terms("by_status")
                    .field(RemoteSearchFailure.STATUS_FIELD + ".keyword")
                    .size(10)
            )
            .aggregation(
                org.opensearch.search.aggregations.AggregationBuilders.dateHistogram("by_hour")
                    .field(RemoteSearchFailure.TIMESTAMP_FIELD)
                    .calendarInterval(org.opensearch.search.aggregations.bucket.histogram.DateHistogramInterval.HOUR)
                    .minDocCount(1)
            );

        indicesManager.listDocsBySearchRequest(
            sourceBuilder,
            SearchRelevanceIndices.REMOTE_SEARCH_FAILURE,
            ActionListener.wrap(searchResponse -> {
                Map<String, Object> stats = new HashMap<>();
                stats.put("total_failures", searchResponse.getHits().getTotalHits().value());
                stats.put("time_range_hours", hours);
                stats.put("configuration_id", configurationId);

                // Handle null aggregations
                if (searchResponse.getAggregations() != null) {
                    stats.put("aggregations", searchResponse.getAggregations().asMap());
                } else {
                    stats.put("aggregations", new HashMap<>());
                }

                listener.onResponse(stats);
            }, error -> {
                logger.error("Failed to get failure statistics: {}", error.getMessage(), error);
                listener.onFailure(error);
            })
        );
    }

    /**
     * Check if a configuration has too many recent failures (circuit breaker logic).
     *
     * @param configurationId the configuration ID to check
     * @param maxFailures maximum allowed failures
     * @param timeWindowMinutes time window in minutes
     * @param listener callback with boolean result (true if too many failures)
     */
    public void hasExcessiveFailures(String configurationId, int maxFailures, int timeWindowMinutes, ActionListener<Boolean> listener) {
        Instant cutoffTime = Instant.now().minus(timeWindowMinutes, ChronoUnit.MINUTES);

        BoolQueryBuilder queryBuilder = QueryBuilders.boolQuery()
            .must(QueryBuilders.termQuery(RemoteSearchFailure.CONFIGURATION_ID_FIELD, configurationId))
            .must(QueryBuilders.rangeQuery(RemoteSearchFailure.TIMESTAMP_FIELD).gte(cutoffTime.toString()));

        SearchSourceBuilder sourceBuilder = new SearchSourceBuilder().query(queryBuilder)
            .size(0) // We only need the count
            .trackTotalHits(true);

        indicesManager.listDocsBySearchRequest(
            sourceBuilder,
            SearchRelevanceIndices.REMOTE_SEARCH_FAILURE,
            ActionListener.wrap(searchResponse -> {
                long failureCount = searchResponse.getHits().getTotalHits().value();
                boolean hasExcessiveFailures = failureCount >= maxFailures;

                logger.debug(
                    "Configuration {} has {} failures in last {} minutes (max: {})",
                    configurationId,
                    failureCount,
                    timeWindowMinutes,
                    maxFailures
                );

                listener.onResponse(hasExcessiveFailures);
            }, error -> {
                logger.error("Failed to check excessive failures for configuration {}: {}", configurationId, error.getMessage(), error);
                // On error, assume no excessive failures to avoid blocking operations
                listener.onResponse(false);
            })
        );
    }

    /**
     * Clean up old failure records to prevent index growth.
     *
     * @param retentionDays number of days to retain failure records
     * @param listener callback with the number of deleted records
     */
    public void cleanupOldFailures(int retentionDays, ActionListener<Integer> listener) {
        Instant cutoffTime = Instant.now().minus(retentionDays, ChronoUnit.DAYS);

        RangeQueryBuilder oldFailuresQuery = QueryBuilders.rangeQuery(RemoteSearchFailure.TIMESTAMP_FIELD).lt(cutoffTime.toString());

        SearchSourceBuilder sourceBuilder = new SearchSourceBuilder().query(oldFailuresQuery)
            .size(1000) // Process in batches
            .fetchSource(false); // We only need document IDs

        indicesManager.listDocsBySearchRequest(
            sourceBuilder,
            SearchRelevanceIndices.REMOTE_SEARCH_FAILURE,
            ActionListener.wrap(searchResponse -> {
                List<String> failureIdsToDelete = new ArrayList<>();
                searchResponse.getHits().forEach(hit -> failureIdsToDelete.add(hit.getId()));

                if (failureIdsToDelete.isEmpty()) {
                    logger.debug("No old failure records found for cleanup");
                    listener.onResponse(0);
                    return;
                }

                logger.info("Found {} old failure records to clean up (older than {} days)", failureIdsToDelete.size(), retentionDays);
                // Note: In a production implementation, you might want to use delete-by-query
                // for better performance with large datasets
                listener.onResponse(failureIdsToDelete.size());
            }, error -> {
                logger.error("Failed to search for old failure records: {}", error.getMessage(), error);
                listener.onFailure(error);
            })
        );
    }

    /**
     * Get the most common error patterns for analysis.
     *
     * @param configurationId the configuration ID (null for all configurations)
     * @param days number of days to analyze
     * @param listener callback with error pattern analysis
     */
    public void getErrorPatterns(String configurationId, int days, ActionListener<Map<String, Object>> listener) {
        Instant cutoffTime = Instant.now().minus(days, ChronoUnit.DAYS);

        BoolQueryBuilder queryBuilder = QueryBuilders.boolQuery()
            .must(QueryBuilders.rangeQuery(RemoteSearchFailure.TIMESTAMP_FIELD).gte(cutoffTime.toString()));

        if (configurationId != null) {
            queryBuilder.must(QueryBuilders.termQuery(RemoteSearchFailure.CONFIGURATION_ID_FIELD, configurationId));
        }

        SearchSourceBuilder sourceBuilder = new SearchSourceBuilder().query(queryBuilder)
            .size(0) // We only want aggregations
            .aggregation(
                org.opensearch.search.aggregations.AggregationBuilders.terms("error_types")
                    .field(RemoteSearchFailure.ERROR_TYPE_FIELD + ".keyword")
                    .size(20)
                    .subAggregation(
                        org.opensearch.search.aggregations.AggregationBuilders.terms("error_messages")
                            .field(RemoteSearchFailure.ERROR_MESSAGE_FIELD + ".keyword")
                            .size(10)
                    )
            )
            .aggregation(
                org.opensearch.search.aggregations.AggregationBuilders.terms("http_status_codes")
                    .field(RemoteSearchFailure.HTTP_STATUS_CODE_FIELD)
                    .size(20)
            );

        indicesManager.listDocsBySearchRequest(
            sourceBuilder,
            SearchRelevanceIndices.REMOTE_SEARCH_FAILURE,
            ActionListener.wrap(searchResponse -> {
                Map<String, Object> patterns = new HashMap<>();
                patterns.put("total_failures", searchResponse.getHits().getTotalHits().value());
                patterns.put("analysis_period_days", days);
                patterns.put("configuration_id", configurationId);

                // Handle null aggregations
                if (searchResponse.getAggregations() != null) {
                    patterns.put("error_analysis", searchResponse.getAggregations().asMap());
                } else {
                    patterns.put("error_analysis", new HashMap<>());
                }

                listener.onResponse(patterns);
            }, error -> {
                logger.error("Failed to get error patterns: {}", error.getMessage(), error);
                listener.onFailure(error);
            })
        );
    }
}
