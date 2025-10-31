/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */
package org.opensearch.searchrelevance.utils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.opensearch.test.OpenSearchTestCase;

public class ParserUtilsTests extends OpenSearchTestCase {

    public void testConvertObjToListOfMaps_ValidInput() {
        // Setup
        Map<String, Object> source = new HashMap<>();
        List<Map<String, Object>> testData = List.of(
            Map.of("searchText", "query 1", "dcg@10", 0.8),
            Map.of("searchText", "query 2", "dcg@10", 0.9)
        );
        source.put("evaluationResultList", testData);

        // Execute
        List<Map<String, Object>> result = ParserUtils.convertObjToListOfMaps(source, "evaluationResultList");

        // Verify
        assertEquals(2, result.size());
        assertEquals("query 1", result.get(0).get("searchText"));
        assertEquals(0.8, result.get(0).get("dcg@10"));
        assertEquals("query 2", result.get(1).get("searchText"));
        assertEquals(0.9, result.get(1).get("dcg@10"));
    }

    public void testConvertObjToListOfMaps_EmptyList() {
        // Setup
        Map<String, Object> source = new HashMap<>();
        source.put("evaluationResultList", new ArrayList<>());

        // Execute
        List<Map<String, Object>> result = ParserUtils.convertObjToListOfMaps(source, "evaluationResultList");

        // Verify
        assertEquals(0, result.size());
    }

    public void testConvertObjToListOfMaps_NullField() {
        // Setup
        Map<String, Object> source = new HashMap<>();
        source.put("evaluationResultList", null);

        // Execute
        List<Map<String, Object>> result = ParserUtils.convertObjToListOfMaps(source, "evaluationResultList");

        // Verify
        assertEquals(0, result.size());
    }

    public void testConvertObjToListOfMaps_MissingField() {
        // Setup
        Map<String, Object> source = new HashMap<>();
        // Intentionally not adding the field

        // Execute
        List<Map<String, Object>> result = ParserUtils.convertObjToListOfMaps(source, "evaluationResultList");

        // Verify
        assertEquals(0, result.size());
    }

    public void testConvertObjToListOfMaps_NonListField() {
        // Setup
        Map<String, Object> source = new HashMap<>();
        source.put("evaluationResultList", "not a list");

        // Execute
        List<Map<String, Object>> result = ParserUtils.convertObjToListOfMaps(source, "evaluationResultList");

        // Verify
        assertEquals(0, result.size());
    }

    public void testConvertObjToListOfMaps_ListWithNonMaps() {
        // Setup
        Map<String, Object> source = new HashMap<>();
        List<Object> mixedData = List.of(
            Map.of("searchText", "query 1", "dcg@10", 0.8),
            "not a map",
            Map.of("searchText", "query 2", "dcg@10", 0.9),
            123
        );
        source.put("evaluationResultList", mixedData);

        // Execute
        List<Map<String, Object>> result = ParserUtils.convertObjToListOfMaps(source, "evaluationResultList");

        // Verify - should only include the actual maps
        assertEquals(2, result.size());
        assertEquals("query 1", result.get(0).get("searchText"));
        assertEquals("query 2", result.get(1).get("searchText"));
    }

    public void testConvertObjToListOfMaps_NestedMapsWithMetrics() {
        // Setup
        Map<String, Object> source = new HashMap<>();
        List<Map<String, Object>> testData = List.of(
            Map.of(
                "searchText",
                "query 1",
                "metrics",
                Map.of("dcg@10", 0.8, "ndcg@10", 0.75),
                "judgmentIds",
                List.of("j1", "j2"),
                "documentIds",
                List.of("d1", "d2")
            )
        );
        source.put("evaluationResultList", testData);

        // Execute
        List<Map<String, Object>> result = ParserUtils.convertObjToListOfMaps(source, "evaluationResultList");

        // Verify
        assertEquals(1, result.size());
        Map<String, Object> resultMap = result.get(0);
        assertEquals("query 1", resultMap.get("searchText"));
        assertTrue(resultMap.containsKey("metrics"));
        assertTrue(resultMap.containsKey("judgmentIds"));
        assertTrue(resultMap.containsKey("documentIds"));

        @SuppressWarnings("unchecked")
        Map<String, Object> metrics = (Map<String, Object>) resultMap.get("metrics");
        assertEquals(0.8, metrics.get("dcg@10"));
        assertEquals(0.75, metrics.get("ndcg@10"));
    }

    public void testConvertObjToListOfMaps_BackwardsCompatibilityWithQueryText() {
        // Setup - test that both queryText and searchText work
        Map<String, Object> source = new HashMap<>();
        List<Map<String, Object>> testData = List.of(
            Map.of("queryText", "legacy query 1", "dcg@10", 0.8),
            Map.of("searchText", "new query 2", "dcg@10", 0.9)
        );
        source.put("evaluationResultList", testData);

        // Execute
        List<Map<String, Object>> result = ParserUtils.convertObjToListOfMaps(source, "evaluationResultList");

        // Verify
        assertEquals(2, result.size());
        assertEquals("legacy query 1", result.get(0).get("queryText"));
        assertEquals("new query 2", result.get(1).get("searchText"));
    }

    public void testConvertObjToListOfMaps_ComplexNestedStructure() {
        // Setup
        Map<String, Object> source = new HashMap<>();
        List<Map<String, Object>> testData = List.of(
            Map.of(
                "searchText",
                "complex query",
                "metrics",
                Map.of("dcg@10", 0.8, "ndcg@10", 0.75, "mrr", 0.6),
                "judgmentIds",
                List.of("j1", "j2", "j3"),
                "documentIds",
                List.of("d1", "d2", "d3"),
                "metadata",
                Map.of("timestamp", "2023-01-01", "source", "external_tool")
            )
        );
        source.put("evaluationResultList", testData);

        // Execute
        List<Map<String, Object>> result = ParserUtils.convertObjToListOfMaps(source, "evaluationResultList");

        // Verify
        assertEquals(1, result.size());
        Map<String, Object> resultMap = result.get(0);
        assertEquals("complex query", resultMap.get("searchText"));

        @SuppressWarnings("unchecked")
        Map<String, Object> metrics = (Map<String, Object>) resultMap.get("metrics");
        assertEquals(3, metrics.size());
        assertEquals(0.8, metrics.get("dcg@10"));
        assertEquals(0.75, metrics.get("ndcg@10"));
        assertEquals(0.6, metrics.get("mrr"));

        @SuppressWarnings("unchecked")
        List<String> judgmentIds = (List<String>) resultMap.get("judgmentIds");
        assertEquals(3, judgmentIds.size());
        assertEquals("j1", judgmentIds.get(0));

        @SuppressWarnings("unchecked")
        Map<String, Object> metadata = (Map<String, Object>) resultMap.get("metadata");
        assertEquals("2023-01-01", metadata.get("timestamp"));
        assertEquals("external_tool", metadata.get("source"));
    }
}
