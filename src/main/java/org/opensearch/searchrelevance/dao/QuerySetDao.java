/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */
package org.opensearch.searchrelevance.dao;

import static org.opensearch.searchrelevance.indices.SearchRelevanceIndices.QUERY_SET;
import static org.opensearch.searchrelevance.metrics.MetricsHelper.METRICS_QUERY_TEXT_FIELD_NAME;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.opensearch.action.StepListener;
import org.opensearch.action.delete.DeleteResponse;
import org.opensearch.action.search.SearchResponse;
import org.opensearch.common.inject.Inject;
import org.opensearch.common.xcontent.XContentFactory;
import org.opensearch.core.action.ActionListener;
import org.opensearch.core.rest.RestStatus;
import org.opensearch.core.xcontent.ToXContent;
import org.opensearch.index.query.QueryBuilders;
import org.opensearch.search.SearchHit;
import org.opensearch.search.builder.SearchSourceBuilder;
import org.opensearch.searchrelevance.exception.SearchRelevanceException;
import org.opensearch.searchrelevance.indices.SearchRelevanceIndicesManager;
import org.opensearch.searchrelevance.model.QuerySet;

/**
 * Data access object layer for query set system index
 */
public class QuerySetDao {

    private static final Logger LOGGER = LogManager.getLogger(QuerySetDao.class);
    private final SearchRelevanceIndicesManager searchRelevanceIndicesManager;

    @Inject
    public QuerySetDao(SearchRelevanceIndicesManager searchRelevanceIndicesManager) {
        this.searchRelevanceIndicesManager = searchRelevanceIndicesManager;
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
            listener.onFailure(new SearchRelevanceException("QuerySet cannot be null", RestStatus.BAD_REQUEST));
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
    public SearchResponse getQuerySet(String querySetId, ActionListener<SearchResponse> listener) {
        if (querySetId == null || querySetId.isEmpty()) {
            listener.onFailure(new SearchRelevanceException("querySetId must not be null or empty", RestStatus.BAD_REQUEST));
            return null;
        }
        return searchRelevanceIndicesManager.getDocByDocId(querySetId, QUERY_SET, listener);
    }

    /**
     * List query set by source builder
     * @param sourceBuilder - source builder to be searched
     * @param listener - action lister for async operation
     */
    public SearchResponse listQuerySet(SearchSourceBuilder sourceBuilder, ActionListener<SearchResponse> listener) {
        // Apply default values if not set
        if (sourceBuilder == null) {
            sourceBuilder = new SearchSourceBuilder();
        }

        // Ensure we have a query
        if (sourceBuilder.query() == null) {
            sourceBuilder.query(QueryBuilders.matchAllQuery());
        }

        return searchRelevanceIndicesManager.listDocsBySearchRequest(sourceBuilder, QUERY_SET, listener);
    }

    /**
     * Get a queryset given a step stepListener and put it back to results mapping.
     * @param querySetId - id to be searched
     * @param results - the results map
     * @param stepListener - step lister
     */
    public void getQuerySetWithStepListener(
        String querySetId,
        Map<String, Object> results,
        StepListener<Map<String, Object>> stepListener
    ) {
        getQuerySet(querySetId, new ActionListener<SearchResponse>() {
            @Override
            public void onResponse(SearchResponse response) {
                try {
                    LOGGER.info("Successfully get response: [{}]", response);
                    QuerySet querySet = convertToQuerySet(response);
                    LOGGER.debug("Converted response into queryset: [{}]", querySet);

                    results.put(METRICS_QUERY_TEXT_FIELD_NAME, new ArrayList<>(querySet.querySetQueries().keySet()));
                    stepListener.onResponse(results);
                } catch (Exception e) {
                    LOGGER.error("Failed to convert response: [{}] into queryset.", response);
                    stepListener.onFailure(new SearchRelevanceException("Failed to convert queryset", e, RestStatus.INTERNAL_SERVER_ERROR));
                }
            }

            @Override
            public void onFailure(Exception e) {
                LOGGER.error("Failed to retrieve query set for querySetId: [{}]", querySetId, e);
                stepListener.onFailure(new SearchRelevanceException("Failed retrieve queryset", e, RestStatus.INTERNAL_SERVER_ERROR));
            }
        });
    }

    private QuerySet convertToQuerySet(SearchResponse response) {
        SearchHit hit = response.getHits().getHits()[0];
        Map<String, Object> sourceMap = hit.getSourceAsMap();

        return QuerySet.Builder.builder()
            .name((String) sourceMap.get(QuerySet.ID))
            .name((String) sourceMap.get(QuerySet.NAME))
            .description((String) sourceMap.get(QuerySet.DESCRIPTION))
            .timestamp((String) sourceMap.get(QuerySet.TIME_STAMP))
            .sampling((String) sourceMap.get(QuerySet.SAMPLING))
            .querySetQueries((Map<String, Integer>) sourceMap.getOrDefault(QuerySet.QUERY_SET_QUERIES, new HashMap<>()))
            .build();
    }
}
