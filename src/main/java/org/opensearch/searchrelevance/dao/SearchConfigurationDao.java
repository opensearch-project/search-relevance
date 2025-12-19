/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */
package org.opensearch.searchrelevance.dao;

import static org.opensearch.searchrelevance.common.MetricsConstants.METRICS_INDEX_AND_QUERIES_FIELD_NAME;
import static org.opensearch.searchrelevance.indices.SearchRelevanceIndices.SEARCH_CONFIGURATION;

import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.opensearch.action.StepListener;
import org.opensearch.action.delete.DeleteResponse;
import org.opensearch.action.search.SearchResponse;
import org.opensearch.action.support.GroupedActionListener;
import org.opensearch.common.inject.Inject;
import org.opensearch.common.xcontent.XContentFactory;
import org.opensearch.core.action.ActionListener;
import org.opensearch.core.rest.RestStatus;
import org.opensearch.core.xcontent.ToXContent;
import org.opensearch.index.query.QueryBuilders;
import org.opensearch.search.builder.SearchSourceBuilder;
import org.opensearch.searchrelevance.exception.SearchRelevanceException;
import org.opensearch.searchrelevance.indices.SearchRelevanceIndicesManager;
import org.opensearch.searchrelevance.model.SearchConfiguration;

public class SearchConfigurationDao {
    private static final Logger LOGGER = LogManager.getLogger(SearchConfigurationDao.class);

    private final SearchRelevanceIndicesManager searchRelevanceIndicesManager;

    @Inject
    public SearchConfigurationDao(SearchRelevanceIndicesManager searchRelevanceIndicesManager) {
        this.searchRelevanceIndicesManager = searchRelevanceIndicesManager;
    }

    /**
     * Create search configuration index if not exists
     * @param stepListener - step listener for async operation
     */
    public void createIndexIfAbsent(final StepListener<Void> stepListener) {
        searchRelevanceIndicesManager.createIndexIfAbsent(SEARCH_CONFIGURATION, stepListener);
    }

    /**
     * Stores search configuration to in the system index
     * @param searchConfiguration - searchConfiguration content to be stored
     * @param listener - action listener for async operation
     */
    public void putSearchConfiguration(final SearchConfiguration searchConfiguration, final ActionListener listener) {
        if (searchConfiguration == null) {
            listener.onFailure(new SearchRelevanceException("SearchConfiguration cannot be null", RestStatus.BAD_REQUEST));
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
     * @param listener - action listener for async operation
     */
    public void deleteSearchConfiguration(final String searchConfigurationId, final ActionListener<DeleteResponse> listener) {
        searchRelevanceIndicesManager.deleteDocByDocId(searchConfigurationId, SEARCH_CONFIGURATION, listener);
    }

    /**
     * Get searchConfiguration by searchConfigurationId
     * @param searchConfigurationId - id to be deleted
     * @param listener - action listener for async operation
     */
    public SearchResponse getSearchConfiguration(String searchConfigurationId, ActionListener<SearchResponse> listener) {
        if (searchConfigurationId == null || searchConfigurationId.isEmpty()) {
            listener.onFailure(new SearchRelevanceException("searchConfigurationId must not be null or empty", RestStatus.BAD_REQUEST));
            return null;
        }
        return searchRelevanceIndicesManager.getDocByDocId(searchConfigurationId, SEARCH_CONFIGURATION, listener);
    }

    public SearchConfiguration getSearchConfigurationSync(String searchConfigurationId) {
        if (searchConfigurationId == null || searchConfigurationId.isEmpty()) {
            throw new SearchRelevanceException("searchConfigurationId must not be null or empty", RestStatus.BAD_REQUEST);
        }
        SearchResponse response = searchRelevanceIndicesManager.getDocByDocIdSync(searchConfigurationId, SEARCH_CONFIGURATION);
        return convertToSearchConfiguration(response);
    }

    /**
     * List searchConfigurationId by source builder
     * @param sourceBuilder - source builder to be searched
     * @param listener - action listener for async operation
     */
    public SearchResponse listSearchConfiguration(SearchSourceBuilder sourceBuilder, ActionListener<SearchResponse> listener) {
        // Apply default values if not set
        if (sourceBuilder == null) {
            sourceBuilder = new SearchSourceBuilder();
        }

        // Ensure we have a query
        if (sourceBuilder.query() == null) {
            sourceBuilder.query(QueryBuilders.matchAllQuery());
        }

        return searchRelevanceIndicesManager.listDocsBySearchRequest(sourceBuilder, SEARCH_CONFIGURATION, listener);
    }

    /**
     * Get list of search configuration given a step stepListener and put it back to results mapping.
     * @param searchConfigurationList - ids to be searched
     * @param results - the results map
     * @param stepListener - step listener
     */
    public void getSearchConfigsWithStepListener(
        List<String> searchConfigurationList,
        Map<String, Object> results,
        ActionListener<Map<String, Object>> stepListener
    ) {
        Map<String, List<String>> indexAndQueries = new HashMap<>();

        GroupedActionListener<SearchResponse> groupedListener = new GroupedActionListener<>(ActionListener.wrap(responses -> {
            results.put(METRICS_INDEX_AND_QUERIES_FIELD_NAME, indexAndQueries);
            stepListener.onResponse(results);
        }, stepListener::onFailure), searchConfigurationList.size());

        for (String searchConfigurationId : searchConfigurationList) {
            getSearchConfiguration(searchConfigurationId, new ActionListener<SearchResponse>() {
                @Override
                public void onResponse(SearchResponse response) {
                    try {
                        LOGGER.info("Successfully get response for searchConfigurationId [{}]: [{}]", searchConfigurationId, response);
                        SearchConfiguration searchConfig = convertToSearchConfiguration(response);
                        LOGGER.debug("Converted response into SearchConfiguration: [{}]", searchConfig);

                        indexAndQueries.put(
                            searchConfigurationId,
                            Arrays.asList(searchConfig.index(), searchConfig.query(), searchConfig.searchPipeline())
                        );
                        groupedListener.onResponse(response);
                    } catch (Exception e) {
                        LOGGER.error(
                            "Failed to convert response: [{}] into SearchConfiguration for id: [{}]",
                            response,
                            searchConfigurationId,
                            e
                        );
                        groupedListener.onFailure(
                            new SearchRelevanceException("Failed to convert SearchConfiguration", e, RestStatus.INTERNAL_SERVER_ERROR)
                        );
                    }
                }

                @Override
                public void onFailure(Exception e) {
                    LOGGER.error("Failed to retrieve SearchConfiguration for id: [{}]", searchConfigurationId, e);
                    groupedListener.onFailure(
                        new SearchRelevanceException("Failed to retrieve SearchConfiguration", e, RestStatus.INTERNAL_SERVER_ERROR)
                    );
                }
            });
        }
    }

    private SearchConfiguration convertToSearchConfiguration(SearchResponse response) {
        Map<String, Object> source = response.getHits().getHits()[0].getSourceAsMap();
        return new SearchConfiguration(
            (String) source.get(SearchConfiguration.ID),
            (String) source.get(SearchConfiguration.NAME),
            (String) source.get(SearchConfiguration.TIME_STAMP),
            (String) source.get(SearchConfiguration.INDEX),
            (String) source.get(SearchConfiguration.QUERY),
            (String) source.get(SearchConfiguration.SEARCH_PIPELINE)
        );
    }
}
