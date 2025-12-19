/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */
package org.opensearch.searchrelevance.dao;

import static org.opensearch.searchrelevance.indices.SearchRelevanceIndices.JUDGMENT;

import java.io.IOException;

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
import org.opensearch.searchrelevance.model.Judgment;

public class JudgmentDao {
    private final SearchRelevanceIndicesManager searchRelevanceIndicesManager;

    public JudgmentDao(SearchRelevanceIndicesManager searchRelevanceIndicesManager) {
        this.searchRelevanceIndicesManager = searchRelevanceIndicesManager;
    }

    /**
     * Create judgment index if not exists
     * @param stepListener - step listener for async operation
     */
    public void createIndexIfAbsent(final StepListener<Void> stepListener) {
        searchRelevanceIndicesManager.createIndexIfAbsent(JUDGMENT, stepListener);
    }

    /**
     * Stores judgment to in the system index
     * @param judgment - Judgment content to be stored
     * @param listener - action listener for async operation
     */
    public void putJudgement(final Judgment judgment, final ActionListener listener) {
        if (judgment == null) {
            listener.onFailure(new SearchRelevanceException("Judgment cannot be null", RestStatus.BAD_REQUEST));
            return;
        }
        try {
            searchRelevanceIndicesManager.putDoc(
                judgment.getId(),
                judgment.toXContent(XContentFactory.jsonBuilder(), ToXContent.EMPTY_PARAMS),
                JUDGMENT,
                listener
            );
        } catch (IOException e) {
            throw new SearchRelevanceException("Failed to store judgment", e, RestStatus.INTERNAL_SERVER_ERROR);
        }
    }

    public void updateJudgment(final Judgment judgment, final ActionListener listener) {
        if (judgment == null) {
            listener.onFailure(new SearchRelevanceException("Judgment cannot be null", RestStatus.BAD_REQUEST));
            return;
        }
        try {
            searchRelevanceIndicesManager.updateDoc(
                judgment.getId(),
                judgment.toXContent(XContentFactory.jsonBuilder(), ToXContent.EMPTY_PARAMS),
                JUDGMENT,
                listener
            );
        } catch (IOException e) {
            throw new SearchRelevanceException("Failed to store judgment", e, RestStatus.INTERNAL_SERVER_ERROR);
        }
    }

    /**
     * Delete judgment by judgmentID
     * @param judgmentId - id to be deleted
     * @param listener - action listener for async operation
     */
    public void deleteJudgment(final String judgmentId, final ActionListener<DeleteResponse> listener) {
        searchRelevanceIndicesManager.deleteDocByDocId(judgmentId, JUDGMENT, listener);
    }

    /**
     * Get judgment by judgmentId
     * @param judgmentId - id to be deleted
     * @param listener - action listener for async operation
     */
    public SearchResponse getJudgment(String judgmentId, ActionListener<SearchResponse> listener) {
        if (judgmentId == null || judgmentId.isEmpty()) {
            listener.onFailure(new SearchRelevanceException("judgmentId must not be null or empty", RestStatus.BAD_REQUEST));
            return null;
        }
        return searchRelevanceIndicesManager.getDocByDocId(judgmentId, JUDGMENT, listener);
    }

    /**
     * Get judgment by judgmentId synchronously
     * @param judgmentId - id to be retrieved
     * @return SearchResponse containing the judgment
     */
    public SearchResponse getJudgmentSync(String judgmentId) {
        if (judgmentId == null || judgmentId.isEmpty()) {
            throw new SearchRelevanceException("judgmentId must not be null or empty", RestStatus.BAD_REQUEST);
        }
        return searchRelevanceIndicesManager.getDocByDocIdSync(judgmentId, JUDGMENT);
    }

    /**
     * List judgment by source builder
     * @param sourceBuilder - source builder to be searched
     * @param listener - action listener for async operation
     */
    public SearchResponse listJudgment(SearchSourceBuilder sourceBuilder, ActionListener<SearchResponse> listener) {
        // Apply default values if not set
        if (sourceBuilder == null) {
            sourceBuilder = new SearchSourceBuilder();
        }

        // Ensure we have a query
        if (sourceBuilder.query() == null) {
            sourceBuilder.query(QueryBuilders.matchAllQuery());
        }

        return searchRelevanceIndicesManager.listDocsBySearchRequest(sourceBuilder, JUDGMENT, listener);
    }
}
