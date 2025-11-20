/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */
package org.opensearch.searchrelevance.transport.scheduledJob;

import java.util.List;
import java.util.Map;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.opensearch.action.delete.DeleteResponse;
import org.opensearch.action.search.SearchResponse;
import org.opensearch.action.support.ActionFilters;
import org.opensearch.action.support.HandledTransportAction;
import org.opensearch.cluster.service.ClusterService;
import org.opensearch.common.annotation.ExperimentalApi;
import org.opensearch.common.inject.Inject;
import org.opensearch.core.action.ActionListener;
import org.opensearch.core.rest.RestStatus;
import org.opensearch.searchrelevance.dao.ExperimentDao;
import org.opensearch.searchrelevance.dao.ScheduledJobsDao;
import org.opensearch.searchrelevance.exception.SearchRelevanceException;
import org.opensearch.searchrelevance.model.AsyncStatus;
import org.opensearch.searchrelevance.model.Experiment;
import org.opensearch.searchrelevance.model.ExperimentType;
import org.opensearch.searchrelevance.transport.OpenSearchDocRequest;
import org.opensearch.searchrelevance.utils.TimeUtils;
import org.opensearch.tasks.Task;
import org.opensearch.transport.TransportService;

@ExperimentalApi
public class DeleteScheduledExperimentTransportAction extends HandledTransportAction<OpenSearchDocRequest, DeleteResponse> {
    private final ClusterService clusterService;
    private final ScheduledJobsDao scheduledJobsDao;
    private final ExperimentDao experimentDao;

    private static final Logger LOGGER = LogManager.getLogger(PostScheduledExperimentTransportAction.class);

    @Inject
    public DeleteScheduledExperimentTransportAction(
        ClusterService clusterService,
        TransportService transportService,
        ActionFilters actionFilters,
        ScheduledJobsDao scheduledJobsDao,
        ExperimentDao experimentDao
    ) {
        super(DeleteScheduledExperimentAction.NAME, transportService, actionFilters, OpenSearchDocRequest::new);
        this.clusterService = clusterService;
        this.scheduledJobsDao = scheduledJobsDao;
        this.experimentDao = experimentDao;
    }

    @Override
    protected void doExecute(Task task, OpenSearchDocRequest request, ActionListener<DeleteResponse> listener) {
        try {
            String jobId = request.getId();
            if (jobId == null || jobId.trim().isEmpty()) {
                listener.onFailure(new SearchRelevanceException("Query set ID cannot be null or empty", RestStatus.BAD_REQUEST));
                return;
            }
            scheduledJobsDao.deleteScheduledJob(jobId, ActionListener.wrap(deleteResponse -> {
                experimentDao.getExperiment(jobId, ActionListener.wrap(experimentResponse -> {
                    Experiment updatedExperiment = convertToExperiment(experimentResponse);
                    final Experiment finalUpdatedExperiment = new Experiment(updatedExperiment, false);
                    experimentDao.updateExperiment(finalUpdatedExperiment, ActionListener.wrap(experimentIndexResponse -> {
                        // Return delete response
                        listener.onResponse(deleteResponse);
                    }, e -> {
                        LOGGER.error("Failed to update experiment to descheduled");
                        listener.onFailure(
                            new SearchRelevanceException("Failed to update experiment to descheduled", e, RestStatus.INTERNAL_SERVER_ERROR)
                        );
                    }));
                }, e -> {
                    LOGGER.error("Underlying job does not exist. Scheduled job will be cleaned anyways");
                    listener.onFailure(
                        new SearchRelevanceException(
                            "Underlying job does not exist. Scheduled job will be cleaned anyways",
                            e,
                            RestStatus.INTERNAL_SERVER_ERROR
                        )
                    );
                }));
            }, e -> {
                LOGGER.error("Failed to delete experiment to scheduled job");
                listener.onFailure(
                    new SearchRelevanceException("Failed to delete experiment to scheduled job", e, RestStatus.INTERNAL_SERVER_ERROR)
                );
            }));
        } catch (Exception e) {
            listener.onFailure(e);
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
