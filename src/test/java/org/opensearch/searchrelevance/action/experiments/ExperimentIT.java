/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */
package org.opensearch.searchrelevance.action.experiments;

import static org.opensearch.searchrelevance.common.PluginConstants.EVALUATION_RESULT_INDEX;
import static org.opensearch.searchrelevance.common.PluginConstants.EXPERIMENTS_URI;
import static org.opensearch.searchrelevance.common.PluginConstants.EXPERIMENT_INDEX;
import static org.opensearch.searchrelevance.common.PluginConstants.JUDGMENTS_URL;
import static org.opensearch.searchrelevance.common.PluginConstants.QUERYSETS_URL;
import static org.opensearch.searchrelevance.common.PluginConstants.SEARCH_CONFIGURATIONS_URL;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.hc.core5.http.HttpHeaders;
import org.apache.hc.core5.http.message.BasicHeader;
import org.opensearch.client.Response;
import org.opensearch.rest.RestRequest;
import org.opensearch.searchrelevance.BaseSearchRelevanceIT;
import org.opensearch.test.OpenSearchIntegTestCase;

import com.carrotsearch.randomizedtesting.annotations.ThreadLeakScope;
import com.google.common.collect.ImmutableList;

import lombok.SneakyThrows;

@ThreadLeakScope(ThreadLeakScope.Scope.NONE)
@OpenSearchIntegTestCase.ClusterScope(scope = OpenSearchIntegTestCase.Scope.SUITE)
public class ExperimentIT extends BaseSearchRelevanceIT {

    public static final List<String> EXPECTED_QUERY_TERMS = List.of(
        "button",
        "keyboard",
        "steel",
        "diamond wheel",
        "phone",
        "metal frame",
        "iphone",
        "giangentic form"
    );
    public static final Map<String, Object> EXPECT_EVALUATION_RESULTS = Map.of(
        "button",
        Map.of(
            "documentIds",
            List.of("B06Y1L1YJD", "B01M3XBRRX", "B07D29PHFY"),
            "metrics",
            Map.of("Coverage@5", 1.0, "Precision@5", 1.0, "MAP@5", 1.0, "NDCG@5", 0.94)
        ),

        "metal frame",
        Map.of(
            "documentIds",
            List.of("B07MBG53JD", "B097Q69V1B", "B00TLYRBMG", "B08G46SS1T", "B07H81Z91C"),
            "metrics",
            Map.of("Coverage@5", 1.0, "Precision@5", 1.0, "MAP@5", 1.0, "NDCG@5", 0.9)
        )
    );
    private static final String INDEX_NAME_ESCI = "ecommerce";

    @SneakyThrows
    public void testPointwiseEvaluationExperiment_whenQueryWithPlaceholder_thenSuccessful() {
        // Arrange
        initializeIndexIfNotExist(INDEX_NAME_ESCI);

        String searchConfigurationId = createSearchConfiguration();
        String querySetId = createQuerySet();
        String judgmentId = createJudgment();

        // Act
        String experimentId = createExperiment(querySetId, searchConfigurationId, judgmentId);

        // Assert
        Map<String, String> queryTextToEvaluationId = assertExperimentCreation(experimentId, judgmentId, searchConfigurationId, querySetId);
        assertEvaluationResults(queryTextToEvaluationId, judgmentId, searchConfigurationId);

        deleteIndex(INDEX_NAME_ESCI);
    }

    private void assertEvaluationResults(Map<String, String> queryTextToEvaluationId, String judgmentId, String searchConfigurationId) throws IOException {
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
            // randomly pick 2 items and check them field by field, do sanity check for others
            String actualQueryTerm = evaluationSource.get("searchText").toString();
            if (EXPECT_EVALUATION_RESULTS.containsKey(actualQueryTerm)) {
                Map<String, Object> expectedResult = (Map<String, Object>) EXPECT_EVALUATION_RESULTS.get(actualQueryTerm);
                List<String> actualDocumentIds = (List<String>) evaluationSource.get("documentIds");
                assertListsHaveSameElements((List<String>) expectedResult.get("documentIds"), actualDocumentIds);
                List<Map> actualMetrics = (List<Map>) evaluationSource.get("metrics");
                Map<String, Double> expectedMetrics = (Map<String, Double>) expectedResult.get("metrics");
                assertEquals(expectedMetrics.size(), actualMetrics.size());
                for (Map<String, Object> actualMetric : actualMetrics) {
                    String metricName = actualMetric.get("metric").toString();
                    Double actualValue = Double.parseDouble(actualMetric.get("value").toString());
                    assertEquals(expectedMetrics.get(metricName), actualValue, 0.02);
                }
            } else {
                assertTrue(EXPECTED_QUERY_TERMS.contains(actualQueryTerm));
                assertEquals(judgmentId, ((List<String>) evaluationSource.get("judgmentIds")).get(0));
                assertEquals(4, ((List<String>) evaluationSource.get("metrics")).size());
                assertEquals(searchConfigurationId, evaluationSource.get("searchConfigurationId"));
                assertFalse(((List<String>) evaluationSource.get("documentIds")).isEmpty());
            }
        }
    }

    private Map<String, String> assertExperimentCreation(String experimentId, String judgmentId, String searchConfigurationId, String querySetId) throws IOException {
        String getExperimentByIdUrl = String.join("/", EXPERIMENT_INDEX, "_doc", experimentId);
        Response getExperimentResponse = makeRequest(
            client(),
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
        assertNotNull(source.get("id"));
        assertNotNull(source.get("timestamp"));
        assertEquals("COMPLETED", source.get("status"));

        List<String> judgmentList = (List<String>) source.get("judgmentList");
        assertNotNull(judgmentList);
        assertEquals(1, judgmentList.size());
        assertEquals(judgmentId, judgmentList.get(0));

        List<String> searchConfigurationList = (List<String>) source.get("searchConfigurationList");
        assertNotNull(searchConfigurationList);
        assertEquals(1, searchConfigurationList.size());
        assertEquals(searchConfigurationId, searchConfigurationList.get(0));

        assertEquals("POINTWISE_EVALUATION", source.get("type"));
        assertEquals(querySetId, source.get("querySetId"));

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
            String evaludationId = (String) resultsMap.get(queryTerm);
            assertNotNull(evaludationId);
            queryTextToEvaluationId.put(queryTerm, evaludationId);
        });

        assertEquals(8, results.size());
        assertEquals(8, queryTextToEvaluationId.size());
        return queryTextToEvaluationId;
    }

    private String createExperiment(String querySetId, String searchConfigurationId, String judgmentId) throws IOException, URISyntaxException, InterruptedException {
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

        Thread.sleep(1000);
        return experimentId;
    }

    private String createJudgment() throws IOException, URISyntaxException, InterruptedException {
        String importJudgmentBody = Files.readString(Path.of(classLoader.getResource("data_esci/ImportJudgment.json").toURI()));
        Response importJudgementResponse = makeRequest(
            client(),
            RestRequest.Method.PUT.name(),
            JUDGMENTS_URL,
            null,
            toHttpEntity(importJudgmentBody),
            ImmutableList.of(new BasicHeader(HttpHeaders.USER_AGENT, DEFAULT_USER_AGENT))
        );
        Map<String, Object> importResultJson = entityAsMap(importJudgementResponse);
        String judgmentId = importResultJson.get("judgment_id").toString();
        assertNotNull(judgmentId);

        // wait for completion of import action
        Thread.sleep(1000);
        return judgmentId;
    }

    private String createQuerySet() throws IOException, URISyntaxException {
        String createQuerySetBody = Files.readString(Path.of(classLoader.getResource("queryset/CreateQuerySet.json").toURI()));
        Response createQuerySetResponse = makeRequest(
            client(),
            RestRequest.Method.PUT.name(),
            QUERYSETS_URL,
            null,
            toHttpEntity(createQuerySetBody),
            ImmutableList.of(new BasicHeader(HttpHeaders.USER_AGENT, DEFAULT_USER_AGENT))
        );
        Map<String, Object> createQuerySetResultJson = entityAsMap(createQuerySetResponse);
        String querySetId = createQuerySetResultJson.get("query_set_id").toString();
        assertNotNull(querySetId);
        return querySetId;
    }

    @SneakyThrows
    private String createSearchConfiguration() {
        String createSearchConfigurationRequestBody = Files.readString(
                Path.of(classLoader.getResource("searchconfig/CreateSearchConfigurationQueryWithPlaceholder.json").toURI()));
        Response createSearchConfigurationResponse = makeRequest(
            client(),
            RestRequest.Method.PUT.name(),
                SEARCH_CONFIGURATIONS_URL,
            null,
            toHttpEntity(createSearchConfigurationRequestBody),
            ImmutableList.of(new BasicHeader(HttpHeaders.USER_AGENT, DEFAULT_USER_AGENT))
        );
        Map<String, Object> createSearchConfigurationResultJson = entityAsMap(createSearchConfigurationResponse);
        String searchConfigurationId = createSearchConfigurationResultJson.get("search_configuration_id").toString();
        assertNotNull(searchConfigurationId);
        return searchConfigurationId;
    }

    @SneakyThrows
    private void initializeIndexIfNotExist(String indexName) {
        if (INDEX_NAME_ESCI.equals(indexName) && !indexExists(indexName)) {
            String indexConfiguration = Files.readString(Path.of(classLoader.getResource("data_esci/CreateIndex.json").toURI()));
            createIndexWithConfiguration(indexName, indexConfiguration);
            String importDatasetBody = Files.readString(Path.of(classLoader.getResource("data_esci/BulkIngestDocuments.json").toURI()));
            bulkIngest(indexName, importDatasetBody);
        }
    }

    private void assertListsHaveSameElements(List<String> expected, List<String> actual) {
        List<String> sortedExpected = new ArrayList<>(expected);
        List<String> sortedActual = new ArrayList<>(actual);
        Collections.sort(sortedExpected);
        Collections.sort(sortedActual);
        assertArrayEquals(sortedExpected.toArray(new String[0]), sortedActual.toArray(new String[0]));
    }
}
