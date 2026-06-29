/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */
package org.opensearch.searchrelevance.judgments;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Handles data transformation for judgment processing
 */
public class JudgmentDataTransformer {

    public static Map<String, Object> createJudgmentResult(String queryTextWithCustomInput, Map<String, String> docIdToScore) {
        Map<String, Object> judgmentForQuery = new HashMap<>();
        judgmentForQuery.put("query", queryTextWithCustomInput);

        List<Map<String, String>> docIdRatings = docIdToScore == null
            ? List.of()
            : docIdToScore.entrySet()
                .stream()
                .map(entry -> Map.of("docId", entry.getKey(), "rating", entry.getValue()))
                .collect(Collectors.toList());

        judgmentForQuery.put("ratings", docIdRatings);
        return judgmentForQuery;
    }

    /**
     * Summarises the per-query judgment results into success/failure counts and a list of failed
     * queries with their reasons. A query is considered failed when it produced no ratings; the
     * reason is taken from the optional "error" entry on the result, or a default message otherwise.
     *
     * @param judgmentResults per-query results, each holding "query", "ratings" and an optional "error"
     * @return a map with totalQueries, successfulQueries, failedQueries and a failures list
     */
    public static Map<String, Object> buildJudgmentSummary(List<Map<String, Object>> judgmentResults) {
        List<Map<String, Object>> failures = new ArrayList<>();
        int successfulQueries = 0;

        for (Map<String, Object> result : judgmentResults) {
            Object ratings = result.get("ratings");
            boolean hasRatings = ratings instanceof List && !((List<?>) ratings).isEmpty();
            if (hasRatings) {
                successfulQueries++;
            } else {
                Object error = result.get("error");
                Map<String, Object> failure = new HashMap<>();
                failure.put("query", result.get("query"));
                failure.put("reason", error != null ? error.toString() : "No ratings were generated");
                failures.add(failure);
            }
        }

        Map<String, Object> summary = new HashMap<>();
        summary.put("totalQueries", judgmentResults.size());
        summary.put("successfulQueries", successfulQueries);
        summary.put("failedQueries", failures.size());
        summary.put("failures", failures);
        return summary;
    }

    public static String extractQueryText(String queryTextWithCustomInput, String delimiter) {
        return queryTextWithCustomInput.split(delimiter, 2)[0];
    }
}
