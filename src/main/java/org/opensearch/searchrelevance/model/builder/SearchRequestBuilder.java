/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */
package org.opensearch.searchrelevance.model.builder;

import static org.opensearch.searchrelevance.common.PluginConstants.WILDCARD_QUERY_TEXT;
import static org.opensearch.searchrelevance.experiment.QuerySourceUtil.validateHybridQuery;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Collections;
import java.util.Map;
import java.util.Objects;

import org.opensearch.action.search.SearchRequest;
import org.opensearch.common.settings.Settings;
import org.opensearch.common.xcontent.json.JsonXContent;
import org.opensearch.core.xcontent.DeprecationHandler;
import org.opensearch.core.xcontent.NamedXContentRegistry;
import org.opensearch.core.xcontent.XContentBuilder;
import org.opensearch.core.xcontent.XContentParser;
import org.opensearch.index.query.QueryBuilders;
import org.opensearch.search.SearchModule;
import org.opensearch.search.builder.SearchSourceBuilder;

import lombok.extern.log4j.Log4j2;

@Log4j2
/**
 * Common Search Request Builder for Search Configuration with placeholder with QueryText filled.
 */
public class SearchRequestBuilder {

    private static final NamedXContentRegistry NAMED_CONTENT_REGISTRY;
    private static final SearchModule SEARCH_MODULE;
    private static final String QUERY_FIELD_NAME = "query";
    private static final String SIZE_FIELD_NAME = "size";

    static {
        SEARCH_MODULE = new SearchModule(Settings.EMPTY, Collections.emptyList());
        NAMED_CONTENT_REGISTRY = new NamedXContentRegistry(SEARCH_MODULE.getNamedXContents());
    }

    /**
     * Builds a search request with the given parameters.
     * @param index - target index to be searched against
     * @param query - DSL query that includes queryBody and optional extra fields, like pipeline, aggregation, exclude ...
     * @param queryText - queryText need to be replaced with placeholder
     * @param searchPipeline - searchPipeline if it is provided
     * @param size - number of returned hits from the search
     * @return SearchRequest
     */
    public static SearchRequest buildSearchRequest(String index, String query, String queryText, String searchPipeline, int size) {
        SearchRequest searchRequest = new SearchRequest(index);

        try {
            // Replace placeholder with actual query text
            String processedQuery = query.replace(WILDCARD_QUERY_TEXT, queryText);

            // Parse the full query into a map
            XContentParser parser = JsonXContent.jsonXContent.createParser(
                NamedXContentRegistry.EMPTY,
                DeprecationHandler.IGNORE_DEPRECATIONS,
                processedQuery
            );
            Map<String, Object> fullQueryMap = parser.map();
            // Preprocess rescore_query to wrap unknown queries (e.g., LTR/sltr) using wrapper query to avoid NamedXContent parsing issues
            try {
                Object rescore = fullQueryMap.get("rescore");
                if (rescore instanceof Map) {
                    Map<String, Object> rescoreEntry = (Map<String, Object>) rescore;
                    Object queryObjInner = rescoreEntry.get("query");
                    if (queryObjInner instanceof Map) {
                        Map<String, Object> queryMap = (Map<String, Object>) queryObjInner;
                        Object rescoreQuery = queryMap.get("rescore_query");
                        if (rescoreQuery != null && (rescoreQuery instanceof Map) && !((Map<?, ?>) rescoreQuery).containsKey("wrapper")) {
                            XContentBuilder tmpBuilder = JsonXContent.contentBuilder();
                            tmpBuilder.value(rescoreQuery);
                            String raw = tmpBuilder.toString();
                            String base64 = Base64.getEncoder().encodeToString(raw.getBytes(StandardCharsets.UTF_8));
                            Map<String, Object> wrapper = Map.of("wrapper", Map.of("query", base64));
                            queryMap.put("rescore_query", wrapper);
                        }
                    }
                } else if (rescore instanceof java.util.List) {
                    for (Object entry : (java.util.List<?>) rescore) {
                        if (entry instanceof Map) {
                            Map<String, Object> rescoreEntry = (Map<String, Object>) entry;
                            Object queryObjInner = rescoreEntry.get("query");
                            if (queryObjInner instanceof Map) {
                                Map<String, Object> queryMap = (Map<String, Object>) queryObjInner;
                                Object rescoreQuery = queryMap.get("rescore_query");
                                if (rescoreQuery != null
                                    && (rescoreQuery instanceof Map)
                                    && !((Map<?, ?>) rescoreQuery).containsKey("wrapper")) {
                                    XContentBuilder tmpBuilder = JsonXContent.contentBuilder();
                                    tmpBuilder.value(rescoreQuery);
                                    String raw = tmpBuilder.toString();
                                    String base64 = Base64.getEncoder().encodeToString(raw.getBytes(StandardCharsets.UTF_8));
                                    Map<String, Object> wrapper = Map.of("wrapper", Map.of("query", base64));
                                    queryMap.put("rescore_query", wrapper);
                                }
                            }
                        }
                    }
                }
            } catch (Exception e) {
                log.debug("Skipping rescore_query wrapper preprocessing: {}", e.getMessage());
            }

            // This implementation handles the 'query' field separately from other fields because:
            // 1. Custom query types (like hybrid, neural) are not registered in the default QueryBuilders
            // 2. Using WrapperQuery allows passing through any query structure without parsing
            // 3. All other fields (aggregations, source filtering, etc.) can be parsed normally by SearchSourceBuilder
            Object queryObject = fullQueryMap.remove(QUERY_FIELD_NAME);

            // Parse everything except query using SearchSourceBuilder.fromXContent
            XContentBuilder builder = JsonXContent.contentBuilder();
            builder.map(fullQueryMap);

            parser = JsonXContent.jsonXContent.createParser(
                NAMED_CONTENT_REGISTRY,
                DeprecationHandler.IGNORE_DEPRECATIONS,
                builder.toString()
            );

            SearchSourceBuilder sourceBuilder = SearchSourceBuilder.fromXContent(parser);

            // Handle query separately using WrapperQuery
            if (queryObject != null) {
                builder = JsonXContent.contentBuilder();
                builder.value(queryObject);
                String queryBody = builder.toString();
                sourceBuilder.query(QueryBuilders.wrapperQuery(queryBody));
            }

            // Precheck if query contains a different size value
            if (fullQueryMap.containsKey(SIZE_FIELD_NAME)) {
                int querySize = ((Number) fullQueryMap.get(SIZE_FIELD_NAME)).intValue();
                if (querySize != size) {
                    log.debug(
                        "Size mismatch detected. Query size: {}, Search Configuration Input size: {}. Using Search Configuration Input size.",
                        querySize,
                        size
                    );
                }
            }
            // Set size
            sourceBuilder.size(size);

            // Set search pipeline if provided
            if (searchPipeline != null && !searchPipeline.isEmpty()) {
                searchRequest.pipeline(searchPipeline);
            }

            searchRequest.source(sourceBuilder);
            return searchRequest;

        } catch (IOException ex) {
            throw new IllegalArgumentException("Failed to build search request", ex);
        }
    }

    public static SearchRequest buildRequestForHybridSearch(
        String index,
        String query,
        Map<String, Object> temporarySearchPipeline,
        String queryText,
        int size
    ) {
        SearchRequest searchRequest = new SearchRequest(index);

        try {
            // Replace placeholder with actual query text
            String processedQuery = query.replace(WILDCARD_QUERY_TEXT, queryText);

            // Parse the full query into a map
            XContentParser parser = JsonXContent.jsonXContent.createParser(
                NamedXContentRegistry.EMPTY,
                DeprecationHandler.IGNORE_DEPRECATIONS,
                processedQuery
            );
            Map<String, Object> fullQueryMap = parser.map();
            // Preprocess rescore_query to wrap unknown queries (e.g., LTR/sltr) using wrapper query to avoid NamedXContent parsing issues
            try {
                Object rescore = fullQueryMap.get("rescore");
                if (rescore instanceof Map) {
                    Map<String, Object> rescoreEntry = (Map<String, Object>) rescore;
                    Object queryObjInner = rescoreEntry.get("query");
                    if (queryObjInner instanceof Map) {
                        Map<String, Object> queryMap = (Map<String, Object>) queryObjInner;
                        Object rescoreQuery = queryMap.get("rescore_query");
                        if (rescoreQuery != null && (rescoreQuery instanceof Map) && !((Map<?, ?>) rescoreQuery).containsKey("wrapper")) {
                            XContentBuilder tmpBuilder = JsonXContent.contentBuilder();
                            tmpBuilder.value(rescoreQuery);
                            String raw = tmpBuilder.toString();
                            String base64 = Base64.getEncoder().encodeToString(raw.getBytes(StandardCharsets.UTF_8));
                            Map<String, Object> wrapper = Map.of("wrapper", Map.of("query", base64));
                            queryMap.put("rescore_query", wrapper);
                        }
                    }
                } else if (rescore instanceof java.util.List) {
                    for (Object entry : (java.util.List<?>) rescore) {
                        if (entry instanceof Map) {
                            Map<String, Object> rescoreEntry = (Map<String, Object>) entry;
                            Object queryObjInner = rescoreEntry.get("query");
                            if (queryObjInner instanceof Map) {
                                Map<String, Object> queryMap = (Map<String, Object>) queryObjInner;
                                Object rescoreQuery = queryMap.get("rescore_query");
                                if (rescoreQuery != null
                                    && (rescoreQuery instanceof Map)
                                    && !((Map<?, ?>) rescoreQuery).containsKey("wrapper")) {
                                    XContentBuilder tmpBuilder = JsonXContent.contentBuilder();
                                    tmpBuilder.value(rescoreQuery);
                                    String raw = tmpBuilder.toString();
                                    String base64 = Base64.getEncoder().encodeToString(raw.getBytes(StandardCharsets.UTF_8));
                                    Map<String, Object> wrapper = Map.of("wrapper", Map.of("query", base64));
                                    queryMap.put("rescore_query", wrapper);
                                }
                            }
                        }
                    }
                }
            } catch (Exception e) {
                log.debug("Skipping rescore_query wrapper preprocessing: {}", e.getMessage());
            }

            validateHybridQuery(fullQueryMap);

            // This implementation handles the 'query' field separately from other fields because:
            // 1. Custom query types (like hybrid, neural) are not registered in the default QueryBuilders
            // 2. Using WrapperQuery allows passing through any query structure without parsing
            // 3. All other fields (aggregations, source filtering, etc.) can be parsed normally by SearchSourceBuilder
            Object queryObject = fullQueryMap.remove(QUERY_FIELD_NAME);

            // Parse everything except query using SearchSourceBuilder.fromXContent
            XContentBuilder builder = JsonXContent.contentBuilder();
            builder.map(fullQueryMap);

            parser = JsonXContent.jsonXContent.createParser(
                NAMED_CONTENT_REGISTRY,
                DeprecationHandler.IGNORE_DEPRECATIONS,
                builder.toString()
            );

            SearchSourceBuilder sourceBuilder = SearchSourceBuilder.fromXContent(parser);

            // validate that query does not have internal temporary pipeline definition
            if (Objects.nonNull(sourceBuilder.searchPipelineSource()) && !sourceBuilder.searchPipelineSource().isEmpty()) {
                log.error("query in search configuration does have temporary search pipeline in its source");
                throw new IllegalArgumentException("search pipeline is not allowed in search request");
            }

            if (temporarySearchPipeline.isEmpty() == false) {
                sourceBuilder.searchPipelineSource(temporarySearchPipeline);
            } else {
                log.debug("no temporary search pipeline");
            }

            // Handle query separately using WrapperQuery
            if (queryObject != null) {
                builder = JsonXContent.contentBuilder();
                builder.value(queryObject);
                String queryBody = builder.toString();
                sourceBuilder.query(QueryBuilders.wrapperQuery(queryBody));
            }

            // Precheck if query contains a different size value
            if (fullQueryMap.containsKey(SIZE_FIELD_NAME)) {
                int querySize = ((Number) fullQueryMap.get(SIZE_FIELD_NAME)).intValue();
                if (querySize != size) {
                    log.debug(
                        "Size mismatch detected. Query size: {}, Search Configuration Input size: {}. Using Search Configuration Input size.",
                        querySize,
                        size
                    );
                }
            }
            // Set size
            sourceBuilder.size(size);

            searchRequest.source(sourceBuilder);
            return searchRequest;

        } catch (IOException ex) {
            throw new IllegalArgumentException("Failed to build search request", ex);
        }
    }
}
