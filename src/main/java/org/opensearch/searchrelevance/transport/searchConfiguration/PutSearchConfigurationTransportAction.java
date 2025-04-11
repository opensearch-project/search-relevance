/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */
package org.opensearch.searchrelevance.transport.searchConfiguration;

import java.util.UUID;

import org.opensearch.action.StepListener;
import org.opensearch.action.index.IndexResponse;
import org.opensearch.action.support.ActionFilters;
import org.opensearch.action.support.HandledTransportAction;
import org.opensearch.cluster.service.ClusterService;
import org.opensearch.common.inject.Inject;
import org.opensearch.core.action.ActionListener;
import org.opensearch.searchrelevance.dao.SearchConfigurationDao;
import org.opensearch.searchrelevance.model.SearchConfiguration;
import org.opensearch.searchrelevance.utils.TimeUtils;
import org.opensearch.tasks.Task;
import org.opensearch.transport.TransportService;

public class PutSearchConfigurationTransportAction extends HandledTransportAction<PutSearchConfigurationRequest, IndexResponse> {
    private final ClusterService clusterService;

    private final SearchConfigurationDao searchConfigurationDao;

    @Inject
    public PutSearchConfigurationTransportAction(
        ClusterService clusterService,
        TransportService transportService,
        ActionFilters actionFilters,
        SearchConfigurationDao searchConfigurationDao
    ) {
        super(PutSearchConfigurationAction.NAME, transportService, actionFilters, PutSearchConfigurationRequest::new);
        this.clusterService = clusterService;
        this.searchConfigurationDao = searchConfigurationDao;
    }

    @Override
    protected void doExecute(Task task, PutSearchConfigurationRequest request, ActionListener<IndexResponse> listener) {
        if (request == null) {
            listener.onFailure(new IllegalArgumentException("Request cannot be null"));
            return;
        }
        String id = UUID.randomUUID().toString();
        String timestamp = TimeUtils.getTimestamp();

        String name = request.getName();
        if (name == null || name.trim().isEmpty()) {
            listener.onFailure(new IllegalArgumentException("Name cannot be null or empty. Request: " + request));
            return;
        }
        String queryBody = request.getQueryBody();
        String searchPipeline = request.getSearchPipeline();

        StepListener<Void> createIndexStep = new StepListener<>();
        searchConfigurationDao.createIndexIfAbsent(createIndexStep);
        createIndexStep.whenComplete(v -> {
            SearchConfiguration searchConfiguration = new SearchConfiguration(id, name, timestamp, queryBody, searchPipeline);
            searchConfigurationDao.putSearchConfiguration(searchConfiguration, listener);
        }, listener::onFailure);
    }
}
