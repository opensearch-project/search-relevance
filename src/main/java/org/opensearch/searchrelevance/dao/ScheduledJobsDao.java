/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */
package org.opensearch.searchrelevance.dao;

import static org.opensearch.searchrelevance.indices.SearchRelevanceIndices.SCHEDULED_JOBS;

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
import org.opensearch.searchrelevance.model.ScheduledJob;

/**
 * Data access object layer for index storing schedules
 * of experiment runs.
 */
public class ScheduledJobsDao {
    private static final Logger LOGGER = LogManager.getLogger(ScheduledJobsDao.class);
    private final SearchRelevanceIndicesManager searchRelevanceIndicesManager;

    public ScheduledJobsDao(SearchRelevanceIndicesManager searchRelevanceIndicesManager) {
        this.searchRelevanceIndicesManager = searchRelevanceIndicesManager;
    }

    /**
     * Create scheduled jobs index if not exists
     * @param stepListener - step lister for async operation
     */
    public void createIndexIfAbsent(final StepListener<Void> stepListener) {
        searchRelevanceIndicesManager.createIndexIfAbsent(SCHEDULED_JOBS, stepListener);
    }

    /**
     * Stores scheduled job to in the system index
     * @param scheduledJob - Scheduled job content to be stored
     * @param listener - action lister for async operation
     */
    public void putScheduledJob(final ScheduledJob scheduledJob, final ActionListener listener) {
        if (scheduledJob == null) {
            listener.onFailure(new SearchRelevanceException("Scheduled job cannot be null", RestStatus.BAD_REQUEST));
            return;
        }
        try {
            searchRelevanceIndicesManager.putDoc(
                scheduledJob.getId(),
                scheduledJob.toXContent(XContentFactory.jsonBuilder(), ToXContent.EMPTY_PARAMS),
                SCHEDULED_JOBS,
                listener
            );
        } catch (IOException e) {
            throw new SearchRelevanceException("Failed to store scheduled job", e, RestStatus.INTERNAL_SERVER_ERROR);
        }
    }

    public void updateScheduledJob(final ScheduledJob scheduledJob, final ActionListener listener) {
        if (scheduledJob == null) {
            listener.onFailure(new SearchRelevanceException("Scheduled job cannot be null", RestStatus.BAD_REQUEST));
            return;
        }
        try {
            searchRelevanceIndicesManager.updateDoc(
                scheduledJob.getId(),
                scheduledJob.toXContent(XContentFactory.jsonBuilder(), ToXContent.EMPTY_PARAMS),
                SCHEDULED_JOBS,
                listener
            );
        } catch (IOException e) {
            throw new SearchRelevanceException("Failed to store scheduled job", e, RestStatus.INTERNAL_SERVER_ERROR);
        }
    }

    /**
     * Delete scheduled job by scheduledJobId
     * @param scheduledJobId - id to be deleted
     * @param listener - action lister for async operation
     */
    public void deleteScheduledJob(final String scheduledJobId, final ActionListener<DeleteResponse> listener) {
        searchRelevanceIndicesManager.deleteDocByDocId(scheduledJobId, SCHEDULED_JOBS, listener);
    }

    /**
     * Get scheduled job by scheduledJobId
     * @param scheduledJobId - id to be deleted
     * @param listener - action lister for async operation
     */
    public SearchResponse getScheduledJob(String scheduledJobId, ActionListener<SearchResponse> listener) {
        if (scheduledJobId == null || scheduledJobId.isEmpty()) {
            listener.onFailure(new SearchRelevanceException("jobId must not be null or empty", RestStatus.BAD_REQUEST));
            return null;
        }
        return searchRelevanceIndicesManager.getDocByDocId(scheduledJobId, SCHEDULED_JOBS, listener);
    }

    /**
     * List scheduled jobs by source builder
     * @param sourceBuilder - source builder to be searched
     * @param listener - action listener for async operation
     */
    public SearchResponse listScheduledJob(SearchSourceBuilder sourceBuilder, ActionListener<SearchResponse> listener) {
        // Apply default values if not set
        if (sourceBuilder == null) {
            sourceBuilder = new SearchSourceBuilder();
        }

        // Ensure we have a query
        if (sourceBuilder.query() == null) {
            sourceBuilder.query(QueryBuilders.matchAllQuery());
        }

        return searchRelevanceIndicesManager.listDocsBySearchRequest(sourceBuilder, SCHEDULED_JOBS, listener);
    }
}
