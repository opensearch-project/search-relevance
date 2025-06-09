/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */
package org.opensearch.searchrelevance.transport.queryset;

import org.opensearch.action.index.IndexResponse;
import org.opensearch.action.support.ActionFilters;
import org.opensearch.action.support.HandledTransportAction;
import org.opensearch.cluster.service.ClusterService;
import org.opensearch.common.inject.Inject;
import org.opensearch.core.action.ActionListener;
import org.opensearch.core.rest.RestStatus;
import org.opensearch.searchrelevance.dao.QuerySetDao;
import org.opensearch.searchrelevance.exception.SearchRelevanceException;
import org.opensearch.searchrelevance.model.QuerySet;
import org.opensearch.searchrelevance.model.QuerySetEntry;
import org.opensearch.searchrelevance.ubi.QuerySampler;
import org.opensearch.searchrelevance.utils.TimeUtils;
import org.opensearch.tasks.Task;
import org.opensearch.transport.TransportService;
import org.opensearch.transport.client.Client;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;

public class PostQuerySetTransportAction extends HandledTransportAction<PostQuerySetRequest, IndexResponse> {
    private final Client client;
    private final ClusterService clusterService;
    private final QuerySetDao querySetDao;

    @Inject
    public PostQuerySetTransportAction(
        ClusterService clusterService,
        TransportService transportService,
        ActionFilters actionFilters,
        Client client,
        QuerySetDao querySetDao
    ) {
        super(PostQuerySetAction.NAME, transportService, actionFilters, PostQuerySetRequest::new);
        this.client = client;
        this.clusterService = clusterService;
        this.querySetDao = querySetDao;
    }

    @Override
    protected void doExecute(Task task, PostQuerySetRequest request, ActionListener<IndexResponse> listener) {
        if (request == null) {
            listener.onFailure(new SearchRelevanceException("Request cannot be null", RestStatus.BAD_REQUEST));
            return;
        }
        String id = UUID.randomUUID().toString();
        String timestamp = TimeUtils.getTimestamp();

        String name = request.getName();
        String description = request.getDescription();

        // Given sampling type and querySetSize, build the queryset accordingly
        String sampling = request.getSampling();
        int querySetSize = request.getQuerySetSize();
        QuerySampler querySampler = QuerySampler.create(sampling, querySetSize, client);
        Map<String, Integer> querySetQueries = new HashMap<>();
        try {
            querySetQueries = querySampler.sample().get();
        } catch (InterruptedException | ExecutionException e) {
            listener.onFailure(
                new SearchRelevanceException("Failed to build querySetQueries. Request: " + request, RestStatus.BAD_REQUEST)
            );
        }

        if (name == null || name.trim().isEmpty()) {
            listener.onFailure(new SearchRelevanceException("Name cannot be null or empty. Request: " + request, RestStatus.BAD_REQUEST));
            return;
        }

        // Convert Map<String, Integer> to List<QuerySetEntry> (discarding count values)
        List<QuerySetEntry> querySetEntries = querySetQueries.entrySet()
            .stream()
            .map(entry -> QuerySetEntry.Builder.builder().queryText(entry.getKey()).build())
            .collect(Collectors.toList());

        QuerySet querySet = new QuerySet(id, name, description, timestamp, sampling, querySetEntries);
        querySetDao.putQuerySet(querySet, listener);
    }
}
