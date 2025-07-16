/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */
package org.opensearch.searchrelevance.action.judgment;

import static org.opensearch.searchrelevance.common.PluginConstants.JUDGMENTS_URL;
import static org.opensearch.searchrelevance.common.PluginConstants.JUDGMENT_INDEX;
import static org.opensearch.searchrelevance.common.PluginConstants.QUERYSETS_URL;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.apache.hc.core5.http.HttpHeaders;
import org.apache.hc.core5.http.message.BasicHeader;
import org.junit.Before;
import org.opensearch.client.Response;
import org.opensearch.rest.RestRequest;
import org.opensearch.searchrelevance.experiment.BaseExperimentIT;
import org.opensearch.test.OpenSearchIntegTestCase;

import com.carrotsearch.randomizedtesting.annotations.ThreadLeakScope;
import com.google.common.collect.ImmutableList;

import lombok.SneakyThrows;

/**
 * Integration tests for LLM-based judgment creation functionality.
 */
@ThreadLeakScope(ThreadLeakScope.Scope.NONE)
@OpenSearchIntegTestCase.ClusterScope(scope = OpenSearchIntegTestCase.Scope.SUITE)
public class LLMJudgmentGenerationIT extends BaseExperimentIT {

    private static final String ML_COMMONS_CONNECTORS_URL = "/_plugins/_ml/connectors/_create";
    private static final String ML_COMMONS_MODELS_REGISTER_URL = "/_plugins/_ml/models/_register";
    private static final String ML_COMMONS_MODELS_DEPLOY_URL = "/_plugins/_ml/models/%s/_deploy";
    private static final String ML_COMMONS_TASKS_URL = "/_plugins/_ml/tasks/%s";
    private static final String ML_COMMONS_MODELS_URL = "/_plugins/_ml/models/%s";
    private static final String CLUSTER_SETTINGS_URL = "/_cluster/settings";

    private static final int MAX_WAIT_TIME_MS = 60000;
    private static final int POLL_INTERVAL_MS = 1000;

    private String connectorId;
    private String modelId;

    @Before
    @SneakyThrows
    public void setupMLCommonsAndLLM() {
        if (!isLLMTestingEnabled()) {
            return;
        }

        configureMLCommonsSettings();

        connectorId = createRemoteConnector();
        assertNotNull("Connector ID should not be null", connectorId);

        String registerTaskId = registerModel(connectorId);
        assertNotNull("Register task ID should not be null", registerTaskId);

        modelId = waitForTaskCompletionAndGetModelId(registerTaskId);
        assertNotNull("Model ID should not be null", modelId);

        String deployTaskId = deployModel(modelId);
        assertNotNull("Deploy task ID should not be null", deployTaskId);

        waitForTaskCompletion(deployTaskId);
        waitForModelDeployment(modelId);
    }

    @SneakyThrows
    public void testLLMJudgmentCreation() {
        if (!isLLMTestingEnabled()) {
            return;
        }

        createSampleDocumentsIndex();

        String judgmentsId = createLLMJudgments();
        assertNotNull("Judgments ID should not be null", judgmentsId);

        Map<String, Object> judgmentSource = pollJudgmentUntilCompleted(judgmentsId);

        verifyLLMJudgments(judgmentSource);

        deleteLLMJudgments(judgmentsId);
    }

    private void createSampleDocumentsIndex() throws IOException {
        try {
            String indexName = "test-products";

            String createIndexBody = Files.readString(Path.of(classLoader.getResource("data/TestProductsIndexMapping.json").toURI()));

            makeRequest(
                adminClient(),
                RestRequest.Method.PUT.name(),
                indexName,
                null,
                toHttpEntity(createIndexBody),
                ImmutableList.of(new BasicHeader(HttpHeaders.USER_AGENT, DEFAULT_USER_AGENT))
            );

            String importDatasetBody = Files.readString(Path.of(classLoader.getResource("data/TestProductsIngestDocuments.json").toURI()));
            importDatasetBody = importDatasetBody.replace("{{index_name}}", indexName);
            bulkIngest(indexName, importDatasetBody);

        } catch (URISyntaxException e) {
            throw new IOException("Failed to load test data resource files", e);
        }
    }

    private String createLLMJudgments() throws IOException {
        String querySetId = createQuerySet();
        String searchConfigId = createSearchConfiguration("test-products");

        String llmJudgmentBody = "{\n"
            + "  \"name\": \"LLM Generated Judgments\",\n"
            + "  \"type\": \"LLM_JUDGMENT\",\n"
            + "  \"querySetId\": \""
            + querySetId
            + "\",\n"
            + "  \"searchConfigurationList\": [\""
            + searchConfigId
            + "\"],\n"
            + "  \"size\": 5,\n"
            + "  \"modelId\": \""
            + modelId
            + "\",\n"
            + "  \"contextFields\": []\n"
            + "}";

        Response response = makeRequest(
            client(),
            RestRequest.Method.PUT.name(),
            JUDGMENTS_URL,
            null,
            toHttpEntity(llmJudgmentBody),
            ImmutableList.of(new BasicHeader(HttpHeaders.USER_AGENT, DEFAULT_USER_AGENT))
        );

        Map<String, Object> responseMap = entityAsMap(response);
        assertNotNull("LLM judgment response should not be null", responseMap);

        String judgmentId = (String) responseMap.get("judgment_id");
        return judgmentId;
    }

    protected String createQuerySet() throws IOException {
        try {
            String querySetBody = Files.readString(Path.of(classLoader.getResource("queryset/CreateQuerySet.json").toURI()));

            Response response = makeRequest(
                client(),
                RestRequest.Method.PUT.name(),
                QUERYSETS_URL,
                null,
                toHttpEntity(querySetBody),
                ImmutableList.of(new BasicHeader(HttpHeaders.USER_AGENT, DEFAULT_USER_AGENT))
            );

            Map<String, Object> responseMap = entityAsMap(response);
            assertNotNull("QuerySet creation response should not be null", responseMap);

            String querySetId = (String) responseMap.get("query_set_id");
            return querySetId;
        } catch (URISyntaxException e) {
            throw new IOException("Failed to load QuerySet resource file", e);
        }
    }

    private Map<String, Object> pollJudgmentUntilCompleted(String judgmentId) throws IOException {
        Map<String, Object> source = null;
        String getJudgmentByIdUrl = String.join("/", JUDGMENT_INDEX, "_doc", judgmentId);

        int retryCount = 0;
        String status = "PROCESSING";

        while (("PROCESSING".equals(status) || status == null) && retryCount < MAX_POLL_RETRIES) {
            Response getJudgmentResponse = makeRequest(
                adminClient(),
                RestRequest.Method.GET.name(),
                getJudgmentByIdUrl,
                null,
                null,
                ImmutableList.of(new BasicHeader(HttpHeaders.USER_AGENT, DEFAULT_USER_AGENT))
            );
            Map<String, Object> getJudgmentResultJson = entityAsMap(getJudgmentResponse);
            assertNotNull(getJudgmentResultJson);
            assertEquals(judgmentId, getJudgmentResultJson.get("_id").toString());

            source = (Map<String, Object>) getJudgmentResultJson.get("_source");
            assertNotNull(source);
            status = (String) source.get("status");

            if ("PROCESSING".equals(status) || status == null) {
                retryCount++;
                try {
                    Thread.sleep(DEFAULT_INTERVAL_MS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            } else {
                break;
            }
        }

        if (retryCount >= MAX_POLL_RETRIES && ("PROCESSING".equals(status) || status == null)) {
            fail("Judgment did not complete within timeout period. Final status: " + status);
        }

        return source;
    }

    private void verifyLLMJudgments(Map<String, Object> source) throws IOException {
        // Basic structure validation
        assertNotNull("Judgment source should not be null", source);
        assertNotNull("Judgment should have ID", source.get("id"));
        assertNotNull("Judgment should have timestamp", source.get("timestamp"));
        assertEquals("LLM Generated Judgments", source.get("name"));

        // Status validation
        String status = (String) source.get("status");
        if ("ERROR".equals(status)) {
            fail("LLM judgment failed with ERROR status");
        }
        assertEquals("COMPLETED", status);

        // Basic ratings validation
        List<Map<String, Object>> judgmentRatings = (List<Map<String, Object>>) source.get("judgmentRatings");
        assertNotNull("Judgment ratings should not be null", judgmentRatings);
        assertFalse("Judgment ratings list should not be empty", judgmentRatings.isEmpty());

        int totalRatingsFound = 0;
        int nonZeroRatingsFound = 0;
        double totalRatingSum = 0.0;

        for (Map<String, Object> judgment : judgmentRatings) {
            String query = (String) judgment.get("query");
            assertNotNull("Query should not be null", query);

            List<Map<String, Object>> ratings = (List<Map<String, Object>>) judgment.get("ratings");
            assertNotNull("Ratings should not be null", ratings);

            for (Map<String, Object> rating : ratings) {
                assertNotNull("Doc ID should not be null", rating.get("docId"));
                assertNotNull("Rating should not be null", rating.get("rating"));

                String ratingStr = rating.get("rating").toString();
                double ratingValue = Double.parseDouble(ratingStr);

                // Verify rating bounds
                assertTrue("Rating should be non-negative", ratingValue >= 0.0);
                assertTrue("Rating should be <= 1.0", ratingValue <= 1.0);

                totalRatingsFound++;
                totalRatingSum += ratingValue;

                if (ratingValue > 0.0) {
                    nonZeroRatingsFound++;
                }
            }
        }

        assertTrue("Should have generated ratings", totalRatingsFound > 0);
        assertTrue(nonZeroRatingsFound > 0);

        double avgRating = totalRatingSum / totalRatingsFound;
        assertTrue("Average rating should be reasonable", avgRating > 0.4);
        assertTrue("Average rating should not be too high", avgRating < 0.9);

        double nonZeroPercentage = (nonZeroRatingsFound * 100.0) / totalRatingsFound;
        assertTrue("LLM should generate high percentage of non-zero ratings", nonZeroPercentage > 80.0);
    }

    private void deleteLLMJudgments(String judgmentsId) throws IOException {
        String deleteJudgmentUrl = String.join("/", JUDGMENT_INDEX, "_doc", judgmentsId);
        Response deleteResponse = makeRequest(
            client(),
            RestRequest.Method.DELETE.name(),
            deleteJudgmentUrl,
            null,
            null,
            ImmutableList.of(new BasicHeader(HttpHeaders.USER_AGENT, DEFAULT_USER_AGENT))
        );

        Map<String, Object> deleteResultJson = entityAsMap(deleteResponse);
        assertNotNull("Delete response should not be null", deleteResultJson);
        assertEquals("Judgment should be deleted", "deleted", deleteResultJson.get("result").toString());
    }

    private void configureMLCommonsSettings() throws IOException {
        String settingsBody = "{\n"
            + "  \"persistent\": {\n"
            + "    \"plugins.ml_commons.only_run_on_ml_node\": false,\n"
            + "    \"plugins.ml_commons.model_access_control_enabled\": false,\n"
            + "    \"plugins.ml_commons.connector_access_control_enabled\": false,\n"
            + "    \"plugins.ml_commons.connector.private_ip_enabled\": true,\n"
            + "    \"plugins.ml_commons.native_memory_threshold\": 99,\n"
            + "    \"plugins.ml_commons.allow_registering_model_via_url\": true,\n"
            + "    \"plugins.ml_commons.allow_registering_model_via_local_file\": true,\n"
            + "    \"plugins.ml_commons.trusted_connector_endpoints_regex\": [\n"
            + "      \"^http://localhost:.*\",\n"
            + "      \"^https://localhost:.*\",\n"
            + "      \"^http://127\\\\.0\\\\.0\\\\.1:.*\",\n"
            + "      \"^https://127\\\\.0\\\\.0\\\\.1:.*\"\n"
            + "    ],\n"
            + "    \"plugins.ml_commons.trusted_url_regex\": [\n"
            + "      \"^http://localhost:.*\",\n"
            + "      \"^https://localhost:.*\",\n"
            + "      \"^http://127\\\\.0\\\\.0\\\\.1:.*\",\n"
            + "      \"^https://127\\\\.0\\\\.0\\\\.1:.*\"\n"
            + "    ]\n"
            + "  }\n"
            + "}";

        Response response = makeRequest(
            adminClient(),
            RestRequest.Method.PUT.name(),
            CLUSTER_SETTINGS_URL,
            null,
            toHttpEntity(settingsBody),
            ImmutableList.of(new BasicHeader(HttpHeaders.USER_AGENT, DEFAULT_USER_AGENT))
        );

        assertEquals("Cluster settings update should be successful", 200, response.getStatusLine().getStatusCode());
    }

    private String createRemoteConnector() throws IOException {
        String llmApiUrl = getLLMApiUrl();
        String modelName = getLLMModelName();

        String connectorBody = "{\n"
            + "  \"name\": \"LLM Judgments Connector\",\n"
            + "  \"description\": \"Connector for LLM-based judgment generation\",\n"
            + "  \"version\": \"1.0.0\",\n"
            + "  \"protocol\": \"http\",\n"
            + "  \"parameters\": {\n"
            + "    \"endpoint\": \""
            + llmApiUrl
            + "\",\n"
            + "    \"model\": \""
            + modelName
            + "\"\n"
            + "  },\n"
            + "  \"credential\": {\n"
            + "    \"access_key\": \"\",\n"
            + "    \"secret_key\": \"\"\n"
            + "  },\n"
            + "  \"actions\": [\n"
            + "    {\n"
            + "      \"action_type\": \"predict\",\n"
            + "      \"method\": \"POST\",\n"
            + "      \"url\": \""
            + llmApiUrl
            + "/v1/chat/completions\",\n"
            + "      \"headers\": {\n"
            + "        \"Content-Type\": \"application/json\"\n"
            + "      },\n"
            + "      \"request_body\": \"{ \\\"model\\\": \\\""
            + modelName
            + "\\\", "
            + "\\\"messages\\\": ${parameters.messages}, "
            + "\\\"temperature\\\": 0.1, "
            + "\\\"max_tokens\\\": 100, "
            + "\\\"stream\\\": false }\"\n"
            + "    }\n"
            + "  ]\n"
            + "}";

        Response response = makeRequest(
            client(),
            RestRequest.Method.POST.name(),
            ML_COMMONS_CONNECTORS_URL,
            null,
            toHttpEntity(connectorBody),
            ImmutableList.of(new BasicHeader(HttpHeaders.USER_AGENT, DEFAULT_USER_AGENT))
        );

        Map<String, Object> responseMap = entityAsMap(response);
        String connectorId = (String) responseMap.get("connector_id");
        return connectorId;
    }

    private String registerModel(String connectorId) throws IOException {
        String registerBody = "{\n"
            + "  \"name\": \"LLM Judgments Model\",\n"
            + "  \"function_name\": \"remote\",\n"
            + "  \"connector_id\": \""
            + connectorId
            + "\"\n"
            + "}";

        Response response = makeRequest(
            client(),
            RestRequest.Method.POST.name(),
            ML_COMMONS_MODELS_REGISTER_URL,
            null,
            toHttpEntity(registerBody),
            ImmutableList.of(new BasicHeader(HttpHeaders.USER_AGENT, DEFAULT_USER_AGENT))
        );

        Map<String, Object> responseMap = entityAsMap(response);
        String taskId = (String) responseMap.get("task_id");
        return taskId;
    }

    private String deployModel(String modelId) throws IOException {
        Response response = makeRequest(
            client(),
            RestRequest.Method.POST.name(),
            String.format(Locale.ROOT, ML_COMMONS_MODELS_DEPLOY_URL, modelId),
            null,
            null,
            ImmutableList.of(new BasicHeader(HttpHeaders.USER_AGENT, DEFAULT_USER_AGENT))
        );

        Map<String, Object> responseMap = entityAsMap(response);
        String taskId = (String) responseMap.get("task_id");
        return taskId;
    }

    private String waitForTaskCompletionAndGetModelId(String taskId) throws IOException, InterruptedException {
        long startTime = System.currentTimeMillis();
        String modelId = null;

        while (System.currentTimeMillis() - startTime < MAX_WAIT_TIME_MS) {
            Response response = makeRequest(
                client(),
                RestRequest.Method.GET.name(),
                String.format(Locale.ROOT, ML_COMMONS_TASKS_URL, taskId),
                null,
                null,
                ImmutableList.of(new BasicHeader(HttpHeaders.USER_AGENT, DEFAULT_USER_AGENT))
            );

            Map<String, Object> responseMap = entityAsMap(response);
            String state = (String) responseMap.get("state");

            if ("COMPLETED".equals(state)) {
                modelId = (String) responseMap.get("model_id");
                break;
            } else if ("FAILED".equals(state)) {
                String error = (String) responseMap.get("error");
                fail("Task failed with error: " + error);
            }

            Thread.sleep(POLL_INTERVAL_MS);
        }

        if (modelId == null) {
            fail("Task did not complete within timeout period");
        }

        return modelId;
    }

    private void waitForTaskCompletion(String taskId) throws IOException, InterruptedException {
        long startTime = System.currentTimeMillis();

        while (System.currentTimeMillis() - startTime < MAX_WAIT_TIME_MS) {
            Response response = makeRequest(
                client(),
                RestRequest.Method.GET.name(),
                String.format(Locale.ROOT, ML_COMMONS_TASKS_URL, taskId),
                null,
                null,
                ImmutableList.of(new BasicHeader(HttpHeaders.USER_AGENT, DEFAULT_USER_AGENT))
            );

            Map<String, Object> responseMap = entityAsMap(response);
            String state = (String) responseMap.get("state");

            if ("COMPLETED".equals(state)) {
                return;
            } else if ("FAILED".equals(state)) {
                String error = (String) responseMap.get("error");
                fail("Task failed with error: " + error);
            }

            Thread.sleep(POLL_INTERVAL_MS);
        }

        fail("Task did not complete within timeout period");
    }

    private void waitForModelDeployment(String modelId) throws IOException, InterruptedException {
        long startTime = System.currentTimeMillis();

        while (System.currentTimeMillis() - startTime < MAX_WAIT_TIME_MS) {
            Response response = makeRequest(
                client(),
                RestRequest.Method.GET.name(),
                String.format(Locale.ROOT, ML_COMMONS_MODELS_URL, modelId),
                null,
                null,
                ImmutableList.of(new BasicHeader(HttpHeaders.USER_AGENT, DEFAULT_USER_AGENT))
            );

            Map<String, Object> responseMap = entityAsMap(response);
            String state = (String) responseMap.get("model_state");

            if ("DEPLOYED".equals(state)) {
                return;
            } else if ("DEPLOY_FAILED".equals(state)) {
                fail("Model deployment failed");
            }

            Thread.sleep(POLL_INTERVAL_MS);
        }

        fail("Model did not deploy within timeout period");
    }

    private boolean isLLMTestingEnabled() {
        String llmEnabled = System.getProperty("tests.cluster.llm.enabled", "false");
        return "true".equalsIgnoreCase(llmEnabled);
    }

    private String getLLMApiUrl() {
        // Check new Ollama environment variables first (from setup script)
        String apiUrl = System.getenv("OLLAMA_API_BASE");
        if (apiUrl != null && !apiUrl.isEmpty()) {
            // Remove /v1 suffix if present since we add it back in the connector
            return apiUrl.replace("/v1", "");
        }

        // Fallback to old environment variable for backward compatibility
        apiUrl = System.getenv("LOCALAI_API_URL");
        if (apiUrl == null || apiUrl.isEmpty()) {
            apiUrl = System.getProperty("tests.cluster.llm.api.url", "http://localhost:11434");
        }
        return apiUrl;
    }

    private String getLLMModelName() {
        String modelName = System.getenv("OLLAMA_MODEL_NAME");
        if (modelName != null && !modelName.isEmpty()) {
            return modelName;
        }

        // Fallback to old environment variable for backward compatibility
        modelName = System.getenv("LLM_MODEL_NAME");
        if (modelName == null || modelName.isEmpty()) {
            // Updated default: Use tinyllama (fast model for testing)
            modelName = System.getProperty("tests.cluster.llm.model.name", "tinyllama");
        }
        return modelName;
    }
}
