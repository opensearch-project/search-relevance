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
import static org.opensearch.searchrelevance.common.PluginConstants.EXPERIMENT_INDEX;

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
 * Integration tests for pointwise evaluation experiments.
 */
@ThreadLeakScope(ThreadLeakScope.Scope.NONE)
@OpenSearchIntegTestCase.ClusterScope(scope = OpenSearchIntegTestCase.Scope.SUITE)
public class PointwiseExperimentIT extends BaseExperimentIT {

    private static final String INDEX_NAME_ESCI = generateUniqueIndexName("pointwise");

    @SneakyThrows
    public void testPointwiseEvaluationExperiment_whenQueryWithPlaceholder_thenSuccessful() {
        // Arrange
        initializeIndexIfNotExist(INDEX_NAME_ESCI);

        String searchConfigurationId = createSearchConfiguration(INDEX_NAME_ESCI);
        String querySetId = createQuerySet();
        String judgmentId = createJudgment();

        // Act
        String experimentId = createPointwiseExperiment(querySetId, searchConfigurationId, judgmentId);

        // Wait for the experiment to be created and indexed
        Thread.sleep(DEFAULT_INTERVAL_MS);
        Map<String, Object> experimentSource = pollExperimentUntilCompleted(experimentId);
        // Assert experiment exists with correct type
        // We don't wait for completion since it may time out in constrained environments
        assertNotNull("Experiment should exist", experimentSource);
        assertEquals("POINTWISE_EVALUATION", experimentSource.get("type"));
        assertEquals(querySetId, experimentSource.get("querySetId"));

        // Assert
        Map<String, String> queryTextToEvaluationId = assertPointwiseExperimentCreation(
            experimentId,
            judgmentId,
            searchConfigurationId,
            querySetId
        );
        assertEvaluationResults(queryTextToEvaluationId, judgmentId, searchConfigurationId);

        deleteIndex(INDEX_NAME_ESCI);
    }

    @SneakyThrows
    public void testPointwiseEvaluationExperiment_whenQueryWithMustacheTemplate_thenSuccessful() {
        // Arrange
        initializeIndexIfNotExist(INDEX_NAME_ESCI);

        String searchConfigurationId = createSearchConfigurationWithMustache(INDEX_NAME_ESCI);
        String querySetId = createQuerySetWithCustomFields();
        String judgmentId = createJudgment();

        // Act
        String experimentId = createPointwiseExperiment(querySetId, searchConfigurationId, judgmentId);

        // Wait for the experiment to be created and indexed
        Thread.sleep(DEFAULT_INTERVAL_MS);
        Map<String, Object> experimentSource = pollExperimentUntilCompleted(experimentId);
        // Assert experiment exists with correct type
        assertNotNull("Experiment should exist", experimentSource);
        assertEquals("POINTWISE_EVALUATION", experimentSource.get("type"));
        assertEquals(querySetId, experimentSource.get("querySetId"));

        // Assert
        Map<String, String> queryTextToEvaluationId = assertPointwiseExperimentCreation(
            experimentId,
            judgmentId,
            searchConfigurationId,
            querySetId
        );
        // We won't test exact metrics here since query texts and data differ, but we check execution didn't fail
        assertFalse(queryTextToEvaluationId.isEmpty());

        deleteIndex(INDEX_NAME_ESCI);
    }

    @SneakyThrows
    public void testPointwiseEvaluationExperiment_whenTemplateFailsToCompile_thenExperimentCompletes() {
        // A search configuration whose query is an invalid Mustache template (an unsupported partial)
        // must not strand the experiment in PROCESSING: the request build fails, the variant is
        // recorded as failed, and the experiment still reaches COMPLETED.
        initializeIndexIfNotExist(INDEX_NAME_ESCI);

        String searchConfigurationId = createSearchConfigurationWithPartialTemplate(INDEX_NAME_ESCI);
        String querySetId = createQuerySetWithCustomFields();
        String judgmentId = createJudgment();

        String experimentId = createPointwiseExperiment(querySetId, searchConfigurationId, judgmentId);

        Thread.sleep(DEFAULT_INTERVAL_MS);
        // pollExperimentUntilCompleted asserts status becomes COMPLETED — before the fix this hung in
        // PROCESSING and the poll would exhaust its retries.
        Map<String, Object> experimentSource = pollExperimentUntilCompleted(experimentId);
        assertNotNull("Experiment should exist", experimentSource);
        assertEquals("COMPLETED", experimentSource.get("status"));

        deleteIndex(INDEX_NAME_ESCI);
    }

    @SneakyThrows
    private String createPointwiseExperiment(String querySetId, String searchConfigurationId, String judgmentId) {
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

    @SneakyThrows
    private Map<String, String> assertPointwiseExperimentCreation(
        String experimentId,
        String judgmentId,
        String searchConfigurationId,
        String querySetId
    ) {
        String getExperimentByIdUrl = String.join("/", EXPERIMENT_INDEX, "_doc", experimentId);
        Response getExperimentResponse = makeRequest(
            adminClient(),
            RestRequest.Method.GET.name(),
            getExperimentByIdUrl,
            null,
            null,
            ImmutableList.of(new BasicHeader(HttpHeaders.USER_AGENT, DEFAULT_USER_AGENT))
        );
        Map<String, Object> getExperimentResultJson = entityAsMap(getExperimentResponse);
        assertNotNull(getExperimentResultJson);
        assertEquals(experimentId, getExperimentResultJson.get("_id").toString());

        Map<String, Object> source = (Map<String, Object>) getExperimentResultJson.get("_source");
        assertNotNull(source);
        assertEquals("COMPLETED", source.get("status"));

        // Assert common experiment fields
        assertCommonExperimentFields(source, judgmentId, searchConfigurationId, querySetId, "POINTWISE_EVALUATION");

        List<Map<String, Object>> results = (List<Map<String, Object>>) source.get("results");
        assertNotNull(results);

        // convert list of actual results to map of query text and evaluation id
        Map<String, Object> resultsMap = new HashMap<>();
        results.forEach(result -> {
            assertEquals(searchConfigurationId, result.get("searchConfigurationId"));
            resultsMap.put((String) result.get("queryText"), result.get("evaluationId"));
        });
        assertEquals(results.size(), resultsMap.size());

        Map<String, String> queryTextToEvaluationId = new HashMap<>();

        EXPECTED_QUERY_TERMS.forEach(queryTerm -> {
            assertTrue(resultsMap.containsKey(queryTerm));
            String evaluationId = (String) resultsMap.get(queryTerm);
            assertNotNull(evaluationId);
            queryTextToEvaluationId.put(queryTerm, evaluationId);
        });

        assertEquals(8, results.size());
        assertEquals(8, queryTextToEvaluationId.size());
        return queryTextToEvaluationId;
    }

    @SneakyThrows
    private void assertEvaluationResults(Map<String, String> queryTextToEvaluationId, String judgmentId, String searchConfigurationId) {
        // assert every evaluation result
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
            // randomly pick 2 items and check them field by field, do sanity check for otherss
            String actualQueryTerm = evaluationSource.get("searchText").toString();

            // Verify experiment fields are present for pointwise evaluation experiments
            assertNotNull("experimentId should be present", evaluationSource.get("experimentId"));
            assertNotNull("experimentVariantId should be null for pointwise evaluation", evaluationSource.get("experimentVariantId"));
            assertNull(
                "experimentVariantParameters should be null for pointwise evaluation",
                evaluationSource.get("experimentVariantParameters")
            );

            if (EXPECT_EVALUATION_RESULTS.containsKey(actualQueryTerm)) {
                Map<String, Object> expectedResult = (Map<String, Object>) EXPECT_EVALUATION_RESULTS.get(actualQueryTerm);
                List<String> actualDocumentIds = (List<String>) evaluationSource.get("documentIds");
                assertListsHaveSameElements((List<String>) expectedResult.get("documentIds"), actualDocumentIds);
                List<Map> actualMetrics = (List<Map>) evaluationSource.get("metrics");
                Map<String, Double> expectedMetrics = (Map<String, Double>) expectedResult.get("metrics");
                assertEquals("Should have exactly 7 metrics", expectedMetrics.size(), actualMetrics.size());
                for (Map<String, Object> actualMetric : actualMetrics) {
                    String metricName = actualMetric.get("metric").toString();
                    Double actualValue = Double.parseDouble(actualMetric.get("value").toString());
                    assertEquals(expectedMetrics.get(metricName), actualValue, getMetricTolerance(metricName));
                }
            } else {
                assertTrue(EXPECTED_QUERY_TERMS.contains(actualQueryTerm));
                assertEquals(judgmentId, ((List<String>) evaluationSource.get("judgmentIds")).get(0));
                int metricsSize = ((List<String>) evaluationSource.get("metrics")).size();
                assertEquals("Should have exactly 7 metrics", 7, metricsSize);
                assertEquals(searchConfigurationId, evaluationSource.get("searchConfigurationId"));
                assertFalse(((List<String>) evaluationSource.get("documentIds")).isEmpty());
            }
        }
    }
}
