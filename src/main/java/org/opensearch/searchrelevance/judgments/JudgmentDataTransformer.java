/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */
package org.opensearch.searchrelevance.judgments;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Handles data transformation for judgment processing
 */
public class JudgmentDataTransformer {

    public static Map<String, Object> createJudgmentResult(String queryTextWithReference, Map<String, String> docIdToScore) {
        Map<String, Object> judgmentForQuery = new HashMap<>();
        judgmentForQuery.put("query", queryTextWithReference);

        List<Map<String, String>> docIdRatings = docIdToScore == null
            ? List.of()
            : docIdToScore.entrySet()
                .stream()
                .map(entry -> Map.of("docId", entry.getKey(), "rating", entry.getValue()))
                .collect(Collectors.toList());

        judgmentForQuery.put("ratings", docIdRatings);
        return judgmentForQuery;
    }

    public static String extractQueryText(String queryTextWithReference, String delimiter) {
        return queryTextWithReference.split(delimiter, 2)[0];
    }
}
