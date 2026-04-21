/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */
package org.opensearch.searchrelevance.transport.queryset;

import static org.opensearch.searchrelevance.common.PluginConstants.LLM_RANDOM;
import static org.opensearch.searchrelevance.common.PluginConstants.MANUAL;
import static org.opensearch.searchrelevance.model.QueryWithReference.DELIMITER;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.stream.Collectors;

import org.opensearch.action.index.IndexResponse;
import org.opensearch.action.support.ActionFilters;
import org.opensearch.action.support.HandledTransportAction;
import org.opensearch.cluster.metadata.MappingMetadata;
import org.opensearch.cluster.service.ClusterService;
import org.opensearch.common.inject.Inject;
import org.opensearch.core.action.ActionListener;
import org.opensearch.core.rest.RestStatus;
import org.opensearch.searchrelevance.common.validator.IndexValidator;
import org.opensearch.searchrelevance.dao.QuerySetDao;
import org.opensearch.searchrelevance.exception.SearchRelevanceException;
import org.opensearch.searchrelevance.model.AsyncStatus;
import org.opensearch.searchrelevance.model.QuerySet;
import org.opensearch.searchrelevance.model.QuerySetEntry;
import org.opensearch.searchrelevance.model.QuerySetType;
import org.opensearch.searchrelevance.model.QueryWithReference;
import org.opensearch.searchrelevance.utils.TimeUtils;
import org.opensearch.tasks.Task;
import org.opensearch.transport.TransportService;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

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

        String sampling = request.getSampling();
        QuerySet querySet;
        if (MANUAL.equals(sampling)) {
            querySet = manualSampling(request);
        } else if (LLM_RANDOM.equals(sampling)) {
            querySet = llmRandomSampling(request, listener);
            if (querySet == null) {
                return;
            }
        } else {
            listener.onFailure(
                new SearchRelevanceException(
                    "Support sampling as manual and llm_random only. sampling: " + sampling,
                    RestStatus.BAD_REQUEST
                )
            );
            return;
        }
        querySetDao.putQuerySet(querySet, listener);
    }

    private QuerySet manualSampling(PutQuerySetRequest request) {
        String id = UUID.randomUUID().toString();
        String timestamp = TimeUtils.getTimestamp();
        List<QuerySetEntry> querySetQueries = convertQuerySetQueriesList(request.getQuerySetQueries());
        return QuerySet.builder()
            .id(id)
            .name(request.getName())
            .description(request.getDescription())
            .timestamp(timestamp)
            .sampling(request.getSampling())
            .status(AsyncStatus.COMPLETED)
            .type(QuerySetType.MANUAL_QUERY_SET)
            .numberOfQueryTerms(querySetQueries.size())
            .querySetQueries(querySetQueries)
            .build();
    }

    // TODO Add llm Random Sampling logic
    private QuerySet llmRandomSampling(PutQuerySetRequest request, ActionListener<IndexResponse> listener) {
        String index = request.getIndex();
        if (!IndexValidator.checkIndexAndMappingExists(clusterService, index)) {
            listener.onFailure(
                new SearchRelevanceException(
                    String.format(Locale.ROOT, "Index with provided name [%s] does not exist", index),
                    RestStatus.BAD_REQUEST
                )
            );
            return null;
        }
        try {
            MappingMetadata mappingMetadata = clusterService.state().metadata().index(index).mapping();
            IndexValidator.validateFieldsExistInIndexMapping(mappingMetadata, request.getContextFields());
        } catch (IllegalArgumentException e) {
            listener.onFailure(new SearchRelevanceException(e.getMessage(), RestStatus.BAD_REQUEST));
            return null;
        }

        String id = UUID.randomUUID().toString();
        String timestamp = TimeUtils.getTimestamp();
        return QuerySet.builder()
            .id(id)
            .name(request.getName())
            .description(request.getDescription())
            .timestamp(timestamp)
            .sampling(request.getSampling())
            .status(AsyncStatus.COMPLETED)
            .type(QuerySetType.LLM_QUERY_SET)
            .numberOfQueryTerms(request.getNumberOfQueryTerms())
            .modelId(request.getModelId())
            .sourceIndex(request.getIndex())
            .contextFields(request.getContextFields())
            .categories(request.getCategories())
            .querySetQueries(new ArrayList<>())
            .build();
    }

    private List<QuerySetEntry> convertQuerySetQueriesList(List<QueryWithReference> queryWithReferenceList) {
        return queryWithReferenceList.stream().map(queryWithReference -> {
            StringBuilder queryTextBuilder = new StringBuilder(queryWithReference.getQueryText());
            if (queryWithReference.getCustomizedKeyValueMap() != null && !queryWithReference.getCustomizedKeyValueMap().isEmpty()) {
                try {
                    queryTextBuilder.append(DELIMITER);
                    queryTextBuilder.append(OBJECT_MAPPER.writeValueAsString(queryWithReference.getCustomizedKeyValueMap()));
                } catch (JsonProcessingException e) {
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
