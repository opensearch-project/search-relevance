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
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import org.opensearch.action.search.SearchRequest;
import org.opensearch.common.xcontent.json.JsonXContent;
import org.opensearch.core.xcontent.DeprecationHandler;
import org.opensearch.core.xcontent.NamedXContentRegistry;
import org.opensearch.core.xcontent.XContentBuilder;
import org.opensearch.core.xcontent.XContentParser;
import org.opensearch.index.query.AbstractQueryBuilder;
import org.opensearch.index.query.QueryBuilder;
import org.opensearch.index.query.QueryBuilders;
import org.opensearch.script.Script;
import org.opensearch.script.ScriptService;
import org.opensearch.script.ScriptType;
import org.opensearch.script.TemplateScript;
import org.opensearch.search.builder.SearchSourceBuilder;
import org.opensearch.searchrelevance.model.QuerySetEntry;

import lombok.extern.log4j.Log4j2;

@Log4j2
/**
 * Common Search Request Builder for Search Configuration with placeholder with QueryText filled.
 *
 * This implementation parses the entire source using the real NamedXContentRegistry provided
 * by the node/plugin wiring, so that any query type registered by any plugin can be parsed
 * without special-casing (no wrapper hacks for query/rescore_query etc).
 */
public class SearchRequestBuilder {

    private static volatile NamedXContentRegistry NAMED_XCONTENT_REGISTRY;
    private static volatile ScriptService SCRIPT_SERVICE;
    private static final String SIZE_FIELD_NAME = "size";
    private static final String QUERY_FIELD_NAME = "query";

    /**
     * Initialize the builder with the cluster's NamedXContentRegistry so that
     * SearchSourceBuilder can parse all plugin-registered query types.
     */
    public static void initialize(NamedXContentRegistry registry, ScriptService scriptService) {
        NAMED_XCONTENT_REGISTRY = registry;
        SCRIPT_SERVICE = scriptService;
        log.debug("SearchRequestBuilder initialized with NamedXContentRegistry and ScriptService");
    }

    private static XContentParser newParserWithRegistry(String json) throws IOException {
        if (NAMED_XCONTENT_REGISTRY == null) {
            throw new IllegalStateException(
                "SearchRequestBuilder is not initialized with NamedXContentRegistry. "
                    + "Ensure SearchRelevancePlugin.createComponents calls SearchRequestBuilder.initialize(xContentRegistry)."
            );
        }
        return JsonXContent.jsonXContent.createParser(NAMED_XCONTENT_REGISTRY, DeprecationHandler.IGNORE_DEPRECATIONS, json);
    }

    /**
     * Renders a Mustache template by compiling and executing it with the current query's values.
     *
     * @param template     - the Mustache template string, referencing variables such as {{queryText}}
     * @param queryText    - the query text, exposed to the template as the {{queryText}} variable
     * @param customFields - query set custom fields, each exposed as a variable by its field name (for example, {{category}})
     * @return the rendered template with substituted values
     * @throws IOException if template compilation or execution fails
     */
    private static String processMustacheTemplate(String template, String queryText, Map<String, String> customFields) throws IOException {
        if (SCRIPT_SERVICE == null) {
            throw new IllegalStateException(
                "SearchRequestBuilder is not initialized with ScriptService. "
                    + "Ensure SearchRelevancePlugin.createComponents calls SearchRequestBuilder.initialize(xContentRegistry, scriptService)."
            );
        }

        Map<String, Object> params = new HashMap<>();
        if (queryText != null) {
            params.put("queryText", queryText);
        }
        if (customFields != null) {
            params.putAll(customFields);
        }

        Script script = new Script(ScriptType.INLINE, "mustache", template, params);

        String compiledQuery = SCRIPT_SERVICE.compile(script, TemplateScript.CONTEXT).newInstance(params).execute();

        return compiledQuery;
    }

    /**
     * Parses the top-level "query" into a QueryBuilder using the cluster's NamedXContentRegistry so
     * that the original query structure is preserved when the request is serialized. Falls back to a
     * wrapper query for query types that cannot be parsed (e.g. custom/unregistered query types).
     */
    private static QueryBuilder buildQueryBuilder(Object queryObject) throws IOException {
        XContentBuilder builder = JsonXContent.contentBuilder();
        builder.value(queryObject);
        String queryBody = builder.toString();

        if (NAMED_XCONTENT_REGISTRY != null) {
            try (XContentParser parser = newParserWithRegistry(queryBody)) {
                return AbstractQueryBuilder.parseInnerQueryBuilder(parser);
            } catch (Exception e) {
                log.warn("Could not parse query with NamedXContentRegistry, falling back to wrapper query: {}", e.getMessage());
            }
        }
        // Fall back to wrapper query to support custom/unregistered query types
        return QueryBuilders.wrapperQuery(queryBody);
    }

    /**
     * Builds a search request with the given parameters.
     * @param index - target index to be searched against
     * @param query - DSL query that includes queryBody and optional extra fields, like pipeline, aggregation, exclude ...
     * @param queryEntry - query entry containing query text and custom fields
     * @param searchPipeline - searchPipeline if it is provided
     * @param size - number of returned hits from the search
     * @return SearchRequest
     */
    public static SearchRequest buildSearchRequest(String index, String query, QuerySetEntry queryEntry, String searchPipeline, int size) {
        return buildSearchRequest(index, query, queryEntry.queryText(), queryEntry.customFields(), searchPipeline, size);
    }

    public static SearchRequest buildSearchRequest(String index, String query, String queryText, String searchPipeline, int size) {
        return buildSearchRequest(index, query, queryText, null, searchPipeline, size);
    }

    public static SearchRequest buildSearchRequest(
        String index,
        String query,
        String queryText,
        Map<String, String> customFields,
        String searchPipeline,
        int size
    ) {
        SearchRequest searchRequest = new SearchRequest(index);

        try {
            // Process query with template engine (Mustache or legacy string replacement)
            String processedQuery;
            if (query.contains("{{")) {
                // Use Mustache templating for queries containing {{
                processedQuery = processMustacheTemplate(query, queryText, customFields);
            } else {
                // Fallback to legacy %SearchText% replacement
                processedQuery = query.replace(WILDCARD_QUERY_TEXT, queryText);
            }

            // Parse to map (using EMPTY registry) for validation/log-only purposes such as size check
            XContentParser tempParser = JsonXContent.jsonXContent.createParser(
                NamedXContentRegistry.EMPTY,
                DeprecationHandler.IGNORE_DEPRECATIONS,
                processedQuery
            );
            Map<String, Object> fullQueryMap = tempParser.map();

            // Handle 'query' separately so it can be parsed into a QueryBuilder
            Object queryObject = fullQueryMap.remove(QUERY_FIELD_NAME);

            // Parse everything except query using SearchSourceBuilder.fromXContent with real registry
            XContentBuilder builder = JsonXContent.contentBuilder();
            builder.map(fullQueryMap);

            XContentParser parser = JsonXContent.jsonXContent.createParser(
                NAMED_XCONTENT_REGISTRY,
                DeprecationHandler.IGNORE_DEPRECATIONS,
                builder.toString()
            );

            SearchSourceBuilder sourceBuilder = SearchSourceBuilder.fromXContent(parser);

            // Set the query, preserving its original structure so search pipeline processors can resolve field paths
            if (queryObject != null) {
                sourceBuilder.query(buildQueryBuilder(queryObject));
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
            // Set size override from configuration input
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
        QuerySetEntry queryEntry,
        int size
    ) {
        return buildRequestForHybridSearch(index, query, temporarySearchPipeline, queryEntry.queryText(), queryEntry.customFields(), size);
    }

    public static SearchRequest buildRequestForHybridSearch(
        String index,
        String query,
        Map<String, Object> temporarySearchPipeline,
        String queryText,
        int size
    ) {
        return buildRequestForHybridSearch(index, query, temporarySearchPipeline, queryText, null, size);
    }

    public static SearchRequest buildRequestForHybridSearch(
        String index,
        String query,
        Map<String, Object> temporarySearchPipeline,
        String queryText,
        Map<String, String> customFields,
        int size
    ) {
        SearchRequest searchRequest = new SearchRequest(index);

        try {
            // Process query with template engine (Mustache or legacy string replacement)
            String processedQuery;
            if (query.contains("{{")) {
                // Use Mustache templating for queries containing {{
                processedQuery = processMustacheTemplate(query, queryText, customFields);
            } else {
                // Fallback to legacy %SearchText% replacement
                processedQuery = query.replace(WILDCARD_QUERY_TEXT, queryText);
            }

            // Parse to map (using EMPTY registry) for validation/log-only purposes (hybrid validation, size check)
            XContentParser tempParser = JsonXContent.jsonXContent.createParser(
                NamedXContentRegistry.EMPTY,
                DeprecationHandler.IGNORE_DEPRECATIONS,
                processedQuery
            );
            Map<String, Object> fullQueryMap = tempParser.map();

            // Validate hybrid query
            validateHybridQuery(fullQueryMap);

            // Handle 'query' separately so it can be parsed into a QueryBuilder
            Object queryObject = fullQueryMap.remove(QUERY_FIELD_NAME);

            // Parse everything except query using SearchSourceBuilder.fromXContent with real registry
            XContentBuilder builder = JsonXContent.contentBuilder();
            builder.map(fullQueryMap);

            XContentParser parser = JsonXContent.jsonXContent.createParser(
                NAMED_XCONTENT_REGISTRY,
                DeprecationHandler.IGNORE_DEPRECATIONS,
                builder.toString()
            );

            SearchSourceBuilder sourceBuilder = SearchSourceBuilder.fromXContent(parser);

            // Set the query, preserving its original structure so search pipeline processors can resolve field paths
            if (queryObject != null) {
                sourceBuilder.query(buildQueryBuilder(queryObject));
            }

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
            // Set size override from configuration input
            sourceBuilder.size(size);

            searchRequest.source(sourceBuilder);
            return searchRequest;

        } catch (IOException ex) {
            throw new IllegalArgumentException("Failed to build search request", ex);
        }
    }
}
