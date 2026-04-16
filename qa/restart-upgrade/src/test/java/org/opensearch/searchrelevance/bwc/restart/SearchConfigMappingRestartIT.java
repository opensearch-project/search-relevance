/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */
package org.opensearch.searchrelevance.bwc.restart;

import java.util.Map;

import org.opensearch.client.Request;
import org.opensearch.client.Response;
import org.opensearch.searchrelevance.bwc.IndexMappingTestHelper;

/**
 * BWC Integration Test for search_configuration mapping update during full cluster restart.
 * Exercises the actual plugin auto-migration path via the REST API.
 */
public class SearchConfigMappingRestartIT extends AbstractSearchRelevanceRestartUpgradeTestCase {

    private static final String SEARCH_CONFIG_INDEX = "search-relevance-search-config";
    private static final String SEARCH_CONFIG_ENDPOINT = "/_plugins/_search_relevance/search_configurations";
    private static final String OLD_MAPPING_RESOURCE = "mappings/search_configuration_v0.json";
    private static final String TEST_DOC_RESOURCE = "mappings/search_configuration_test_document.json";
    private static final String TEST_DOC_ID = "test-search-config-bwc";

    public void testSearchConfigMappingUpdate_RestartUpgrade() throws Exception {
        switch (getClusterType()) {
            case OLD:
                testOldCluster();
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

    private void testOldCluster() throws Exception {
        if (!IndexMappingTestHelper.checkIndexExists(client(), SEARCH_CONFIG_INDEX, logger)) {
            String oldMapping = IndexMappingTestHelper.readMappingResource(OLD_MAPPING_RESOURCE);
            IndexMappingTestHelper.createIndexWithMapping(client(), SEARCH_CONFIG_INDEX, oldMapping, logger);
        }

        String testDoc = IndexMappingTestHelper.readMappingResource(TEST_DOC_RESOURCE);
        IndexMappingTestHelper.insertTestDocument(client(), SEARCH_CONFIG_INDEX, TEST_DOC_ID, testDoc);

        logger.info("OLD cluster: search_configuration index ready with test document");
    }

    private void testUpgradedCluster() throws Exception {
        // Verify old data survives
        Map<String, Object> oldDoc = IndexMappingTestHelper.getDocument(client(), SEARCH_CONFIG_INDEX, TEST_DOC_ID, logger);
        assertNotNull("Old document should survive restart upgrade", oldDoc);

        // Create via plugin API — triggers auto-migration
        Request request = new Request("PUT", SEARCH_CONFIG_ENDPOINT);
        request.setJsonEntity(
            "{"
                + "\"name\": \"bwc-restart-config\","
                + "\"description\": \"Created after restart to test auto-migration\","
                + "\"index\": \"test-index\","
                + "\"query\": \"{\\\"match_all\\\": {}}\""
                + "}"
        );

        Response response = client().performRequest(request);
        assertEquals("Plugin API should succeed after auto-migration", 200, response.getStatusLine().getStatusCode());

        // Verify mapping updated
        Map<String, Object> mapping = IndexMappingTestHelper.getIndexMapping(client(), SEARCH_CONFIG_INDEX);
        Map<String, Object> properties = IndexMappingTestHelper.getMappingProperties(mapping);
        assertTrue("Mapping should have description field", properties.containsKey("description"));

        Map<String, Object> meta = IndexMappingTestHelper.getMappingMeta(mapping);
        assertEquals("Schema version should be 1", 1, ((Number) meta.get("schema_version")).intValue());

        // Old data still accessible
        oldDoc = IndexMappingTestHelper.getDocument(client(), SEARCH_CONFIG_INDEX, TEST_DOC_ID, logger);
        assertNotNull("Old document should still be accessible", oldDoc);

        logger.info("UPGRADED cluster: Plugin auto-migration succeeded after restart");
    }
}
