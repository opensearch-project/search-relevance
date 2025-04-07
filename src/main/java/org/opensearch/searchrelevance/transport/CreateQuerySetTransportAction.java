/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */
package org.opensearch.searchrelevance.transport;

import java.util.UUID;

import org.opensearch.action.StepListener;
import org.opensearch.action.index.IndexResponse;
import org.opensearch.action.support.ActionFilters;
import org.opensearch.action.support.HandledTransportAction;
import org.opensearch.cluster.service.ClusterService;
import org.opensearch.common.inject.Inject;
import org.opensearch.core.action.ActionListener;
import org.opensearch.searchrelevance.dao.QuerySetDao;
import org.opensearch.searchrelevance.model.QuerySet;
import org.opensearch.tasks.Task;
import org.opensearch.transport.TransportService;
import org.opensearch.transport.client.Client;

public class CreateQuerySetTransportAction extends HandledTransportAction<CreateQuerySetRequest, CreateQuerySetResponse> {
    private final Client client;
    private final ClusterService clusterService;

    private final QuerySetDao querySetDao;

    @Inject
    public CreateQuerySetTransportAction(
        ClusterService clusterService,
        TransportService transportService,
        ActionFilters actionFilters,
        Client client
    ) {
        super(CreateQuerySetAction.NAME, transportService, actionFilters, CreateQuerySetRequest::new);
        this.client = client;
        this.clusterService = clusterService;
        this.querySetDao = new QuerySetDao(client, clusterService);
    }

    @Override
    protected void doExecute(Task task, CreateQuerySetRequest request, ActionListener<CreateQuerySetResponse> listener) {
        if (request == null) {
            listener.onFailure(new IllegalArgumentException("Request cannot be null"));
            return;
        }
        try {
            createQuerySet(request, listener);
        } catch (Exception e) {
            listener.onFailure(e);
        }
    }

    private void createQuerySet(CreateQuerySetRequest request, ActionListener<CreateQuerySetResponse> listener) {
        String id = UUID.randomUUID().toString();
        String name = request.getName();
        String description = request.getDescription();

        if (name == null || name.trim().isEmpty()) {
            listener.onFailure(new IllegalArgumentException("Name cannot be null or empty. Request: " + request));
            return;
        }

        StepListener<Void> createIndexStep = new StepListener<>();
        querySetDao.createIndexIfAbsent(createIndexStep);
        createIndexStep.whenComplete(v -> {
            QuerySet querySet = new QuerySet(id, name, description);
            querySetDao.putQuerySet(querySet, getIndexResponseListener(querySet, listener));
        }, listener::onFailure);
    }

    protected ActionListener<IndexResponse> getIndexResponseListener(QuerySet querySet, ActionListener<CreateQuerySetResponse> listener) {
        return new ActionListener<>() {
            @Override
            public void onResponse(final IndexResponse indexResponse) {
                listener.onResponse(new CreateQuerySetResponse(querySet.id()));
            }

            @Override
            public void onFailure(final Exception e) {
                listener.onFailure(e);
            }
        };
    }
}
