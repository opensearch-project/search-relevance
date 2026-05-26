/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */
package org.opensearch.searchrelevance.transport.queryset;

import static org.opensearch.searchrelevance.model.QueryWithReference.DELIMITER;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

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
import org.opensearch.searchrelevance.model.QueryWithReference;
import org.opensearch.searchrelevance.utils.TimeUtils;
import org.opensearch.tasks.Task;
import org.opensearch.transport.TransportService;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

public class PutQuerySetTransportAction extends HandledTransportAction<PutQuerySetRequest, IndexResponse> {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private final ClusterService clusterService;
    private final QuerySetDao querySetDao;

    @Inject
    public PutQuerySetTransportAction(
        ClusterService clusterService,
        TransportService transportService,
        ActionFilters actionFilters,
        QuerySetDao querySetDao
    ) {
        super(PutQuerySetAction.NAME, transportService, actionFilters, PutQuerySetRequest::new);
        this.clusterService = clusterService;
        this.querySetDao = querySetDao;
    }

    @Override
    protected void doExecute(Task task, PutQuerySetRequest request, ActionListener<IndexResponse> listener) {
        if (request == null) {
            listener.onFailure(new SearchRelevanceException("Request cannot be null", RestStatus.BAD_REQUEST));
            return;
        }
        String id = UUID.randomUUID().toString();
        String timestamp = TimeUtils.getTimestamp();

        String name = request.getName();
        String description = request.getDescription();

        // Given sampling type by default "manual" to support manually uploaded querySetQueries.
        String sampling = request.getSampling();
        if (!"manual".equals(sampling)) {
            listener.onFailure(
                new SearchRelevanceException("Support sampling as manual only. sampling: " + sampling, RestStatus.BAD_REQUEST)
            );
        }
        List<QueryWithReference> queryWithReferenceList = request.getQuerySetQueries();
        List<QuerySetEntry> querySetQueries = convertQuerySetQueriesList(queryWithReferenceList);

        QuerySet querySet = new QuerySet(id, name, description, timestamp, sampling, querySetQueries);
        querySetDao.putQuerySet(querySet, listener);
    }

    /**
     * Query set input is a list of queryText and customizedKeyValueMap pair.
     * Converts to format: "queryText#json_format"
     * e.g:
     * Input:
     * {
     *     "queryText": "What is OpenSearch?",
     *     "referenceAnswer": "OpenSearch is a community-driven, open source search and analytics suite"
     * }
     * Output: "What is OpenSearch?#{"referenceAnswer":"OpenSearch is a community-driven, open source search and analytics suite"}"
     *
     * @param queryWithReferenceList - list of queryText and customizedKeyValueMap pair
     * @return - querySetQueries as a list of QuerySetEntry objects
     */
    private List<QuerySetEntry> convertQuerySetQueriesList(List<QueryWithReference> queryWithReferenceList) {
        return queryWithReferenceList.stream().map(queryWithReference -> {
            StringBuilder queryTextBuilder = new StringBuilder(queryWithReference.getQueryText());

            // Append customizedKeyValueMap as JSON format
            if (queryWithReference.getCustomizedKeyValueMap() != null && !queryWithReference.getCustomizedKeyValueMap().isEmpty()) {
                try {
                    queryTextBuilder.append(DELIMITER);
                    queryTextBuilder.append(OBJECT_MAPPER.writeValueAsString(queryWithReference.getCustomizedKeyValueMap()));
                } catch (JacksonException e) {
                    throw new SearchRelevanceException(
                        "Failed to serialize custom fields to JSON: " + e.getMessage(),
                        RestStatus.INTERNAL_SERVER_ERROR
                    );
                }
            }

            return QuerySetEntry.Builder.builder().queryText(queryTextBuilder.toString()).build();
        }).collect(Collectors.toList());
    }
}
