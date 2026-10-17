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

    /** Metadata key holding the most recent failure reason across all queries in the judgment. */
    public static final String LAST_FAILURE_REASON = "lastFailureReason";

    /** Metadata key holding the number of queries with at least one unrated doc. */
    public static final String FAILED_QUERIES = "failedQueries";

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
        summary.put(FAILED_QUERIES, failedQueries);
        if (lastFailureReason != null) {
            summary.put(LAST_FAILURE_REASON, lastFailureReason);
        }
        return summary;
    }

    /**
     * Writes the summary of {@code judgmentResults} into {@code metadata}, replacing the previous
     * counts and clearing a stale failure reason.
     *
     * <p>Prefer this over calling {@link #buildJudgmentSummary} and merging the result by hand.
     * {@code metadata.putAll(summary)} alone cannot clear a key, so a {@code lastFailureReason} left
     * over from an earlier run would survive even after every failed doc had been rated — leaving a
     * healthy judgment reporting a failure that no longer applies. This method drops the key once the
     * recomputed {@code failedQueries} reaches 0, keeping the two consistent: a reason is reported
     * only while something is actually failing.
     *
     * <p>The removal keys off the recomputed count rather than the absence of a reason in the summary,
     * because {@link #RESULT_FAILURE_REASON} is transient. A judgment reloaded from the index carries
     * no per-query reason, so the summary omits {@code lastFailureReason} even when queries are still
     * failing; keying off the count preserves the stored reason in that case.
     *
     * @param metadata the judgment metadata to update in place
     * @param judgmentResults per-query results to summarise, in the form described on {@link #buildJudgmentSummary}
     */
    public static void applyJudgmentSummary(Map<String, Object> metadata, List<Map<String, Object>> judgmentResults) {
        Map<String, Object> summary = buildJudgmentSummary(judgmentResults);
        metadata.putAll(summary);
        if (Integer.valueOf(0).equals(summary.get(FAILED_QUERIES))) {
            metadata.remove(LAST_FAILURE_REASON);
        }
    }

    public static String extractQueryText(String queryTextWithCustomInput, String delimiter) {
        return queryTextWithCustomInput.split(delimiter, 2)[0];
    }
}
