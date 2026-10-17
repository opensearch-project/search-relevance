/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */
package org.opensearch.searchrelevance.dao;

import static org.opensearch.searchrelevance.common.PluginConstants.EXPERIMENT_ID;
import static org.opensearch.searchrelevance.indices.SearchRelevanceIndices.EXPERIMENT_VARIANT;

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
import org.opensearch.index.reindex.BulkByScrollResponse;
import org.opensearch.search.builder.SearchSourceBuilder;
import org.opensearch.searchrelevance.exception.SearchRelevanceException;
import org.opensearch.searchrelevance.indices.SearchRelevanceIndicesManager;
import org.opensearch.searchrelevance.model.ExperimentVariant;

import lombok.extern.log4j.Log4j2;

@Log4j2
public class ExperimentVariantDao {
    private final SearchRelevanceIndicesManager searchRelevanceIndicesManager;

    public ExperimentVariantDao(SearchRelevanceIndicesManager searchRelevanceIndicesManager) {
        this.searchRelevanceIndicesManager = searchRelevanceIndicesManager;
    }

    /**
     * Create experiment variant index if not exists
     * @param stepListener - step listener for async operation
     */
    public void createIndexIfAbsent(final StepListener<Void> stepListener) {
        searchRelevanceIndicesManager.createIndexIfAbsent(EXPERIMENT_VARIANT, stepListener);
    }

    /**
     * Stores experiment variant to in the system index
     * @param experimentVariant - Experiment content to be stored
     * @param listener - action listener for async operation
     */
    public void putExperimentVariant(final ExperimentVariant experimentVariant, final ActionListener listener) {
        if (Objects.isNull(experimentVariant)) {
            listener.onFailure(new SearchRelevanceException("Experiment cannot be null", RestStatus.BAD_REQUEST));
            return;
        }
        try {
            searchRelevanceIndicesManager.putDoc(
                experimentVariant.getId(),
                experimentVariant.toXContent(XContentFactory.jsonBuilder(), ToXContent.EMPTY_PARAMS),
                EXPERIMENT_VARIANT,
                listener
            );
        } catch (IOException e) {
            throw new SearchRelevanceException("Failed to store experiment variant", e, RestStatus.INTERNAL_SERVER_ERROR);
        }
    }

    /**
     * Stores experiment variant to in the system index with efficient refresh policy (recommended for experiments)
     * @param experimentVariant - Experiment content to be stored
     * @param listener - action listener for async operation
     */
    public void putExperimentVariantEfficient(final ExperimentVariant experimentVariant, final ActionListener listener) {
        if (Objects.isNull(experimentVariant)) {
            listener.onFailure(new SearchRelevanceException("Experiment cannot be null", RestStatus.BAD_REQUEST));
            return;
        }
        try {
            searchRelevanceIndicesManager.putDocEfficient(
                experimentVariant.getId(),
                experimentVariant.toXContent(XContentFactory.jsonBuilder(), ToXContent.EMPTY_PARAMS),
                EXPERIMENT_VARIANT,
                listener
            );
        } catch (IOException e) {
            throw new SearchRelevanceException("Failed to store experiment variant", e, RestStatus.INTERNAL_SERVER_ERROR);
        }
    }

    public void updateExperimentVariant(final ExperimentVariant experimentVariant, final ActionListener listener) {
        if (experimentVariant == null) {
            listener.onFailure(new SearchRelevanceException("Experiment variant cannot be null", RestStatus.BAD_REQUEST));
            return;
        }
        try {
            searchRelevanceIndicesManager.updateDoc(
                experimentVariant.getId(),
                experimentVariant.toXContent(XContentFactory.jsonBuilder(), ToXContent.EMPTY_PARAMS),
                EXPERIMENT_VARIANT,
                listener
            );
        } catch (IOException e) {
            throw new SearchRelevanceException("Failed to store experiment variant", e, RestStatus.INTERNAL_SERVER_ERROR);
        }
    }

    /**
     * Update experiment variant with efficient refresh policy (recommended for experiments)
     * @param experimentVariant - Experiment variant to be updated
     * @param listener - action listener for async operation
     */
    public void updateExperimentVariantEfficient(final ExperimentVariant experimentVariant, final ActionListener listener) {
        if (experimentVariant == null) {
            listener.onFailure(new SearchRelevanceException("Experiment variant cannot be null", RestStatus.BAD_REQUEST));
            return;
        }
        try {
            searchRelevanceIndicesManager.updateDocEfficient(
                experimentVariant.getId(),
                experimentVariant.toXContent(XContentFactory.jsonBuilder(), ToXContent.EMPTY_PARAMS),
                EXPERIMENT_VARIANT,
                listener
            );
        } catch (IOException e) {
            throw new SearchRelevanceException("Failed to store experiment variant", e, RestStatus.INTERNAL_SERVER_ERROR);
        }
    }

    /**
     * Delete experiment variant by experimentVariantId
     * @param experimentVariantId - id to be deleted
     * @param listener - action listener for async operation
     */
    public void deleteExperimentVariant(final String experimentVariantId, final ActionListener<DeleteResponse> listener) {
        searchRelevanceIndicesManager.deleteDocByDocId(experimentVariantId, EXPERIMENT_VARIANT, listener);
    }

    /**
     * Delete experiment variant by experimentId
     * @param experimentId - id to be deleted
     *
     * @param listener - action listener for async operation
     */
    public void deleteExperimentVariantByExperimentId(final String experimentId, final ActionListener<BulkByScrollResponse> listener) {
        searchRelevanceIndicesManager.deleteByQuery(experimentId, EXPERIMENT_ID, EXPERIMENT_VARIANT, listener);
    }

    /**
     * Get experiment variant by id
     * @param experimentVariantId - id to be deleted
     * @param listener - action listener for async operation
     */
    public SearchResponse getExperiment(String experimentVariantId, ActionListener<SearchResponse> listener) {
        if (experimentVariantId == null || experimentVariantId.isEmpty()) {
            listener.onFailure(new SearchRelevanceException("experimentVariantId must not be null or empty", RestStatus.BAD_REQUEST));
            return null;
        }
        return searchRelevanceIndicesManager.getDocByDocId(experimentVariantId, EXPERIMENT_VARIANT, listener);
    }

    /**
     * List experiment variant by source builder
     * @param sourceBuilder - source builder to be searched
     * @param listener - action listener for async operation
     */
    public SearchResponse listExperimentVariant(SearchSourceBuilder sourceBuilder, ActionListener<SearchResponse> listener) {
        // Apply default values if not set
        if (sourceBuilder == null) {
            sourceBuilder = new SearchSourceBuilder();
        }

        // Ensure we have a query
        if (sourceBuilder.query() == null) {
            sourceBuilder.query(QueryBuilders.matchAllQuery());
        }

        return searchRelevanceIndicesManager.listDocsBySearchRequest(sourceBuilder, EXPERIMENT_VARIANT, listener);
    }
}
