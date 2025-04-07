/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */
package org.opensearch.searchrelevance.dao;

import static org.opensearch.searchrelevance.indices.SearchRelevanceIndices.QUERY_SET;

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
import org.opensearch.search.sort.SortOrder;
import org.opensearch.searchrelevance.exception.SearchRelevanceException;
import org.opensearch.searchrelevance.indices.SearchRelevanceIndicesManager;
import org.opensearch.searchrelevance.model.QuerySet;
import org.opensearch.transport.client.Client;

import reactor.util.annotation.NonNull;

/**
 * Data access object layer for query set system index
 */
public class QuerySetDao {

    private static final Logger LOGGER = LogManager.getLogger(QuerySetDao.class);
    private final Client client;
    private final ClusterService clusterService;
    private final SearchRelevanceIndicesManager searchRelevanceIndicesManager;

    public QuerySetDao(@NonNull Client client, @NonNull ClusterService clusterService) {
        this.client = client;
        this.clusterService = clusterService;
        this.searchRelevanceIndicesManager = new SearchRelevanceIndicesManager(clusterService, client);
    }

    /**
     * Create query set index if not exists
     * @param stepListener - step lister for async operation
     */
    public void createIndexIfAbsent(final StepListener<Void> stepListener) {
        searchRelevanceIndicesManager.createIndexIfAbsent(QUERY_SET, stepListener);
    }

    /**
     * Stores query set to in the system index
     * @param querySet - QuerySet content to be stored
     * @param listener - action lister for async operation
     */
    public void putQuerySet(final QuerySet querySet, final ActionListener listener) {
        if (querySet == null) {
            listener.onFailure(new IllegalArgumentException("QuerySet cannot be null"));
            return;
        }
        try {
            searchRelevanceIndicesManager.putDoc(
                querySet.id(),
                querySet.toXContent(XContentFactory.jsonBuilder(), ToXContent.EMPTY_PARAMS),
                QUERY_SET,
                listener
            );
        } catch (IOException e) {
            throw new SearchRelevanceException("Failed to store query set", e, RestStatus.INTERNAL_SERVER_ERROR);
        }
    }

    /**
     * Delete query set by querySetID
     * @param querySetId - id to be deleted
     * @param listener - action lister for async operation
     */
    public void deleteQuerySet(final String querySetId, final ActionListener<DeleteResponse> listener) {
        searchRelevanceIndicesManager.deleteDocByDocId(querySetId, QUERY_SET, listener);
    }

    /**
     * Get query set by querySetID
     * @param querySetId - id to be deleted
     * @param listener - action lister for async operation
     */
    public void getQuerySet(String querySetId, ActionListener<SearchResponse> listener) {
        searchRelevanceIndicesManager.getDocByDocId(querySetId, QUERY_SET, listener);
    }

    /**
     * List query set by source builder
     * @param sourceBuilder - source builder to be searched
     * @param listener - action lister for async operation
     */
    public void listQuerySet(SearchSourceBuilder sourceBuilder, ActionListener<SearchResponse> listener) {
        // Apply default values if not set
        if (sourceBuilder == null) {
            sourceBuilder = new SearchSourceBuilder();
        }

        // Set size (default or from request)
        int size = sourceBuilder.size();
        if (size <= 0) {
            size = 1000;  // default size
        }
        sourceBuilder.size(size);

        // Set sort (default or from request)
        if (sourceBuilder.sorts() == null || sourceBuilder.sorts().isEmpty()) {
            sourceBuilder.sort("_id", SortOrder.DESC);  // default sort
        }

        // Ensure we have a query
        if (sourceBuilder.query() == null) {
            sourceBuilder.query(QueryBuilders.matchAllQuery());
        }

        searchRelevanceIndicesManager.listDocsBySearchRequest(sourceBuilder, QUERY_SET, listener);
    }
}
