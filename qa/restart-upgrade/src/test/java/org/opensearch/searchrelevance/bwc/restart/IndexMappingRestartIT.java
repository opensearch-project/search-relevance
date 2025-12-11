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
import org.opensearch.client.Response;
import org.opensearch.common.xcontent.json.JsonXContent;
import org.opensearch.core.xcontent.DeprecationHandler;
import org.opensearch.core.xcontent.NamedXContentRegistry;
import org.opensearch.core.xcontent.XContentParser;

/**
 * BWC (Backward Compatibility) Integration Test for Index Mapping Updates during restart upgrade.
 *
 * This test validates that index mappings are properly updated during full cluster restart upgrades.
 * It simulates a scenario where an index was created with a minimal/old mapping and
 * verifies that the mapping update mechanism adds new fields after upgrade.
 *
 * Test flow:
 * 1. OLD cluster: Creates the search-relevance-queryset index with a MINIMAL mapping
 *    (only 'id' and 'name' fields) to simulate an older version's mapping.
 *    Also inserts a test document.
 * 2. UPGRADED cluster: After full cluster restart with upgraded plugin:
 *    - Validates that the mapping was updated with NEW fields
 *    - Validates that existing data is still accessible
 *    - Validates that new documents can be written with the updated mapping
 */
public class IndexMappingRestartIT extends AbstractSearchRelevanceRestartUpgradeTestCase {

    private static final String QUERY_SET_INDEX = "search-relevance-queryset";
    private static final String TEST_DOC_ID = "bwc-restart-test-doc-1";

    // Minimal mapping simulating an "old" version - only has id and name fields
    private static final String MINIMAL_OLD_MAPPING = "{"
        + "\"properties\": {"
        + "  \"id\": {\"type\": \"keyword\"},"
        + "  \"name\": {\"type\": \"keyword\"}"
        + "}"
        + "}";

    // Fields that should be ADDED by the mapping update (exist in current queryset.json but not in minimal mapping)
    private static final String[] NEW_FIELDS_AFTER_UPGRADE = { "description", "timestamp", "sampling", "querySetQueries" };

    /**
     * Main BWC test for Index Mapping Update functionality during restart upgrade.
     */
    public void testIndexMappingUpdate_RestartUpgrade() throws Exception {
        switch (getClusterType()) {
            case OLD:
                testCreateIndexWithMinimalMappingInOldCluster();
                break;
            case UPGRADED:
                testValidateMappingUpdatedInUpgradedCluster();
                break;
            default:
                throw new IllegalStateException("Unknown cluster type: " + getClusterType());
        }
    }

    /**
     * OLD cluster test: Create the index with a MINIMAL mapping to simulate an older version.
     */
    @SuppressWarnings("unchecked")
    private void testCreateIndexWithMinimalMappingInOldCluster() throws Exception {
        // Create the index with minimal mapping (simulating old version)
        if (!checkIndexExists(QUERY_SET_INDEX)) {
            createIndexWithMapping(QUERY_SET_INDEX, MINIMAL_OLD_MAPPING);
            logger.info("Created {} index with minimal mapping in OLD cluster", QUERY_SET_INDEX);
        }

        // Verify the index was created
        assertTrue("Index should exist", checkIndexExists(QUERY_SET_INDEX));

        // Get and verify the mapping only has minimal fields
        Map<String, Object> mapping = getIndexMapping(QUERY_SET_INDEX);
        assertNotNull("Index mapping should exist", mapping);
        logger.info("OLD cluster mapping for {}: {}", QUERY_SET_INDEX, mapping);

        Map<String, Object> properties = (Map<String, Object>) mapping.get("properties");
        assertNotNull("Mapping should have properties", properties);

        // Verify minimal fields exist
        assertTrue("Mapping should contain 'id' field", properties.containsKey("id"));
        assertTrue("Mapping should contain 'name' field", properties.containsKey("name"));

        // Verify NEW fields do NOT exist yet (they should be added after upgrade)
        for (String newField : NEW_FIELDS_AFTER_UPGRADE) {
            assertFalse(
                "Field '" + newField + "' should NOT exist in OLD cluster (will be added after upgrade)",
                properties.containsKey(newField)
            );
        }

        // Insert a test document with minimal fields
        String docBody = "{\"id\": \"" + TEST_DOC_ID + "\", \"name\": \"BWC Restart Test Document\"}";
        indexDocument(QUERY_SET_INDEX, TEST_DOC_ID, docBody);
        logger.info("Inserted test document in OLD cluster");

        // Verify document was inserted
        refreshIndex(QUERY_SET_INDEX);
        Map<String, Object> doc = getDocument(QUERY_SET_INDEX, TEST_DOC_ID);
        assertNotNull("Document should be retrievable", doc);
        assertEquals("Document name should match", "BWC Restart Test Document", doc.get("name"));
    }

    /**
     * UPGRADED cluster test: Validate that mappings were updated with new fields.
     * After full cluster restart with upgraded plugin, the cluster manager should
     * update all existing index mappings via the ClusterStateListener.
     */
    @SuppressWarnings("unchecked")
    private void testValidateMappingUpdatedInUpgradedCluster() throws Exception {
        // Verify the index still exists
        assertTrue("Index should still exist in UPGRADED cluster", checkIndexExists(QUERY_SET_INDEX));

        // Verify original document is still accessible (data compatibility)
        Map<String, Object> doc = getDocument(QUERY_SET_INDEX, TEST_DOC_ID);
        assertNotNull("Document from OLD cluster should be accessible in UPGRADED cluster", doc);
        assertEquals("Document name should still match after upgrade", "BWC Restart Test Document", doc.get("name"));

        // Wait for the mapping to be updated by the ClusterStateListener
        // The listener waits 5 seconds after becoming cluster manager before updating mappings
        // We retry for up to 30 seconds to allow for the update to complete
        Map<String, Object> properties = waitForMappingUpdate(QUERY_SET_INDEX, NEW_FIELDS_AFTER_UPGRADE, 30);
        assertNotNull("Mapping properties should exist after upgrade", properties);

        // Log the final mapping
        Map<String, Object> mapping = getIndexMapping(QUERY_SET_INDEX);
        logger.info("UPGRADED cluster mapping for {}: {}", QUERY_SET_INDEX, mapping);

        // Verify original fields still exist
        assertTrue("Mapping should still contain 'id' field", properties.containsKey("id"));
        assertTrue("Mapping should still contain 'name' field", properties.containsKey("name"));

        // CRITICAL: Verify NEW fields were ADDED by the mapping update
        for (String newField : NEW_FIELDS_AFTER_UPGRADE) {
            assertTrue(
                "Field '" + newField + "' should NOW exist after upgrade (added by mapping update listener)",
                properties.containsKey(newField)
            );
        }

        // Verify the nested structure of querySetQueries
        Map<String, Object> querySetQueriesMapping = (Map<String, Object>) properties.get("querySetQueries");
        assertNotNull("querySetQueries mapping should exist after upgrade", querySetQueriesMapping);
        assertEquals("querySetQueries should be nested type", "nested", querySetQueriesMapping.get("type"));

        // Verify we can write a NEW document using the updated mapping fields
        String newDocId = "bwc-restart-test-doc-upgraded";
        String newDocBody = "{"
            + "\"id\": \""
            + newDocId
            + "\","
            + "\"name\": \"Upgraded Restart Test Document\","
            + "\"description\": \"Document created after restart upgrade with new fields\","
            + "\"sampling\": \"random\","
            + "\"timestamp\": \"2024-01-01T00:00:00.000Z\""
            + "}";
        indexDocument(QUERY_SET_INDEX, newDocId, newDocBody);
        logger.info("Inserted new document with updated mapping fields in UPGRADED cluster");

        // Verify new document was inserted successfully
        refreshIndex(QUERY_SET_INDEX);
        Map<String, Object> newDoc = getDocument(QUERY_SET_INDEX, newDocId);
        assertNotNull("New document should be retrievable", newDoc);
        assertEquals("New document name should match", "Upgraded Restart Test Document", newDoc.get("name"));
        assertEquals(
            "New document description should match",
            "Document created after restart upgrade with new fields",
            newDoc.get("description")
        );
        assertEquals("New document sampling should match", "random", newDoc.get("sampling"));

        // Cleanup test documents
        deleteDocument(QUERY_SET_INDEX, TEST_DOC_ID);
        deleteDocument(QUERY_SET_INDEX, newDocId);
    }

    /**
     * Creates an index with the given mapping.
     */
    private void createIndexWithMapping(String indexName, String mapping) throws IOException {
        Request request = new Request("PUT", "/" + indexName);
        String body = "{\"mappings\": " + mapping + "}";
        request.setJsonEntity(body);
        client().performRequest(request);
    }

    /**
     * Checks if an index exists.
     */
    private boolean checkIndexExists(String indexName) throws IOException {
        try {
            Request request = new Request("HEAD", "/" + indexName);
            Response response = client().performRequest(request);
            return response.getStatusLine().getStatusCode() == 200;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Gets the mapping of an index.
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> getIndexMapping(String indexName) throws IOException, ParseException {
        Request request = new Request("GET", "/" + indexName + "/_mapping");
        Response response = client().performRequest(request);

        String responseBody = EntityUtils.toString(response.getEntity());
        try (
            XContentParser parser = JsonXContent.jsonXContent.createParser(
                NamedXContentRegistry.EMPTY,
                DeprecationHandler.IGNORE_DEPRECATIONS,
                responseBody
            )
        ) {
            Map<String, Object> responseMap = parser.map();
            Map<String, Object> indexMapping = (Map<String, Object>) responseMap.get(indexName);
            if (indexMapping != null) {
                return (Map<String, Object>) indexMapping.get("mappings");
            }
        }
        return null;
    }

    /**
     * Waits for the mapping to be updated with the expected new fields.
     * Retries for up to the specified timeout in seconds.
     *
     * @param indexName the index to check
     * @param expectedFields the fields that should exist in the mapping
     * @param timeoutSeconds maximum time to wait
     * @return the properties map from the mapping, or null if timeout
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> waitForMappingUpdate(String indexName, String[] expectedFields, int timeoutSeconds) throws Exception {
        long startTime = System.currentTimeMillis();
        long timeoutMillis = timeoutSeconds * 1000L;

        while (System.currentTimeMillis() - startTime < timeoutMillis) {
            Map<String, Object> mapping = getIndexMapping(indexName);
            if (mapping != null) {
                Map<String, Object> properties = (Map<String, Object>) mapping.get("properties");
                if (properties != null) {
                    // Check if all expected fields are present
                    boolean allFieldsPresent = true;
                    for (String field : expectedFields) {
                        if (!properties.containsKey(field)) {
                            allFieldsPresent = false;
                            break;
                        }
                    }
                    if (allFieldsPresent) {
                        logger.info("Mapping update detected - all expected fields are present");
                        return properties;
                    }
                }
            }

            logger.info("Waiting for mapping update... (elapsed: {}ms)", System.currentTimeMillis() - startTime);
            Thread.sleep(1000); // Check every second
        }

        // Return the current properties even if not all fields are present (for better error messages)
        Map<String, Object> mapping = getIndexMapping(indexName);
        if (mapping != null) {
            return (Map<String, Object>) mapping.get("properties");
        }
        return null;
    }

    /**
     * Indexes a document.
     */
    private void indexDocument(String indexName, String docId, String body) throws IOException {
        Request request = new Request("PUT", "/" + indexName + "/_doc/" + docId);
        request.setJsonEntity(body);
        client().performRequest(request);
    }

    /**
     * Gets a document by ID.
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> getDocument(String indexName, String docId) throws IOException, ParseException {
        try {
            Request request = new Request("GET", "/" + indexName + "/_doc/" + docId);
            Response response = client().performRequest(request);

            String responseBody = EntityUtils.toString(response.getEntity());
            try (
                XContentParser parser = JsonXContent.jsonXContent.createParser(
                    NamedXContentRegistry.EMPTY,
                    DeprecationHandler.IGNORE_DEPRECATIONS,
                    responseBody
                )
            ) {
                Map<String, Object> responseMap = parser.map();
                return (Map<String, Object>) responseMap.get("_source");
            }
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Deletes a document by ID.
     */
    private void deleteDocument(String indexName, String docId) throws IOException {
        try {
            Request request = new Request("DELETE", "/" + indexName + "/_doc/" + docId);
            client().performRequest(request);
        } catch (Exception e) {
            logger.warn("Failed to delete document: " + docId, e);
        }
    }

    /**
     * Refreshes an index to make documents searchable.
     */
    private void refreshIndex(String indexName) throws IOException {
        Request request = new Request("POST", "/" + indexName + "/_refresh");
        client().performRequest(request);
    }
}
