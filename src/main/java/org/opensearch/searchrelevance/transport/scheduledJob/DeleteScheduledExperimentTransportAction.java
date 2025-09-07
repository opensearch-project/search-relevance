/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */
package org.opensearch.searchrelevance.transport.scheduledJob;

import org.opensearch.action.delete.DeleteResponse;
import org.opensearch.action.support.ActionFilters;
import org.opensearch.action.support.HandledTransportAction;
import org.opensearch.cluster.service.ClusterService;
import org.opensearch.common.annotation.ExperimentalApi;
import org.opensearch.common.inject.Inject;
import org.opensearch.core.action.ActionListener;
import org.opensearch.core.rest.RestStatus;
import org.opensearch.searchrelevance.dao.ScheduledJobsDao;
import org.opensearch.searchrelevance.exception.SearchRelevanceException;
import org.opensearch.searchrelevance.transport.OpenSearchDocRequest;
import org.opensearch.tasks.Task;
import org.opensearch.transport.TransportService;

@ExperimentalApi
public class DeleteScheduledExperimentTransportAction extends HandledTransportAction<OpenSearchDocRequest, DeleteResponse> {
    private final ClusterService clusterService;
    private final ScheduledJobsDao scheduledJobsDao;

    @Inject
    public DeleteScheduledExperimentTransportAction(
        ClusterService clusterService,
        TransportService transportService,
        ActionFilters actionFilters,
        ScheduledJobsDao scheduledJobsDao
    ) {
        super(DeleteScheduledExperimentAction.NAME, transportService, actionFilters, OpenSearchDocRequest::new);
        this.clusterService = clusterService;
        this.scheduledJobsDao = scheduledJobsDao;
    }

    @Override
    protected void doExecute(Task task, OpenSearchDocRequest request, ActionListener<DeleteResponse> listener) {
        try {
            String jobId = request.getId();
            if (jobId == null || jobId.trim().isEmpty()) {
                listener.onFailure(new SearchRelevanceException("Query set ID cannot be null or empty", RestStatus.BAD_REQUEST));
                return;
            }
            scheduledJobsDao.deleteScheduledJob(jobId, listener);
        } catch (Exception e) {
            listener.onFailure(e);
        }
    }
}
