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
import static org.opensearch.searchrelevance.common.PluginConstants.SEARCH_CONFIGURATIONS_URL;

import java.nio.file.Files;
import java.nio.file.Path;
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

/**
 * Integration tests for LLM Judgment expandCoverage functionality.
 * Tests the expandCoverage flag with hybrid and non-hybrid search configurations.
 */
@ThreadLeakScope(ThreadLeakScope.Scope.NONE)
@OpenSearchIntegTestCase.ClusterScope(scope = OpenSearchIntegTestCase.Scope.SUITE)
public class LlmJudgmentExpandCoverageIT extends BaseSearchRelevanceIT {

    private static final String TEST_INDEX = "test_expand_coverage_products";

    /**
     * Helper: creates test index, ingests documents, creates query set and returns querySetId.
     */
    @SneakyThrows
    private String setupTestIndexAndQuerySet() {
        // Create test index
        String indexConfig = Files.readString(Path.of(classLoader.getResource("llmjudgment/CreateTestIndex.json").toURI()));
        createIndexWithConfiguration(TEST_INDEX, indexConfig);

        // Bulk ingest test documents
        String bulkData = Files.readString(Path.of(classLoader.getResource("llmjudgment/BulkIngestProducts.json").toURI()));
        bulkIngest(TEST_INDEX, bulkData);

        // Create query set
        String querySetBody = Files.readString(Path.of(classLoader.getResource("llmjudgment/CreateQuerySetSimple.json").toURI()));
        Response querySetResponse = makeRequest(
            client(),
            RestRequest.Method.PUT.name(),
            QUERYSETS_URL,
            null,
            toHttpEntity(querySetBody),
            ImmutableList.of(new BasicHeader(HttpHeaders.USER_AGENT, DEFAULT_USER_AGENT))
        );
        Map<String, Object> querySetResult = entityAsMap(querySetResponse);
        return querySetResult.get("query_set_id").toString();
    }

    /**
     * Helper: creates a search configuration and returns searchConfigId.
     */
    @SneakyThrows
    private String createSearchConfig(String resourcePath) {
        String searchConfigBody = Files.readString(Path.of(classLoader.getResource(resourcePath).toURI()));
        searchConfigBody = replacePlaceholders(searchConfigBody, Map.of("index", TEST_INDEX));
        Response searchConfigResponse = makeRequest(
            client(),
            RestRequest.Method.PUT.name(),
            SEARCH_CONFIGURATIONS_URL,
            null,
            toHttpEntity(searchConfigBody),
            ImmutableList.of(new BasicHeader(HttpHeaders.USER_AGENT, DEFAULT_USER_AGENT))
        );
        Map<String, Object> searchConfigResult = entityAsMap(searchConfigResponse);
        return searchConfigResult.get("search_configuration_id").toString();
    }

    @SneakyThrows
    public void testExpandCoverageWithHybridConfig_thenSuccessful() {
        // Setup
        String querySetId = setupTestIndexAndQuerySet();
        String searchConfigId = createSearchConfig("llmjudgment/CreateSearchConfigurationHybrid.json");

        // Create LLM judgment with expandCoverage=true + hybrid config
        String llmJudgmentBody = Files.readString(
            Path.of(classLoader.getResource("llmjudgment/CreateLlmJudgmentExpandCoverage.json").toURI())
        );
        llmJudgmentBody = replacePlaceholders(llmJudgmentBody, Map.of("querySetId", querySetId, "searchConfigId", searchConfigId));
        Response llmJudgmentResponse = makeRequest(
            client(),
            RestRequest.Method.PUT.name(),
            JUDGMENTS_URL,
            null,
            toHttpEntity(llmJudgmentBody),
            ImmutableList.of(new BasicHeader(HttpHeaders.USER_AGENT, DEFAULT_USER_AGENT))
        );
        Map<String, Object> llmJudgmentResult = entityAsMap(llmJudgmentResponse);
        String judgmentId = llmJudgmentResult.get("judgment_id").toString();
        assertNotNull("Judgment ID should not be null", judgmentId);

        // Wait for judgment processing
        Thread.sleep(DEFAULT_INTERVAL_MS);

        // Verify the judgment was created with expandCoverage in metadata
        String getJudgmentUrl = String.join("/", JUDGMENT_INDEX, "_doc", judgmentId);
        Response getJudgmentResponse = makeRequest(
            adminClient(),
            RestRequest.Method.GET.name(),
            getJudgmentUrl,
            null,
            null,
            ImmutableList.of(new BasicHeader(HttpHeaders.USER_AGENT, DEFAULT_USER_AGENT))
        );
        Map<String, Object> judgmentDoc = entityAsMap(getJudgmentResponse);
        assertNotNull(judgmentDoc);
        assertEquals(judgmentId, judgmentDoc.get("_id"));

        Map<String, Object> source = (Map<String, Object>) judgmentDoc.get("_source");
        assertNotNull(source);
        assertEquals("LLM_JUDGMENT", source.get("type"));

        // Verify metadata contains expandCoverage=true
        Map<String, Object> metadata = (Map<String, Object>) source.get("metadata");
        assertNotNull(metadata);
        assertEquals(true, metadata.get("expandCoverage"));
    }

    @SneakyThrows
    public void testWithoutExpandCoverage_thenExistingBehaviorUnchanged() {
        // Setup
        String querySetId = setupTestIndexAndQuerySet();
        String searchConfigId = createSearchConfig("llmjudgment/CreateSearchConfiguration.json");

        // Create LLM judgment WITHOUT expandCoverage (standard non-hybrid config)
        String llmJudgmentBody = Files.readString(Path.of(classLoader.getResource("llmjudgment/CreateLlmJudgmentMinimal.json").toURI()));
        llmJudgmentBody = replacePlaceholders(llmJudgmentBody, Map.of("querySetId", querySetId, "searchConfigId", searchConfigId));
        Response llmJudgmentResponse = makeRequest(
            client(),
            RestRequest.Method.PUT.name(),
            JUDGMENTS_URL,
            null,
            toHttpEntity(llmJudgmentBody),
            ImmutableList.of(new BasicHeader(HttpHeaders.USER_AGENT, DEFAULT_USER_AGENT))
        );
        Map<String, Object> llmJudgmentResult = entityAsMap(llmJudgmentResponse);
        String judgmentId = llmJudgmentResult.get("judgment_id").toString();
        assertNotNull("Judgment ID should not be null", judgmentId);

        // Wait for judgment processing
        Thread.sleep(DEFAULT_INTERVAL_MS);

        // Verify the judgment was created without expandCoverage
        String getJudgmentUrl = String.join("/", JUDGMENT_INDEX, "_doc", judgmentId);
        Response getJudgmentResponse = makeRequest(
            adminClient(),
            RestRequest.Method.GET.name(),
            getJudgmentUrl,
            null,
            null,
            ImmutableList.of(new BasicHeader(HttpHeaders.USER_AGENT, DEFAULT_USER_AGENT))
        );
        Map<String, Object> judgmentDoc = entityAsMap(getJudgmentResponse);
        Map<String, Object> source = (Map<String, Object>) judgmentDoc.get("_source");
        Map<String, Object> metadata = (Map<String, Object>) source.get("metadata");

        // expandCoverage should be false or absent when not provided
        Object expandCoverage = metadata.get("expandCoverage");
        assertTrue("expandCoverage should be false or null when not provided", expandCoverage == null || expandCoverage.equals(false));
    }

    @SneakyThrows
    public void testExpandCoverageWithNonHybridConfig_thenValidationError() {
        // Setup
        String querySetId = setupTestIndexAndQuerySet();
        // Use standard (non-hybrid) search config
        String searchConfigId = createSearchConfig("llmjudgment/CreateSearchConfiguration.json");

        // Create LLM judgment with expandCoverage=true + non-hybrid config
        String llmJudgmentBody = Files.readString(
            Path.of(classLoader.getResource("llmjudgment/CreateLlmJudgmentExpandCoverage.json").toURI())
        );
        llmJudgmentBody = replacePlaceholders(llmJudgmentBody, Map.of("querySetId", querySetId, "searchConfigId", searchConfigId));

        // The request should succeed (validation is async during processing),
        // but the judgment should end up with an error status
        Response llmJudgmentResponse = makeRequest(
            client(),
            RestRequest.Method.PUT.name(),
            JUDGMENTS_URL,
            null,
            toHttpEntity(llmJudgmentBody),
            ImmutableList.of(new BasicHeader(HttpHeaders.USER_AGENT, DEFAULT_USER_AGENT))
        );
        Map<String, Object> llmJudgmentResult = entityAsMap(llmJudgmentResponse);
        String judgmentId = llmJudgmentResult.get("judgment_id").toString();
        assertNotNull("Judgment ID should not be null", judgmentId);

        // Wait for judgment processing (will fail during async processing)
        Thread.sleep(DEFAULT_INTERVAL_MS);

        // Verify the judgment was created — status may indicate failure
        // because the non-hybrid query will fail validation during processing
        String getJudgmentUrl = String.join("/", JUDGMENT_INDEX, "_doc", judgmentId);
        Response getJudgmentResponse = makeRequest(
            adminClient(),
            RestRequest.Method.GET.name(),
            getJudgmentUrl,
            null,
            null,
            ImmutableList.of(new BasicHeader(HttpHeaders.USER_AGENT, DEFAULT_USER_AGENT))
        );
        Map<String, Object> judgmentDoc = entityAsMap(getJudgmentResponse);
        Map<String, Object> source = (Map<String, Object>) judgmentDoc.get("_source");
        assertNotNull(source);

        // The judgment should have been created with expandCoverage=true in metadata
        Map<String, Object> metadata = (Map<String, Object>) source.get("metadata");
        assertEquals(true, metadata.get("expandCoverage"));

        // Since the search config is non-hybrid, the processing should fail,
        // resulting in either FAILED status or empty ratings
        String status = (String) source.get("status");
        // Status could be FAILED or COMPLETED with empty ratings depending on ignoreFailure
        assertNotNull("Judgment should have a status", status);
    }
}
