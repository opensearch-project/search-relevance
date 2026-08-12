/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */
package org.opensearch.searchrelevance.experiment;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.opensearch.searchrelevance.model.Judgment;

/**
 * Builds a once-loaded, query-keyed view of judgment ratings.
 *
 * <p>This avoids re-fetching the same large judgment document for every query
 * in the hot path of HYBRID_OPTIMIZER and POINTWISE_EVALUATION experiments.
 */
public final class JudgmentRatingsMapper {

    private JudgmentRatingsMapper() {}

    /**
     * Build a map of query text -&gt; doc id -&gt; rating from the already loaded judgments.
     *
     * @param judgments the full judgments loaded once before per-query processing
     * @return a query-keyed map of document ratings; empty if judgments is null/empty
     */
    @SuppressWarnings("unchecked")
    public static Map<String, Map<String, String>> buildQueryTextToDocIdRatingsMap(List<Judgment> judgments) {
        if (judgments == null || judgments.isEmpty()) {
            return Collections.emptyMap();
        }

        Map<String, Map<String, String>> queryTextToDocIdToRating = new HashMap<>();
        for (Judgment judgment : judgments) {
            List<Map<String, Object>> judgmentRatings = judgment.getJudgmentRatings();
            if (judgmentRatings == null) {
                continue;
            }
            for (Map<String, Object> judgmentRating : judgmentRatings) {
                String queryText = (String) judgmentRating.get("query");
                if (queryText == null) {
                    continue;
                }
                List<Map<String, Object>> docScoreRatings = (List<Map<String, Object>>) judgmentRating.get("ratings");
                if (docScoreRatings == null) {
                    continue;
                }
                Map<String, String> docIdToRating = queryTextToDocIdToRating.computeIfAbsent(queryText, k -> new HashMap<>());
                for (Map<String, Object> docScoreRating : docScoreRatings) {
                    String docId = (String) docScoreRating.get("docId");
                    String rating = (String) docScoreRating.get("rating");
                    if (docId != null && rating != null) {
                        docIdToRating.put(docId, rating);
                    }
                }
            }
        }
        return queryTextToDocIdToRating;
    }
}
