/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */
package org.opensearch.searchrelevance.experiment;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.opensearch.searchrelevance.model.AsyncStatus;
import org.opensearch.searchrelevance.model.Judgment;
import org.opensearch.searchrelevance.model.JudgmentType;
import org.opensearch.test.OpenSearchTestCase;

public class JudgmentRatingsMapperTests extends OpenSearchTestCase {

    public void testBuildQueryTextToDocIdRatingsMap_EmptyJudgments() {
        assertTrue(JudgmentRatingsMapper.buildQueryTextToDocIdRatingsMap(null).isEmpty());
        assertTrue(JudgmentRatingsMapper.buildQueryTextToDocIdRatingsMap(Collections.emptyList()).isEmpty());
    }

    public void testBuildQueryTextToDocIdRatingsMap_MergesMultipleJudgments() {
        Judgment judgment1 = new Judgment(
            "j1",
            "ts",
            "name",
            AsyncStatus.COMPLETED,
            JudgmentType.IMPORT_JUDGMENT,
            Map.of(),
            Arrays.asList(
                Map.of("query", "q1", "ratings", Arrays.asList(Map.of("docId", "d1", "rating", "5"), Map.of("docId", "d2", "rating", "3"))),
                Map.of("query", "q2", "ratings", Arrays.asList(Map.of("docId", "d3", "rating", "1")))
            )
        );

        Judgment judgment2 = new Judgment(
            "j2",
            "ts",
            "name",
            AsyncStatus.COMPLETED,
            JudgmentType.IMPORT_JUDGMENT,
            Map.of(),
            Arrays.asList(
                Map.of("query", "q1", "ratings", Arrays.asList(Map.of("docId", "d2", "rating", "4"), Map.of("docId", "d4", "rating", "2")))
            )
        );

        Map<String, Map<String, String>> result = JudgmentRatingsMapper.buildQueryTextToDocIdRatingsMap(
            Arrays.asList(judgment1, judgment2)
        );

        assertEquals(2, result.size());
        Map<String, String> q1Ratings = result.get("q1");
        assertEquals(3, q1Ratings.size());
        assertEquals("5", q1Ratings.get("d1"));
        assertEquals("4", q1Ratings.get("d2")); // later judgment overwrites earlier rating
        assertEquals("2", q1Ratings.get("d4"));

        Map<String, String> q2Ratings = result.get("q2");
        assertEquals(1, q2Ratings.size());
        assertEquals("1", q2Ratings.get("d3"));
    }

    public void testBuildQueryTextToDocIdRatingsMap_SkipsNullEntries() {
        Map<String, Object> nullQueryRating = new HashMap<>();
        nullQueryRating.put("query", null);
        nullQueryRating.put("ratings", Arrays.asList(Map.of("docId", "d2", "rating", "3")));

        Map<String, Object> nullRatingsEntry = new HashMap<>();
        nullRatingsEntry.put("query", "q2");
        nullRatingsEntry.put("ratings", null);

        Judgment judgment = new Judgment(
            "j1",
            "ts",
            "name",
            AsyncStatus.COMPLETED,
            JudgmentType.IMPORT_JUDGMENT,
            Map.of(),
            Arrays.asList(
                Map.of("query", "q1", "ratings", Arrays.asList(Map.of("docId", "d1", "rating", "5"))),
                nullQueryRating,
                nullRatingsEntry
            )
        );

        Map<String, Map<String, String>> result = JudgmentRatingsMapper.buildQueryTextToDocIdRatingsMap(List.of(judgment));

        assertEquals(1, result.size());
        assertTrue(result.containsKey("q1"));
        assertFalse(result.containsKey("q2"));
        assertEquals("5", result.get("q1").get("d1"));
    }
}
