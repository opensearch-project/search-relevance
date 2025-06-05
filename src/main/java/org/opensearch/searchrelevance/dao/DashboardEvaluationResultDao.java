/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */
package org.opensearch.searchrelevance.dao;

import static org.opensearch.searchrelevance.indices.SearchRelevanceIndices.DASHBOARD_EVALUATION_RESULT;

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
import org.opensearch.searchrelevance.model.DashboardEvaluationResult;

public class DashboardEvaluationResultDao {
    private static final Logger LOGGER = LogManager.getLogger(DashboardEvaluationResultDao.class);
    private final SearchRelevanceIndicesManager searchRelevanceIndicesManager;

    public DashboardEvaluationResultDao(SearchRelevanceIndicesManager searchRelevanceIndicesManager) {
        this.searchRelevanceIndicesManager = searchRelevanceIndicesManager;
    }

    /**
     * Create dashboard evaluation result index if not exists
     * @param stepListener - step listener for async operation
     */
    public void createIndexIfAbsent(final StepListener<Void> stepListener) {
        searchRelevanceIndicesManager.createIndexIfAbsent(DASHBOARD_EVALUATION_RESULT, stepListener);
    }

    /**
     * Stores dashboard evaluation result in the system index
     * @param dashboardEvaluationResult - DashboardEvaluationResult content to be stored
     * @param listener - action listener for async operation
     */
    public void putDashboardEvaluationResult(final DashboardEvaluationResult dashboardEvaluationResult, final ActionListener listener) {
        if (dashboardEvaluationResult == null) {
            listener.onFailure(new SearchRelevanceException("DashboardEvaluationResult cannot be null", RestStatus.BAD_REQUEST));
            return;
        }
        if (dashboardEvaluationResult.id() == null || dashboardEvaluationResult.id().isEmpty()) {
            listener.onFailure(new SearchRelevanceException("Document ID cannot be null or empty", RestStatus.BAD_REQUEST));
            return;
        }
        try {
            searchRelevanceIndicesManager.putDoc(
                dashboardEvaluationResult.id(),
                dashboardEvaluationResult.toXContent(XContentFactory.jsonBuilder(), ToXContent.EMPTY_PARAMS),
                DASHBOARD_EVALUATION_RESULT,
                listener
            );
        } catch (IOException e) {
            throw new SearchRelevanceException("Failed to store dashboardEvaluationResult", e, RestStatus.INTERNAL_SERVER_ERROR);
        }
    }

    /**
     * Delete dashboard evaluation result by document ID
     * @param id - document ID to be deleted
     * @param listener - action listener for async operation
     */
    public void deleteDashboardEvaluationResult(final String id, final ActionListener<DeleteResponse> listener) {
        if (id == null || id.isEmpty()) {
            listener.onFailure(new SearchRelevanceException("Document ID must not be null or empty", RestStatus.BAD_REQUEST));
            return;
        }
        searchRelevanceIndicesManager.deleteDocByDocId(id, DASHBOARD_EVALUATION_RESULT, listener);
    }

    /**
     * Get dashboard evaluation result by document ID
     * @param id - document ID to be retrieved
     * @param listener - action listener for async operation
     */
    public SearchResponse getDashboardEvaluationResult(String id, ActionListener<SearchResponse> listener) {
        if (id == null || id.isEmpty()) {
            listener.onFailure(new SearchRelevanceException("Document ID must not be null or empty", RestStatus.BAD_REQUEST));
            return null;
        }
        return searchRelevanceIndicesManager.getDocByDocId(id, DASHBOARD_EVALUATION_RESULT, listener);
    }

    /**
     * List dashboard evaluation results by source builder
     * @param sourceBuilder - source builder to be searched
     * @param listener - action listener for async operation
     */
    public SearchResponse listDashboardEvaluationResults(SearchSourceBuilder sourceBuilder, ActionListener<SearchResponse> listener) {
        // Apply default values if not set
        if (sourceBuilder == null) {
            sourceBuilder = new SearchSourceBuilder();
        }

        // Ensure we have a query
        if (sourceBuilder.query() == null) {
            sourceBuilder.query(QueryBuilders.matchAllQuery());
        }

        return searchRelevanceIndicesManager.listDocsBySearchRequest(sourceBuilder, DASHBOARD_EVALUATION_RESULT, listener);
    }
} 