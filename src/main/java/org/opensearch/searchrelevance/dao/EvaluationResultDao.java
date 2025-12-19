/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */
package org.opensearch.searchrelevance.dao;

import static org.opensearch.searchrelevance.common.PluginConstants.EXPERIMENT_ID;
import static org.opensearch.searchrelevance.indices.SearchRelevanceIndices.EVALUATION_RESULT;

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
import org.opensearch.index.reindex.BulkByScrollResponse;
import org.opensearch.search.builder.SearchSourceBuilder;
import org.opensearch.searchrelevance.exception.SearchRelevanceException;
import org.opensearch.searchrelevance.indices.SearchRelevanceIndicesManager;
import org.opensearch.searchrelevance.model.EvaluationResult;

public class EvaluationResultDao {
    private static final Logger LOGGER = LogManager.getLogger(EvaluationResultDao.class);
    private final SearchRelevanceIndicesManager searchRelevanceIndicesManager;

    public EvaluationResultDao(SearchRelevanceIndicesManager searchRelevanceIndicesManager) {
        this.searchRelevanceIndicesManager = searchRelevanceIndicesManager;
    }

    /**
     * Create evaluation result index if not exists
     * @param stepListener - step listener for async operation
     */
    public void createIndexIfAbsent(final StepListener<Void> stepListener) {
        searchRelevanceIndicesManager.createIndexIfAbsent(EVALUATION_RESULT, stepListener);
    }

    /**
     * Stores evaluation result to in the system index
     * @param evaluationResult - EvaluationResult content to be stored
     * @param listener - action listener for async operation
     */
    public void putEvaluationResult(final EvaluationResult evaluationResult, final ActionListener listener) {
        if (evaluationResult == null) {
            listener.onFailure(new SearchRelevanceException("EvaluationResult cannot be null", RestStatus.BAD_REQUEST));
            return;
        }
        try {
            searchRelevanceIndicesManager.putDoc(
                evaluationResult.id(),
                evaluationResult.toXContent(XContentFactory.jsonBuilder(), ToXContent.EMPTY_PARAMS),
                EVALUATION_RESULT,
                listener
            );
        } catch (IOException e) {
            throw new SearchRelevanceException("Failed to store evaluationResult", e, RestStatus.INTERNAL_SERVER_ERROR);
        }
    }

    /**
     * Stores evaluation result to in the system index with efficient refresh policy (recommended for experiments)
     * @param evaluationResult - EvaluationResult content to be stored
     * @param listener - action listener for async operation
     */
    public void putEvaluationResultEfficient(final EvaluationResult evaluationResult, final ActionListener listener) {
        if (evaluationResult == null) {
            listener.onFailure(new SearchRelevanceException("EvaluationResult cannot be null", RestStatus.BAD_REQUEST));
            return;
        }
        try {
            searchRelevanceIndicesManager.putDocEfficient(
                evaluationResult.id(),
                evaluationResult.toXContent(XContentFactory.jsonBuilder(), ToXContent.EMPTY_PARAMS),
                EVALUATION_RESULT,
                listener
            );
        } catch (IOException e) {
            throw new SearchRelevanceException("Failed to store evaluationResult", e, RestStatus.INTERNAL_SERVER_ERROR);
        }
    }

    /**
     * Delete evaluationResult by evaluationResultId
     * @param evaluationResultId - id to be deleted
     * @param listener - action listener for async operation
     */
    public void deleteEvaluationResult(final String evaluationResultId, final ActionListener<DeleteResponse> listener) {
        searchRelevanceIndicesManager.deleteDocByDocId(evaluationResultId, EVALUATION_RESULT, listener);
    }

    /**
     * Delete evaluationResult by experimentId
     * @param experimentId - id to be deleted
     * @param listener - action listener for async operation
     */
    public void deleteEvaluationResultByExperimentId(final String experimentId, final ActionListener<BulkByScrollResponse> listener) {
        searchRelevanceIndicesManager.deleteByQuery(experimentId, EXPERIMENT_ID, EVALUATION_RESULT, listener);
    }

    /**
     * Get evaluationResult by evaluationResultId
     * @param evaluationResultId - id to be deleted
     * @param listener - action listener for async operation
     */
    public SearchResponse getEvaluationResult(String evaluationResultId, ActionListener<SearchResponse> listener) {
        if (evaluationResultId == null || evaluationResultId.isEmpty()) {
            listener.onFailure(new SearchRelevanceException("evaluationResultId must not be null or empty", RestStatus.BAD_REQUEST));
            return null;
        }
        return searchRelevanceIndicesManager.getDocByDocId(evaluationResultId, EVALUATION_RESULT, listener);
    }

    /**
     * List evaluationResult by source builder
     * @param sourceBuilder - source builder to be searched
     * @param listener - action listener for async operation
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

        return searchRelevanceIndicesManager.listDocsBySearchRequest(sourceBuilder, EVALUATION_RESULT, listener);
    }
}
