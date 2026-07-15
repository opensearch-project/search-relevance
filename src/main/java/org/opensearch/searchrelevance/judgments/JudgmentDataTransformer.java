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
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Handles data transformation for judgment processing
 */
public class JudgmentDataTransformer {

    /** Transient key on a per-query result carrying its failure reason. Used to build the metadata summary; not persisted. */
    public static final String RESULT_FAILURE_REASON = "failureReason";

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
     * Builds the list of docs that were sent to the LLM but never received a rating. Each entry holds
     * only the docId: a failure is request-level (throttling, timeout, parse error), so there is no
     * reliable per-doc reason to attach.
     *
     * @param sentDocIds  docs that were sent to the LLM (all search hits for the query)
     * @param ratedDocIds docs that came back with a rating
     * @return a list of {"docId": ...} maps for the unrated docs, empty when everything was rated
     */
    public static List<Map<String, String>> buildFailedDocs(Set<String> sentDocIds, Set<String> ratedDocIds) {
        List<Map<String, String>> failures = new ArrayList<>();
        for (String docId : sentDocIds) {
            if (!ratedDocIds.contains(docId)) {
                failures.add(Map.of("docId", docId));
            }
        }
        return failures;
    }

    /**
     * Summarises the per-query results into a metadata overview: total/successful/failed query counts
     * plus the last failure reason seen. A query counts as failed when it has at least one unrated doc
     * (a non-empty "failures" list), so a partially-rated query is reported as failed rather than
     * hiding the gap. This is a quick overview only; the per-doc detail lives on each entry's "failures".
     *
     * @param judgmentResults per-query results, each holding "query", "ratings", an optional "failures" list and an optional {@link #RESULT_FAILURE_REASON}
     * @return a map with totalQueries, successfulQueries, failedQueries and (when any failed) lastFailureReason
     */
    public static Map<String, Object> buildJudgmentSummary(List<Map<String, Object>> judgmentResults) {
        int failedQueries = 0;
        String lastFailureReason = null;

        for (Map<String, Object> result : judgmentResults) {
            Object failures = result.get("failures");
            if (failures instanceof List && !((List<?>) failures).isEmpty()) {
                failedQueries++;
            }
            Object reason = result.get(RESULT_FAILURE_REASON);
            if (reason != null) {
                lastFailureReason = reason.toString();
            }
        }

        Map<String, Object> summary = new HashMap<>();
        summary.put("totalQueries", judgmentResults.size());
        summary.put("successfulQueries", judgmentResults.size() - failedQueries);
        summary.put("failedQueries", failedQueries);
        if (lastFailureReason != null) {
            summary.put("lastFailureReason", lastFailureReason);
        }
        return summary;
    }

    public static String extractQueryText(String queryTextWithCustomInput, String delimiter) {
        return queryTextWithCustomInput.split(delimiter, 2)[0];
    }
}
