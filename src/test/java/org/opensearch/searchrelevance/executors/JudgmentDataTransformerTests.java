/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */
package org.opensearch.searchrelevance.executors;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

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
        String queryTextWithCustomInput = "laptop||Professional laptop for business";
        Map<String, String> docIdToScore = Map.of("doc1", "0.9", "doc2", "0.7", "doc3", "0.5");

        // Act
        Map<String, Object> result = transformer.createJudgmentResult(queryTextWithCustomInput, docIdToScore);

        // Assert
        assertEquals(queryTextWithCustomInput, result.get("query"));

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
        String queryTextWithCustomInput = "laptop||Professional laptop for business";
        Map<String, String> docIdToScore = Map.of();

        // Act
        Map<String, Object> result = transformer.createJudgmentResult(queryTextWithCustomInput, docIdToScore);

        // Assert
        assertEquals(queryTextWithCustomInput, result.get("query"));

        List<Map<String, Object>> ratings = (List<Map<String, Object>>) result.get("ratings");
        assertEquals(0, ratings.size());
    }

    public void testCreateJudgmentResultWithNullRatings() {
        // Arrange
        String queryTextWithCustomInput = "laptop||Professional laptop for business";
        Map<String, String> docIdToScore = null;

        // Act
        Map<String, Object> result = transformer.createJudgmentResult(queryTextWithCustomInput, docIdToScore);

        // Assert
        assertEquals(queryTextWithCustomInput, result.get("query"));

        List<Map<String, Object>> ratings = (List<Map<String, Object>>) result.get("ratings");
        assertEquals(0, ratings.size());
    }

    public void testCreateJudgmentResultWithQueryOnly() {
        // Arrange
        String queryTextWithCustomInput = "laptop";
        Map<String, String> docIdToScore = Map.of("doc1", "0.8");

        // Act
        Map<String, Object> result = transformer.createJudgmentResult(queryTextWithCustomInput, docIdToScore);

        // Assert
        assertEquals(queryTextWithCustomInput, result.get("query"));

        List<Map<String, Object>> ratings = (List<Map<String, Object>>) result.get("ratings");
        assertEquals(1, ratings.size());
        assertEquals("doc1", ratings.get(0).get("docId"));
        assertEquals("0.8", ratings.get(0).get("rating"));
    }

    public void testCreateJudgmentResultRatingStructure() {
        // Arrange
        String queryTextWithCustomInput = "test query";
        Map<String, String> docIdToScore = Map.of("testDoc", "0.95");

        // Act
        Map<String, Object> result = transformer.createJudgmentResult(queryTextWithCustomInput, docIdToScore);

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
        String queryTextWithCustomInput = "test query";
        Map<String, String> docIdToScore = Map.of("docA", "0.1", "docB", "0.2", "docC", "0.3");

        // Act
        Map<String, Object> result = transformer.createJudgmentResult(queryTextWithCustomInput, docIdToScore);

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
        String queryTextWithCustomInput = "special||query with \"quotes\" and 'apostrophes'";
        Map<String, String> docIdToScore = Map.of("doc-with-dash", "0.6");

        // Act
        Map<String, Object> result = transformer.createJudgmentResult(queryTextWithCustomInput, docIdToScore);

        // Assert
        assertEquals(queryTextWithCustomInput, result.get("query"));

        List<Map<String, Object>> ratings = (List<Map<String, Object>>) result.get("ratings");
        assertEquals(1, ratings.size());
        assertEquals("doc-with-dash", ratings.get(0).get("docId"));
        assertEquals("0.6", ratings.get(0).get("rating"));
    }

    public void testCreateJudgmentResultWithZeroRating() {
        // Arrange
        String queryTextWithCustomInput = "test query";
        Map<String, String> docIdToScore = Map.of("doc1", "0.0");

        // Act
        Map<String, Object> result = transformer.createJudgmentResult(queryTextWithCustomInput, docIdToScore);

        // Assert
        List<Map<String, Object>> ratings = (List<Map<String, Object>>) result.get("ratings");
        assertEquals(1, ratings.size());
        assertEquals("doc1", ratings.get(0).get("docId"));
        assertEquals("0.0", ratings.get(0).get("rating"));
    }

    public void testCreateJudgmentResultWithMaxRating() {
        // Arrange
        String queryTextWithCustomInput = "test query";
        Map<String, String> docIdToScore = Map.of("doc1", "1.0");

        // Act
        Map<String, Object> result = transformer.createJudgmentResult(queryTextWithCustomInput, docIdToScore);

        // Assert
        List<Map<String, Object>> ratings = (List<Map<String, Object>>) result.get("ratings");
        assertEquals(1, ratings.size());
        assertEquals("doc1", ratings.get(0).get("docId"));
        assertEquals("1.0", ratings.get(0).get("rating"));
    }

    public void testBuildFailedDocsListsUnratedDocs() {
        Set<String> sentDocIds = Set.of("A", "B", "C");
        Set<String> ratedDocIds = Set.of("A", "B");

        List<Map<String, String>> failures = JudgmentDataTransformer.buildFailedDocs(sentDocIds, ratedDocIds);

        assertEquals(1, failures.size());
        assertEquals("C", failures.get(0).get("docId"));
    }

    public void testBuildFailedDocsAllRated() {
        Set<String> sentDocIds = Set.of("A", "B");
        Set<String> ratedDocIds = Set.of("A", "B");

        assertTrue(JudgmentDataTransformer.buildFailedDocs(sentDocIds, ratedDocIds).isEmpty());
    }

    public void testBuildFailedDocsNoDocsSent() {
        // No search hits for the query: nothing was sent, so there is nothing to report as failed.
        assertTrue(JudgmentDataTransformer.buildFailedDocs(Set.of(), Set.of()).isEmpty());
    }

    public void testBuildFailedDocsAllFailed() {
        Set<String> sentDocIds = Set.of("A", "B");
        Set<String> ratedDocIds = Set.of();

        List<Map<String, String>> failures = JudgmentDataTransformer.buildFailedDocs(sentDocIds, ratedDocIds);

        assertEquals(2, failures.size());
        Set<String> failedIds = failures.stream().map(f -> f.get("docId")).collect(java.util.stream.Collectors.toSet());
        assertEquals(Set.of("A", "B"), failedIds);
    }

    private Map<String, Object> resultWithFailures(String query, Map<String, String> ratings, String... failedDocIds) {
        Map<String, Object> result = transformer.createJudgmentResult(query, ratings);
        if (failedDocIds.length > 0) {
            List<Map<String, String>> failures = new java.util.ArrayList<>();
            for (String docId : failedDocIds) {
                failures.add(Map.of("docId", docId));
            }
            result.put("failures", failures);
        }
        return result;
    }

    public void testBuildJudgmentSummaryAllSuccessful() {
        List<Map<String, Object>> results = List.of(
            transformer.createJudgmentResult("q1", Map.of("doc1", "0.9")),
            transformer.createJudgmentResult("q2", Map.of("doc2", "0.4"))
        );

        Map<String, Object> summary = JudgmentDataTransformer.buildJudgmentSummary(results);

        assertEquals(2, summary.get("totalQueries"));
        assertEquals(2, summary.get("successfulQueries"));
        assertEquals(0, summary.get("failedQueries"));
        assertFalse("no lastFailureReason when nothing failed", summary.containsKey("lastFailureReason"));
    }

    public void testBuildJudgmentSummaryCountsQueryWithFailuresAsFailed() {
        // "good" is fully rated; "bad" has an unrated doc, so it counts as failed.
        List<Map<String, Object>> results = List.of(
            resultWithFailures("good", Map.of("doc1", "1.0")),
            resultWithFailures("bad", Map.of(), "doc2")
        );

        Map<String, Object> summary = JudgmentDataTransformer.buildJudgmentSummary(results);

        assertEquals(2, summary.get("totalQueries"));
        assertEquals(1, summary.get("successfulQueries"));
        assertEquals(1, summary.get("failedQueries"));
    }

    public void testBuildJudgmentSummaryPartiallyRatedQueryCountsAsFailed() {
        // A query with some real ratings AND an unrated doc still counts as failed.
        List<Map<String, Object>> results = List.of(resultWithFailures("laptop", Map.of("doc1", "1.0"), "doc6"));

        Map<String, Object> summary = JudgmentDataTransformer.buildJudgmentSummary(results);

        assertEquals(1, summary.get("totalQueries"));
        assertEquals(0, summary.get("successfulQueries"));
        assertEquals(1, summary.get("failedQueries"));
    }

    public void testBuildJudgmentSummaryRecordsLastFailureReason() {
        Map<String, Object> failed = resultWithFailures("bad", Map.of(), "doc1");
        failed.put(JudgmentDataTransformer.RESULT_FAILURE_REASON, "model timed out");

        List<Map<String, Object>> results = List.of(transformer.createJudgmentResult("good", Map.of("doc1", "1.0")), failed);

        Map<String, Object> summary = JudgmentDataTransformer.buildJudgmentSummary(results);

        assertEquals(1, summary.get("successfulQueries"));
        assertEquals(1, summary.get("failedQueries"));
        assertEquals("model timed out", summary.get("lastFailureReason"));
    }

    public void testBuildJudgmentSummaryEmptyResults() {
        Map<String, Object> summary = JudgmentDataTransformer.buildJudgmentSummary(List.of());

        assertEquals(0, summary.get("totalQueries"));
        assertEquals(0, summary.get("successfulQueries"));
        assertEquals(0, summary.get("failedQueries"));
        assertFalse(summary.containsKey("lastFailureReason"));
    }

    public void testApplyJudgmentSummaryClearsStaleFailureReason() {
        // Metadata from an earlier partially-failed run: every doc has since been rated, so the
        // recorded reason no longer applies and must not survive the recompute.
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("modelId", "test-model");
        metadata.put("failedQueries", 2);
        metadata.put("lastFailureReason", "ThrottlingException: Rate exceeded");

        List<Map<String, Object>> results = List.of(
            transformer.createJudgmentResult("q1", Map.of("doc1", "0.9")),
            transformer.createJudgmentResult("q2", Map.of("doc2", "0.4"))
        );

        JudgmentDataTransformer.applyJudgmentSummary(metadata, results);

        assertEquals(0, metadata.get("failedQueries"));
        assertEquals(2, metadata.get("successfulQueries"));
        assertFalse("stale lastFailureReason must be cleared", metadata.containsKey("lastFailureReason"));
        assertEquals("unrelated metadata must be preserved", "test-model", metadata.get("modelId"));
    }

    public void testApplyJudgmentSummaryKeepsFailureReasonWhileStillFailing() {
        // Some docs are still unrated, so the reason from this run is recorded and kept.
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("lastFailureReason", "an older reason");

        Map<String, Object> failed = resultWithFailures("bad", Map.of(), "doc1");
        failed.put(JudgmentDataTransformer.RESULT_FAILURE_REASON, "model timed out");
        List<Map<String, Object>> results = List.of(transformer.createJudgmentResult("good", Map.of("doc1", "1.0")), failed);

        JudgmentDataTransformer.applyJudgmentSummary(metadata, results);

        assertEquals(1, metadata.get("failedQueries"));
        assertEquals("model timed out", metadata.get("lastFailureReason"));
    }

    public void testApplyJudgmentSummaryKeepsStoredReasonForReloadedJudgment() {
        // A judgment reloaded from the index has no transient RESULT_FAILURE_REASON on its per-query
        // entries, so the recomputed summary carries no reason. Queries are still failing, so the
        // reason already stored in metadata must be left alone.
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("lastFailureReason", "ThrottlingException: Rate exceeded");

        List<Map<String, Object>> results = List.of(
            resultWithFailures("good", Map.of("doc1", "1.0")),
            resultWithFailures("bad", Map.of(), "doc2")
        );

        JudgmentDataTransformer.applyJudgmentSummary(metadata, results);

        assertEquals(1, metadata.get("failedQueries"));
        assertEquals("ThrottlingException: Rate exceeded", metadata.get("lastFailureReason"));
    }

    public void testApplyJudgmentSummaryNoFailureReasonToClear() {
        // Nothing failed before or now: the key was never there and must not be added.
        Map<String, Object> metadata = new HashMap<>();

        JudgmentDataTransformer.applyJudgmentSummary(metadata, List.of(transformer.createJudgmentResult("q1", Map.of("doc1", "0.9"))));

        assertEquals(0, metadata.get("failedQueries"));
        assertFalse(metadata.containsKey("lastFailureReason"));
    }
}
