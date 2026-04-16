/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */
package org.opensearch.searchrelevance.bwc.rolling;

import java.util.Map;

import org.opensearch.client.Request;
import org.opensearch.client.Response;
import org.opensearch.searchrelevance.bwc.IndexMappingTestHelper;

/**
 * BWC Integration Test for search_configuration index mapping updates during rolling upgrade.
 *
 * This test validates that the updateMappingSync fix (CountDownLatch) works correctly
 * by exercising the actual plugin auto-migration path — creating a search configuration
 * via the plugin REST API in the UPGRADED cluster, which triggers createIndexIfAbsentSync
 * → updateMappingSync → document write. If the mapping update doesn't complete before
 * the document write, the description field would be auto-mapped incorrectly.
 *
 * Test scenario:
 * 1. OLD cluster: Verify search_configuration index exists (created by plugin on startup)
 *    and insert a test document with old schema (no description field)
 * 2. MIXED cluster: Validate old data is still accessible
 * 3. UPGRADED cluster: Create a new search configuration via plugin API — this triggers
 *    the auto-migration from schema_version 0 to 1, proving the CountDownLatch fix works
 */
public class SearchConfigMappingBWCIT extends AbstractSearchRelevanceRollingUpgradeTestCase {

    private static final String SEARCH_CONFIG_INDEX = "search-relevance-search-config";
    private static final String SEARCH_CONFIG_ENDPOINT = "/_plugins/_search_relevance/search_configurations";
    private static final String OLD_MAPPING_RESOURCE = "mappings/search_configuration_v0.json";
    private static final String TEST_DOC_RESOURCE = "mappings/search_configuration_test_document.json";
    private static final String TEST_DOC_ID = "test-search-config-bwc";

    public void testSearchConfigMappingUpdate_RollingUpgrade() throws Exception {
        switch (getClusterType()) {
            case OLD:
                testOldCluster();
                break;
            case MIXED:
                testMixedCluster();
                break;
            case UPGRADED:
                try {
                    testUpgradedCluster();
                } finally {
                    wipeOfTestResources(SEARCH_CONFIG_INDEX);
                }
                break;
            default:
                throw new IllegalStateException("Unknown cluster type: " + getClusterType());
        }
    }

    /**
     * OLD cluster: The plugin auto-creates the index on startup.
     * Insert a test document without the description field.
     */
    private void testOldCluster() throws Exception {
        // Index may already exist from plugin startup; if not, create with old mapping
        if (!IndexMappingTestHelper.checkIndexExists(client(), SEARCH_CONFIG_INDEX, logger)) {
            String oldMapping = IndexMappingTestHelper.readMappingResource(OLD_MAPPING_RESOURCE);
            IndexMappingTestHelper.createIndexWithMapping(client(), SEARCH_CONFIG_INDEX, oldMapping, logger);
        }

        assertTrue(
            "search_configuration index should exist",
            IndexMappingTestHelper.checkIndexExists(client(), SEARCH_CONFIG_INDEX, logger)
        );

        // Insert a document without description (old format)
        String testDoc = IndexMappingTestHelper.readMappingResource(TEST_DOC_RESOURCE);
        IndexMappingTestHelper.insertTestDocument(client(), SEARCH_CONFIG_INDEX, TEST_DOC_ID, testDoc);

        logger.info("OLD cluster: search_configuration index ready with test document");
    }

    /**
     * MIXED cluster: Verify old data is still accessible.
     */
    private void testMixedCluster() throws Exception {
        assertTrue(
            "search_configuration index should exist in MIXED cluster",
            IndexMappingTestHelper.checkIndexExists(client(), SEARCH_CONFIG_INDEX, logger)
        );

        Map<String, Object> doc = IndexMappingTestHelper.getDocument(client(), SEARCH_CONFIG_INDEX, TEST_DOC_ID, logger);
        assertNotNull("Test document should be accessible in MIXED cluster", doc);
        assertEquals("bwc-test-config", doc.get("name"));

        logger.info("MIXED cluster: search_configuration data accessible");
    }

    /**
     * UPGRADED cluster: Create a search configuration via the plugin REST API.
     * This exercises the real auto-migration path:
     *   createIndexIfAbsentSync → detects version 0 < 1 → updateMappingSync (with CountDownLatch)
     *   → document write with description field
     *
     * If updateMappingSync doesn't wait for completion, the description field could be
     * auto-mapped as a different type, or the mapping update could conflict.
     */
    private void testUpgradedCluster() throws Exception {
        assertTrue(
            "search_configuration index should exist after upgrade",
            IndexMappingTestHelper.checkIndexExists(client(), SEARCH_CONFIG_INDEX, logger)
        );

        // Verify old data survives
        Map<String, Object> oldDoc = IndexMappingTestHelper.getDocument(client(), SEARCH_CONFIG_INDEX, TEST_DOC_ID, logger);
        assertNotNull("Old document should survive upgrade", oldDoc);

        // Create a new search configuration via the plugin API.
        // This triggers the actual auto-migration: updateMappingSync adds description field
        // to the mapping, then the document is written with description.
        Request request = new Request("PUT", SEARCH_CONFIG_ENDPOINT);
        request.setJsonEntity(
            "{"
                + "\"name\": \"bwc-upgraded-config\","
                + "\"description\": \"Created after upgrade to test auto-migration\","
                + "\"index\": \"test-index\","
                + "\"query\": \"{\\\"match_all\\\": {}}\""
                + "}"
        );

        Response response = client().performRequest(request);
        assertEquals("Plugin API should succeed after auto-migration", 200, response.getStatusLine().getStatusCode());

        Map<String, Object> responseMap = IndexMappingTestHelper.parseResponse(response);
        String newConfigId = (String) responseMap.get("search_configuration_id");
        assertNotNull("Should get a search_configuration_id back", newConfigId);

        // Verify the mapping now has the description field with correct type
        Map<String, Object> mapping = IndexMappingTestHelper.getIndexMapping(client(), SEARCH_CONFIG_INDEX);
        Map<String, Object> properties = IndexMappingTestHelper.getMappingProperties(mapping);
        assertTrue("Mapping should have description field after auto-migration", properties.containsKey("description"));

        // Verify schema_version was updated to 1
        Map<String, Object> meta = IndexMappingTestHelper.getMappingMeta(mapping);
        assertNotNull("Mapping should have _meta", meta);
        assertEquals("Schema version should be 1 after auto-migration", 1, ((Number) meta.get("schema_version")).intValue());

        // Verify old data still accessible
        oldDoc = IndexMappingTestHelper.getDocument(client(), SEARCH_CONFIG_INDEX, TEST_DOC_ID, logger);
        assertNotNull("Old document should still be accessible after auto-migration", oldDoc);

        logger.info("UPGRADED cluster: Plugin auto-migration succeeded, description field added, old data preserved");
    }
}
