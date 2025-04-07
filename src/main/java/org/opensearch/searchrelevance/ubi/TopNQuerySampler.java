/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */
package org.opensearch.searchrelevance.ubi;

import static org.opensearch.searchrelevance.common.PluginConstants.UBI_QUERIES_INDEX;
import static org.opensearch.searchrelevance.common.PluginConstants.USER_QUERY_FIELD;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.opensearch.action.search.SearchRequest;
import org.opensearch.action.search.SearchResponse;
import org.opensearch.core.action.ActionListener;
import org.opensearch.index.query.BoolQueryBuilder;
import org.opensearch.index.query.QueryBuilders;
import org.opensearch.search.aggregations.AggregationBuilder;
import org.opensearch.search.aggregations.AggregationBuilders;
import org.opensearch.search.aggregations.bucket.terms.Terms;
import org.opensearch.search.builder.SearchSourceBuilder;
import org.opensearch.transport.client.Client;

/**
 * TopN Query Sampling method.
 * Swallow all exceptions for query sampler if users haven't onboarded with UBI.
 */
public class TopNQuerySampler extends QuerySampler {
    public static final String NAME = "topn";
    private static final Logger LOGGER = LogManager.getLogger(TopNQuerySampler.class);
    private static final String AGGREGATION_NAME = "By_User_Query";

    public TopNQuerySampler(int size, Client client) {
        super(size, client);
    }

    @Override
    public CompletableFuture<Map<String, Long>> sample() {
        CompletableFuture<Map<String, Long>> future = new CompletableFuture<>();
        try {
            SearchRequest searchRequest = buildSearchRequest();

            getClient().search(searchRequest, new ActionListener<SearchResponse>() {
                @Override
                public void onResponse(SearchResponse searchResponse) {
                    try {
                        Map<String, Long> querySet = processSearchResponse(searchResponse);
                        if (querySet.isEmpty()) {
                            LOGGER.warn("No queries found in the search response");
                        }
                        future.complete(querySet);
                    } catch (Exception e) {
                        LOGGER.error("Error processing search response: {}", e.getMessage(), e);
                        future.complete(new HashMap<>());
                    }
                }

                @Override
                public void onFailure(Exception e) {
                    LOGGER.error("Search request failed: {}", e.getMessage(), e);
                    future.complete(new HashMap<>());
                }
            });
        } catch (Exception e) {
            LOGGER.error("Error creating search request: {}", e.getMessage(), e);
            future.complete(new HashMap<>());
        }

        return future;
    }

    private SearchRequest buildSearchRequest() {
        // Build aggregation
        AggregationBuilder userQueryAggregation = AggregationBuilders.terms(AGGREGATION_NAME).field(USER_QUERY_FIELD).size(getSize());

        // Build query
        BoolQueryBuilder boolQuery = QueryBuilders.boolQuery()
            .must(QueryBuilders.existsQuery(USER_QUERY_FIELD))
            .mustNot(QueryBuilders.termQuery(USER_QUERY_FIELD, ""));

        // Build search source
        SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder().query(boolQuery).aggregation(userQueryAggregation).size(0);
        // Build search request
        return new SearchRequest(UBI_QUERIES_INDEX).source(searchSourceBuilder);
    }

    private Map<String, Long> processSearchResponse(SearchResponse searchResponse) {
        Map<String, Long> querySet = new HashMap<>();

        Terms byUserQuery = searchResponse.getAggregations().get(AGGREGATION_NAME);
        List<? extends Terms.Bucket> buckets = byUserQuery.getBuckets();

        for (Terms.Bucket bucket : buckets) {
            String query = bucket.getKeyAsString();
            long count = bucket.getDocCount();

            if (query != null && !query.trim().isEmpty()) {
                LOGGER.debug("Adding query to set: {} (count: {})", query, count);
                querySet.put(query, count);
            }
        }

        LOGGER.info("Created query set with {} queries", querySet.size());
        return querySet;
    }
}
