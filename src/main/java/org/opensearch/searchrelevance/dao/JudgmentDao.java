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
import org.opensearch.searchrelevance.model.Judgment;
import org.opensearch.transport.client.Client;

import reactor.util.annotation.NonNull;

public class JudgmentDao {
    private static final Logger LOGGER = LogManager.getLogger(JudgmentDao.class);
    private final Client client;
    private final ClusterService clusterService;
    private final SearchRelevanceIndicesManager searchRelevanceIndicesManager;

    public JudgmentDao(@NonNull Client client, @NonNull ClusterService clusterService) {
        this.client = client;
        this.clusterService = clusterService;
        this.searchRelevanceIndicesManager = new SearchRelevanceIndicesManager(clusterService, client);
    }

    /**
     * Create judgment index if not exists
     * @param stepListener - step lister for async operation
     */
    public void createIndexIfAbsent(final StepListener<Void> stepListener) {
        searchRelevanceIndicesManager.createIndexIfAbsent(JUDGMENT, stepListener);
    }

    /**
     * Stores judgment to in the system index
     * @param judgment - Judgment content to be stored
     * @param listener - action lister for async operation
     */
    public void putJudgement(final Judgment judgment, final ActionListener listener) {
        if (judgment == null) {
            listener.onFailure(new IllegalArgumentException("Judgment cannot be null"));
            return;
        }
        try {
            searchRelevanceIndicesManager.putDoc(
                judgment.id(),
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
     * @param listener - action lister for async operation
     */
    public void deleteJudgment(final String judgmentId, final ActionListener<DeleteResponse> listener) {
        searchRelevanceIndicesManager.deleteDocByDocId(judgmentId, JUDGMENT, listener);
    }

    /**
     * Get judgment by judgmentId
     * @param judgmentId - id to be deleted
     * @param listener - action lister for async operation
     */
    public void getJudgment(String judgmentId, ActionListener<SearchResponse> listener) {
        searchRelevanceIndicesManager.getDocByDocId(judgmentId, JUDGMENT, listener);
    }

    /**
     * List judgment by source builder
     * @param sourceBuilder - source builder to be searched
     * @param listener - action lister for async operation
     */
    public void listJudgment(SearchSourceBuilder sourceBuilder, ActionListener<SearchResponse> listener) {
        // Apply default values if not set
        if (sourceBuilder == null) {
            sourceBuilder = new SearchSourceBuilder();
        }

        // Ensure we have a query
        if (sourceBuilder.query() == null) {
            sourceBuilder.query(QueryBuilders.matchAllQuery());
        }

        searchRelevanceIndicesManager.listDocsBySearchRequest(sourceBuilder, JUDGMENT, listener);
    }
}
