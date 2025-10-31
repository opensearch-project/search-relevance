/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */
package org.opensearch.searchrelevance.experiment;

import static org.opensearch.searchrelevance.common.PluginConstants.EVALUATION_RESULT_INDEX;
import static org.opensearch.searchrelevance.common.PluginConstants.EXPERIMENTS_URI;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.hc.core5.http.HttpHeaders;
import org.apache.hc.core5.http.message.BasicHeader;
import org.opensearch.client.Response;
import org.opensearch.rest.RestRequest;
import org.opensearch.test.OpenSearchIntegTestCase;

import com.carrotsearch.randomizedtesting.annotations.ThreadLeakScope;
import com.google.common.collect.ImmutableList;

import lombok.SneakyThrows;

/**
 * Integration tests for search evaluation experiments (POINTWISE_EVALUATION type).
 * This test class verifies the functionality of running pointwise evaluation experiments
 * that evaluate search configurations against query sets and judgments.
 */
@ThreadLeakScope(ThreadLeakScope.Scope.NONE)
@OpenSearchIntegTestCase.ClusterScope(scope = OpenSearchIntegTestCase.Scope.SUITE)
public class SearchEvaluationExperimentIT extends BaseExperimentIT {

    private static final String INDEX_NAME_ESCI = generateUniqueIndexName("searchevaluation");
    public static final int DEFAULT_SIZE_FOR_SEARCH_CONFIGURATION = 5;

    @SneakyThrows
    public void testSearchEvaluationExperiment_whenSimpleMatchQuery_thenSuccessful() {
        // Arrange
        initializeIndexIfNotExist(INDEX_NAME_ESCI);

        String searchConfigurationId = createSimpleSearchConfiguration(INDEX_NAME_ESCI);
        String querySetId = createQuerySet();
        String judgmentId = createJudgment();

        // Act
        String experimentId = createSearchEvaluationExperiment(querySetId, searchConfigurationId, judgmentId);

        // Assert
        Map<String, Object> experimentSource = pollExperimentUntilCompleted(experimentId);
        assertSearchEvaluationExperimentCreation(experimentSource, judgmentId, searchConfigurationId, querySetId);

        Map<String, String> queryTextToEvaluationId = extractQueryTextToEvaluationId(experimentSource);
        assertEvaluationResults(queryTextToEvaluationId, judgmentId, searchConfigurationId);

        deleteIndex(INDEX_NAME_ESCI);
    }

    @SneakyThrows
    private String createSearchEvaluationExperiment(String querySetId, String searchConfigurationId, String judgmentId) {
        String createExperimentBody = replacePlaceholders(
            Files.readString(Path.of(classLoader.getResource("experiment/CreateExperimentPointwiseEvaluation.json").toURI())),
            Map.of("query_set_id", querySetId, "search_configuration_id", searchConfigurationId, "judgment_id", judgmentId)
        );
        Response createExperimentResponse = makeRequest(
            client(),
            RestRequest.Method.PUT.name(),
            EXPERIMENTS_URI,
            null,
            toHttpEntity(createExperimentBody),
            ImmutableList.of(new BasicHeader(HttpHeaders.USER_AGENT, DEFAULT_USER_AGENT))
        );
        Map<String, Object> createExperimentResultJson = entityAsMap(createExperimentResponse);
        String experimentId = createExperimentResultJson.get("experiment_id").toString();
        assertNotNull(experimentId);
        assertEquals("CREATED", createExperimentResultJson.get("experiment_result").toString());

        Thread.sleep(DEFAULT_INTERVAL_MS);
        return experimentId;
    }

    private void assertSearchEvaluationExperimentCreation(
        Map<String, Object> source,
        String judgmentId,
        String searchConfigurationId,
        String querySetId
    ) {
        // Assert common experiment fields
        assertCommonExperimentFields(source, judgmentId, searchConfigurationId, querySetId, "POINTWISE_EVALUATION");

        // Assert experiment status
        assertEquals("COMPLETED", source.get("status"));

        // Assert results structure
        List<Map<String, Object>> results = (List<Map<String, Object>>) source.get("results");
        assertNotNull(results);
        assertEquals(8, results.size()); // Should have results for all 8 expected query terms

        // Assert each result has the required fields
        for (Map<String, Object> result : results) {
            assertNotNull("evaluationId should be present", result.get("evaluationId"));
            assertEquals("searchConfigurationId should match", searchConfigurationId, result.get("searchConfigurationId"));
            assertNotNull("queryText should be present", result.get("queryText"));
            assertTrue("queryText should be one of the expected terms", EXPECTED_QUERY_TERMS.contains(result.get("queryText")));
        }

        // Assert size field if present
        if (source.containsKey("size")) {
            assertEquals(DEFAULT_SIZE_FOR_SEARCH_CONFIGURATION, source.get("size"));
        }
    }

    private Map<String, String> extractQueryTextToEvaluationId(Map<String, Object> experimentSource) {
        List<Map<String, Object>> results = (List<Map<String, Object>>) experimentSource.get("results");
        Map<String, String> queryTextToEvaluationId = new HashMap<>();

        for (Map<String, Object> result : results) {
            String queryText = (String) result.get("queryText");
            String evaluationId = (String) result.get("evaluationId");
            queryTextToEvaluationId.put(queryText, evaluationId);
        }

        return queryTextToEvaluationId;
    }

    @SneakyThrows
    private void assertEvaluationResults(Map<String, String> queryTextToEvaluationId, String judgmentId, String searchConfigurationId) {
        for (String queryTerm : queryTextToEvaluationId.keySet()) {
            String evaluationId = queryTextToEvaluationId.get(queryTerm);

            String getEvaluationByIdUrl = String.join("/", EVALUATION_RESULT_INDEX, "_doc", evaluationId);
            Response getEvaluationResponse = makeRequest(
                client(),
                RestRequest.Method.GET.name(),
                getEvaluationByIdUrl,
                null,
                null,
                ImmutableList.of(new BasicHeader(HttpHeaders.USER_AGENT, DEFAULT_USER_AGENT))
            );
            Map<String, Object> getEvaluationResultJson = entityAsMap(getEvaluationResponse);
            assertNotNull(getEvaluationResultJson);

            Map<String, Object> evaluationSource = (Map<String, Object>) getEvaluationResultJson.get("_source");
            assertNotNull(evaluationSource);

            // Verify the search text matches the query
            String actualQueryTerm = evaluationSource.get("searchText").toString();
            assertEquals("Evaluation search text should match query term", queryTerm, actualQueryTerm);

            // Verify judgment reference
            List<String> judgmentIds = (List<String>) evaluationSource.get("judgmentIds");
            assertNotNull("JudgmentIds should exist", judgmentIds);
            assertFalse("JudgmentIds should not be empty", judgmentIds.isEmpty());
            assertEquals("First judgment ID should match", judgmentId, judgmentIds.get(0));

            // Verify search configuration reference
            assertEquals("Search configuration ID should match", searchConfigurationId, evaluationSource.get("searchConfigurationId"));

            // Verify experiment fields are present for pointwise evaluation experiments
            assertNotNull("experimentId should be present", evaluationSource.get("experimentId"));
            assertNotNull("experimentVariantId should be present", evaluationSource.get("experimentVariantId"));
            assertNull(
                "experimentVariantParameters should be null for pointwise evaluation",
                evaluationSource.get("experimentVariantParameters")
            );

            // Verify we have metrics
            List<Map> metrics = (List<Map>) evaluationSource.get("metrics");
            assertNotNull("Metrics should exist", metrics);
            assertFalse("Metrics should not be empty", metrics.isEmpty());
            assertEquals("Should have 4 metrics", 4, metrics.size());

            // Verify we have document IDs
            List<String> documentIds = (List<String>) evaluationSource.get("documentIds");
            assertNotNull("Document IDs should exist", documentIds);
            assertFalse("Document IDs should not be empty", documentIds.isEmpty());

            // For specific queries, verify detailed results match expectations
            if (EXPECT_EVALUATION_RESULTS.containsKey(actualQueryTerm)) {
                Map<String, Object> expectedResult = (Map<String, Object>) EXPECT_EVALUATION_RESULTS.get(actualQueryTerm);
                List<String> expectedDocumentIds = (List<String>) expectedResult.get("documentIds");
                assertListsHaveSameElements(expectedDocumentIds, documentIds);

                Map<String, Double> expectedMetrics = (Map<String, Double>) expectedResult.get("metrics");
                for (Map<String, Object> actualMetric : metrics) {
                    String metricName = actualMetric.get("metric").toString();
                    Double actualValue = Double.parseDouble(actualMetric.get("value").toString());
                    if (expectedMetrics.containsKey(metricName)) {
                        assertEquals(
                            "Metric " + metricName + " should match expected value",
                            expectedMetrics.get(metricName),
                            actualValue,
                            0.02
                        );
                    }
                }
            }
        }
    }
}
