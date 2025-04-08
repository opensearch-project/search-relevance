/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */
package org.opensearch.searchrelevance.transport.queryset;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
import org.opensearch.searchrelevance.utils.TimeUtils;
import org.opensearch.tasks.Task;
import org.opensearch.transport.TransportService;
import org.opensearch.transport.client.Client;

public class PutQuerySetTransportAction extends HandledTransportAction<PutQuerySetRequest, IndexResponse> {
    private final Client client;
    private final ClusterService clusterService;

    private final QuerySetDao querySetDao;

    @Inject
    public PutQuerySetTransportAction(
        ClusterService clusterService,
        TransportService transportService,
        ActionFilters actionFilters,
        Client client
    ) {
        super(PutQuerySetAction.NAME, transportService, actionFilters, PutQuerySetRequest::new);
        this.client = client;
        this.clusterService = clusterService;
        this.querySetDao = new QuerySetDao(client, clusterService);
    }

    @Override
    protected void doExecute(Task task, PutQuerySetRequest request, ActionListener<IndexResponse> listener) {
        if (request == null) {
            listener.onFailure(new IllegalArgumentException("Request cannot be null"));
            return;
        }
        String id = UUID.randomUUID().toString();
        String timestamp = TimeUtils.getTimestamp();

        String name = request.getName();
        String description = request.getDescription();

        // Given sampling type by default "manual" to support manually uploaded querySetQueries.
        String sampling = request.getSampling();
        if (!"manual".equals(sampling)) {
            listener.onFailure(new IllegalArgumentException("Support sampling as manual only. sampling: " + sampling));
        }
        String querySetQueriesStr = request.getQuerySetQueries();
        Map<String, Long> querySetQueries = convertQuerySetQueriesMap(querySetQueriesStr);

        StepListener<Void> createIndexStep = new StepListener<>();
        querySetDao.createIndexIfAbsent(createIndexStep);
        createIndexStep.whenComplete(v -> {
            QuerySet querySet = new QuerySet(id, name, description, sampling, timestamp, querySetQueries);
            querySetDao.putQuerySet(querySet, listener);
        }, listener::onFailure);
    }

    /**
     * Query set input is a list of query set text split with comma.
     * e.g: "querySetQueries": "apple, banana, orange"
     * @param querySetQueriesStr - input
     * @return - querySetQueries as a map of string and long
     */
    private Map<String, Long> convertQuerySetQueriesMap(String querySetQueriesStr) {
        List<String> querySetInputs = List.of(querySetQueriesStr.split(","));
        Map<String, Long> result = new HashMap<>();
        querySetInputs.forEach(e -> { result.put(e.trim(), 0L); });
        return result;
    }
}
