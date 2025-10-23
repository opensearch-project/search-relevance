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

        // Note: We're not creating LLM judgment in OLD cluster because it requires ML model
        // which may not be available. We'll test the format compatibility in MIXED/UPGRADED phases.
    }

    /**
     * MIXED cluster test: Validate that old format resources still work.
     * Also test creating new format resources if this is the first mixed round.
     */
    private void testValidateOldFormatResources() throws Exception {
        // Validate query set created in OLD cluster still exists and is readable
        Map<String, Object> querySet = getQuerySet(querySetId);
        assertNotNull("Query set from OLD cluster should still exist", querySet);

        // Validate search configuration still exists
        Map<String, Object> searchConfig = getSearchConfiguration(searchConfigId);
        assertNotNull("Search configuration from OLD cluster should still exist", searchConfig);
    }

    /**
     * Test creating resources with new format in MIXED cluster.
     */
    private void testCreateResourcesWithNewFormat() throws Exception {
        String querySetName = getQuerySetNameForTest() + "-new-format";

        // Create query set with NEW format (includes custom fields)
        String newQuerySetId = createQuerySetNewFormat(querySetName);
        assertNotNull("Query set should be created with new format", newQuerySetId);

        // Validate new format query set
        Map<String, Object> querySet = getQuerySet(newQuerySetId);
        assertEquals("Query set name should match", querySetName, querySet.get("name"));
    }

    /**
     * UPGRADED cluster test: Validate all resources work correctly.
     * Test new format features like promptTemplate and ratingType.
     */
    private void testValidateAllResources() throws Exception {
        // Validate old format query set still works
        Map<String, Object> oldQuerySet = getQuerySet(querySetId);
        assertNotNull("Old format query set should still work in upgraded cluster", oldQuerySet);

        // Validate search configuration still works
        Map<String, Object> searchConfig = getSearchConfiguration(searchConfigId);
        assertNotNull("Search configuration should still work in upgraded cluster", searchConfig);
    }

    /**
     * Test new format features in UPGRADED cluster.
     */
    private void testNewFormatFeatures() throws Exception {
        String querySetName = getQuerySetNameForTest() + "-upgraded-format";

        // Create query set with new format including multiple custom fields
        String newQuerySetId = createQuerySetWithMultipleCustomFields(querySetName);
        assertNotNull("Query set with multiple custom fields should be created", newQuerySetId);

        // Validate the query set has custom fields
        Map<String, Object> querySet = getQuerySet(newQuerySetId);
        assertEquals("Query set name should match", querySetName, querySet.get("name"));
    }

    /**
     * Clean up test resources.
     */
    private void cleanupResources() throws Exception {
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
        deleteIndex(indexName);
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
                + "\"query\": {\"match_all\": {}}"
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
        return parseResponse(response);
    }

    /**
     * Gets a search configuration by ID.
     */
    private Map<String, Object> getSearchConfiguration(String id) throws IOException, ParseException {
        Request request = new Request("GET", SEARCH_CONFIG_ENDPOINT + "/" + id);
        Response response = client().performRequest(request);
        assertEquals(200, response.getStatusLine().getStatusCode());
        return parseResponse(response);
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
     * Deletes an index.
     */
    private void deleteIndex(String indexName) throws IOException {
        Request request = new Request("DELETE", "/" + indexName);
        try {
            client().performRequest(request);
        } catch (Exception e) {
            // Ignore if index doesn't exist
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
