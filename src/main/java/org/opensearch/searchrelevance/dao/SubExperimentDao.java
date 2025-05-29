/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */
package org.opensearch.searchrelevance.dao;

import static org.opensearch.searchrelevance.indices.SearchRelevanceIndices.SUB_EXPERIMENT;

import java.io.IOException;
import java.util.Objects;

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
import org.opensearch.searchrelevance.model.SubExperiment;

import lombok.extern.log4j.Log4j2;

@Log4j2
public class SubExperimentDao {
    private final SearchRelevanceIndicesManager searchRelevanceIndicesManager;

    public SubExperimentDao(SearchRelevanceIndicesManager searchRelevanceIndicesManager) {
        this.searchRelevanceIndicesManager = searchRelevanceIndicesManager;
    }

    /**
     * Create sub experiment index if not exists
     * @param stepListener - step lister for async operation
     */
    public void createIndexIfAbsent(final StepListener<Void> stepListener) {
        searchRelevanceIndicesManager.createIndexIfAbsent(SUB_EXPERIMENT, stepListener);
    }

    /**
     * Stores sub experiment to in the system index
     * @param subExperiment - Experiment content to be stored
     * @param listener - action lister for async operation
     */
    public void putSubExperiment(final SubExperiment subExperiment, final ActionListener listener) {
        if (Objects.isNull(subExperiment)) {
            listener.onFailure(new SearchRelevanceException("Experiment cannot be null", RestStatus.BAD_REQUEST));
            return;
        }
        try {
            searchRelevanceIndicesManager.putDoc(
                subExperiment.getId(),
                subExperiment.toXContent(XContentFactory.jsonBuilder(), ToXContent.EMPTY_PARAMS),
                SUB_EXPERIMENT,
                listener
            );
        } catch (IOException e) {
            throw new SearchRelevanceException("Failed to store sub experiment", e, RestStatus.INTERNAL_SERVER_ERROR);
        }
    }

    public void updateSubExperiment(final SubExperiment subExperiment, final ActionListener listener) {
        if (subExperiment == null) {
            listener.onFailure(new SearchRelevanceException("Sub experiment cannot be null", RestStatus.BAD_REQUEST));
            return;
        }
        try {
            searchRelevanceIndicesManager.updateDoc(
                subExperiment.getId(),
                subExperiment.toXContent(XContentFactory.jsonBuilder(), ToXContent.EMPTY_PARAMS),
                SUB_EXPERIMENT,
                listener
            );
        } catch (IOException e) {
            throw new SearchRelevanceException("Failed to store sub experiment", e, RestStatus.INTERNAL_SERVER_ERROR);
        }
    }

    /**
     * Delete sub experiment by experimentId
     * @param subExperimentId - id to be deleted
     * @param listener - action lister for async operation
     */
    public void deleteSubExperiment(final String subExperimentId, final ActionListener<DeleteResponse> listener) {
        searchRelevanceIndicesManager.deleteDocByDocId(subExperimentId, SUB_EXPERIMENT, listener);
    }

    /**
     * Get sub experiment by id
     * @param subExperimentId - id to be deleted
     * @param listener - action lister for async operation
     */
    public SearchResponse getExperiment(String subExperimentId, ActionListener<SearchResponse> listener) {
        if (subExperimentId == null || subExperimentId.isEmpty()) {
            listener.onFailure(new SearchRelevanceException("subExperimentId must not be null or empty", RestStatus.BAD_REQUEST));
            return null;
        }
        return searchRelevanceIndicesManager.getDocByDocId(subExperimentId, SUB_EXPERIMENT, listener);
    }

    /**
     * List sub experiment by source builder
     * @param sourceBuilder - source builder to be searched
     * @param listener - action lister for async operation
     */
    public SearchResponse listSubExperiment(SearchSourceBuilder sourceBuilder, ActionListener<SearchResponse> listener) {
        // Apply default values if not set
        if (sourceBuilder == null) {
            sourceBuilder = new SearchSourceBuilder();
        }

        // Ensure we have a query
        if (sourceBuilder.query() == null) {
            sourceBuilder.query(QueryBuilders.matchAllQuery());
        }

        return searchRelevanceIndicesManager.listDocsBySearchRequest(sourceBuilder, SUB_EXPERIMENT, listener);
    }
}
