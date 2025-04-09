/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */
package org.opensearch.searchrelevance.ubi;

import static org.opensearch.searchrelevance.common.PluginConstants.UBI_QUERIES_INDEX;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import org.apache.commons.math3.distribution.UniformRealDistribution;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.opensearch.action.search.SearchRequest;
import org.opensearch.action.search.SearchResponse;
import org.opensearch.action.search.SearchScrollRequest;
import org.opensearch.common.unit.TimeValue;
import org.opensearch.core.action.ActionListener;
import org.opensearch.index.query.QueryBuilders;
import org.opensearch.search.Scroll;
import org.opensearch.search.SearchHit;
import org.opensearch.search.builder.SearchSourceBuilder;
import org.opensearch.transport.client.Client;

/**
 * Probability Proportional To Size Query Sampling method.
 * Swallow all exceptions for query sampler if users haven't onboarded with UBI.
 */
public class ProbabilityProportionalToSizeQuerySampler extends QuerySampler {
    public static final String NAME = "pptss";
    private static final Logger LOGGER = LogManager.getLogger(ProbabilityProportionalToSizeQuerySampler.class);
    private static final double EPSILON = 0.00001;

    public ProbabilityProportionalToSizeQuerySampler(int size, Client client) {
        super(size, client);
    }

    @Override
    public CompletableFuture<Map<String, Integer>> sample() {
        // Get queries from the UBI queries index.
        SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder().query(QueryBuilders.matchAllQuery()).size(10000);
        CompletableFuture<Map<String, Integer>> future = new CompletableFuture<>();

        getUserQueries(searchSourceBuilder, new ActionListener<Collection<String>>() {
            @Override
            public void onResponse(Collection<String> userQueries) {
                try {
                    if (userQueries.isEmpty()) {
                        LOGGER.warn("No queries found in {}", UBI_QUERIES_INDEX);
                        future.complete(new HashMap<>());
                        return;
                    }
                    Map<String, Integer> result = getQuerySet(userQueries);
                    future.complete(result);
                } catch (Exception e) {
                    LOGGER.error("Error processing user queries", e);
                    future.complete(new HashMap<>());
                }
            }

            @Override
            public void onFailure(Exception e) {
                LOGGER.error("Failed to retrieve queries from {}: {}", UBI_QUERIES_INDEX, e.getMessage());
                future.complete(new HashMap<>());
            }
        });

        return future;

    }

    private Map<String, Integer> getQuerySet(Collection<String> userQueries) {
        final Map<String, Long> weights = new HashMap<>();
        final Map<String, Double> normalizedWeights = new HashMap<>();
        final Map<String, Double> cumulativeWeights = new HashMap<>();
        final Map<String, Integer> querySet = new HashMap<>();

        // Increment the weight for each user query.
        userQueries.forEach(query -> weights.merge(query, 1L, Long::sum));

        // The total number of queries will be used to normalize the weights.
        final long countOfQueries = userQueries.size();

        // Calculate normalized weights
        weights.forEach((query, weight) -> normalizedWeights.put(query, weight.doubleValue() / countOfQueries));

        // Validate normalized weights
        double sumOfNormalizedWeights = normalizedWeights.values().stream().mapToDouble(Double::doubleValue).sum();

        if (!compareDouble(1.0, sumOfNormalizedWeights)) {
            throw new IllegalStateException("Summed normalized weights do not equal 1.0: " + sumOfNormalizedWeights);
        }

        // Create weight "ranges" for each query.
        double[] lastWeight = { 0.0 };
        normalizedWeights.forEach((query, weight) -> {
            lastWeight[0] += weight;
            cumulativeWeights.put(query, lastWeight[0]);
        });

        // The last weight should be 1.0.
        if (!compareDouble(lastWeight[0], 1.0)) {
            throw new IllegalStateException("The sum of cumulative weights does not equal 1.0: " + lastWeight[0]);
        }

        final UniformRealDistribution uniform = new UniformRealDistribution(0, 1);

        for (int i = 1; i <= getSize(); i++) {

            final double r = uniform.sample();

            for (final String userQuery : cumulativeWeights.keySet()) {

                final double cumulativeWeight = cumulativeWeights.get(userQuery);
                if (cumulativeWeight >= r) {
                    // This ignores duplicate queries.
                    querySet.put(userQuery, Math.toIntExact(weights.get(userQuery)));
                    break;
                }

            }

        }
        return querySet;
    }

    private void getUserQueries(SearchSourceBuilder searchSourceBuilder, ActionListener<Collection<String>> listener) {
        Collection<String> userQueries = new ArrayList<>();
        scrollUserQueries(searchSourceBuilder, new Scroll(TimeValue.timeValueMinutes(10L)), userQueries, null, listener);
    }

    private void scrollUserQueries(
        SearchSourceBuilder searchSourceBuilder,
        Scroll scroll,
        Collection<String> accumulator,
        String scrollId,
        ActionListener<Collection<String>> listener
    ) {

        try {
            if (scrollId == null) {
                // Initial search
                SearchRequest searchRequest = new SearchRequest(UBI_QUERIES_INDEX).scroll(scroll).source(searchSourceBuilder);

                getClient().search(
                    searchRequest,
                    ActionListener.wrap(
                        searchResponse -> processSearchResponse(searchResponse, scroll, accumulator, listener),
                        listener::onFailure
                    )
                );
            } else {
                // Subsequent scroll requests
                SearchScrollRequest scrollRequest = new SearchScrollRequest(scrollId).scroll(scroll);

                getClient().searchScroll(
                    scrollRequest,
                    ActionListener.wrap(
                        searchResponse -> processSearchResponse(searchResponse, scroll, accumulator, listener),
                        listener::onFailure
                    )
                );
            }
        } catch (Exception e) {
            listener.onFailure(e);
        }
    }

    private void processSearchResponse(
        SearchResponse searchResponse,
        Scroll scroll,
        Collection<String> accumulator,
        ActionListener<Collection<String>> listener
    ) {
        try {
            SearchHit[] hits = searchResponse.getHits().getHits();

            if (hits == null || hits.length == 0) {
                listener.onResponse(accumulator);
                return;
            }

            for (SearchHit hit : hits) {
                Map<String, Object> fields = hit.getSourceAsMap();
                String userQuery = fields.get("user_query").toString();
                accumulator.add(userQuery);
                LOGGER.debug("User queries count: {} user query: {}", accumulator.size(), userQuery);
            }

            // Continue scrolling
            scrollUserQueries(null, scroll, accumulator, searchResponse.getScrollId(), listener);
        } catch (Exception e) {
            listener.onFailure(e);
        }
    }

    private boolean compareDouble(double a, double b) {
        return Math.abs(a - b) < EPSILON;
    }
}
