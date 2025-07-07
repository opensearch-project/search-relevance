/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */
package org.opensearch.searchrelevance.executors;

import java.util.List;
import java.util.Map;

import org.opensearch.searchrelevance.judgments.JudgmentDataTransformer;
import org.opensearch.test.OpenSearchTestCase;

public class JudgmentDataTransformerTests extends OpenSearchTestCase {

    private JudgmentDataTransformer transformer;

    @Override
    public void setUp() throws Exception {
        super.setUp();
        transformer = new JudgmentDataTransformer();
    }

    public void testCreateJudgmentResultWithRatings() {
        // Arrange
        String queryTextWithReference = "laptop||Professional laptop for business";
        Map<String, String> docIdToScore = Map.of("doc1", "0.9", "doc2", "0.7", "doc3", "0.5");

        // Act
        Map<String, Object> result = transformer.createJudgmentResult(queryTextWithReference, docIdToScore);

        // Assert
        assertEquals(queryTextWithReference, result.get("query"));

        List<Map<String, Object>> ratings = (List<Map<String, Object>>) result.get("ratings");
        assertEquals(3, ratings.size());

        // Verify ratings content
        Map<String, String> ratingsMap = Map.of(
            (String) ratings.get(0).get("docId"),
            (String) ratings.get(0).get("rating"),
            (String) ratings.get(1).get("docId"),
            (String) ratings.get(1).get("rating"),
            (String) ratings.get(2).get("docId"),
            (String) ratings.get(2).get("rating")
        );

        assertEquals("0.9", ratingsMap.get("doc1"));
        assertEquals("0.7", ratingsMap.get("doc2"));
        assertEquals("0.5", ratingsMap.get("doc3"));
    }

    public void testCreateJudgmentResultWithEmptyRatings() {
        // Arrange
        String queryTextWithReference = "laptop||Professional laptop for business";
        Map<String, String> docIdToScore = Map.of();

        // Act
        Map<String, Object> result = transformer.createJudgmentResult(queryTextWithReference, docIdToScore);

        // Assert
        assertEquals(queryTextWithReference, result.get("query"));

        List<Map<String, Object>> ratings = (List<Map<String, Object>>) result.get("ratings");
        assertEquals(0, ratings.size());
    }

    public void testCreateJudgmentResultWithNullRatings() {
        // Arrange
        String queryTextWithReference = "laptop||Professional laptop for business";
        Map<String, String> docIdToScore = null;

        // Act
        Map<String, Object> result = transformer.createJudgmentResult(queryTextWithReference, docIdToScore);

        // Assert
        assertEquals(queryTextWithReference, result.get("query"));

        List<Map<String, Object>> ratings = (List<Map<String, Object>>) result.get("ratings");
        assertEquals(0, ratings.size());
    }

    public void testCreateJudgmentResultWithQueryOnly() {
        // Arrange
        String queryTextWithReference = "laptop";
        Map<String, String> docIdToScore = Map.of("doc1", "0.8");

        // Act
        Map<String, Object> result = transformer.createJudgmentResult(queryTextWithReference, docIdToScore);

        // Assert
        assertEquals(queryTextWithReference, result.get("query"));

        List<Map<String, Object>> ratings = (List<Map<String, Object>>) result.get("ratings");
        assertEquals(1, ratings.size());
        assertEquals("doc1", ratings.get(0).get("docId"));
        assertEquals("0.8", ratings.get(0).get("rating"));
    }

    public void testCreateJudgmentResultRatingStructure() {
        // Arrange
        String queryTextWithReference = "test query";
        Map<String, String> docIdToScore = Map.of("testDoc", "0.95");

        // Act
        Map<String, Object> result = transformer.createJudgmentResult(queryTextWithReference, docIdToScore);

        // Assert
        List<Map<String, Object>> ratings = (List<Map<String, Object>>) result.get("ratings");
        Map<String, Object> rating = ratings.get(0);

        assertEquals(2, rating.size());
        assertTrue(rating.containsKey("docId"));
        assertTrue(rating.containsKey("rating"));
        assertEquals("testDoc", rating.get("docId"));
        assertEquals("0.95", rating.get("rating"));
    }

    public void testCreateJudgmentResultMultipleRatingsOrder() {
        // Arrange
        String queryTextWithReference = "test query";
        Map<String, String> docIdToScore = Map.of("docA", "0.1", "docB", "0.2", "docC", "0.3");

        // Act
        Map<String, Object> result = transformer.createJudgmentResult(queryTextWithReference, docIdToScore);

        // Assert
        List<Map<String, Object>> ratings = (List<Map<String, Object>>) result.get("ratings");
        assertEquals(3, ratings.size());

        // Verify all expected docIds are present
        List<String> docIds = ratings.stream().map(rating -> (String) rating.get("docId")).toList();

        assertTrue(docIds.contains("docA"));
        assertTrue(docIds.contains("docB"));
        assertTrue(docIds.contains("docC"));
    }

    public void testCreateJudgmentResultWithSpecialCharacters() {
        // Arrange
        String queryTextWithReference = "special||query with \"quotes\" and 'apostrophes'";
        Map<String, String> docIdToScore = Map.of("doc-with-dash", "0.6");

        // Act
        Map<String, Object> result = transformer.createJudgmentResult(queryTextWithReference, docIdToScore);

        // Assert
        assertEquals(queryTextWithReference, result.get("query"));

        List<Map<String, Object>> ratings = (List<Map<String, Object>>) result.get("ratings");
        assertEquals(1, ratings.size());
        assertEquals("doc-with-dash", ratings.get(0).get("docId"));
        assertEquals("0.6", ratings.get(0).get("rating"));
    }

    public void testCreateJudgmentResultWithZeroRating() {
        // Arrange
        String queryTextWithReference = "test query";
        Map<String, String> docIdToScore = Map.of("doc1", "0.0");

        // Act
        Map<String, Object> result = transformer.createJudgmentResult(queryTextWithReference, docIdToScore);

        // Assert
        List<Map<String, Object>> ratings = (List<Map<String, Object>>) result.get("ratings");
        assertEquals(1, ratings.size());
        assertEquals("doc1", ratings.get(0).get("docId"));
        assertEquals("0.0", ratings.get(0).get("rating"));
    }

    public void testCreateJudgmentResultWithMaxRating() {
        // Arrange
        String queryTextWithReference = "test query";
        Map<String, String> docIdToScore = Map.of("doc1", "1.0");

        // Act
        Map<String, Object> result = transformer.createJudgmentResult(queryTextWithReference, docIdToScore);

        // Assert
        List<Map<String, Object>> ratings = (List<Map<String, Object>>) result.get("ratings");
        assertEquals(1, ratings.size());
        assertEquals("doc1", ratings.get(0).get("docId"));
        assertEquals("1.0", ratings.get(0).get("rating"));
    }
}
