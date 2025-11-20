/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */
package org.opensearch.searchrelevance.transport.scheduledJob;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.opensearch.action.search.SearchResponse;
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
public class GetScheduledExperimentTransportAction extends HandledTransportAction<OpenSearchDocRequest, SearchResponse> {
    private static final Logger LOGGER = LogManager.getLogger(GetScheduledExperimentTransportAction.class);
    private final ClusterService clusterService;
    private final ScheduledJobsDao scheduledJobsDao;

    @Inject
    public GetScheduledExperimentTransportAction(
        ClusterService clusterService,
        TransportService transportService,
        ActionFilters actionFilters,
        ScheduledJobsDao scheduledJobsDao
    ) {
        super(GetScheduledExperimentAction.NAME, transportService, actionFilters, OpenSearchDocRequest::new);
        this.clusterService = clusterService;
        this.scheduledJobsDao = scheduledJobsDao;
    }

    @Override
    protected void doExecute(Task task, OpenSearchDocRequest request, ActionListener<SearchResponse> listener) {
        try {
            if (request.getId() != null) {
                // Handle single scheduled job request
                scheduledJobsDao.getScheduledJob(request.getId(), listener);
            } else {
                // Handle list request
                scheduledJobsDao.listScheduledJob(request.getSearchSourceBuilder(), listener);
            }
        } catch (Exception e) {
            listener.onFailure(new SearchRelevanceException("Failed to get/list ScheduledJob", e, RestStatus.INTERNAL_SERVER_ERROR));
        }
    }
}
