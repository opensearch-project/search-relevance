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
 * BWC Integration Test for QuerySet index mapping updates during rolling upgrade.
 *
 * Exercises the actual plugin auto-migration path by creating a query set via the
 * plugin REST API in the UPGRADED cluster, which triggers createIndexIfAbsentSync
 * → updateMappingSync → document write.
 *
 * Test scenario:
 * 1. OLD cluster: Verify queryset index exists, insert old-format test document
 * 2. MIXED cluster: Validate old data is still accessible
 * 3. UPGRADED cluster: Create query set via plugin API → triggers auto-migration
 *    from schema_version 0 to 1 → verifies new fields in mapping, old data preserved
 */
public class QuerySetMappingBWCIT extends AbstractSearchRelevanceRollingUpgradeTestCase {

    private static final String QUERYSET_INDEX = "search-relevance-queryset";
    private static final String QUERYSET_ENDPOINT = "/_plugins/_search_relevance/query_sets";
    private static final String OLD_MAPPING_RESOURCE = "mappings/queryset_v0.json";
    private static final String TEST_DOC_RESOURCE = "mappings/queryset_test_document.json";
    private static final String TEST_DOC_ID = "test-queryset-bwc";

    public void testQuerySetMappingUpdate_RollingUpgrade() throws Exception {
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
                    wipeOfTestResources(QUERYSET_INDEX);
                }
                break;
            default:
                throw new IllegalStateException("Unknown cluster type: " + getClusterType());
        }
    }

    private void testOldCluster() throws Exception {
        if (!IndexMappingTestHelper.checkIndexExists(client(), QUERYSET_INDEX, logger)) {
            String oldMapping = IndexMappingTestHelper.readMappingResource(OLD_MAPPING_RESOURCE);
            IndexMappingTestHelper.createIndexWithMapping(client(), QUERYSET_INDEX, oldMapping, logger);
        }

        assertTrue("QuerySet index should exist", IndexMappingTestHelper.checkIndexExists(client(), QUERYSET_INDEX, logger));

        String testDoc = IndexMappingTestHelper.readMappingResource(TEST_DOC_RESOURCE);
        IndexMappingTestHelper.insertTestDocument(client(), QUERYSET_INDEX, TEST_DOC_ID, testDoc);

        logger.info("OLD cluster: QuerySet index ready with test document");
    }

    private void testMixedCluster() throws Exception {
        assertTrue(
            "QuerySet index should exist in MIXED cluster",
            IndexMappingTestHelper.checkIndexExists(client(), QUERYSET_INDEX, logger)
        );

        Map<String, Object> doc = IndexMappingTestHelper.getDocument(client(), QUERYSET_INDEX, TEST_DOC_ID, logger);
        assertNotNull("Test document should be accessible in MIXED cluster", doc);
        assertEquals("manual", doc.get("sampling"));
        assertEquals("bwc-test-queryset", doc.get("name"));

        logger.info("MIXED cluster: QuerySet data accessible");
    }

    /**
     * UPGRADED cluster: Create a query set via the plugin REST API.
     * This exercises the real auto-migration path:
     *   createIndexIfAbsentSync → detects version 0 < 1 → updateMappingSync
     *   → document write with status/type/numberOfQueryTerms fields
     */
    private void testUpgradedCluster() throws Exception {
        assertTrue("QuerySet index should exist after upgrade", IndexMappingTestHelper.checkIndexExists(client(), QUERYSET_INDEX, logger));

        // Verify old data survives
        Map<String, Object> oldDoc = IndexMappingTestHelper.getDocument(client(), QUERYSET_INDEX, TEST_DOC_ID, logger);
        assertNotNull("Old document should survive upgrade", oldDoc);
        assertNull("Old document should not have status field", oldDoc.get("status"));

        // Create a new query set via plugin API — triggers auto-migration
        Request request = new Request("PUT", QUERYSET_ENDPOINT);
        request.setJsonEntity(
            "{"
                + "\"name\": \"bwc-upgraded-queryset\","
                + "\"description\": \"Created after upgrade to test auto-migration\","
                + "\"sampling\": \"manual\","
                + "\"querySetQueries\": [{\"queryText\": \"test query\"}]"
                + "}"
        );

        Response response = client().performRequest(request);
        assertEquals("Plugin API should succeed after auto-migration", 200, response.getStatusLine().getStatusCode());

        // Verify mapping now has new fields
        Map<String, Object> mapping = IndexMappingTestHelper.getIndexMapping(client(), QUERYSET_INDEX);
        Map<String, Object> properties = IndexMappingTestHelper.getMappingProperties(mapping);
        assertTrue("Mapping should have status field", properties.containsKey("status"));
        assertTrue("Mapping should have type field", properties.containsKey("type"));
        assertTrue("Mapping should have numberOfQueryTerms field", properties.containsKey("numberOfQueryTerms"));

        // Verify schema_version updated
        Map<String, Object> meta = IndexMappingTestHelper.getMappingMeta(mapping);
        assertEquals("Schema version should be 1", 1, ((Number) meta.get("schema_version")).intValue());

        // Old data still accessible
        oldDoc = IndexMappingTestHelper.getDocument(client(), QUERYSET_INDEX, TEST_DOC_ID, logger);
        assertNotNull("Old document should survive auto-migration", oldDoc);
        assertEquals("manual", oldDoc.get("sampling"));

        logger.info("UPGRADED cluster: Plugin auto-migration succeeded, new fields added, old data preserved");
    }
}
