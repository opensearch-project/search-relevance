/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */
package org.opensearch.searchrelevance.dao;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.opensearch.action.delete.DeleteResponse;
import org.opensearch.action.index.IndexResponse;
import org.opensearch.action.search.SearchResponse;
import org.opensearch.common.xcontent.XContentType;
import org.opensearch.core.action.ActionListener;
import org.opensearch.core.xcontent.XContentBuilder;
import org.opensearch.index.query.QueryBuilders;
import org.opensearch.search.SearchHit;
import org.opensearch.search.builder.SearchSourceBuilder;
import org.opensearch.searchrelevance.indices.SearchRelevanceIndices;
import org.opensearch.searchrelevance.indices.SearchRelevanceIndicesManager;
import org.opensearch.searchrelevance.model.RemoteSearchConfiguration;

/**
 * Data Access Object for RemoteSearchConfiguration operations.
 * Handles CRUD operations for remote search engine configurations.
 */
public class RemoteSearchConfigurationDao {
    private static final Logger log = LogManager.getLogger(RemoteSearchConfigurationDao.class);

    private final SearchRelevanceIndicesManager searchRelevanceIndicesManager;

    public RemoteSearchConfigurationDao(SearchRelevanceIndicesManager searchRelevanceIndicesManager) {
        this.searchRelevanceIndicesManager = searchRelevanceIndicesManager;
    }

    /**
     * Create or update a remote search configuration
     */
    public void createRemoteSearchConfiguration(RemoteSearchConfiguration configuration, ActionListener<IndexResponse> listener) {
        try {
            XContentBuilder builder = configuration.toXContent(XContentBuilder.builder(XContentType.JSON.xContent()), null);
            // Use indices manager to ensure index exists and upsert the doc
            searchRelevanceIndicesManager.updateDocEfficient(
                configuration.getId(),
                builder,
                SearchRelevanceIndices.REMOTE_SEARCH_CONFIGURATION,
                listener
            );
        } catch (IOException e) {
            log.error("Failed to create remote search configuration", e);
            listener.onFailure(e);
        }
    }

    /**
     * Get a remote search configuration by ID
     */
    public void getRemoteSearchConfiguration(String id, ActionListener<RemoteSearchConfiguration> listener) {
        searchRelevanceIndicesManager.getDocByDocId(
            id,
            SearchRelevanceIndices.REMOTE_SEARCH_CONFIGURATION,
            new ActionListener<SearchResponse>() {
                @Override
                public void onResponse(SearchResponse response) {
                    try {
                        if (response.getHits().getTotalHits().value() == 0) {
                            listener.onResponse(null);
                            return;
                        }
                        RemoteSearchConfiguration configuration = parseRemoteSearchConfiguration(
                            response.getHits().getAt(0).getSourceAsMap()
                        );
                        listener.onResponse(configuration);
                    } catch (Exception e) {
                        log.error("Failed to parse remote search configuration", e);
                        listener.onFailure(e);
                    }
                }

                @Override
                public void onFailure(Exception e) {
                    log.error("Failed to get remote search configuration", e);
                    listener.onFailure(e);
                }
            }
        );
    }

    /**
     * List all remote search configurations
     */
    public void listRemoteSearchConfigurations(ActionListener<List<RemoteSearchConfiguration>> listener) {
        SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder().query(QueryBuilders.matchAllQuery()).size(1000); // TODO:
                                                                                                                             // pagination

        searchRelevanceIndicesManager.listDocsBySearchRequest(
            searchSourceBuilder,
            SearchRelevanceIndices.REMOTE_SEARCH_CONFIGURATION,
            new ActionListener<SearchResponse>() {
                @Override
                public void onResponse(SearchResponse searchResponse) {
                    try {
                        List<RemoteSearchConfiguration> configurations = new ArrayList<>();
                        for (SearchHit hit : searchResponse.getHits().getHits()) {
                            RemoteSearchConfiguration configuration = parseRemoteSearchConfiguration(hit.getSourceAsMap());
                            configurations.add(configuration);
                        }
                        listener.onResponse(configurations);
                    } catch (Exception e) {
                        log.error("Failed to parse remote search configurations", e);
                        listener.onFailure(e);
                    }
                }

                @Override
                public void onFailure(Exception e) {
                    log.error("Failed to list remote search configurations", e);
                    listener.onFailure(e);
                }
            }
        );
    }

    /**
     * Delete a remote search configuration
     */
    public void deleteRemoteSearchConfiguration(String id, ActionListener<DeleteResponse> listener) {
        searchRelevanceIndicesManager.deleteDocByDocId(id, SearchRelevanceIndices.REMOTE_SEARCH_CONFIGURATION, listener);
    }

    /**
     * Parse a remote search configuration from source map
     */
    private RemoteSearchConfiguration parseRemoteSearchConfiguration(Map<String, Object> sourceMap) {
        return new RemoteSearchConfiguration(
            (String) sourceMap.get(RemoteSearchConfiguration.ID),
            (String) sourceMap.get(RemoteSearchConfiguration.NAME),
            (String) sourceMap.get(RemoteSearchConfiguration.DESCRIPTION),
            (String) sourceMap.get(RemoteSearchConfiguration.CONNECTION_URL),
            (String) sourceMap.get(RemoteSearchConfiguration.USERNAME),
            (String) sourceMap.get(RemoteSearchConfiguration.PASSWORD),
            (String) sourceMap.get(RemoteSearchConfiguration.QUERY_TEMPLATE),
            (String) sourceMap.get(RemoteSearchConfiguration.RESPONSE_TEMPLATE),
            sourceMap.get(RemoteSearchConfiguration.MAX_REQUESTS_PER_SECOND) != null
                ? (Integer) sourceMap.get(RemoteSearchConfiguration.MAX_REQUESTS_PER_SECOND)
                : RemoteSearchConfiguration.DEFAULT_MAX_REQUESTS_PER_SECOND,
            sourceMap.get(RemoteSearchConfiguration.MAX_CONCURRENT_REQUESTS) != null
                ? (Integer) sourceMap.get(RemoteSearchConfiguration.MAX_CONCURRENT_REQUESTS)
                : RemoteSearchConfiguration.DEFAULT_MAX_CONCURRENT_REQUESTS,
            sourceMap.get(RemoteSearchConfiguration.CACHE_DURATION_MINUTES) != null
                ? ((Number) sourceMap.get(RemoteSearchConfiguration.CACHE_DURATION_MINUTES)).longValue()
                : RemoteSearchConfiguration.DEFAULT_CACHE_DURATION_MINUTES,
            sourceMap.get(RemoteSearchConfiguration.REFRESH_CACHE) != null
                ? (Boolean) sourceMap.get(RemoteSearchConfiguration.REFRESH_CACHE)
                : false,
            (Map<String, Object>) sourceMap.get(RemoteSearchConfiguration.METADATA),
            (String) sourceMap.get(RemoteSearchConfiguration.TIMESTAMP)
        );
    }
}
