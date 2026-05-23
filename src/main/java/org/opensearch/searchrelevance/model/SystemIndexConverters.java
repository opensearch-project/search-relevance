/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */
package org.opensearch.searchrelevance.model;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.opensearch.action.search.SearchResponse;
import org.opensearch.core.rest.RestStatus;
import org.opensearch.searchrelevance.exception.SearchRelevanceException;

/**
 * Converts system index search hits into model objects (shared across transport paths).
 */
public final class SystemIndexConverters {

    private SystemIndexConverters() {}

    public static QuerySet toQuerySet(SearchResponse response) {
        if (response.getHits().getTotalHits().value() == 0) {
            throw new SearchRelevanceException("QuerySet not found", RestStatus.NOT_FOUND);
        }
        Map<String, Object> sourceMap = response.getHits().getHits()[0].getSourceAsMap();
        List<QuerySetEntry> querySetEntries = new ArrayList<>();
        Object querySetQueriesObj = sourceMap.get(QuerySet.QUERY_SET_QUERIES);
        if (querySetQueriesObj instanceof List) {
            List<Map<String, Object>> querySetQueriesList = (List<Map<String, Object>>) querySetQueriesObj;
            querySetEntries = querySetQueriesList.stream()
                .map(entryMap -> QuerySetEntry.Builder.builder().queryText((String) entryMap.get(QuerySetEntry.QUERY_TEXT)).build())
                .collect(Collectors.toList());
        }
        return QuerySet.Builder.builder()
            .id((String) sourceMap.get(QuerySet.ID))
            .name((String) sourceMap.get(QuerySet.NAME))
            .description((String) sourceMap.get(QuerySet.DESCRIPTION))
            .timestamp((String) sourceMap.get(QuerySet.TIME_STAMP))
            .sampling((String) sourceMap.get(QuerySet.SAMPLING))
            .querySetQueries(querySetEntries)
            .build();
    }

    public static SearchConfiguration toSearchConfiguration(SearchResponse response) {
        if (response.getHits().getTotalHits().value() == 0) {
            throw new SearchRelevanceException("SearchConfiguration not found", RestStatus.NOT_FOUND);
        }
        Map<String, Object> source = response.getHits().getHits()[0].getSourceAsMap();
        return new SearchConfiguration(
            (String) source.get(SearchConfiguration.ID),
            (String) source.get(SearchConfiguration.NAME),
            (String) source.get(SearchConfiguration.TIME_STAMP),
            (String) source.get(SearchConfiguration.INDEX),
            (String) source.get(SearchConfiguration.QUERY),
            (String) source.get(SearchConfiguration.SEARCH_PIPELINE),
            (String) source.get(SearchConfiguration.DESCRIPTION)
        );
    }

    @SuppressWarnings("unchecked")
    public static Judgment toJudgment(SearchResponse response) {
        if (response.getHits().getTotalHits().value() == 0) {
            throw new SearchRelevanceException("Judgment not found", RestStatus.NOT_FOUND);
        }
        Map<String, Object> source = response.getHits().getHits()[0].getSourceAsMap();
        Map<String, Object> metadata = Map.of();
        Object metaObj = source.get(Judgment.METADATA);
        if (metaObj instanceof Map<?, ?> m) {
            metadata = new HashMap<>();
            for (Map.Entry<?, ?> e : m.entrySet()) {
                if (e.getKey() != null) {
                    metadata.put(String.valueOf(e.getKey()), e.getValue());
                }
            }
        }
        List<Map<String, Object>> ratings = new ArrayList<>();
        Object jr = source.get(Judgment.JUDGMENT_RATINGS);
        if (jr instanceof List<?> list) {
            for (Object o : list) {
                if (o instanceof Map<?, ?> row) {
                    ratings.add((Map<String, Object>) row);
                }
            }
        }
        String id = source.get(Judgment.ID) == null ? "" : String.valueOf(source.get(Judgment.ID));
        String ts = source.get(Judgment.TIME_STAMP) == null ? "" : String.valueOf(source.get(Judgment.TIME_STAMP));
        String name = source.get(Judgment.NAME) == null ? "" : String.valueOf(source.get(Judgment.NAME));
        return new Judgment(
            id,
            ts,
            name,
            AsyncStatus.valueOf(String.valueOf(source.get(Judgment.STATUS))),
            JudgmentType.valueOf(String.valueOf(source.get(Judgment.TYPE))),
            metadata,
            ratings
        );
    }
}
