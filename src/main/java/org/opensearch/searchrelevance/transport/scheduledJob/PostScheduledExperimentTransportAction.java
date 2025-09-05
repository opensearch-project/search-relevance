/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */
package org.opensearch.searchrelevance.transport.scheduledJob;

import java.time.Instant;
import java.time.ZoneId;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.opensearch.action.index.IndexResponse;
import org.opensearch.action.support.ActionFilters;
import org.opensearch.action.support.HandledTransportAction;
import org.opensearch.cluster.service.ClusterService;
import org.opensearch.common.inject.Inject;
import org.opensearch.core.action.ActionListener;
import org.opensearch.core.rest.RestStatus;
import org.opensearch.jobscheduler.spi.schedule.CronSchedule;
import org.opensearch.jobscheduler.spi.schedule.Schedule;
import org.opensearch.searchrelevance.dao.ScheduledJobsDao;
import org.opensearch.searchrelevance.exception.SearchRelevanceException;
import org.opensearch.searchrelevance.model.ScheduledJob;
import org.opensearch.tasks.Task;
import org.opensearch.transport.TransportService;

public class PostScheduledExperimentTransportAction extends HandledTransportAction<PostScheduledExperimentRequest, IndexResponse> {
    private final ClusterService clusterService;
    private final ScheduledJobsDao scheduledJobsDao;

    private static final Logger LOGGER = LogManager.getLogger(PostScheduledExperimentTransportAction.class);

    @Inject
    public PostScheduledExperimentTransportAction(
        ClusterService clusterService,
        TransportService transportService,
        ActionFilters actionFilters,
        ScheduledJobsDao scheduledJobsDao
    ) {
        super(PostScheduledExperimentAction.NAME, transportService, actionFilters, PostScheduledExperimentRequest::new);
        this.clusterService = clusterService;
        this.scheduledJobsDao = scheduledJobsDao;
    }

    @Override
    protected void doExecute(Task task, PostScheduledExperimentRequest request, ActionListener<IndexResponse> listener) {
        if (request == null) {
            listener.onFailure(new SearchRelevanceException("Request cannot be null", RestStatus.BAD_REQUEST));
            return;
        }
        try {
            String experimentId = request.getExperimentId();
            String cronExpression = request.getCronExpression();
            Schedule schedule = new CronSchedule(cronExpression, ZoneId.systemDefault());
            String id = experimentId; // Since there is at most 1 scheduled job per experiment, the ids could be the same.

            Instant now = Instant.now();

            ScheduledJob job = new ScheduledJob(id, now, now, true, schedule);

            scheduledJobsDao.putScheduledJob(job, ActionListener.wrap(response -> {
                // Return response immediately
                listener.onResponse((IndexResponse) response);
            }, e -> {
                LOGGER.error("Failed to index job", e);
                listener.onFailure(new SearchRelevanceException("Failed to index job", e, RestStatus.INTERNAL_SERVER_ERROR));
            }));

        } catch (Exception e) {
            LOGGER.error("Failed to process job request", e);
            listener.onFailure(new SearchRelevanceException("Failed to process job request", e, RestStatus.INTERNAL_SERVER_ERROR));
        }
    }
}
