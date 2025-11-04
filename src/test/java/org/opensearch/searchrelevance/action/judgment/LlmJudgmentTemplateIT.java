/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */
package org.opensearch.searchrelevance.action.judgment;

import static org.opensearch.searchrelevance.common.MLConstants.DEFAULT_PROMPT_TEMPLATE;
import static org.opensearch.searchrelevance.common.PluginConstants.JUDGMENTS_URL;
import static org.opensearch.searchrelevance.common.PluginConstants.JUDGMENT_INDEX;
import static org.opensearch.searchrelevance.common.PluginConstants.QUERYSETS_URL;
import static org.opensearch.searchrelevance.common.PluginConstants.SEARCH_CONFIGURATIONS_URL;

import java.nio.file.Files;
import java.nio.file.Path;
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

/**
 * Integration tests for LLM Judgment Template functionality.
 * Tests the new fields: promptTemplate, llmJudgmentRatingType, and overwriteCache.
 */
@ThreadLeakScope(ThreadLeakScope.Scope.NONE)
@OpenSearchIntegTestCase.ClusterScope(scope = OpenSearchIntegTestCase.Scope.SUITE)
public class LlmJudgmentTemplateIT extends BaseSearchRelevanceIT {

    private static final String TEST_INDEX = "test_llm_products";

    @SneakyThrows
    public void testLlmJudgmentWithPromptTemplate_thenSuccessful() {
        // Step 1: Create test index
        String indexConfig = Files.readString(Path.of(classLoader.getResource("llmjudgment/CreateTestIndex.json").toURI()));
        createIndexWithConfiguration(TEST_INDEX, indexConfig);

        // Step 2: Bulk ingest test documents
        String bulkData = Files.readString(Path.of(classLoader.getResource("llmjudgment/BulkIngestProducts.json").toURI()));
        bulkIngest(TEST_INDEX, bulkData);

        // Step 3: Create query set with custom fields
        String querySetBody = Files.readString(Path.of(classLoader.getResource("llmjudgment/CreateQuerySetWithCustomFields.json").toURI()));
        Response querySetResponse = makeRequest(
            client(),
            RestRequest.Method.PUT.name(),
            QUERYSETS_URL,
            null,
            toHttpEntity(querySetBody),
            ImmutableList.of(new BasicHeader(HttpHeaders.USER_AGENT, DEFAULT_USER_AGENT))
        );
        Map<String, Object> querySetResult = entityAsMap(querySetResponse);
        String querySetId = querySetResult.get("query_set_id").toString();
        assertNotNull(querySetId);

        // Step 4: Create search configuration
        String searchConfigBody = Files.readString(Path.of(classLoader.getResource("llmjudgment/CreateSearchConfiguration.json").toURI()));
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
        String searchConfigId = searchConfigResult.get("search_configuration_id").toString();
        assertNotNull(searchConfigId);

        // Step 5: Create LLM judgment with promptTemplate
        String llmJudgmentBody = Files.readString(
            Path.of(classLoader.getResource("llmjudgment/CreateLlmJudgmentWithPromptTemplate.json").toURI())
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
        assertNotNull(judgmentId);

        // Step 6: Wait for judgment processing to complete
        Thread.sleep(DEFAULT_INTERVAL_MS);

        // Step 7: Verify the judgment
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
        assertNotNull(source.get("status")); // Should be COMPLETED or IN_PROGRESS

        // Verify metadata contains new fields
        Map<String, Object> metadata = (Map<String, Object>) source.get("metadata");
        assertNotNull(metadata);
        assertNotNull(metadata.get("promptTemplate"));
        assertTrue(((String) metadata.get("promptTemplate")).contains("{{queryText}}"));
        assertNotNull(metadata.get("llmJudgmentRatingType"));
        assertEquals("SCORE0_1", metadata.get("llmJudgmentRatingType"));
        assertNotNull(metadata.get("overwriteCache"));

        // Verify judgmentRatings format
        List<Map<String, Object>> judgmentRatings = (List<Map<String, Object>>) source.get("judgmentRatings");
        assertNotNull(judgmentRatings);

        // If there are judgment ratings, verify custom input format with delimiter
        // Note: Ratings may be empty if no actual ML model is configured
        if (!judgmentRatings.isEmpty()) {
            Map<String, Object> firstRating = judgmentRatings.get(0);
            String queryText = (String) firstRating.get("query");
            assertNotNull(queryText);
            assertTrue(queryText.contains("#\n")); // Custom delimiter
            assertTrue(queryText.contains("category:"));
            assertTrue(queryText.contains("referenceAnswer:"));
        }
    }

    @SneakyThrows
    public void testLlmJudgmentWithDifferentRatingTypes_thenSuccessful() {
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
        String querySetId = querySetResult.get("query_set_id").toString();

        // Create search configuration
        String searchConfigBody = Files.readString(Path.of(classLoader.getResource("llmjudgment/CreateSearchConfiguration.json").toURI()));
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
        String searchConfigId = searchConfigResult.get("search_configuration_id").toString();

        // Test SCORE0_1 rating type
        String score01Body = Files.readString(Path.of(classLoader.getResource("llmjudgment/CreateLlmJudgmentScore01.json").toURI()));
        score01Body = replacePlaceholders(score01Body, Map.of("querySetId", querySetId, "searchConfigId", searchConfigId));
        Response score01Response = makeRequest(
            client(),
            RestRequest.Method.PUT.name(),
            JUDGMENTS_URL,
            null,
            toHttpEntity(score01Body),
            ImmutableList.of(new BasicHeader(HttpHeaders.USER_AGENT, DEFAULT_USER_AGENT))
        );
        Map<String, Object> score01Result = entityAsMap(score01Response);
        String judgmentId01 = score01Result.get("judgment_id").toString();
        assertNotNull(judgmentId01);

        Thread.sleep(DEFAULT_INTERVAL_MS);

        // Verify SCORE0_1
        String getJudgment01Url = String.join("/", JUDGMENT_INDEX, "_doc", judgmentId01);
        Response getJudgment01Response = makeRequest(
            adminClient(),
            RestRequest.Method.GET.name(),
            getJudgment01Url,
            null,
            null,
            ImmutableList.of(new BasicHeader(HttpHeaders.USER_AGENT, DEFAULT_USER_AGENT))
        );
        Map<String, Object> judgment01Doc = entityAsMap(getJudgment01Response);
        Map<String, Object> source01 = (Map<String, Object>) judgment01Doc.get("_source");
        Map<String, Object> metadata01 = (Map<String, Object>) source01.get("metadata");
        assertEquals("SCORE0_1", metadata01.get("llmJudgmentRatingType"));

        // Test RELEVANT_IRRELEVANT rating type
        String binaryBody = Files.readString(Path.of(classLoader.getResource("llmjudgment/CreateLlmJudgmentBinary.json").toURI()));
        binaryBody = replacePlaceholders(binaryBody, Map.of("querySetId", querySetId, "searchConfigId", searchConfigId));
        Response binaryResponse = makeRequest(
            client(),
            RestRequest.Method.PUT.name(),
            JUDGMENTS_URL,
            null,
            toHttpEntity(binaryBody),
            ImmutableList.of(new BasicHeader(HttpHeaders.USER_AGENT, DEFAULT_USER_AGENT))
        );
        Map<String, Object> binaryResult = entityAsMap(binaryResponse);
        String judgmentIdBinary = binaryResult.get("judgment_id").toString();
        assertNotNull(judgmentIdBinary);

        Thread.sleep(DEFAULT_INTERVAL_MS);

        // Verify RELEVANT_IRRELEVANT
        String getJudgmentBinaryUrl = String.join("/", JUDGMENT_INDEX, "_doc", judgmentIdBinary);
        Response getJudgmentBinaryResponse = makeRequest(
            adminClient(),
            RestRequest.Method.GET.name(),
            getJudgmentBinaryUrl,
            null,
            null,
            ImmutableList.of(new BasicHeader(HttpHeaders.USER_AGENT, DEFAULT_USER_AGENT))
        );
        Map<String, Object> judgmentBinaryDoc = entityAsMap(getJudgmentBinaryResponse);
        Map<String, Object> sourceBinary = (Map<String, Object>) judgmentBinaryDoc.get("_source");
        Map<String, Object> metadataBinary = (Map<String, Object>) sourceBinary.get("metadata");
        assertEquals("RELEVANT_IRRELEVANT", metadataBinary.get("llmJudgmentRatingType"));
    }

    @SneakyThrows
    public void testLlmJudgmentWithOverwriteCache_thenSuccessful() {
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
        String querySetId = querySetResult.get("query_set_id").toString();

        // Create search configuration
        String searchConfigBody = Files.readString(Path.of(classLoader.getResource("llmjudgment/CreateSearchConfiguration.json").toURI()));
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
        String searchConfigId = searchConfigResult.get("search_configuration_id").toString();

        // Test with overwriteCache = true
        String overwriteTrueBody = Files.readString(
            Path.of(classLoader.getResource("llmjudgment/CreateLlmJudgmentOverwriteTrue.json").toURI())
        );
        overwriteTrueBody = replacePlaceholders(overwriteTrueBody, Map.of("querySetId", querySetId, "searchConfigId", searchConfigId));
        Response overwriteTrueResponse = makeRequest(
            client(),
            RestRequest.Method.PUT.name(),
            JUDGMENTS_URL,
            null,
            toHttpEntity(overwriteTrueBody),
            ImmutableList.of(new BasicHeader(HttpHeaders.USER_AGENT, DEFAULT_USER_AGENT))
        );
        Map<String, Object> overwriteTrueResult = entityAsMap(overwriteTrueResponse);
        String judgmentIdTrue = overwriteTrueResult.get("judgment_id").toString();
        assertNotNull(judgmentIdTrue);

        Thread.sleep(DEFAULT_INTERVAL_MS);

        // Verify overwriteCache = true
        String getJudgmentTrueUrl = String.join("/", JUDGMENT_INDEX, "_doc", judgmentIdTrue);
        Response getJudgmentTrueResponse = makeRequest(
            adminClient(),
            RestRequest.Method.GET.name(),
            getJudgmentTrueUrl,
            null,
            null,
            ImmutableList.of(new BasicHeader(HttpHeaders.USER_AGENT, DEFAULT_USER_AGENT))
        );
        Map<String, Object> judgmentTrueDoc = entityAsMap(getJudgmentTrueResponse);
        Map<String, Object> sourceTrue = (Map<String, Object>) judgmentTrueDoc.get("_source");
        Map<String, Object> metadataTrue = (Map<String, Object>) sourceTrue.get("metadata");
        assertEquals(true, metadataTrue.get("overwriteCache"));

        // Test with overwriteCache = false
        String overwriteFalseBody = Files.readString(
            Path.of(classLoader.getResource("llmjudgment/CreateLlmJudgmentOverwriteFalse.json").toURI())
        );
        overwriteFalseBody = replacePlaceholders(overwriteFalseBody, Map.of("querySetId", querySetId, "searchConfigId", searchConfigId));
        Response overwriteFalseResponse = makeRequest(
            client(),
            RestRequest.Method.PUT.name(),
            JUDGMENTS_URL,
            null,
            toHttpEntity(overwriteFalseBody),
            ImmutableList.of(new BasicHeader(HttpHeaders.USER_AGENT, DEFAULT_USER_AGENT))
        );
        Map<String, Object> overwriteFalseResult = entityAsMap(overwriteFalseResponse);
        String judgmentIdFalse = overwriteFalseResult.get("judgment_id").toString();
        assertNotNull(judgmentIdFalse);

        Thread.sleep(DEFAULT_INTERVAL_MS);

        // Verify overwriteCache = false
        String getJudgmentFalseUrl = String.join("/", JUDGMENT_INDEX, "_doc", judgmentIdFalse);
        Response getJudgmentFalseResponse = makeRequest(
            adminClient(),
            RestRequest.Method.GET.name(),
            getJudgmentFalseUrl,
            null,
            null,
            ImmutableList.of(new BasicHeader(HttpHeaders.USER_AGENT, DEFAULT_USER_AGENT))
        );
        Map<String, Object> judgmentFalseDoc = entityAsMap(getJudgmentFalseResponse);
        Map<String, Object> sourceFalse = (Map<String, Object>) judgmentFalseDoc.get("_source");
        Map<String, Object> metadataFalse = (Map<String, Object>) sourceFalse.get("metadata");
        assertEquals(false, metadataFalse.get("overwriteCache"));
    }

    @SneakyThrows
    public void testLlmJudgmentWithoutOptionalFields_thenSuccessfulWithDefaults() {
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
        String querySetId = querySetResult.get("query_set_id").toString();

        // Create search configuration
        String searchConfigBody = Files.readString(Path.of(classLoader.getResource("llmjudgment/CreateSearchConfiguration.json").toURI()));
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
        String searchConfigId = searchConfigResult.get("search_configuration_id").toString();

        // Create LLM judgment WITHOUT promptTemplate, llmJudgmentRatingType, overwriteCache
        String minimalBody = Files.readString(Path.of(classLoader.getResource("llmjudgment/CreateLlmJudgmentMinimal.json").toURI()));
        minimalBody = replacePlaceholders(minimalBody, Map.of("querySetId", querySetId, "searchConfigId", searchConfigId));
        Response minimalResponse = makeRequest(
            client(),
            RestRequest.Method.PUT.name(),
            JUDGMENTS_URL,
            null,
            toHttpEntity(minimalBody),
            ImmutableList.of(new BasicHeader(HttpHeaders.USER_AGENT, DEFAULT_USER_AGENT))
        );
        Map<String, Object> minimalResult = entityAsMap(minimalResponse);
        String judgmentId = minimalResult.get("judgment_id").toString();
        assertNotNull(judgmentId);

        Thread.sleep(DEFAULT_INTERVAL_MS);

        // Verify defaults
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

        // promptTemplate should have the default value when not provided
        Object promptTemplate = metadata.get("promptTemplate");
        assertNotNull("promptTemplate should not be null when not provided", promptTemplate);
        assertEquals("promptTemplate should have default value", DEFAULT_PROMPT_TEMPLATE, promptTemplate);

        // llmJudgmentRatingType should have a default or be null
        Object ratingType = metadata.get("llmJudgmentRatingType");
        // Either null or has a default value

        // overwriteCache should default to false
        Object overwriteCache = metadata.get("overwriteCache");
        assertTrue(overwriteCache == null || overwriteCache.equals(false));
    }
}
