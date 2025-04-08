/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */
package org.opensearch.searchrelevance.dao;

import static org.opensearch.searchrelevance.indices.SearchRelevanceIndices.SEARCH_CONFIGURATION;

import java.io.IOException;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.opensearch.action.StepListener;
import org.opensearch.action.delete.DeleteResponse;
import org.opensearch.action.search.SearchResponse;
import org.opensearch.cluster.service.ClusterService;
import org.opensearch.common.xcontent.XContentFactory;
import org.opensearch.core.action.ActionListener;
import org.opensearch.core.rest.RestStatus;
import org.opensearch.core.xcontent.ToXContent;
import org.opensearch.index.query.QueryBuilders;
import org.opensearch.search.builder.SearchSourceBuilder;
import org.opensearch.searchrelevance.exception.SearchRelevanceException;
import org.opensearch.searchrelevance.indices.SearchRelevanceIndicesManager;
import org.opensearch.searchrelevance.model.SearchConfiguration;
import org.opensearch.transport.client.Client;

import reactor.util.annotation.NonNull;

public class SearchConfigurationDao {
    private static final Logger LOGGER = LogManager.getLogger(SearchConfigurationDao.class);
    private final Client client;
    private final ClusterService clusterService;
    private final SearchRelevanceIndicesManager searchRelevanceIndicesManager;

    public SearchConfigurationDao(@NonNull Client client, @NonNull ClusterService clusterService) {
        this.client = client;
        this.clusterService = clusterService;
        this.searchRelevanceIndicesManager = new SearchRelevanceIndicesManager(clusterService, client);
    }

    /**
     * Create search configuration index if not exists
     * @param stepListener - step lister for async operation
     */
    public void createIndexIfAbsent(final StepListener<Void> stepListener) {
        searchRelevanceIndicesManager.createIndexIfAbsent(SEARCH_CONFIGURATION, stepListener);
    }

    /**
     * Stores search configuration to in the system index
     * @param searchConfiguration - searchConfiguration content to be stored
     * @param listener - action lister for async operation
     */
    public void putSearchConfiguration(final SearchConfiguration searchConfiguration, final ActionListener listener) {
        if (searchConfiguration == null) {
            listener.onFailure(new IllegalArgumentException("SearchConfiguration cannot be null"));
            return;
        }
        try {
            searchRelevanceIndicesManager.putDoc(
                searchConfiguration.id(),
                searchConfiguration.toXContent(XContentFactory.jsonBuilder(), ToXContent.EMPTY_PARAMS),
                SEARCH_CONFIGURATION,
                listener
            );
        } catch (IOException e) {
            throw new SearchRelevanceException("Failed to store searchConfiguration", e, RestStatus.INTERNAL_SERVER_ERROR);
        }
    }

    /**
     * Delete searchConfigurationId by judgmentID
     * @param searchConfigurationId - id to be deleted
     * @param listener - action lister for async operation
     */
    public void deleteSearchConfiguration(final String searchConfigurationId, final ActionListener<DeleteResponse> listener) {
        searchRelevanceIndicesManager.deleteDocByDocId(searchConfigurationId, SEARCH_CONFIGURATION, listener);
    }

    /**
     * Get searchConfiguration by searchConfigurationId
     * @param searchConfigurationId - id to be deleted
     * @param listener - action lister for async operation
     */
    public void getSearchConfiguration(String searchConfigurationId, ActionListener<SearchResponse> listener) {
        searchRelevanceIndicesManager.getDocByDocId(searchConfigurationId, SEARCH_CONFIGURATION, listener);
    }

    /**
     * List searchConfigurationId by source builder
     * @param sourceBuilder - source builder to be searched
     * @param listener - action lister for async operation
     */
    public void listSearchConfiguration(SearchSourceBuilder sourceBuilder, ActionListener<SearchResponse> listener) {
        // Apply default values if not set
        if (sourceBuilder == null) {
            sourceBuilder = new SearchSourceBuilder();
        }

        // Ensure we have a query
        if (sourceBuilder.query() == null) {
            sourceBuilder.query(QueryBuilders.matchAllQuery());
        }

        searchRelevanceIndicesManager.listDocsBySearchRequest(sourceBuilder, SEARCH_CONFIGURATION, listener);
    }
}
