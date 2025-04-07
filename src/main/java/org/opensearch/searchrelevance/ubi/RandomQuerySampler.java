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
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.opensearch.action.search.SearchRequest;
import org.opensearch.action.search.SearchResponse;
import org.opensearch.core.action.ActionListener;
import org.opensearch.index.query.BoolQueryBuilder;
import org.opensearch.index.query.QueryBuilders;
import org.opensearch.index.query.functionscore.FunctionScoreQueryBuilder;
import org.opensearch.index.query.functionscore.RandomScoreFunctionBuilder;
import org.opensearch.index.query.functionscore.ScoreFunctionBuilders;
import org.opensearch.search.SearchHit;
import org.opensearch.search.builder.SearchSourceBuilder;
import org.opensearch.search.collapse.CollapseBuilder;
import org.opensearch.transport.client.Client;

/**
 * Randomize Query Sampling method.
 * Swallow all exceptions for query sampler if users haven't onboarded with UBI.
 */
public class RandomQuerySampler extends QuerySampler {
    public static final String NAME = "random";
    private static final Logger LOGGER = LogManager.getLogger(RandomQuerySampler.class);
    private static final int SEARCH_TIMEOUT_SECONDS = 30;

    public RandomQuerySampler(int size, Client client) {
        super(size, client);
    }

    @Override
    public CompletableFuture<Map<String, Long>> sample() {
        CompletableFuture<Map<String, Long>> future = new CompletableFuture<>();
        SearchRequest searchRequest = buildSearchRequest();

        getClient().search(searchRequest, new ActionListener<SearchResponse>() {
            @Override
            public void onResponse(SearchResponse searchResponse) {
                getQuerySet(searchResponse).thenAccept(result -> {
                    if (result.isEmpty()) {
                        LOGGER.warn("No queries found in the search response");
                    }
                    future.complete(result);
                }).exceptionally(ex -> {
                    LOGGER.error("Error processing query set: {}", ex.getMessage(), ex);
                    future.complete(new HashMap<>());
                    return null;
                });
            }

            @Override
            public void onFailure(Exception ex) {
                LOGGER.error("Error executing search request: {}", ex.getMessage(), ex);
                future.complete(new HashMap<>());
            }
        });

        return future;
    }

    private SearchRequest buildSearchRequest() {
        RandomScoreFunctionBuilder randomScoreFunction = ScoreFunctionBuilders.randomFunction();

        FunctionScoreQueryBuilder functionScoreQueryBuilder = QueryBuilders.functionScoreQuery(
            QueryBuilders.matchAllQuery(),
            new FunctionScoreQueryBuilder.FilterFunctionBuilder[] {
                new FunctionScoreQueryBuilder.FilterFunctionBuilder(randomScoreFunction) }
        );

        BoolQueryBuilder boolQueryBuilder = QueryBuilders.boolQuery()
            .must(QueryBuilders.existsQuery(USER_QUERY_FIELD))
            .must(functionScoreQueryBuilder)
            .mustNot(QueryBuilders.termQuery(USER_QUERY_FIELD, ""));

        SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder().query(boolQueryBuilder)
            .collapse(new CollapseBuilder(USER_QUERY_FIELD))
            .size(getSize());

        return new SearchRequest(UBI_QUERIES_INDEX).source(searchSourceBuilder);
    }

    private CompletableFuture<Map<String, Long>> getQuerySet(SearchResponse searchResponse) {
        Map<String, Long> querySet = new HashMap<>();
        CompletableFuture<?>[] futures = new CompletableFuture[searchResponse.getHits().getHits().length];

        int i = 0;
        for (SearchHit hit : searchResponse.getHits().getHits()) {
            String userQuery = (String) hit.getSourceAsMap().get(USER_QUERY_FIELD);
            futures[i++] = getUserQueryCount(userQuery).thenAccept(count -> {
                LOGGER.info("Adding user query to query set: {} with frequency {}", userQuery, count);
                querySet.put(userQuery, count);
            });
        }

        return CompletableFuture.allOf(futures).thenApply(v -> querySet);
    }

    private CompletableFuture<Long> getUserQueryCount(String userQuery) {
        CompletableFuture<Long> future = new CompletableFuture<>();

        SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder().query(QueryBuilders.termQuery(USER_QUERY_FIELD, userQuery))
            .size(0)
            .trackTotalHits(true);

        SearchRequest searchRequest = new SearchRequest(UBI_QUERIES_INDEX).source(searchSourceBuilder);

        getClient().search(searchRequest, new ActionListener<SearchResponse>() {
            @Override
            public void onResponse(SearchResponse searchResponse) {
                future.complete(searchResponse.getHits().getTotalHits().value());
            }

            @Override
            public void onFailure(Exception ex) {
                LOGGER.error("Error getting user query count for {}: {}", userQuery, ex.getMessage(), ex);
                future.complete(0L);
            }
        });

        return future.orTimeout(SEARCH_TIMEOUT_SECONDS, TimeUnit.SECONDS).exceptionally(ex -> {
            LOGGER.error("Operation timed out or failed for query {}: {}", userQuery, ex.getMessage(), ex);
            return 0L;
        });
    }
}
