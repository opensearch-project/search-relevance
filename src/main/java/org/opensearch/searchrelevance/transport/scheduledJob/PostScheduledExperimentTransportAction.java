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
import java.util.List;
import java.util.Map;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.opensearch.action.index.IndexResponse;
import org.opensearch.action.search.SearchResponse;
import org.opensearch.action.support.ActionFilters;
import org.opensearch.action.support.HandledTransportAction;
import org.opensearch.cluster.service.ClusterService;
import org.opensearch.common.annotation.ExperimentalApi;
import org.opensearch.common.inject.Inject;
import org.opensearch.core.action.ActionListener;
import org.opensearch.core.rest.RestStatus;
import org.opensearch.jobscheduler.spi.schedule.CronSchedule;
import org.opensearch.jobscheduler.spi.schedule.Schedule;
import org.opensearch.searchrelevance.dao.ExperimentDao;
import org.opensearch.searchrelevance.dao.ScheduledJobsDao;
import org.opensearch.searchrelevance.exception.SearchRelevanceException;
import org.opensearch.searchrelevance.model.AsyncStatus;
import org.opensearch.searchrelevance.model.Experiment;
import org.opensearch.searchrelevance.model.ExperimentType;
import org.opensearch.searchrelevance.model.ScheduledJob;
import org.opensearch.searchrelevance.utils.TimeUtils;
import org.opensearch.tasks.Task;
import org.opensearch.transport.TransportService;

@ExperimentalApi
public class PostScheduledExperimentTransportAction extends HandledTransportAction<PostScheduledExperimentRequest, IndexResponse> {
    private final ClusterService clusterService;
    private final ScheduledJobsDao scheduledJobsDao;
    private final ExperimentDao experimentDao;

    private static final Logger LOGGER = LogManager.getLogger(PostScheduledExperimentTransportAction.class);

    @Inject
    public PostScheduledExperimentTransportAction(
        ClusterService clusterService,
        TransportService transportService,
        ActionFilters actionFilters,
        ScheduledJobsDao scheduledJobsDao,
        ExperimentDao experimentDao
    ) {
        super(PostScheduledExperimentAction.NAME, transportService, actionFilters, PostScheduledExperimentRequest::new);
        this.clusterService = clusterService;
        this.scheduledJobsDao = scheduledJobsDao;
        this.experimentDao = experimentDao;
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

            ScheduledJob job = new ScheduledJob(id, now, now, true, schedule, TimeUtils.getTimestamp());

            experimentDao.getExperiment(experimentId, ActionListener.wrap(experimentResponse -> {
                Experiment updatedExperiment = convertToExperiment(experimentResponse);
                final Experiment finalUpdatedExperiment = new Experiment(updatedExperiment, true);
                scheduledJobsDao.putScheduledJob(job, ActionListener.wrap(indexResponse -> {
                    experimentDao.updateExperiment(finalUpdatedExperiment, ActionListener.wrap(experimentIndexResponse -> {
                        // Return response immediately
                        listener.onResponse((IndexResponse) indexResponse);
                    }, e -> {
                        LOGGER.error("Failed to update experiment to scheduled");
                        listener.onFailure(
                            new SearchRelevanceException("Failed to update experiment to scheduled", e, RestStatus.INTERNAL_SERVER_ERROR)
                        );
                    }));
                }, e -> {
                    LOGGER.error("Failed to index job", e);
                    listener.onFailure(new SearchRelevanceException("Failed to index job", e, RestStatus.INTERNAL_SERVER_ERROR));
                }));
            }, e -> {
                LOGGER.error("No experiment underlying experiment provided with id {}", experimentId);
                listener.onFailure(new SearchRelevanceException("Failed to find experiment", RestStatus.INTERNAL_SERVER_ERROR));
            }));

        } catch (Exception e) {
            LOGGER.error("Failed to process job request", e);
            listener.onFailure(new SearchRelevanceException("Failed to process job request", e, RestStatus.INTERNAL_SERVER_ERROR));
        }
    }

    private Experiment convertToExperiment(SearchResponse response) {
        if (response.getHits().getTotalHits().value() == 0) {
            throw new SearchRelevanceException("QuerySet not found", RestStatus.NOT_FOUND);
        }

        Map<String, Object> sourceMap = response.getHits().getHits()[0].getSourceAsMap();

        return new Experiment(
            (String) sourceMap.get("id"),
            TimeUtils.getTimestamp(),
            ExperimentType.valueOf((String) sourceMap.get("type")),
            AsyncStatus.valueOf((String) sourceMap.get("status")),
            (String) sourceMap.get("querySetId"),
            (List<String>) sourceMap.get("searchConfigurationList"),
            (List<String>) sourceMap.get("judgmentList"),
            (int) sourceMap.get("size"),
            (List<Map<String, Object>>) sourceMap.get("results")
        );
    }
}
