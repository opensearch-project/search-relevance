/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */
package org.opensearch.searchrelevance.rest;

import static java.util.Collections.singletonList;
import static org.opensearch.rest.RestRequest.Method.PUT;
import static org.opensearch.searchrelevance.common.PluginConstants.CATEGORIES;
import static org.opensearch.searchrelevance.common.PluginConstants.CONTEXT_FIELDS;
import static org.opensearch.searchrelevance.common.PluginConstants.DEFAULT_CATEGORIES;
import static org.opensearch.searchrelevance.common.PluginConstants.DEFAULT_NUMBER_OF_QUERY_TERMS;
import static org.opensearch.searchrelevance.common.PluginConstants.DESCRIPTION;
import static org.opensearch.searchrelevance.common.PluginConstants.INDEX;
import static org.opensearch.searchrelevance.common.PluginConstants.LEXICAL;
import static org.opensearch.searchrelevance.common.PluginConstants.LLM_QUERY_SET;
import static org.opensearch.searchrelevance.common.PluginConstants.LLM_RANDOM;
import static org.opensearch.searchrelevance.common.PluginConstants.MANUAL;
import static org.opensearch.searchrelevance.common.PluginConstants.MODEL_ID;
import static org.opensearch.searchrelevance.common.PluginConstants.NAME;
import static org.opensearch.searchrelevance.common.PluginConstants.NUMBER_OF_QUERY_TERMS;
import static org.opensearch.searchrelevance.common.PluginConstants.QUERYSETS_URL;
import static org.opensearch.searchrelevance.common.PluginConstants.QUERY_SET_QUERIES;
import static org.opensearch.searchrelevance.common.PluginConstants.SAMPLING;
import static org.opensearch.searchrelevance.common.PluginConstants.SEMANTIC;
import static org.opensearch.searchrelevance.common.PluginConstants.TYPE;

import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.opensearch.ExceptionsHelper;
import org.opensearch.action.index.IndexResponse;
import org.opensearch.core.action.ActionListener;
import org.opensearch.core.rest.RestStatus;
import org.opensearch.core.xcontent.XContentBuilder;
import org.opensearch.core.xcontent.XContentParser;
import org.opensearch.rest.BaseRestHandler;
import org.opensearch.rest.BytesRestResponse;
import org.opensearch.rest.RestRequest;
import org.opensearch.searchrelevance.model.QueryWithReference;
import org.opensearch.searchrelevance.settings.SearchRelevanceSettingsAccessor;
import org.opensearch.searchrelevance.transport.queryset.PutLlmQuerySetRequest;
import org.opensearch.searchrelevance.transport.queryset.PutQuerySetAction;
import org.opensearch.searchrelevance.transport.queryset.PutQuerySetRequest;
import org.opensearch.searchrelevance.utils.ParserUtils;
import org.opensearch.searchrelevance.utils.TextValidationUtil;
import org.opensearch.transport.client.node.NodeClient;

import lombok.AllArgsConstructor;

/**
 * Rest Action to facilitate requests to put a query set from manual input.
 */
@AllArgsConstructor
public class RestPutQuerySetAction extends BaseRestHandler {
    private static final Logger LOGGER = LogManager.getLogger(RestPutQuerySetAction.class);
    private static final String PUT_QUERYSET_ACTION = "put_queryset_action";
    private SearchRelevanceSettingsAccessor settingsAccessor;

    @Override
    public String getName() {
        return PUT_QUERYSET_ACTION;
    }

    @Override
    public List<Route> routes() {
        return singletonList(new Route(PUT, QUERYSETS_URL));
    }

    @Override
    protected RestChannelConsumer prepareRequest(RestRequest request, NodeClient client) throws IOException {
        if (!settingsAccessor.isWorkbenchEnabled()) {
            return channel -> channel.sendResponse(new BytesRestResponse(RestStatus.FORBIDDEN, "Search Relevance Workbench is disabled"));
        }
        XContentParser parser = request.contentParser();
        Map<String, Object> source = parser.map();

        PutQuerySetRequest putRequest;
        try {
            Object type = source.getOrDefault(TYPE, null);
            if (type != null && !LLM_QUERY_SET.equals(type)) {
                throw new IllegalArgumentException("Invalid type: must be '" + LLM_QUERY_SET + "' or absent");
            }
            if (LLM_QUERY_SET.equals(type)) {
                putRequest = prepareLlmRandomQuerySetRequest(source);
            } else {
                putRequest = prepareManualQuerySetRequest(source);
            }
        } catch (IllegalArgumentException e) {
            return channel -> channel.sendResponse(new BytesRestResponse(RestStatus.BAD_REQUEST, e.getMessage()));
        } catch (IllegalStateException e) {
            return channel -> channel.sendResponse(new BytesRestResponse(RestStatus.FORBIDDEN, e.getMessage()));
        }

        return executePutQuerySetRequest(client, putRequest);
    }

    private RestChannelConsumer executePutQuerySetRequest(NodeClient client, PutQuerySetRequest putRequest) {
        return channel -> client.execute(PutQuerySetAction.INSTANCE, putRequest, new ActionListener<>() {
            @Override
            public void onResponse(IndexResponse response) {
                try {
                    XContentBuilder builder = channel.newBuilder();
                    builder.startObject();
                    builder.field("query_set_id", response.getId());
                    builder.field("query_set_result", response.getResult());
                    builder.endObject();
                    channel.sendResponse(new BytesRestResponse(RestStatus.OK, builder));
                } catch (IOException e) {
                    onFailure(e);
                }
            }

            @Override
            public void onFailure(Exception e) {
                try {
                    channel.sendResponse(new BytesRestResponse(channel, ExceptionsHelper.status(e), e));
                } catch (IOException ex) {
                    LOGGER.error("Failed to send error response", ex);
                }
            }
        });
    }

    private PutLlmQuerySetRequest prepareLlmRandomQuerySetRequest(Map<String, Object> source) {
        String name = validateFieldOrThrow(source.get(NAME), "name");
        String description = source.get(DESCRIPTION) != null ? validateFieldOrThrow(source.get(DESCRIPTION), "description") : null;
        String sampling = validateSampling(source.getOrDefault(SAMPLING, LLM_RANDOM));
        if (!LLM_RANDOM.equals(sampling)) {
            throw new IllegalArgumentException("Sampling must be '" + LLM_RANDOM + "' for generating query sets by LLM");
        }
        String indexName = validateFieldOrThrow(source.get(INDEX), INDEX);
        int numberOfQueryTerms = parseNumberOfQueryTerms(source);
        String modelId = validateFieldOrThrow(source.get(MODEL_ID), MODEL_ID);
        List<String> categories = parseCategories(source);

        List<String> contextFields = ParserUtils.convertObjToList(source, CONTEXT_FIELDS);
        if (contextFields.isEmpty()) {
            throw new IllegalArgumentException("ContextFields cannot be empty");
        }
        return new PutLlmQuerySetRequest(name, description, sampling, indexName, modelId, numberOfQueryTerms, contextFields, categories);
    }

    private PutQuerySetRequest prepareManualQuerySetRequest(Map<String, Object> source) {
        String name = validateFieldOrThrow(source.get(NAME), "name");
        String description = source.get(DESCRIPTION) != null ? validateFieldOrThrow(source.get(DESCRIPTION), "description") : null;
        String sampling = validateSampling(source.getOrDefault(SAMPLING, MANUAL));
        if (!MANUAL.equals(sampling)) {
            throw new IllegalArgumentException("Sampling must be '" + MANUAL + "' for manual query sets");
        }
        List<QueryWithReference> querySetQueries = parseAndValidateManualQuerySet(source);
        return new PutQuerySetRequest(name, description, sampling, querySetQueries);
    }

    private String validateSampling(Object value) {
        if (!MANUAL.equals(value) && !LLM_RANDOM.equals(value)) {
            throw new IllegalArgumentException("Invalid sampling: must be '" + MANUAL + "' or '" + LLM_RANDOM + "'");
        }
        return (String) value;
    }

    private int parseNumberOfQueryTerms(Map<String, Object> source) {
        Object rawValue = source.getOrDefault(NUMBER_OF_QUERY_TERMS, DEFAULT_NUMBER_OF_QUERY_TERMS);
        if (rawValue instanceof Number) {
            return ((Number) rawValue).intValue();
        }
        throw new IllegalArgumentException("Invalid numberOfQueryTerms: must be a number");
    }

    private List<String> parseCategories(Map<String, Object> source) {
        List<String> categories = ParserUtils.convertObjToList(source, CATEGORIES);
        if (categories.isEmpty()) {
            return DEFAULT_CATEGORIES;
        }
        if (categories.size() != new HashSet<>(categories).size()) {
            throw new IllegalArgumentException("Duplicate categories are not allowed");
        }
        if (!DEFAULT_CATEGORIES.containsAll(categories)) {
            throw new IllegalArgumentException("Invalid categories: must be '" + LEXICAL + "' and/or '" + SEMANTIC + "'");
        }
        return categories;
    }

    private String validateFieldOrThrow(Object fieldValue, String fieldName) {
        if (!(fieldValue instanceof String value)) {
            throw new IllegalArgumentException("Invalid " + fieldName + ": must be a string");
        }
        TextValidationUtil.ValidationResult validation;
        if ("name".equals(fieldName)) {
            validation = TextValidationUtil.validateName(value);
        } else if ("description".equals(fieldName)) {
            validation = TextValidationUtil.validateDescription(value);
        } else {
            validation = TextValidationUtil.validateText(value);
        }
        if (!validation.isValid()) {
            throw new IllegalArgumentException("Invalid " + fieldName + ": " + validation.getErrorMessage());
        }
        return value;
    }

    /**
     * Parses and validates manual query set queries from the request source.
     *
     * @param source the request body as a map
     * @return list of validated QueryWithReference objects
     * @throws IllegalStateException    if query set size exceeds the allowed limit
     * @throws IllegalArgumentException if any query fails validation
     */
    private List<QueryWithReference> parseAndValidateManualQuerySet(Map<String, Object> source) {
        Object rawQueriesObj = source.get(QUERY_SET_QUERIES);
        if (!(rawQueriesObj instanceof List)) {
            throw new IllegalArgumentException("Query set queries must be a list");
        }
        @SuppressWarnings("unchecked")
        List<Object> rawQueries = (List<Object>) rawQueriesObj;
        if (rawQueries.isEmpty()) {
            throw new IllegalArgumentException("Query set queries cannot be empty for manual sampling");
        }
        if (rawQueries.size() > settingsAccessor.getMaxQuerySetAllowed()) {
            throw new IllegalStateException("Query Set Limit Exceeded.");
        }

        return rawQueries.stream().map(obj -> {
            if (!(obj instanceof Map)) {
                throw new IllegalArgumentException("Each query must be a JSON object");
            }
            @SuppressWarnings("unchecked")
            Map<String, Object> queryMap = (Map<String, Object>) obj;
            TextValidationUtil.QueryValidationResult validationResult = TextValidationUtil.validateAndParseQuery(queryMap);
            if (!validationResult.isValid()) {
                throw new IllegalArgumentException(validationResult.getErrorMessage());
            }
            return validationResult.getQueryWithReference();
        }).collect(Collectors.toList());
    }

}
