/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */
package org.opensearch.searchrelevance.transport.queryset;

import static org.opensearch.searchrelevance.ubi.UbiValidator.validateUbiQueriesIndex;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import org.opensearch.ExceptionsHelper;
import org.opensearch.action.index.IndexResponse;
import org.opensearch.action.support.ActionFilters;
import org.opensearch.action.support.HandledTransportAction;
import org.opensearch.cluster.metadata.IndexNameExpressionResolver;
import org.opensearch.cluster.service.ClusterService;
import org.opensearch.common.inject.Inject;
import org.opensearch.core.action.ActionListener;
import org.opensearch.core.rest.RestStatus;
import org.opensearch.searchrelevance.dao.QuerySetDao;
import org.opensearch.searchrelevance.exception.SearchRelevanceException;
import org.opensearch.searchrelevance.model.QuerySet;
import org.opensearch.searchrelevance.model.QuerySetEntry;
import org.opensearch.searchrelevance.ubi.QuerySampler;
import org.opensearch.searchrelevance.ubi.UbiValidator;
import org.opensearch.searchrelevance.utils.TimeUtils;
import org.opensearch.tasks.Task;
import org.opensearch.transport.TransportService;
import org.opensearch.transport.client.Client;

public class PostQuerySetTransportAction extends HandledTransportAction<PostQuerySetRequest, IndexResponse> {
    private final Client client;
    private final ClusterService clusterService;
    private final IndexNameExpressionResolver indexNameExpressionResolver;
    private final QuerySetDao querySetDao;

    @Inject
    public PostQuerySetTransportAction(
        ClusterService clusterService,
        IndexNameExpressionResolver indexNameExpressionResolver,
        TransportService transportService,
        ActionFilters actionFilters,
        Client client,
        QuerySetDao querySetDao
    ) {
        super(PostQuerySetAction.NAME, transportService, actionFilters, PostUbiQuerySetRequest::new);
        this.client = client;
        this.clusterService = clusterService;
        this.indexNameExpressionResolver = indexNameExpressionResolver;
        this.querySetDao = querySetDao;
    }

    @Override
    protected void doExecute(Task task, PostQuerySetRequest request, ActionListener<IndexResponse> listener) {
        if (request == null) {
            listener.onFailure(new SearchRelevanceException("Request cannot be null", RestStatus.BAD_REQUEST));
            return;
        }
        String name = request.getName();
        if (name == null || name.trim().isEmpty()) {
            listener.onFailure(new SearchRelevanceException("Name cannot be null or empty. Request: " + request, RestStatus.BAD_REQUEST));
            return;
        }

        // Currently only UBI-based query sets are supported. Cast to access UBI-specific fields.
        // Future implementations may introduce other types (e.g., LLM-generated)
        // which would require type checking before casting, similar to PutJudgmentTransportAction.
        PostUbiQuerySetRequest ubiRequest = (PostUbiQuerySetRequest) request;
        String ubiQueriesIndex = ubiRequest.getUbiQueriesIndex();

        UbiValidator.ValidationResult ubiQueriesIndexValidation = validateUbiQueriesIndex(
            clusterService.state(),
            indexNameExpressionResolver,
            ubiQueriesIndex
        );
        if (!ubiQueriesIndexValidation.isValid()) {
            listener.onFailure(new SearchRelevanceException(ubiQueriesIndexValidation.getErrorMessage(), RestStatus.BAD_REQUEST));
            return;
        }

        String id = UUID.randomUUID().toString();
        String timestamp = TimeUtils.getTimestamp();
        String description = request.getDescription();
        String sampling = request.getSampling();
        int querySetSize = request.getQuerySetSize();

        try {
            QuerySampler querySampler = QuerySampler.create(sampling, querySetSize, client, ubiQueriesIndex);
            querySampler.sample().whenComplete((querySetQueries, throwable) -> {
                if (throwable != null) {
                    listener.onFailure(
                        new SearchRelevanceException(
                            "Failed to build querySetQueries. Request: " + request,
                            throwable,
                            ExceptionsHelper.status(throwable)
                        )
                    );
                    return;
                }
                try {
                    // Convert Map<String, Integer> to List<QuerySetEntry> (discarding count values)
                    List<QuerySetEntry> querySetEntries = querySetQueries.entrySet()
                        .stream()
                        .map(entry -> QuerySetEntry.Builder.builder().queryText(entry.getKey()).build())
                        .collect(Collectors.toList());

                    QuerySet querySet = new QuerySet(id, name, description, timestamp, sampling, querySetEntries);
                    querySetDao.putQuerySet(querySet, listener);
                } catch (Exception e) {
                    listener.onFailure(
                        new SearchRelevanceException("Failed to create query set. Request: " + request, e, RestStatus.INTERNAL_SERVER_ERROR)
                    );
                }
            });
        } catch (Exception e) {
            listener.onFailure(
                new SearchRelevanceException("Failed to build querySetQueries. Request: " + request, e, ExceptionsHelper.status(e))
            );
        }
    }
}
