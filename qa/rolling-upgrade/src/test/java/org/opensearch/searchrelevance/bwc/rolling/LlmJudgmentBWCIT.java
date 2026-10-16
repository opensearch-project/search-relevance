/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */
package org.opensearch.searchrelevance.bwc.rolling;

import java.io.IOException;
import java.util.Map;

import org.apache.hc.core5.http.ParseException;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.opensearch.client.Request;
import org.opensearch.client.Response;
import org.opensearch.common.xcontent.json.JsonXContent;
import org.opensearch.core.xcontent.DeprecationHandler;
import org.opensearch.core.xcontent.NamedXContentRegistry;
import org.opensearch.core.xcontent.XContentParser;

/**
 * BWC (Backward Compatibility) Integration Test for LLM Judgment functionality.
 *
 * This test validates that:
 * 1. OLD cluster: Creates query sets and judgments using the old format (no promptTemplate, no ratingType)
 * 2. MIXED cluster: Can read and process both old and new format data
 * 3. UPGRADED cluster: Supports new features (promptTemplate, ratingType) while maintaining old format compatibility
 */
public class LlmJudgmentBWCIT extends AbstractSearchRelevanceRollingUpgradeTestCase {

    private static final String QUERY_SET_ENDPOINT = "/_plugins/_search_relevance/query_sets";
    private static final String JUDGMENT_ENDPOINT = "/_plugins/_search_relevance/judgments";
    private static final String SEARCH_CONFIG_ENDPOINT = "/_plugins/_search_relevance/search_configurations";

    private static String querySetId;
    private static String judgmentId;
    private static String searchConfigId;

    /**
     * Main BWC test for LLM Judgment functionality.
     * Tests backward compatibility during rolling upgrade:
     * - OLD: Create resources with old format (no promptTemplate, no ratingType)
     * - MIXED: Validate existing resources still work, can create new resources with new format
     * - UPGRADED: Full new format support, old format still works
     */
    public void testLlmJudgment_RollingUpgrade() throws Exception {
        switch (getClusterType()) {
            case OLD:
                testCreateResourcesWithOldFormat();
                testValidateOldFormatResources();
                break;
            case MIXED:
                testValidateOldFormatResources();
                if (isFirstMixedRound()) {
                    testCreateResourcesWithNewFormat();
                }
                break;
            case UPGRADED:
                testValidateAllResources();
                testNewFormatFeatures();
                cleanupResources();
                break;
            default:
                throw new IllegalStateException("Unknown cluster type: " + getClusterType());
        }
    }

    /**
     * OLD cluster test: Create resources using old format.
     * Old format characteristics:
     * - Query set: Only queryText and referenceAnswer (no custom fields)
     * - LLM Judgment: No promptTemplate, no llmJudgmentRatingType (uses defaults)
     */
    private void testCreateResourcesWithOldFormat() throws Exception {
        String indexName = getIndexNameForTest();

        // Create test index
        createTestIndex(indexName);

        // Create search configuration (this hasn't changed)
        searchConfigId = createSearchConfiguration(indexName);
        assertNotNull("Search configuration should be created", searchConfigId);

        // Create query set with OLD format (no custom fields, just queryText and referenceAnswer)
        String querySetName = getQuerySetNameForTest();
        querySetId = createQuerySetOldFormat(querySetName);
        assertNotNull("Query set should be created with old format", querySetId);

        // Validate query set was created correctly
        Map<String, Object> querySet = getQuerySet(querySetId);
        assertEquals("Query set name should match", querySetName, querySet.get("name"));

        // Validate that we can retrieve the resources by name (same approach used in MIXED/UPGRADED cluster)
        String searchConfigName = getSearchConfigNameForTest();
        String retrievedQuerySetId = getQuerySetIdByName(querySetName);
        String retrievedSearchConfigId = getSearchConfigIdByName(searchConfigName);

        assertEquals("Query set ID should match when retrieved by name", querySetId, retrievedQuerySetId);
        assertEquals("Search config ID should match when retrieved by name", searchConfigId, retrievedSearchConfigId);

        // Create LLM judgment with OLD format (no promptTemplate, no llmJudgmentRatingType)
        String judgmentName = getJudgmentNameForTest();
        judgmentId = createLlmJudgmentOldFormat(judgmentName, querySetId, searchConfigId);
        assertNotNull("LLM judgment should be created with old format", judgmentId);

        // Validate the judgment can be retrieved and has correct OLD format
        Map<String, Object> judgment = getLlmJudgment(judgmentId);
        assertNotNull("LLM judgment should be retrievable", judgment);
        assertEquals("Judgment name should match", judgmentName, judgment.get("name"));
        assertEquals("Judgment type should be LLM_JUDGMENT", "LLM_JUDGMENT", judgment.get("type"));

        // Validate OLD format fields based on BWC version
        // promptTemplate was introduced in 3.5.0 (PR #264), so versions before 3.5.0 should NOT have it
        Map<String, Object> metadata = (Map<String, Object>) judgment.get("metadata");
        if (metadata != null) {
            String bwcVersion = getBWCVersion();
            if (bwcVersion != null && (bwcVersion.startsWith("3.3") || bwcVersion.startsWith("3.4"))) {
                assertNull("OLD format (pre-3.5.0) should not have promptTemplate", metadata.get("promptTemplate"));
                assertNull("OLD format (pre-3.5.0) should not have llmJudgmentRatingType", metadata.get("llmJudgmentRatingType"));
            } else {
                // For 3.5.0+, promptTemplate is supported and a default value is stored
                assertNotNull("3.5.0+ format should have default promptTemplate", metadata.get("promptTemplate"));
            }
        }
    }

    /**
     * MIXED cluster test: Validate that old format resources still work.
     * Also test creating new format resources if this is the first mixed round.
     */
    private void testValidateOldFormatResources() throws Exception {
        // Retrieve IDs by name (since static variables don't persist across test phases)
        String querySetName = getQuerySetNameForTest();
        String searchConfigName = getSearchConfigNameForTest();

        querySetId = getQuerySetIdByName(querySetName);
        searchConfigId = getSearchConfigIdByName(searchConfigName);

        // Validate query set created in OLD cluster still exists and is readable
        Map<String, Object> querySet = getQuerySet(querySetId);
        assertNotNull("Query set from OLD cluster should still exist", querySet);

        // Validate search configuration still exists
        Map<String, Object> searchConfig = getSearchConfiguration(searchConfigId);
        assertNotNull("Search configuration from OLD cluster should still exist", searchConfig);

        // Validate LLM judgment created in OLD cluster still exists and can be retrieved
        String judgmentName = getJudgmentNameForTest();
        judgmentId = getLlmJudgmentIdByName(judgmentName);
        assertNotNull("LLM judgment from OLD cluster should still exist", judgmentId);

        // Retrieve and validate the old judgment to ensure backward compatibility
        Map<String, Object> judgment = getLlmJudgment(judgmentId);
        assertNotNull("Old format LLM judgment should be retrievable in MIXED cluster", judgment);
        assertEquals("Judgment name should match", judgmentName, judgment.get("name"));
    }

    /**
     * Test creating resources with new format in MIXED cluster.
     */
    private void testCreateResourcesWithNewFormat() throws Exception {
        String querySetName = getQuerySetNameForTest() + "-new";

        // Create query set with NEW format (includes custom fields)
        String newQuerySetId = createQuerySetNewFormat(querySetName);
        assertNotNull("Query set should be created with new format", newQuerySetId);

        // Validate new format query set
        Map<String, Object> querySet = getQuerySet(newQuerySetId);
        assertEquals("Query set name should match", querySetName, querySet.get("name"));

        // Create LLM judgment with NEW format (with promptTemplate and ratingType)
        String newJudgmentId = createLlmJudgmentNewFormat(newQuerySetId, searchConfigId);
        assertNotNull("New format LLM judgment should be created", newJudgmentId);

        // Validate new format judgment can be retrieved
        Map<String, Object> newJudgment = getLlmJudgment(newJudgmentId);
        assertNotNull("New format LLM judgment should be retrievable", newJudgment);

        // In MIXED cluster, the new format fields might not be stored/returned by old nodes (3.3.0)
        // We just verify the judgment was created successfully
        // Full validation of new fields will happen in UPGRADED cluster where all nodes support them
    }

    /**
     * UPGRADED cluster test: Validate all resources work correctly.
     * Test new format features like promptTemplate and ratingType.
     */
    private void testValidateAllResources() throws Exception {
        // Retrieve IDs by name (since static variables don't persist across test phases)
        String querySetName = getQuerySetNameForTest();
        String searchConfigName = getSearchConfigNameForTest();
        String judgmentName = getJudgmentNameForTest();

        querySetId = getQuerySetIdByName(querySetName);
        searchConfigId = getSearchConfigIdByName(searchConfigName);
        judgmentId = getLlmJudgmentIdByName(judgmentName);

        // Validate old format query set still works
        Map<String, Object> oldQuerySet = getQuerySet(querySetId);
        assertNotNull("Old format query set should still work in upgraded cluster", oldQuerySet);

        // Validate search configuration still works
        Map<String, Object> searchConfig = getSearchConfiguration(searchConfigId);
        assertNotNull("Search configuration should still work in upgraded cluster", searchConfig);

        // Validate old format judgment still works
        Map<String, Object> oldJudgment = getLlmJudgment(judgmentId);
        assertNotNull("Old format LLM judgment should still work in upgraded cluster", oldJudgment);
        assertEquals("Judgment name should match", judgmentName, oldJudgment.get("name"));
    }

    /**
     * Test new format features in UPGRADED cluster.
     */
    private void testNewFormatFeatures() throws Exception {
        String querySetName = getQuerySetNameForTest() + "-upg";

        // Create query set with new format including multiple custom fields
        String newQuerySetId = createQuerySetWithMultipleCustomFields(querySetName);
        assertNotNull("Query set with multiple custom fields should be created", newQuerySetId);

        // Validate the query set has custom fields
        Map<String, Object> querySet = getQuerySet(newQuerySetId);
        assertEquals("Query set name should match", querySetName, querySet.get("name"));

        // Create LLM judgment with new format and validate it works
        String newJudgmentId = createLlmJudgmentNewFormat(newQuerySetId, searchConfigId);
        assertNotNull("New format LLM judgment should be created in upgraded cluster", newJudgmentId);

        // Validate new judgment exists and can be retrieved with new format fields
        Map<String, Object> newJudgment = getLlmJudgment(newJudgmentId);
        assertNotNull("New judgment should exist", newJudgment);
        assertEquals("New judgment name should match", "bwc-judgment-new-format", newJudgment.get("name"));

        // Validate NEW format fields are present in UPGRADED cluster
        Map<String, Object> newMetadata = (Map<String, Object>) newJudgment.get("metadata");
        assertNotNull("Metadata should exist", newMetadata);
        assertNotNull("NEW format should have promptTemplate", newMetadata.get("promptTemplate"));
        assertEquals(
            "Prompt template should match",
            "Query: {{queryText}}\\n\\nDocuments: {{hits}}\\n\\nEvaluate the relevance of the search result.",
            newMetadata.get("promptTemplate")
        );
        assertNotNull("NEW format should have llmJudgmentRatingType", newMetadata.get("llmJudgmentRatingType"));
        assertEquals("Rating type should be SCORE0_1", "SCORE0_1", newMetadata.get("llmJudgmentRatingType"));
    }

    /**
     * Clean up test resources.
     */
    private void cleanupResources() throws Exception {
        // Clean up LLM judgments
        if (judgmentId != null) {
            deleteLlmJudgment(judgmentId);
        }

        // Clean up query sets
        if (querySetId != null) {
            deleteQuerySet(querySetId);
        }

        // Clean up search configurations
        if (searchConfigId != null) {
            deleteSearchConfiguration(searchConfigId);
        }

        // Clean up test index
        String indexName = getIndexNameForTest();
        deleteIndexSilently(indexName);
    }

    // ==================== Helper Methods ====================

    /**
     * Creates a test index for search configuration.
     */
    private void createTestIndex(String indexName) throws IOException {
        Request request = new Request("PUT", "/" + indexName);
        request.setJsonEntity(
            "{"
                + "\"settings\": {\"index\": {\"number_of_shards\": 1, \"number_of_replicas\": 0}},"
                + "\"mappings\": {\"properties\": {\"text\": {\"type\": \"text\"}}}"
                + "}"
        );
        Response response = client().performRequest(request);
        assertEquals(200, response.getStatusLine().getStatusCode());
    }

    /**
     * Creates a search configuration.
     */
    private String createSearchConfiguration(String indexName) throws IOException, ParseException {
        Request request = new Request("PUT", SEARCH_CONFIG_ENDPOINT);
        request.setJsonEntity(
            "{"
                + "\"name\": \""
                + getSearchConfigNameForTest()
                + "\","
                + "\"description\": \"BWC test search configuration\","
                + "\"index\": \""
                + indexName
                + "\","
                + "\"query\": \"{\\\"match_all\\\": {}}\""
                + "}"
        );

        Response response = client().performRequest(request);
        assertEquals(200, response.getStatusLine().getStatusCode());

        Map<String, Object> responseMap = parseResponse(response);
        return (String) responseMap.get("search_configuration_id");
    }

    /**
     * Creates a query set using OLD format (no custom fields).
     * Format: [{queryText: "...", referenceAnswer: "..."}]
     */
    private String createQuerySetOldFormat(String name) throws IOException, ParseException {
        Request request = new Request("PUT", QUERY_SET_ENDPOINT);
        request.setJsonEntity(
            "{"
                + "\"name\": \""
                + name
                + "\","
                + "\"description\": \"BWC test query set - old format\","
                + "\"sampling\": \"manual\","
                + "\"querySetQueries\": ["
                + "  {\"queryText\": \"What is OpenSearch?\", \"referenceAnswer\": \"OpenSearch is a search and analytics suite\"},"
                + "  {\"queryText\": \"red shoes\", \"referenceAnswer\": \"High quality leather shoes\"}"
                + "]"
                + "}"
        );

        Response response = client().performRequest(request);
        assertEquals(200, response.getStatusLine().getStatusCode());

        Map<String, Object> responseMap = parseResponse(response);
        return (String) responseMap.get("query_set_id");
    }

    /**
     * Creates a query set using NEW format (with custom fields).
     * Format: [{queryText: "...", referenceAnswer: "...", category: "...", expectedScore: "..."}]
     */
    private String createQuerySetNewFormat(String name) throws IOException, ParseException {
        Request request = new Request("PUT", QUERY_SET_ENDPOINT);
        request.setJsonEntity(
            "{"
                + "\"name\": \""
                + name
                + "\","
                + "\"description\": \"BWC test query set - new format\","
                + "\"sampling\": \"manual\","
                + "\"querySetQueries\": ["
                + "  {\"queryText\": \"What is OpenSearch?\", \"referenceAnswer\": \"OpenSearch is a search suite\", \"category\": \"technology\"},"
                + "  {\"queryText\": \"red shoes\", \"referenceAnswer\": \"Leather shoes\", \"category\": \"fashion\", \"expectedScore\": \"0.95\"}"
                + "]"
                + "}"
        );

        Response response = client().performRequest(request);
        assertEquals(200, response.getStatusLine().getStatusCode());

        Map<String, Object> responseMap = parseResponse(response);
        return (String) responseMap.get("query_set_id");
    }

    /**
     * Creates a query set with multiple custom fields.
     */
    private String createQuerySetWithMultipleCustomFields(String name) throws IOException, ParseException {
        Request request = new Request("PUT", QUERY_SET_ENDPOINT);
        request.setJsonEntity(
            "{"
                + "\"name\": \""
                + name
                + "\","
                + "\"description\": \"BWC test query set - multiple custom fields\","
                + "\"sampling\": \"manual\","
                + "\"querySetQueries\": ["
                + "  {"
                + "    \"queryText\": \"red leather shoes\","
                + "    \"referenceAnswer\": \"High quality red leather shoes\","
                + "    \"category\": \"footwear\","
                + "    \"expectedScore\": \"0.95\","
                + "    \"brand\": \"Nike\","
                + "    \"priceRange\": \"premium\""
                + "  }"
                + "]"
                + "}"
        );

        Response response = client().performRequest(request);
        assertEquals(200, response.getStatusLine().getStatusCode());

        Map<String, Object> responseMap = parseResponse(response);
        return (String) responseMap.get("query_set_id");
    }

    /**
     * Gets a query set by ID.
     */
    private Map<String, Object> getQuerySet(String id) throws IOException, ParseException {
        Request request = new Request("GET", QUERY_SET_ENDPOINT + "/" + id);
        Response response = client().performRequest(request);
        assertEquals(200, response.getStatusLine().getStatusCode());
        Map<String, Object> responseMap = parseResponse(response);

        // Extract the query set from the search response
        Map<String, Object> hits = (Map<String, Object>) responseMap.get("hits");
        if (hits != null && hits.get("hits") != null) {
            java.util.List<Map<String, Object>> hitsList = (java.util.List<Map<String, Object>>) hits.get("hits");
            if (!hitsList.isEmpty()) {
                return (Map<String, Object>) hitsList.get(0).get("_source");
            }
        }
        return null;
    }

    /**
     * Gets a search configuration by ID.
     */
    private Map<String, Object> getSearchConfiguration(String id) throws IOException, ParseException {
        Request request = new Request("GET", SEARCH_CONFIG_ENDPOINT + "/" + id);
        Response response = client().performRequest(request);
        assertEquals(200, response.getStatusLine().getStatusCode());
        Map<String, Object> responseMap = parseResponse(response);

        // Extract the search configuration from the search response
        Map<String, Object> hits = (Map<String, Object>) responseMap.get("hits");
        if (hits != null && hits.get("hits") != null) {
            java.util.List<Map<String, Object>> hitsList = (java.util.List<Map<String, Object>>) hits.get("hits");
            if (!hitsList.isEmpty()) {
                return (Map<String, Object>) hitsList.get(0).get("_source");
            }
        }
        return null;
    }

    /**
     * Deletes a query set by ID.
     */
    private void deleteQuerySet(String id) throws IOException {
        Request request = new Request("DELETE", QUERY_SET_ENDPOINT + "/" + id);
        client().performRequest(request);
    }

    /**
     * Deletes a search configuration by ID.
     */
    private void deleteSearchConfiguration(String id) throws IOException {
        Request request = new Request("DELETE", SEARCH_CONFIG_ENDPOINT + "/" + id);
        client().performRequest(request);
    }

    /**
     * Deletes an index silently (ignoring errors if index doesn't exist).
     */
    private void deleteIndexSilently(String indexName) throws IOException {
        Request request = new Request("DELETE", "/" + indexName);
        try {
            client().performRequest(request);
        } catch (Exception e) {
            // Ignore if index doesn't exist
        }
    }

    /**
     * Gets query set ID by searching for it by name in the index.
     * Similar to how neural-search BWC tests get model ID from pipeline.
     */
    private String getQuerySetIdByName(String name) throws IOException, ParseException {
        // Index name from PluginConstants.QUERY_SET_INDEX = "search-relevance-queryset"
        String indexName = "search-relevance-queryset";

        try {
            Request request = new Request("POST", "/" + indexName + "/_search");
            // name is already a keyword field, no need for .keyword suffix
            request.setJsonEntity("{" + "\"query\": {" + "  \"term\": {" + "    \"name\": \"" + name + "\"" + "  }" + "}" + "}");

            Response response = client().performRequest(request);
            if (response.getStatusLine().getStatusCode() == 200) {
                Map<String, Object> responseMap = parseResponse(response);

                // Extract the ID from the search response
                Map<String, Object> hits = (Map<String, Object>) responseMap.get("hits");
                if (hits != null && hits.get("hits") != null) {
                    java.util.List<Map<String, Object>> hitsList = (java.util.List<Map<String, Object>>) hits.get("hits");
                    if (!hitsList.isEmpty()) {
                        return (String) hitsList.get(0).get("_id");
                    }
                }
            }
        } catch (Exception e) {
            // Index might not exist yet
            logger.debug("Failed to query index {}: {}", indexName, e.getMessage());
        }
        return null;
    }

    /**
     * Gets search configuration ID by searching for it by name in the index.
     */
    private String getSearchConfigIdByName(String name) throws IOException, ParseException {
        // Index name from PluginConstants.SEARCH_CONFIGURATION_INDEX = "search-relevance-search-config"
        String indexName = "search-relevance-search-config";

        try {
            Request request = new Request("POST", "/" + indexName + "/_search");
            // name is already a keyword field, no need for .keyword suffix
            request.setJsonEntity("{" + "\"query\": {" + "  \"term\": {" + "    \"name\": \"" + name + "\"" + "  }" + "}" + "}");

            Response response = client().performRequest(request);
            if (response.getStatusLine().getStatusCode() == 200) {
                Map<String, Object> responseMap = parseResponse(response);

                // Extract the ID from the search response
                Map<String, Object> hits = (Map<String, Object>) responseMap.get("hits");
                if (hits != null && hits.get("hits") != null) {
                    java.util.List<Map<String, Object>> hitsList = (java.util.List<Map<String, Object>>) hits.get("hits");
                    if (!hitsList.isEmpty()) {
                        return (String) hitsList.get(0).get("_id");
                    }
                }
            }
        } catch (Exception e) {
            // Index might not exist yet
            logger.debug("Failed to query index {}: {}", indexName, e.getMessage());
        }
        return null;
    }

    /**
     * Gets judgment ID by searching for it by name in the index.
     */
    private String getLlmJudgmentIdByName(String name) throws IOException, ParseException {
        // Index name from PluginConstants.JUDGMENT_INDEX = "search-relevance-judgment"
        String indexName = "search-relevance-judgment";

        try {
            Request request = new Request("POST", "/" + indexName + "/_search");
            // name is already a keyword field, no need for .keyword suffix
            request.setJsonEntity("{" + "\"query\": {" + "  \"term\": {" + "    \"name\": \"" + name + "\"" + "  }" + "}" + "}");

            Response response = client().performRequest(request);
            if (response.getStatusLine().getStatusCode() == 200) {
                Map<String, Object> responseMap = parseResponse(response);

                // Extract the ID from the search response
                Map<String, Object> hits = (Map<String, Object>) responseMap.get("hits");
                if (hits != null && hits.get("hits") != null) {
                    java.util.List<Map<String, Object>> hitsList = (java.util.List<Map<String, Object>>) hits.get("hits");
                    if (!hitsList.isEmpty()) {
                        return (String) hitsList.get(0).get("_id");
                    }
                }
            }
        } catch (Exception e) {
            // Index might not exist yet
            logger.debug("Failed to query index {}: {}", indexName, e.getMessage());
        }
        return null;
    }

    /**
     * Creates an LLM judgment using OLD format (no promptTemplate, no llmJudgmentRatingType).
     * Uses default values for these fields.
     */
    private String createLlmJudgmentOldFormat(String name, String querySetId, String searchConfigId) throws IOException, ParseException {
        Request request = new Request("PUT", JUDGMENT_ENDPOINT);
        request.setJsonEntity(
            "{"
                + "\"name\": \""
                + name
                + "\","
                + "\"description\": \"BWC test judgment - old format\","
                + "\"type\": \"LLM_JUDGMENT\","
                + "\"modelId\": \"test-model-id\","
                + "\"querySetId\": \""
                + querySetId
                + "\","
                + "\"searchConfigurationList\": [\""
                + searchConfigId
                + "\"],"
                + "\"size\": 10,"
                + "\"tokenLimit\": 1000,"
                + "\"contextFields\": [\"text\"],"
                + "\"ignoreFailure\": false"
                + "}"
        );

        Response response = client().performRequest(request);
        assertEquals(200, response.getStatusLine().getStatusCode());

        Map<String, Object> responseMap = parseResponse(response);
        return (String) responseMap.get("judgment_id");
    }

    /**
     * Creates an LLM judgment using NEW format (with promptTemplate and llmJudgmentRatingType).
     */
    private String createLlmJudgmentNewFormat(String querySetId, String searchConfigId) throws IOException, ParseException {
        Request request = new Request("PUT", JUDGMENT_ENDPOINT);
        request.setJsonEntity(
            "{"
                + "\"name\": \"bwc-judgment-new-format\","
                + "\"description\": \"BWC test judgment - new format\","
                + "\"type\": \"LLM_JUDGMENT\","
                + "\"modelId\": \"test-model-id\","
                + "\"querySetId\": \""
                + querySetId
                + "\","
                + "\"searchConfigurationList\": [\""
                + searchConfigId
                + "\"],"
                + "\"size\": 10,"
                + "\"tokenLimit\": 1000,"
                + "\"contextFields\": [\"text\"],"
                + "\"ignoreFailure\": false,"
                + "\"promptTemplate\": \"Query: {{queryText}}\\\\n\\\\nDocuments: {{hits}}\\\\n\\\\nEvaluate the relevance of the search result.\","
                + "\"llmJudgmentRatingType\": \"SCORE0_1\","
                + "\"overwriteCache\": true"
                + "}"
        );

        Response response = client().performRequest(request);
        assertEquals(200, response.getStatusLine().getStatusCode());

        Map<String, Object> responseMap = parseResponse(response);
        return (String) responseMap.get("judgment_id");
    }

    /**
     * Gets an LLM judgment by ID.
     */
    private Map<String, Object> getLlmJudgment(String id) throws IOException, ParseException {
        Request request = new Request("GET", JUDGMENT_ENDPOINT + "/" + id);
        Response response = client().performRequest(request);
        assertEquals(200, response.getStatusLine().getStatusCode());
        Map<String, Object> responseMap = parseResponse(response);

        // Extract the judgment from the search response
        Map<String, Object> hits = (Map<String, Object>) responseMap.get("hits");
        if (hits != null && hits.get("hits") != null) {
            java.util.List<Map<String, Object>> hitsList = (java.util.List<Map<String, Object>>) hits.get("hits");
            if (!hitsList.isEmpty()) {
                return (Map<String, Object>) hitsList.get(0).get("_source");
            }
        }
        return null;
    }

    /**
     * Deletes an LLM judgment by ID.
     */
    private void deleteLlmJudgment(String id) throws IOException {
        Request request = new Request("DELETE", JUDGMENT_ENDPOINT + "/" + id);
        try {
            client().performRequest(request);
        } catch (Exception e) {
            // Ignore if judgment doesn't exist
        }
    }

    /**
     * Parses HTTP response to Map.
     */
    private Map<String, Object> parseResponse(Response response) throws IOException, ParseException {
        String responseBody = EntityUtils.toString(response.getEntity());
        try (
            XContentParser parser = JsonXContent.jsonXContent.createParser(
                NamedXContentRegistry.EMPTY,
                DeprecationHandler.THROW_UNSUPPORTED_OPERATION,
                responseBody
            )
        ) {
            return parser.map();
        }
    }
}
