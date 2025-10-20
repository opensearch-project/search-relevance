/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */
package org.opensearch.searchrelevance.dao;

import static org.opensearch.searchrelevance.indices.SearchRelevanceIndices.SCHEDULED_EXPERIMENT_HISTORY;

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
import org.opensearch.searchrelevance.model.ScheduledExperimentResult;

/**
 * Data access object layer for scheduled experiment history
 * index that stores the results of experiments created based
 * on a scheduled job.
 */
public class ScheduledExperimentHistoryDao {
    private static final Logger LOGGER = LogManager.getLogger(ScheduledExperimentHistoryDao.class);
    private final SearchRelevanceIndicesManager searchRelevanceIndicesManager;

    public ScheduledExperimentHistoryDao(SearchRelevanceIndicesManager searchRelevanceIndicesManager) {
        this.searchRelevanceIndicesManager = searchRelevanceIndicesManager;
    }

    /**
     * Create scheduled experiment history index if not exists
     * @param stepListener - step lister for async operation
     */
    public void createIndexIfAbsent(final StepListener<Void> stepListener) {
        searchRelevanceIndicesManager.createIndexIfAbsent(SCHEDULED_EXPERIMENT_HISTORY, stepListener);
    }

    /**
     * Stores scheduled experiment result in the system index
     * @param scheduledExperimentResult - Scheduled experiment result content to be stored
     * @param listener - action lister for async operation
     */
    public void putScheduledExperimentResult(final ScheduledExperimentResult scheduledExperimentResult, final ActionListener listener) {
        if (scheduledExperimentResult == null) {
            listener.onFailure(new SearchRelevanceException("Scheduled experiment result cannot be null", RestStatus.BAD_REQUEST));
            return;
        }
        try {
            searchRelevanceIndicesManager.putDoc(
                scheduledExperimentResult.getId(),
                scheduledExperimentResult.toXContent(XContentFactory.jsonBuilder(), ToXContent.EMPTY_PARAMS),
                SCHEDULED_EXPERIMENT_HISTORY,
                listener
            );
        } catch (IOException e) {
            throw new SearchRelevanceException("Failed to store scheduled experiment result", e, RestStatus.INTERNAL_SERVER_ERROR);
        }
    }

    public void updateScheduledExperimentResult(final ScheduledExperimentResult scheduledExperimentResult, final ActionListener listener) {
        if (scheduledExperimentResult == null) {
            listener.onFailure(new SearchRelevanceException("Scheduled experiment result cannot be null", RestStatus.BAD_REQUEST));
            return;
        }
        try {
            searchRelevanceIndicesManager.updateDoc(
                scheduledExperimentResult.getId(),
                scheduledExperimentResult.toXContent(XContentFactory.jsonBuilder(), ToXContent.EMPTY_PARAMS),
                SCHEDULED_EXPERIMENT_HISTORY,
                listener
            );
        } catch (IOException e) {
            throw new SearchRelevanceException("Failed to store scheduled experiment result", e, RestStatus.INTERNAL_SERVER_ERROR);
        }
    }

    /**
     * Delete scheduled experiment result by scheduledExperimentResultId
     * @param scheduledExperimentResultId - id to be deleted
     * @param listener - action lister for async operation
     */
    public void deleteScheduledExperimentResult(final String scheduledExperimentResultId, final ActionListener<DeleteResponse> listener) {
        searchRelevanceIndicesManager.deleteDocByDocId(scheduledExperimentResultId, SCHEDULED_EXPERIMENT_HISTORY, listener);
    }

    /**
     * Get scheduled experiment result by scheduledExperimentResultId
     * @param scheduledExperimentResultId - id to be deleted
     * @param listener - action lister for async operation
     */
    public SearchResponse getScheduledExperimentResult(String scheduledExperimentResultId, ActionListener<SearchResponse> listener) {
        if (scheduledExperimentResultId == null || scheduledExperimentResultId.isEmpty()) {
            listener.onFailure(
                new SearchRelevanceException("scheduledExperimentResultId must not be null or empty", RestStatus.BAD_REQUEST)
            );
            return null;
        }
        return searchRelevanceIndicesManager.getDocByDocId(scheduledExperimentResultId, SCHEDULED_EXPERIMENT_HISTORY, listener);
    }

    /**
     * List scheduled experiment result by source builder
     * @param sourceBuilder - source builder to be searched
     * @param listener - action lister for async operation
     */
    public SearchResponse listScheduledExperimentResult(SearchSourceBuilder sourceBuilder, ActionListener<SearchResponse> listener) {
        // Apply default values if not set
        if (sourceBuilder == null) {
            sourceBuilder = new SearchSourceBuilder();
        }

        // Ensure we have a query
        if (sourceBuilder.query() == null) {
            sourceBuilder.query(QueryBuilders.matchAllQuery());
        }

        return searchRelevanceIndicesManager.listDocsBySearchRequest(sourceBuilder, SCHEDULED_EXPERIMENT_HISTORY, listener);
    }
}
