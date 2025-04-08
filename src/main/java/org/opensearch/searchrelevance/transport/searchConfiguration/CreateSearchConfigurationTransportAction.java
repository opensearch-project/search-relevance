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
import org.opensearch.transport.client.Client;

public class CreateSearchConfigurationTransportAction extends HandledTransportAction<CreateSearchConfigurationRequest, IndexResponse> {
    private final Client client;
    private final ClusterService clusterService;

    private final SearchConfigurationDao searchConfigurationDao;

    @Inject
    public CreateSearchConfigurationTransportAction(
        ClusterService clusterService,
        TransportService transportService,
        ActionFilters actionFilters,
        Client client
    ) {
        super(CreateSearchConfigurationAction.NAME, transportService, actionFilters, CreateSearchConfigurationRequest::new);
        this.client = client;
        this.clusterService = clusterService;
        this.searchConfigurationDao = new SearchConfigurationDao(client, clusterService);
    }

    @Override
    protected void doExecute(Task task, CreateSearchConfigurationRequest request, ActionListener<IndexResponse> listener) {
        if (request == null) {
            listener.onFailure(new IllegalArgumentException("Request cannot be null"));
            return;
        }
        String id = UUID.randomUUID().toString();
        String timestamp = TimeUtils.getTimestamp();

        StepListener<Void> createIndexStep = new StepListener<>();
        searchConfigurationDao.createIndexIfAbsent(createIndexStep);
        createIndexStep.whenComplete(v -> {
            SearchConfiguration searchConfiguration = new SearchConfiguration(id, timestamp);
            searchConfigurationDao.putSearchConfiguration(searchConfiguration, listener);
        }, listener::onFailure);
    }
}
