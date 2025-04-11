/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */
package org.opensearch.searchrelevance.dao;

import static org.opensearch.searchrelevance.indices.SearchRelevanceIndices.EXPERIMENT;

import java.io.IOException;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.opensearch.action.StepListener;
import org.opensearch.action.delete.DeleteResponse;
import org.opensearch.action.search.SearchResponse;
import org.opensearch.common.xcontent.XContentFactory;
import org.opensearch.core.action.ActionListener;
import org.opensearch.core.rest.RestStatus;
import org.opensearch.core.xcontent.ToXContent;
import org.opensearch.index.query.QueryBuilders;
import org.opensearch.search.builder.SearchSourceBuilder;
import org.opensearch.searchrelevance.exception.SearchRelevanceException;
import org.opensearch.searchrelevance.indices.SearchRelevanceIndicesManager;
import org.opensearch.searchrelevance.model.Experiment;

public class ExperimentDao {
    private static final Logger LOGGER = LogManager.getLogger(ExperimentDao.class);
    private final SearchRelevanceIndicesManager searchRelevanceIndicesManager;

    public ExperimentDao(SearchRelevanceIndicesManager searchRelevanceIndicesManager) {
        this.searchRelevanceIndicesManager = searchRelevanceIndicesManager;
    }

    /**
     * Create experiment index if not exists
     * @param stepListener - step lister for async operation
     */
    public void createIndexIfAbsent(final StepListener<Void> stepListener) {
        searchRelevanceIndicesManager.createIndexIfAbsent(EXPERIMENT, stepListener);
    }

    /**
     * Stores experiment to in the system index
     * @param experiment - Experiment content to be stored
     * @param listener - action lister for async operation
     */
    public void putExperiment(final Experiment experiment, final ActionListener listener) {
        if (experiment == null) {
            listener.onFailure(new SearchRelevanceException("Experiment cannot be null", RestStatus.BAD_REQUEST));
            return;
        }
        try {
            searchRelevanceIndicesManager.putDoc(
                experiment.id(),
                experiment.toXContent(XContentFactory.jsonBuilder(), ToXContent.EMPTY_PARAMS),
                EXPERIMENT,
                listener
            );
        } catch (IOException e) {
            throw new SearchRelevanceException("Failed to store experiment", e, RestStatus.INTERNAL_SERVER_ERROR);
        }
    }

    /**
     * Delete experiment by experimentId
     * @param experimentId - id to be deleted
     * @param listener - action lister for async operation
     */
    public void deleteExperiment(final String experimentId, final ActionListener<DeleteResponse> listener) {
        searchRelevanceIndicesManager.deleteDocByDocId(experimentId, EXPERIMENT, listener);
    }

    /**
     * Get experiment by experimentId
     * @param experimentId - id to be deleted
     * @param listener - action lister for async operation
     */
    public SearchResponse getExperiment(String experimentId, ActionListener<SearchResponse> listener) {
        if (experimentId == null || experimentId.isEmpty()) {
            listener.onFailure(new SearchRelevanceException("experimentId must not be null or empty", RestStatus.BAD_REQUEST));
            return null;
        }
        return searchRelevanceIndicesManager.getDocByDocId(experimentId, EXPERIMENT, listener);
    }

    /**
     * List experiment by source builder
     * @param sourceBuilder - source builder to be searched
     * @param listener - action lister for async operation
     */
    public SearchResponse listExperiment(SearchSourceBuilder sourceBuilder, ActionListener<SearchResponse> listener) {
        // Apply default values if not set
        if (sourceBuilder == null) {
            sourceBuilder = new SearchSourceBuilder();
        }

        // Ensure we have a query
        if (sourceBuilder.query() == null) {
            sourceBuilder.query(QueryBuilders.matchAllQuery());
        }

        return searchRelevanceIndicesManager.listDocsBySearchRequest(sourceBuilder, EXPERIMENT, listener);
    }
}
