/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */
package org.opensearch.searchrelevance.judgments.clickmodel.coec;

import static org.opensearch.searchrelevance.common.PluginConstants.UBI_EVENTS_INDEX;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.opensearch.action.search.ClearScrollRequest;
import org.opensearch.action.search.SearchRequest;
import org.opensearch.action.search.SearchResponse;
import org.opensearch.action.search.SearchScrollRequest;
import org.opensearch.common.unit.TimeValue;
import org.opensearch.core.action.ActionListener;
import org.opensearch.index.query.BoolQueryBuilder;
import org.opensearch.index.query.QueryBuilders;
import org.opensearch.search.SearchHit;
import org.opensearch.search.aggregations.AggregationBuilders;
import org.opensearch.search.aggregations.bucket.terms.Terms;
import org.opensearch.search.aggregations.bucket.terms.TermsAggregationBuilder;
import org.opensearch.search.builder.SearchSourceBuilder;
import org.opensearch.searchrelevance.judgments.clickmodel.ClickModel;
import org.opensearch.searchrelevance.model.ClickthroughRate;
import org.opensearch.searchrelevance.model.ubi.event.UbiEvent;
import org.opensearch.searchrelevance.utils.JsonUtils;
import org.opensearch.transport.client.Client;

public class CoecClickModel extends ClickModel {

    public static final String CLICK_MODEL_NAME = "coec";
    private static final TimeValue SEARCH_TIMEOUT = TimeValue.timeValueMinutes(5);
    private static final int SCROLL_SIZE = 1000;
    private static final TimeValue SCROLL_TIMEOUT = TimeValue.timeValueMinutes(10);

    private final CoecClickModelParameters parameters;
    private final Client client;

    private static final Logger LOGGER = LogManager.getLogger(CoecClickModel.class.getName());

    public CoecClickModel(final Client client, final CoecClickModelParameters parameters) {
        this.parameters = parameters;
        this.client = client;
    }

    @Override
    public void calculateJudgments(ActionListener<Map<String, Map<String, String>>> listener) {
        // Step 1: Calculate rank-aggregated click-through
        getRankAggregatedClickThrough(ActionListener.wrap(rankAggregatedClickThrough -> {
            // Step 2: Get clickthrough rates
            getClickthroughRate(ActionListener.wrap(clickthroughRates -> {
                try {
                    // Step 3: Calculate final judgments
                    calculateCoecJudgments(rankAggregatedClickThrough, clickthroughRates, listener);
                } catch (Exception e) {
                    listener.onFailure(e);
                }
            }, listener::onFailure));
        }, listener::onFailure));
    }

    private void getRankAggregatedClickThrough(ActionListener<Map<Integer, Double>> listener) {
        LOGGER.info("Starting rank aggregated clickthrough calculation");

        BoolQueryBuilder queryBuilder = QueryBuilders.boolQuery()
            .must(QueryBuilders.rangeQuery("event_attributes.position.ordinal").lte(parameters.getMaxRank()));

        SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder().query(queryBuilder).size(SCROLL_SIZE).timeout(SEARCH_TIMEOUT);

        // Add aggregations to see distribution
        TermsAggregationBuilder actionAgg = AggregationBuilders.terms("actions")
            .field("action_name")
            .subAggregation(
                AggregationBuilders.terms("positions").field("event_attributes.position.ordinal").size(parameters.getMaxRank())
            );

        searchSourceBuilder.aggregation(actionAgg);

        SearchRequest searchRequest = new SearchRequest(UBI_EVENTS_INDEX).source(searchSourceBuilder);

        client.search(searchRequest, ActionListener.wrap(response -> {
            try {
                Map<Integer, Double> rankAggregatedClickThrough = new HashMap<>();
                Map<Integer, Long> clickCounts = new HashMap<>();
                Map<Integer, Long> impressionCounts = new HashMap<>();

                Terms actionTerms = response.getAggregations().get("actions");

                // Log overall statistics
                LOGGER.debug("Total buckets in aggregation: {}", actionTerms.getBuckets().size());

                for (Terms.Bucket actionBucket : actionTerms.getBuckets()) {
                    String action = actionBucket.getKeyAsString();
                    long docCount = actionBucket.getDocCount();
                    LOGGER.debug("Action: {} - Count: {}", action, docCount);

                    Terms positionTerms = actionBucket.getAggregations().get("positions");
                    for (Terms.Bucket positionBucket : positionTerms.getBuckets()) {
                        int position = Integer.parseInt(positionBucket.getKeyAsString());
                        long count = positionBucket.getDocCount();

                        if ("click".equalsIgnoreCase(action)) {
                            clickCounts.put(position, count);
                            LOGGER.debug("Position {} clicks: {}", position, count);
                        } else if ("impression".equalsIgnoreCase(action)) {
                            impressionCounts.put(position, count);
                            LOGGER.debug("Position {} impressions: {}", position, count);
                        }
                    }
                }

                // Calculate CTR for each position
                for (int rank = 0; rank < parameters.getMaxRank(); rank++) {
                    long impressions = impressionCounts.getOrDefault(rank, 0L);
                    long clicks = clickCounts.getOrDefault(rank, 0L);

                    LOGGER.debug("Rank {}: {} clicks / {} impressions", rank, clicks, impressions);

                    if (impressions > 0) {
                        double ctr = (double) clicks / impressions;
                        rankAggregatedClickThrough.put(rank, ctr);
                        LOGGER.debug("CTR for rank {}: {}", rank, ctr);
                    } else {
                        rankAggregatedClickThrough.put(rank, 0.0);
                        LOGGER.debug("No impressions for rank {}, CTR set to 0", rank);
                    }
                }

                listener.onResponse(rankAggregatedClickThrough);
            } catch (Exception e) {
                LOGGER.error("Error processing aggregations", e);
                listener.onFailure(e);
            }
        }, e -> {
            LOGGER.error("Search failed", e);
            listener.onFailure(e);
        }));
    }

    private void getClickthroughRate(ActionListener<Map<String, Set<ClickthroughRate>>> listener) {
        LOGGER.info("Starting clickthrough rate calculation");
        Map<String, Set<ClickthroughRate>> queriesToClickthroughRates = new ConcurrentHashMap<>();

        BoolQueryBuilder queryBuilder = QueryBuilders.boolQuery()
            .must(QueryBuilders.rangeQuery("event_attributes.position.ordinal").lte(parameters.getMaxRank()));

        SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder().query(queryBuilder)
            .size(SCROLL_SIZE)
            .timeout(SEARCH_TIMEOUT)
            .fetchSource(
                new String[] {
                    "query_id",
                    "action_name",
                    "user_query",
                    "event_attributes.object.object_id",
                    "event_attributes.position.ordinal" },
                null
            );

        SearchRequest searchRequest = new SearchRequest(UBI_EVENTS_INDEX).source(searchSourceBuilder).scroll(SCROLL_TIMEOUT);

        processClickthroughSearch(searchRequest, queriesToClickthroughRates, listener);
    }

    private void processClickthroughSearch(
        SearchRequest searchRequest,
        Map<String, Set<ClickthroughRate>> queriesToClickthroughRates,
        ActionListener<Map<String, Set<ClickthroughRate>>> listener
    ) {
        client.search(searchRequest, new ActionListener<SearchResponse>() {
            @Override
            public void onResponse(SearchResponse response) {
                SearchHit[] hits = response.getHits().getHits();
                LOGGER.info("Processing batch of {} hits", hits.length);

                for (SearchHit hit : hits) {
                    try {
                        UbiEvent event = JsonUtils.fromJson(hit.getSourceAsString(), UbiEvent.class);
                        String userQuery = event.getUserQuery();
                        String objectId = event.getEventAttributes().getObject().getObjectId();
                        String action = event.getActionName();
                        int rank = event.getEventAttributes().getPosition().getOrdinal();

                        Set<ClickthroughRate> rates = queriesToClickthroughRates.computeIfAbsent(
                            userQuery,
                            k -> ConcurrentHashMap.newKeySet()
                        );

                        ClickthroughRate rate = rates.stream().filter(r -> r.getObjectId().equals(objectId)).findFirst().orElseGet(() -> {
                            ClickthroughRate newRate = new ClickthroughRate(objectId);
                            rates.add(newRate);
                            return newRate;
                        });

                        if ("click".equalsIgnoreCase(action)) {
                            rate.logClick();
                            rate.logRank(rank);
                            LOGGER.debug("Logged click for query: {} doc: {} rank: {}", userQuery, objectId, rank);
                        } else if ("impression".equalsIgnoreCase(action)) {
                            rate.logImpression();
                            rate.logRank(rank);
                            LOGGER.debug("Logged impression for query: {} doc: {} rank: {}", userQuery, objectId, rank);
                        }
                    } catch (Exception e) {
                        LOGGER.warn("Error processing hit: " + hit.getId(), e);
                    }
                }

                if (hits.length == 0) {
                    // Print final statistics
                    for (Map.Entry<String, Set<ClickthroughRate>> entry : queriesToClickthroughRates.entrySet()) {
                        LOGGER.debug("Query: {} - Number of docs: {}", entry.getKey(), entry.getValue().size());
                        for (ClickthroughRate rate : entry.getValue()) {
                            LOGGER.debug(
                                "Doc: {} - Clicks: {} Impressions: {}",
                                rate.getObjectId(),
                                rate.getClicks(),
                                rate.getImpressions()
                            );
                        }
                    }

                    listener.onResponse(queriesToClickthroughRates);
                } else {
                    // Continue scrolling
                    SearchScrollRequest scrollRequest = new SearchScrollRequest(response.getScrollId()).scroll(SCROLL_TIMEOUT);
                    client.searchScroll(scrollRequest, this);
                }
            }

            @Override
            public void onFailure(Exception e) {
                LOGGER.error("Search failed", e);
                listener.onFailure(e);
            }
        });
    }

    private void processClickEvents(Map<Integer, Long> clickCounts, ActionListener<Map<Integer, Long>> listener) {
        BoolQueryBuilder queryBuilder = QueryBuilders.boolQuery()
            .must(QueryBuilders.termQuery("action_name.keyword", "click"))
            .must(QueryBuilders.rangeQuery("event_attributes.position.ordinal").lte(parameters.getMaxRank()));

        SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder().query(queryBuilder).size(SCROLL_SIZE).timeout(SEARCH_TIMEOUT);

        SearchRequest searchRequest = new SearchRequest(UBI_EVENTS_INDEX).source(searchSourceBuilder).scroll(SCROLL_TIMEOUT);

        LOGGER.debug("Starting click events scroll search");
        scrollEvents(searchRequest, null, clickCounts, "click", listener);
    }

    private void processImpressionEvents(
        Map<Integer, Long> clickCounts,
        Map<Integer, Long> impressionCounts,
        ActionListener<Map<Integer, Double>> finalListener
    ) {
        BoolQueryBuilder queryBuilder = QueryBuilders.boolQuery()
            .must(QueryBuilders.termQuery("action_name.keyword", "impression"))
            .must(QueryBuilders.rangeQuery("event_attributes.position.ordinal").lte(parameters.getMaxRank()));

        SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder().query(queryBuilder).size(SCROLL_SIZE).timeout(SEARCH_TIMEOUT);

        SearchRequest searchRequest = new SearchRequest(UBI_EVENTS_INDEX).source(searchSourceBuilder).scroll(SCROLL_TIMEOUT);

        LOGGER.debug("Starting impression events scroll search");
        scrollEvents(searchRequest, null, impressionCounts, "impression", ActionListener.wrap(impressionCountsResult -> {
            LOGGER.info("Completed processing impression events, calculating CTR");
            calculateCTR(clickCounts, impressionCountsResult, finalListener);
        }, e -> {
            LOGGER.error("Failed to process impression events", e);
            finalListener.onFailure(e);
        }));
    }

    private void scrollEvents(
        SearchRequest initialRequest,
        String scrollId,
        Map<Integer, Long> counts,
        String eventType,
        ActionListener<Map<Integer, Long>> listener
    ) {
        if (scrollId == null) {
            client.search(initialRequest, new ActionListener<SearchResponse>() {
                @Override
                public void onResponse(SearchResponse response) {
                    processScrollResponse(response, counts, eventType, listener);
                }

                @Override
                public void onFailure(Exception e) {
                    LOGGER.error("Initial search request failed for " + eventType, e);
                    listener.onFailure(e);
                }
            });
        } else {
            SearchScrollRequest scrollRequest = new SearchScrollRequest(scrollId).scroll(SCROLL_TIMEOUT);

            client.searchScroll(scrollRequest, new ActionListener<SearchResponse>() {
                @Override
                public void onResponse(SearchResponse response) {
                    processScrollResponse(response, counts, eventType, listener);
                }

                @Override
                public void onFailure(Exception e) {
                    LOGGER.error("Scroll request failed for " + eventType, e);
                    listener.onFailure(e);
                }
            });
        }
    }

    private void processScrollResponse(
        SearchResponse response,
        Map<Integer, Long> counts,
        String eventType,
        ActionListener<Map<Integer, Long>> listener
    ) {
        try {
            SearchHit[] hits = response.getHits().getHits();
            LOGGER.debug("Processing {} {} events", hits.length, eventType);

            if (hits.length == 0) {
                if (response.getScrollId() != null) {
                    ClearScrollRequest clearScrollRequest = new ClearScrollRequest();
                    clearScrollRequest.addScrollId(response.getScrollId());
                    client.clearScroll(clearScrollRequest, ActionListener.wrap(clearResponse -> {
                        LOGGER.debug("Scroll cleared for {} events", eventType);
                        listener.onResponse(counts);
                    }, e -> {
                        LOGGER.warn("Failed to clear scroll", e);
                        listener.onResponse(counts); // Continue anyway
                    }));
                } else {
                    listener.onResponse(counts);
                }
                return;
            }

            for (SearchHit hit : hits) {
                try {
                    UbiEvent event = JsonUtils.fromJson(hit.getSourceAsString(), UbiEvent.class);
                    int position = event.getEventAttributes().getPosition().getOrdinal();
                    if (position < parameters.getMaxRank()) {
                        counts.merge(position, 1L, Long::sum);
                    }
                } catch (Exception e) {
                    LOGGER.warn("Error processing hit: " + hit.getId(), e);
                }
            }

            // Continue scrolling
            scrollEvents(null, response.getScrollId(), counts, eventType, listener);
        } catch (Exception e) {
            LOGGER.error("Error processing scroll response for " + eventType, e);
            listener.onFailure(e);
        }
    }

    private void calculateCTR(
        Map<Integer, Long> clickCounts,
        Map<Integer, Long> impressionCounts,
        ActionListener<Map<Integer, Double>> listener
    ) {
        try {
            Map<Integer, Double> rankAggregatedClickThrough = new HashMap<>();

            for (int rank = 0; rank < parameters.getMaxRank(); rank++) {
                long impressions = impressionCounts.getOrDefault(rank, 0L);
                if (impressions > 0) {
                    double ctr = (double) clickCounts.getOrDefault(rank, 0L) / impressions;
                    rankAggregatedClickThrough.put(rank, ctr);
                    LOGGER.debug(
                        "Rank {}: {} clicks / {} impressions = {} CTR",
                        rank,
                        clickCounts.getOrDefault(rank, 0L),
                        impressions,
                        ctr
                    );
                } else {
                    rankAggregatedClickThrough.put(rank, 0.0);
                    LOGGER.debug("Rank {}: No impressions, CTR = 0.0", rank);
                }
            }

            LOGGER.info("CTR calculation completed for {} ranks", rankAggregatedClickThrough.size());
            listener.onResponse(rankAggregatedClickThrough);
        } catch (Exception e) {
            LOGGER.error("Error calculating CTR", e);
            listener.onFailure(e);
        }
    }

    private void scrollRankAggregatedData(
        SearchRequest initialRequest,
        String scrollId,
        Map<Integer, Long> clickCounts,
        Map<Integer, Long> impressionCounts,
        ActionListener<Map<Integer, Double>> listener
    ) {
        if (scrollId == null) {
            client.search(initialRequest, new ActionListener<SearchResponse>() {
                @Override
                public void onResponse(SearchResponse response) {
                    processScrolledHits(response, clickCounts, impressionCounts, listener);
                }

                @Override
                public void onFailure(Exception e) {
                    listener.onFailure(e);
                }
            });
        } else {
            SearchScrollRequest scrollRequest = new SearchScrollRequest(scrollId).scroll(SCROLL_TIMEOUT);

            client.searchScroll(scrollRequest, new ActionListener<SearchResponse>() {
                @Override
                public void onResponse(SearchResponse response) {
                    processScrolledHits(response, clickCounts, impressionCounts, listener);
                }

                @Override
                public void onFailure(Exception e) {
                    listener.onFailure(e);
                }
            });
        }
    }

    private void processScrolledHits(
        SearchResponse response,
        Map<Integer, Long> clickCounts,
        Map<Integer, Long> impressionCounts,
        ActionListener<Map<Integer, Double>> listener
    ) {
        SearchHit[] hits = response.getHits().getHits();

        if (hits.length == 0) {
            // No more hits, calculate final results
            Map<Integer, Double> rankAggregatedClickThrough = new HashMap<>();

            for (int rank = 0; rank < parameters.getMaxRank(); rank++) {
                long impressions = impressionCounts.getOrDefault(rank, 0L);
                if (impressions > 0) {
                    double ctr = (double) clickCounts.getOrDefault(rank, 0L) / impressions;
                    rankAggregatedClickThrough.put(rank, ctr);
                } else {
                    rankAggregatedClickThrough.put(rank, 0.0);
                }
            }

            // Clear scroll and return results
            if (response.getScrollId() != null) {
                ClearScrollRequest clearScrollRequest = new ClearScrollRequest();
                clearScrollRequest.addScrollId(response.getScrollId());
                client.clearScroll(
                    clearScrollRequest,
                    ActionListener.wrap(clearResponse -> listener.onResponse(rankAggregatedClickThrough), listener::onFailure)
                );
            } else {
                listener.onResponse(rankAggregatedClickThrough);
            }
            return;
        }

        // Process current batch
        for (SearchHit hit : hits) {
            try {
                UbiEvent event = JsonUtils.fromJson(hit.getSourceAsString(), UbiEvent.class);
                int position = event.getEventAttributes().getPosition().getOrdinal();

                if (position < parameters.getMaxRank()) {
                    if ("click".equalsIgnoreCase(event.getActionName())) {
                        clickCounts.merge(position, 1L, Long::sum);
                    } else if ("impression".equalsIgnoreCase(event.getActionName())) {
                        impressionCounts.merge(position, 1L, Long::sum);
                    }
                }
            } catch (Exception e) {
                LOGGER.warn("Error processing hit: " + hit.getId(), e);
            }
        }

        // Continue scrolling
        scrollRankAggregatedData(null, response.getScrollId(), clickCounts, impressionCounts, listener);
    }

    private void scrollClickthroughRates(
        SearchRequest initialRequest,
        String scrollId,
        Map<String, Set<ClickthroughRate>> queriesToClickthroughRates,
        ActionListener<Map<String, Set<ClickthroughRate>>> listener
    ) {
        if (scrollId == null) {
            client.search(initialRequest, new ActionListener<SearchResponse>() {
                @Override
                public void onResponse(SearchResponse response) {
                    processClickthroughBatch(response, queriesToClickthroughRates, listener);
                }

                @Override
                public void onFailure(Exception e) {
                    LOGGER.error("Initial clickthrough search failed", e);
                    listener.onFailure(e);
                }
            });
        } else {
            SearchScrollRequest scrollRequest = new SearchScrollRequest(scrollId).scroll(SCROLL_TIMEOUT);

            client.searchScroll(scrollRequest, new ActionListener<SearchResponse>() {
                @Override
                public void onResponse(SearchResponse response) {
                    processClickthroughBatch(response, queriesToClickthroughRates, listener);
                }

                @Override
                public void onFailure(Exception e) {
                    LOGGER.error("Clickthrough scroll request failed", e);
                    listener.onFailure(e);
                }
            });
        }
    }

    private void processClickthroughBatch(
        SearchResponse response,
        Map<String, Set<ClickthroughRate>> queriesToClickthroughRates,
        ActionListener<Map<String, Set<ClickthroughRate>>> listener
    ) {
        SearchHit[] hits = response.getHits().getHits();
        LOGGER.debug("Processing {} hits for clickthrough rates", hits.length);

        if (hits.length == 0) {
            if (response.getScrollId() != null) {
                ClearScrollRequest clearScrollRequest = new ClearScrollRequest();
                clearScrollRequest.addScrollId(response.getScrollId());
                client.clearScroll(clearScrollRequest, ActionListener.wrap(clearResponse -> {
                    LOGGER.info("Completed clickthrough rate calculation with {} queries", queriesToClickthroughRates.size());
                    listener.onResponse(queriesToClickthroughRates);
                }, e -> {
                    LOGGER.warn("Failed to clear scroll", e);
                    listener.onResponse(queriesToClickthroughRates);
                }));
            } else {
                listener.onResponse(queriesToClickthroughRates);
            }
            return;
        }

        AtomicInteger pendingHits = new AtomicInteger(hits.length);
        AtomicBoolean hasError = new AtomicBoolean(false);

        for (SearchHit hit : hits) {
            try {
                UbiEvent ubiEvent = JsonUtils.fromJson(hit.getSourceAsString(), UbiEvent.class);
                String queryId = ubiEvent.getQueryId();

                getUserQuery(queryId, ActionListener.wrap(userQuery -> {
                    if (userQuery != null) {
                        synchronized (queriesToClickthroughRates) {
                            Set<ClickthroughRate> clickthroughRates = queriesToClickthroughRates.computeIfAbsent(
                                userQuery,
                                k -> new HashSet<>()
                            );

                            ClickthroughRate clickthroughRate = clickthroughRates.stream()
                                .filter(ctr -> ctr.getObjectId().equals(ubiEvent.getEventAttributes().getObject().getObjectId()))
                                .findFirst()
                                .orElseGet(() -> new ClickthroughRate(ubiEvent.getEventAttributes().getObject().getObjectId()));

                            if ("click".equalsIgnoreCase(ubiEvent.getActionName())) {
                                clickthroughRate.logClick();
                            } else if ("impression".equalsIgnoreCase(ubiEvent.getActionName())) {
                                clickthroughRate.logImpression();
                            }

                            clickthroughRates.add(clickthroughRate);
                        }
                    }
                    checkBatchCompletion(pendingHits, hasError, response.getScrollId(), queriesToClickthroughRates, listener);
                }, e -> {
                    LOGGER.warn("Error processing user query for hit: " + hit.getId(), e);
                    hasError.set(true);
                    checkBatchCompletion(pendingHits, hasError, response.getScrollId(), queriesToClickthroughRates, listener);
                }));
            } catch (Exception e) {
                LOGGER.warn("Error processing hit: " + hit.getId(), e);
                hasError.set(true);
                checkBatchCompletion(pendingHits, hasError, response.getScrollId(), queriesToClickthroughRates, listener);
            }
        }
    }

    private void checkBatchCompletion(
        AtomicInteger pendingHits,
        AtomicBoolean hasError,
        String scrollId,
        Map<String, Set<ClickthroughRate>> queriesToClickthroughRates,
        ActionListener<Map<String, Set<ClickthroughRate>>> listener
    ) {
        if (pendingHits.decrementAndGet() == 0) {
            if (hasError.get()) {
                listener.onFailure(new IllegalStateException("Error processing some hits in batch"));
            } else {
                // Continue scrolling
                scrollClickthroughRates(null, scrollId, queriesToClickthroughRates, listener);
            }
        }
    }

    private void getUserQuery(String queryId, ActionListener<String> listener) {
        SearchSourceBuilder sourceBuilder = new SearchSourceBuilder().query(QueryBuilders.termQuery("query_id.keyword", queryId))
            .size(1)
            // Specify only the fields we need
            .fetchSource(new String[] { "user_query" }, null);

        SearchRequest searchRequest = new SearchRequest("ubi_queries").source(sourceBuilder);

        client.search(searchRequest, ActionListener.wrap(response -> {
            if (response.getHits().getHits().length > 0) {
                SearchHit hit = response.getHits().getHits()[0];
                Map<String, Object> source = hit.getSourceAsMap();
                listener.onResponse((String) source.get("user_query"));
            } else {
                listener.onResponse(null);
            }
        }, e -> {
            LOGGER.warn("Failed to get user query for queryId: " + queryId, e);
            listener.onResponse(null);
        }));
    }

    private void getQueryCount(String userQuery, String objectId, int rank, ActionListener<Long> listener) {
        BoolQueryBuilder queryBuilder = QueryBuilders.boolQuery()
            .must(QueryBuilders.termQuery("action_name", "impression"))
            .must(QueryBuilders.termQuery("event_attributes.position.ordinal", rank))
            .must(QueryBuilders.termQuery("event_attributes.object.object_id", objectId));

        SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder().query(queryBuilder).trackTotalHits(true).size(0);

        SearchRequest searchRequest = new SearchRequest(UBI_EVENTS_INDEX).source(searchSourceBuilder);

        client.search(
            searchRequest,
            ActionListener.wrap(response -> listener.onResponse(response.getHits().getTotalHits().value()), listener::onFailure)
        );
    }

    private void calculateCoecJudgments(
        Map<Integer, Double> rankAggregatedClickThrough,
        Map<String, Set<ClickthroughRate>> clickthroughRates,
        ActionListener<Map<String, Map<String, String>>> listener
    ) {
        LOGGER.debug("Starting COEC calculation with rank CTR: {}", rankAggregatedClickThrough);
        Map<String, Map<String, String>> judgmentScores = new HashMap<>();

        for (Map.Entry<String, Set<ClickthroughRate>> entry : clickthroughRates.entrySet()) {
            String userQuery = entry.getKey();
            Map<String, String> docScores = new HashMap<>();

            for (ClickthroughRate ctr : entry.getValue()) {
                // Get the lowest rank at which this query-document pair was interacted with
                int observedRank = ctr.getRank();
                double expectedCtrForThisRank = rankAggregatedClickThrough.getOrDefault(observedRank, 0.0);
                // Calculate expected clicks for *this* document at its observed rank
                double expectedClicksForDocAtRank = expectedCtrForThisRank * ctr.getImpressions();

                // Calculate COEC score
                double score;
                if (expectedClicksForDocAtRank > 0) {
                    score = ctr.getClicks() / expectedClicksForDocAtRank;
                } else {
                    // if there are neither impressions nor a rank-aggregated CTR the COEC score is 0
                    score = 0.0;
                }
                LOGGER.debug("judgment score: {}, query: {}, doc: {}, rank: {}", score, userQuery, ctr.getObjectId(), observedRank);
                docScores.put(ctr.getObjectId(), String.format(Locale.ROOT, "%.3f", score));
            }

            if (!docScores.isEmpty()) {
                judgmentScores.put(userQuery, docScores);
            }
            LOGGER.debug(
                "Final judgment scores size - Queries: {}, Total Documents: {}",
                judgmentScores.size(),
                judgmentScores.values().stream().mapToInt(Map::size).sum()
            );

            listener.onResponse(judgmentScores);
        }
    }

}
