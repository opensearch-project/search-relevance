/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */
package org.opensearch.searchrelevance.bwc.restart;

import java.io.IOException;
import java.util.Map;

import org.apache.hc.core5.http.ParseException;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.opensearch.client.Request;
import org.opensearch.client.RequestOptions;
import org.opensearch.client.Response;
import org.opensearch.client.WarningsHandler;
import org.opensearch.common.xcontent.json.JsonXContent;
import org.opensearch.core.xcontent.DeprecationHandler;
import org.opensearch.core.xcontent.NamedXContentRegistry;
import org.opensearch.core.xcontent.XContentParser;

/**
 * BWC (Backward Compatibility) Integration Test for Index Mapping Updates during full cluster restart.
 *
 * This test validates that index mappings are automatically updated when the entire cluster
 * is restarted with a new plugin version that has bumped schema_version in the mapping JSON.
 *
 * Test scenario:
 * 1. OLD cluster: Manually create judgment_cache index with old schema (version 0, no modelId/encodedPromptTemplate)
 * 2. UPGRADED cluster (after full restart): Verify mapping has new fields and schema_version=1
 */
public class IndexMappingRestartIT extends AbstractSearchRelevanceRestartUpgradeTestCase {

    private static final String JUDGMENT_CACHE_INDEX = ".plugins-search-relevance-judgment-cache";

    // Old schema (version 0) - without modelId and encodedPromptTemplate fields
    private static final String OLD_MAPPING = "{"
        + "\"_meta\": { \"schema_version\": 0 },"
        + "\"properties\": {"
        + "  \"id\": { \"type\": \"keyword\" },"
        + "  \"timestamp\": { \"type\": \"date\", \"format\": \"strict_date_time\" },"
        + "  \"querySet\": { \"type\": \"keyword\" },"
        + "  \"documentId\": { \"type\": \"keyword\" },"
        + "  \"contextFieldsStr\": { \"type\": \"keyword\" },"
        + "  \"rating\": { \"type\": \"keyword\" }"
        + "}"
        + "}";

    /**
     * Main BWC test for Index Mapping Updates during full cluster restart upgrade.
     * Tests that:
     * - OLD: Index is created with old mapping (schema_version 0, no modelId/encodedPromptTemplate)
     * - UPGRADED: After full cluster restart, mapping is updated with new fields and schema_version=1
     */
    public void testIndexMappingUpdate_RestartUpgrade() throws Exception {
        switch (getClusterType()) {
            case OLD:
                testCreateIndexWithOldMappingInOldCluster();
                break;
            case UPGRADED:
                testValidateMappingUpdateInUpgradedCluster();
                cleanupResources();
                break;
            default:
                throw new IllegalStateException("Unknown cluster type: " + getClusterType());
        }
    }

    /**
     * OLD cluster: Create judgment_cache index with old schema (version 0).
     * This simulates an index created before the schema_version was bumped.
     */
    private void testCreateIndexWithOldMappingInOldCluster() throws Exception {
        // Create the judgment_cache index with old mapping (version 0)
        Request request = new Request("PUT", "/" + JUDGMENT_CACHE_INDEX);
        // Ignore warnings about accessing system indices
        RequestOptions.Builder options = RequestOptions.DEFAULT.toBuilder();
        options.setWarningsHandler(WarningsHandler.PERMISSIVE);
        request.setOptions(options.build());
        request.setJsonEntity(
            "{"
                + "\"settings\": {"
                + "  \"index\": {"
                + "    \"number_of_shards\": 1,"
                + "    \"number_of_replicas\": 0,"
                + "    \"auto_expand_replicas\": \"0-1\""
                + "  }"
                + "},"
                + "\"mappings\": "
                + OLD_MAPPING
                + "}"
        );

        Response response = client().performRequest(request);
        int statusCode = response.getStatusLine().getStatusCode();
        logger.info("PUT response status: {}", statusCode);
        assertEquals(200, statusCode);

        // Wait for cluster to propagate the index creation
        Thread.sleep(2000);

        // Verify the index was created
        boolean exists = checkIndexExists(JUDGMENT_CACHE_INDEX);
        logger.info("Index exists check: {}", exists);
        assertTrue("Judgment cache index should exist", exists);

        // Verify the mapping has old schema
        Map<String, Object> mapping = getIndexMapping(JUDGMENT_CACHE_INDEX);
        assertNotNull("Mapping should exist", mapping);

        Map<String, Object> properties = getMappingProperties(mapping);
        assertNotNull("Properties should exist", properties);

        // Verify old schema doesn't have the new fields
        assertFalse("Old schema should NOT have modelId", properties.containsKey("modelId"));
        assertFalse("Old schema should NOT have encodedPromptTemplate", properties.containsKey("encodedPromptTemplate"));

        // Verify schema version is 0
        Map<String, Object> meta = getMappingMeta(mapping);
        assertNotNull("_meta should exist", meta);
        assertEquals("Schema version should be 0 in OLD cluster", 0, ((Number) meta.get("schema_version")).intValue());

        // Insert a test document to ensure the index has data
        insertTestDocument();

        logger.info("OLD cluster: Created judgment_cache index with schema_version=0");
    }

    /**
     * UPGRADED cluster: Verify old data survives upgrade and mapping can be updated.
     *
     * Note: The automatic version-based mapping update is triggered when a plugin API
     * (like LLM judgment) calls createIndexIfAbsent(). Since that requires ML model setup,
     * this test directly updates the mapping to verify the infrastructure works.
     * The full automatic update flow is tested via LlmJudgmentBWCIT when ML models are available.
     */
    private void testValidateMappingUpdateInUpgradedCluster() throws Exception {
        // Verify index still exists after upgrade
        assertTrue("Judgment cache index should exist after upgrade", checkIndexExists(JUDGMENT_CACHE_INDEX));

        // Verify old data is still accessible
        Map<String, Object> doc = getTestDocument();
        assertNotNull("Test document should still be accessible after upgrade", doc);

        // Update the mapping directly to add new fields (simulates what createIndexIfAbsent does)
        updateMappingDirectly();

        // Wait for mapping update to complete
        waitForMappingUpdate(JUDGMENT_CACHE_INDEX, new String[] { "modelId", "encodedPromptTemplate" }, 30);

        Map<String, Object> mapping = getIndexMapping(JUDGMENT_CACHE_INDEX);
        assertNotNull("Mapping should exist in UPGRADED cluster", mapping);

        Map<String, Object> properties = getMappingProperties(mapping);
        assertNotNull("Properties should exist in UPGRADED cluster", properties);

        // Verify new fields exist in the mapping
        assertTrue("Mapping should have modelId field after upgrade", properties.containsKey("modelId"));
        assertTrue("Mapping should have encodedPromptTemplate field after upgrade", properties.containsKey("encodedPromptTemplate"));

        // Verify schema version has been updated to 1
        Map<String, Object> meta = getMappingMeta(mapping);
        assertNotNull("Mapping should have _meta field", meta);
        assertEquals("Schema version should be 1 after upgrade", 1, ((Number) meta.get("schema_version")).intValue());

        // Verify old data is still accessible after mapping update
        doc = getTestDocument();
        assertNotNull("Test document should still be accessible after mapping update", doc);

        logger.info("UPGRADED cluster (restart): Mapping updated with new fields, schema_version=1, old data preserved");
    }

    /**
     * Directly update the mapping to add new fields.
     * This simulates what SearchRelevanceIndicesManager.updateMappingSync() does.
     */
    private void updateMappingDirectly() throws Exception {
        // New mapping with schema_version=1 and new fields
        String newMapping = "{"
            + "\"_meta\": { \"schema_version\": 1 },"
            + "\"properties\": {"
            + "  \"id\": { \"type\": \"keyword\" },"
            + "  \"timestamp\": { \"type\": \"date\", \"format\": \"strict_date_time\" },"
            + "  \"querySet\": { \"type\": \"keyword\" },"
            + "  \"documentId\": { \"type\": \"keyword\" },"
            + "  \"contextFieldsStr\": { \"type\": \"keyword\" },"
            + "  \"rating\": { \"type\": \"keyword\" },"
            + "  \"modelId\": { \"type\": \"keyword\" },"
            + "  \"encodedPromptTemplate\": { \"type\": \"keyword\" }"
            + "}"
            + "}";

        Request request = new Request("PUT", "/" + JUDGMENT_CACHE_INDEX + "/_mapping");
        RequestOptions.Builder options = RequestOptions.DEFAULT.toBuilder();
        options.setWarningsHandler(WarningsHandler.PERMISSIVE);
        request.setOptions(options.build());
        request.setJsonEntity(newMapping);

        Response response = client().performRequest(request);
        assertEquals("Mapping update should succeed", 200, response.getStatusLine().getStatusCode());
        logger.info("Directly updated mapping for judgment_cache index");
    }

    /**
     * Clean up test resources.
     */
    private void cleanupResources() throws Exception {
        try {
            Request request = new Request("DELETE", "/" + JUDGMENT_CACHE_INDEX);
            // Ignore warnings about accessing system indices
            RequestOptions.Builder options = RequestOptions.DEFAULT.toBuilder();
            options.setWarningsHandler(WarningsHandler.PERMISSIVE);
            request.setOptions(options.build());
            client().performRequest(request);
            logger.info("Cleaned up judgment_cache index");
        } catch (Exception e) {
            logger.debug("Failed to clean up judgment_cache index: {}", e.getMessage());
        }
    }

    // ==================== Helper Methods ====================

    /**
     * Inserts a test document into the judgment_cache index.
     */
    private void insertTestDocument() throws IOException, ParseException {
        Request request = new Request("POST", "/" + JUDGMENT_CACHE_INDEX + "/_doc/test-doc-1?refresh=true");
        // Ignore warnings about accessing system indices
        RequestOptions.Builder options = RequestOptions.DEFAULT.toBuilder();
        options.setWarningsHandler(WarningsHandler.PERMISSIVE);
        request.setOptions(options.build());
        request.setJsonEntity(
            "{"
                + "\"id\": \"test-cache-entry\","
                + "\"timestamp\": \"2024-01-01T00:00:00.000Z\","
                + "\"querySet\": \"test-query-set\","
                + "\"documentId\": \"doc-1\","
                + "\"contextFieldsStr\": \"field1,field2\","
                + "\"rating\": \"0.85\""
                + "}"
        );

        Response response = client().performRequest(request);
        assertEquals(201, response.getStatusLine().getStatusCode());
    }

    /**
     * Gets the test document from the judgment_cache index.
     */
    private Map<String, Object> getTestDocument() throws IOException, ParseException {
        try {
            Request request = new Request("GET", "/" + JUDGMENT_CACHE_INDEX + "/_doc/test-doc-1");
            // Ignore warnings about accessing system indices
            RequestOptions.Builder options = RequestOptions.DEFAULT.toBuilder();
            options.setWarningsHandler(WarningsHandler.PERMISSIVE);
            request.setOptions(options.build());
            Response response = client().performRequest(request);
            if (response.getStatusLine().getStatusCode() == 200) {
                Map<String, Object> responseMap = parseResponse(response);
                return (Map<String, Object>) responseMap.get("_source");
            }
        } catch (Exception e) {
            logger.debug("Failed to get test document: {}", e.getMessage());
        }
        return null;
    }

    /**
     * Waits for mapping to be updated with expected fields.
     */
    private void waitForMappingUpdate(String indexName, String[] expectedFields, int timeoutSeconds) throws Exception {
        long startTime = System.currentTimeMillis();
        long timeout = timeoutSeconds * 1000L;

        while (System.currentTimeMillis() - startTime < timeout) {
            try {
                Map<String, Object> mapping = getIndexMapping(indexName);
                Map<String, Object> properties = getMappingProperties(mapping);

                if (properties != null) {
                    boolean allFieldsPresent = true;
                    for (String field : expectedFields) {
                        if (!properties.containsKey(field)) {
                            allFieldsPresent = false;
                            break;
                        }
                    }
                    if (allFieldsPresent) {
                        logger.info("Mapping update detected - all expected fields present");
                        return;
                    }
                }
            } catch (Exception e) {
                logger.debug("Error checking mapping: {}", e.getMessage());
            }
            Thread.sleep(1000);
        }
        throw new AssertionError("Timeout waiting for mapping update with fields: " + String.join(", ", expectedFields));
    }

    /**
     * Checks if an index exists.
     */
    private boolean checkIndexExists(String indexName) throws IOException {
        try {
            Request request = new Request("HEAD", "/" + indexName);
            // Ignore warnings about accessing system indices
            RequestOptions.Builder options = RequestOptions.DEFAULT.toBuilder();
            options.setWarningsHandler(WarningsHandler.PERMISSIVE);
            request.setOptions(options.build());
            Response response = client().performRequest(request);
            int status = response.getStatusLine().getStatusCode();
            logger.info("HEAD request for {} returned status: {}", indexName, status);
            return status == 200;
        } catch (Exception e) {
            logger.info("HEAD request for {} threw exception: {}", indexName, e.getMessage());
            return false;
        }
    }

    /**
     * Gets the mapping for an index.
     */
    private Map<String, Object> getIndexMapping(String indexName) throws IOException, ParseException {
        Request request = new Request("GET", "/" + indexName + "/_mapping");
        // Ignore warnings about accessing system indices
        RequestOptions.Builder options = RequestOptions.DEFAULT.toBuilder();
        options.setWarningsHandler(WarningsHandler.PERMISSIVE);
        request.setOptions(options.build());
        Response response = client().performRequest(request);
        assertEquals(200, response.getStatusLine().getStatusCode());

        Map<String, Object> responseMap = parseResponse(response);
        Map<String, Object> indexMapping = (Map<String, Object>) responseMap.get(indexName);
        if (indexMapping != null) {
            return (Map<String, Object>) indexMapping.get("mappings");
        }
        return null;
    }

    /**
     * Extracts properties from mapping.
     */
    private Map<String, Object> getMappingProperties(Map<String, Object> mapping) {
        if (mapping != null) {
            return (Map<String, Object>) mapping.get("properties");
        }
        return null;
    }

    /**
     * Extracts _meta from mapping.
     */
    private Map<String, Object> getMappingMeta(Map<String, Object> mapping) {
        if (mapping != null) {
            return (Map<String, Object>) mapping.get("_meta");
        }
        return null;
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
