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
import static org.opensearch.searchrelevance.common.PluginConstants.EXPERIMENT_VARIANT_INDEX;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
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
 * Integration tests for hybrid optimizer experiments.
 */
@ThreadLeakScope(ThreadLeakScope.Scope.NONE)
@OpenSearchIntegTestCase.ClusterScope(scope = OpenSearchIntegTestCase.Scope.SUITE)
public class HybridOptimizerExperimentIT extends BaseExperimentIT {

    // Expected number of variants per query for HYBRID_OPTIMIZER experiments
    private static final int EXPECTED_VARIANTS_PER_QUERY = 66;
    private static final String INDEX_NAME_ESCI = generateUniqueIndexName("hybridoptimizer");

    @SneakyThrows
    public void testHybridOptimizerExperiment_whenHybridQueries_thenSuccessful() {
        // Arrange
        initializeIndexIfNotExist(INDEX_NAME_ESCI);

        String hybridSearchConfigId = createHybridSearchConfiguration(INDEX_NAME_ESCI);
        String querySetId = createQuerySet();
        String judgmentId = createJudgment();

        try {
            // Act
            String experimentId = createHybridOptimizerExperiment(querySetId, hybridSearchConfigId, judgmentId);

            // Wait for the experiment to be created and indexed
            Thread.sleep(DEFAULT_INTERVAL_MS);

            Map<String, Object> experimentSource = pollExperimentUntilCompleted(experimentId);

            // Assert experiment exists with correct type
            // We don't wait for completion since it may time out in constrained environments
            assertNotNull("Experiment should exist", experimentSource);
            assertEquals("HYBRID_OPTIMIZER", experimentSource.get("type"));
            assertEquals(querySetId, experimentSource.get("querySetId"));

            assertExperimentResults(experimentId);
            int countOfExperimentVariantBeforeDeletion = findExperimentResultCount(EXPERIMENT_VARIANT_INDEX, experimentId);
            assertTrue(countOfExperimentVariantBeforeDeletion > 0);

            int countOfExperimentResultsBeforeDeletion = findExperimentResultCount(EVALUATION_RESULT_INDEX, experimentId);
            assertTrue(countOfExperimentResultsBeforeDeletion > 0);

            deleteHybridOptimizerExperiment(experimentId);

            // Wait for all results and variants to get deleted.
            Thread.sleep(DEFAULT_INTERVAL_MS);

            int countOfExperimentVariantAfterDeletion = findExperimentResultCount(EXPERIMENT_VARIANT_INDEX, experimentId);
            assertEquals(0, countOfExperimentVariantAfterDeletion);

            int countOfExperimentResultsAfterDeletion = findExperimentResultCount(EVALUATION_RESULT_INDEX, experimentId);
            assertEquals(0, countOfExperimentResultsAfterDeletion);
        } finally {
            deleteIndex(INDEX_NAME_ESCI);
        }
    }

    private String createHybridOptimizerExperiment(String querySetId, String searchConfigurationId, String judgmentId) throws IOException,
        URISyntaxException, InterruptedException {
        String createExperimentBody = replacePlaceholders(
            Files.readString(Path.of(classLoader.getResource("experiment/CreateExperimentHybridOptimizer.json").toURI())),
            Map.of("query_set_id", querySetId, "search_config_id", searchConfigurationId, "judgment_id", judgmentId)
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

        // Refresh all related indices to ensure documents are available for search
        Thread.sleep(DEFAULT_INTERVAL_MS);

        return experimentId;
    }

    private String deleteHybridOptimizerExperiment(String experimentId) throws IOException, URISyntaxException, InterruptedException {

        String deleteExperimentURL = String.format(Locale.ROOT, "%s/%s", EXPERIMENTS_URI, experimentId);
        Response deleteExperimentResponse = makeRequest(
            client(),
            RestRequest.Method.DELETE.name(),
            deleteExperimentURL,
            null,
            null,
            ImmutableList.of(new BasicHeader(HttpHeaders.USER_AGENT, DEFAULT_USER_AGENT))
        );
        Map<String, Object> deleteExperimentResultJson = entityAsMap(deleteExperimentResponse);
        String status = deleteExperimentResultJson.get("result").toString();
        // Refresh all related indices to ensure documents are available for search
        assertEquals("deleted", status);
        return experimentId;
    }

    private void assertExperimentResults(String experimentId) throws IOException {
        // refresh indexes to make sure data is propagated
        makeRequest(
            client(),
            RestRequest.Method.POST.name(),
            EXPERIMENT_VARIANT_INDEX + "/_refresh",
            null,
            null,
            ImmutableList.of(new BasicHeader(HttpHeaders.USER_AGENT, DEFAULT_USER_AGENT))
        );

        makeRequest(
            client(),
            RestRequest.Method.POST.name(),
            EVALUATION_RESULT_INDEX + "/_refresh",
            null,
            null,
            ImmutableList.of(new BasicHeader(HttpHeaders.USER_AGENT, DEFAULT_USER_AGENT))
        );

        // If experiment completed, try to verify variants
        for (String sampleQueryTerm : EXPECTED_QUERY_TERMS) {
            assertHybridOptimizerExperimentVariantsForQuery(experimentId, sampleQueryTerm);
        }
    }

    private void assertHybridOptimizerExperimentVariantsForQuery(String experimentId, String queryText) throws IOException {
        // First get all evaluation results for this query text
        List<String> evaluationIds = getEvaluationResultIdsForQuery(queryText);

        // We should have found some evaluation results
        assertFalse("Should have evaluation results for query: " + queryText, evaluationIds.isEmpty());

        // Poll for experiment variants until we have the expected number or reach max retries
        List<Map<String, Object>> variants = pollForExperimentVariants(experimentId, evaluationIds, queryText);

        // We should have found some experiment variants
        assertFalse("Should have experiment variants for query: " + queryText, variants.isEmpty());

        // We should have multiple variants for the query
        assertEquals(
            "Expected " + EXPECTED_VARIANTS_PER_QUERY + " variants for query: " + queryText,
            EXPECTED_VARIANTS_PER_QUERY,
            variants.size()
        );

        // Verify structure of a few variants
        int variantsToCheck = Math.min(3, variants.size());

        for (int i = 0; i < variantsToCheck; i++) {
            Map<String, Object> source = variants.get(i);
            assertNotNull(source);

            assertEquals(experimentId, source.get("experimentId"));

            // Parameters are nested in a "parameters" object
            Map<String, Object> parameters = (Map<String, Object>) source.get("parameters");
            assertNotNull("Parameters object should exist", parameters);
            assertNotNull("Normalization should exist", parameters.get("normalization"));
            assertNotNull("Combination should exist", parameters.get("combination"));
            assertNotNull("Weights should exist", parameters.get("weights"));

            // Check results
            Map<String, Object> results = (Map<String, Object>) source.get("results");
            assertNotNull("Results should exist", results);
            String evaluationResultId = (String) results.get("evaluationResultId");
            assertNotNull("Evaluation result ID should exist", evaluationResultId);

            // Verify the evaluation result exists and has the correct query text
            verifyEvaluationResult(evaluationResultId, queryText);
        }
    }

    /**
     * Poll for experiment variants until we have the expected number or reach max retries
     */
    private List<Map<String, Object>> pollForExperimentVariants(String experimentId, List<String> evaluationIds, String queryText)
        throws IOException {
        List<Map<String, Object>> variants = new ArrayList<>();
        int retryCount = 0;

        while (variants.size() < EXPECTED_VARIANTS_PER_QUERY && retryCount < MAX_POLL_RETRIES) {
            // Get the current experiment variants
            variants = getExperimentVariantsForEvaluationIds(experimentId, evaluationIds);

            if (variants.size() >= EXPECTED_VARIANTS_PER_QUERY) {
                // We have enough variants, break out of the loop
                break;
            }

            retryCount++;
            try {
                Thread.sleep(DEFAULT_INTERVAL_MS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }

        // If we reached max polling iterations and still don't have enough variants, fail the test
        if (variants.size() < EXPECTED_VARIANTS_PER_QUERY && retryCount >= MAX_POLL_RETRIES) {
            fail(
                "Expected "
                    + EXPECTED_VARIANTS_PER_QUERY
                    + " variants for query '"
                    + queryText
                    + "' but only found "
                    + variants.size()
                    + " after "
                    + MAX_POLL_RETRIES
                    + " polling attempts"
            );
        }

        return variants;
    }

    private List<String> getEvaluationResultIdsForQuery(String queryText) throws IOException {
        String getEvaluationResultsUrl = String.join("/", EVALUATION_RESULT_INDEX, "_search");
        String evaluationResultsQuery = "{ \"size\": 100, \"query\": { \"term\": { \"searchText\": \"" + queryText + "\" } } }";

        Response getEvaluationResponse = makeRequest(
            client(),
            RestRequest.Method.POST.name(),
            getEvaluationResultsUrl,
            null,
            toHttpEntity(evaluationResultsQuery),
            ImmutableList.of(new BasicHeader(HttpHeaders.USER_AGENT, DEFAULT_USER_AGENT))
        );

        Map<String, Object> getEvaluationResultJson = entityAsMap(getEvaluationResponse);
        Map<String, Object> hitsObj = (Map<String, Object>) getEvaluationResultJson.get("hits");
        List<Map<String, Object>> evaluationHits = (List<Map<String, Object>>) hitsObj.get("hits");

        // Extract the evaluation result IDs
        List<String> evaluationIds = new ArrayList<>();
        for (Map<String, Object> hit : evaluationHits) {
            String evalId = (String) hit.get("_id");
            evaluationIds.add(evalId);
        }

        return evaluationIds;
    }

    private List<Map<String, Object>> getExperimentVariantsForEvaluationIds(String experimentId, List<String> evaluationIds)
        throws IOException {
        if (evaluationIds.isEmpty()) {
            return Collections.emptyList();
        }

        String getVariantsUrl = String.join("/", EXPERIMENT_VARIANT_INDEX, "_search");

        // Build the nested query for evaluationResultId
        StringBuilder termsBuilder = new StringBuilder();
        for (int i = 0; i < evaluationIds.size(); i++) {
            termsBuilder.append("\"").append(evaluationIds.get(i)).append("\"");
            if (i < evaluationIds.size() - 1) {
                termsBuilder.append(", ");
            }
        }

        // Use nested query for results.evaluationResultId
        String nestedQuery = "{ \"size\": 100, \"query\": { \"bool\": { \"must\": [ "
            + "  { \"term\": { \"experimentId\": \""
            + experimentId
            + "\" } }, "
            + "  { \"nested\": { "
            + "      \"path\": \"results\", "
            + "      \"query\": { "
            + "        \"terms\": { "
            + "          \"results.evaluationResultId\": ["
            + termsBuilder
            + "]"
            + "        } "
            + "      } "
            + "    } "
            + "  } "
            + "] } } }";

        Response getVariantsResponse = makeRequest(
            client(),
            RestRequest.Method.POST.name(),
            getVariantsUrl,
            null,
            toHttpEntity(nestedQuery),
            ImmutableList.of(new BasicHeader(HttpHeaders.USER_AGENT, DEFAULT_USER_AGENT))
        );

        Map<String, Object> getVariantsResultJson = entityAsMap(getVariantsResponse);
        Map<String, Object> variantHitsObj = (Map<String, Object>) getVariantsResultJson.get("hits");
        List<Map<String, Object>> variantHits = (List<Map<String, Object>>) variantHitsObj.get("hits");

        // Extract the sources
        List<Map<String, Object>> sources = new ArrayList<>();
        for (Map<String, Object> hit : variantHits) {
            sources.add((Map<String, Object>) hit.get("_source"));
        }

        return sources;
    }

    private void verifyEvaluationResult(String evaluationResultId, String queryText) throws IOException {
        String getEvaluationByIdUrl = String.join("/", EVALUATION_RESULT_INDEX, "_doc", evaluationResultId);
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
        assertEquals("Evaluation search text should match query", queryText, evaluationSource.get("searchText"));

        // Verify experiment fields are present for hybrid optimizer experiments
        assertNotNull("experimentId should be present", evaluationSource.get("experimentId"));
        assertNotNull("experimentVariantId should be present for hybrid experiments", evaluationSource.get("experimentVariantId"));
        assertNotNull(
            "experimentVariantParameters should be present for hybrid experiments",
            evaluationSource.get("experimentVariantParameters")
        );

        // Verify we have metrics
        List<Map> metrics = (List<Map>) evaluationSource.get("metrics");
        assertNotNull("Metrics should exist", metrics);
        assertFalse("Metrics should not be empty", metrics.isEmpty());

        // Verify we have document IDs
        List<String> documentIds = (List<String>) evaluationSource.get("documentIds");
        assertNotNull("Document IDs should exist", documentIds);
        assertFalse("Document IDs should not be empty", documentIds.isEmpty());
    }
}
