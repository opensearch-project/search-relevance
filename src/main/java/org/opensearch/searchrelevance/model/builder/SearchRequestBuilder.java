/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */
package org.opensearch.searchrelevance.model.builder;

import static org.opensearch.searchrelevance.common.PluginConstants.WILDCARD_QUERY_TEXT;

import org.opensearch.action.search.SearchRequest;
import org.opensearch.index.query.QueryBuilders;
import org.opensearch.search.builder.SearchSourceBuilder;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Common Search Request Builder for Search Configuration with placeholder with QueryText filled.
 */
public class SearchRequestBuilder {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private static final String SEARCH_PIPELINE_FIELD_NAME = "search_pipeline";
    private static final String QUERY_BODY_FIELD_NAME = "query";
    private static final String SOURCE_FIELD_NAME = "_source";
    private static final String EXCLUDE_FIELD_NAME = "exclude";

    /**
     * Builds a search request with the given parameters.
     * @param index - target index to be searched against
     * @param query - DSL query that includes queryBody and optional searchPipelineBody and excluding fields from source
     * @param queryText - queryText need to be replaced with placeholder
     * @param searchPipeline - searchPipeline if it is provided
     * @param size - number of returned hits from the search
     * @return SearchRequest
     */
    public static SearchRequest buildSearchRequest(String index, String query, String queryText, String searchPipeline, int size) {
        SearchRequest searchRequest = new SearchRequest(index);
        SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder();

        String processedQueryBody = fetchQueryBody(query).replace(WILDCARD_QUERY_TEXT, queryText);
        searchSourceBuilder.query(QueryBuilders.wrapperQuery(processedQueryBody));
        searchSourceBuilder.size(size);

        String[] excludedFields = fetchExcludingFields(query);
        if (excludedFields != null && excludedFields.length > 0) {
            searchSourceBuilder.fetchSource(null, excludedFields);
        }

        // set search pipeline from query if it's provided
        String pipelineBody = fetchPipelineBody(query);
        if (pipelineBody != null) {
            searchSourceBuilder.pipeline(pipelineBody);
        }

        // set search pipeline if searchPipeline is provided
        if (searchPipeline != null && !searchPipeline.isEmpty()) {
            searchRequest.pipeline(searchPipeline);
        }

        searchRequest.source(searchSourceBuilder);
        return searchRequest;
    }

    public static String fetchPipelineBody(String query) {
        try {
            JsonNode rootNode = OBJECT_MAPPER.readTree(query);
            JsonNode pipelineNode = rootNode.get(SEARCH_PIPELINE_FIELD_NAME);
            return pipelineNode != null ? OBJECT_MAPPER.writeValueAsString(pipelineNode) : null;
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Failed to parse pipeline body from query", e);
        }
    }

    public static String[] fetchExcludingFields(String query) {
        try {
            JsonNode rootNode = OBJECT_MAPPER.readTree(query);
            JsonNode sourceNode = rootNode.get(SOURCE_FIELD_NAME);
            if (sourceNode != null && sourceNode.has(EXCLUDE_FIELD_NAME)) {
                return OBJECT_MAPPER.convertValue(sourceNode.get(EXCLUDE_FIELD_NAME), String[].class);
            }
            return null;
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Failed to parse excluded fields from query", e);
        }
    }

    public static String fetchQueryBody(String query) {
        try {
            JsonNode rootNode = OBJECT_MAPPER.readTree(query);
            JsonNode queryNode = rootNode.get(QUERY_BODY_FIELD_NAME);
            return queryNode != null ? OBJECT_MAPPER.writeValueAsString(queryNode) : null;
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Failed to parse query body from query", e);
        }
    }
}
