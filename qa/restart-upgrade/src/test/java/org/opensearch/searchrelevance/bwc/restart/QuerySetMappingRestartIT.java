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
 * BWC Integration Test for QuerySet index mapping updates during full cluster restart.
 * Exercises the actual plugin auto-migration path via the REST API.
 */
public class QuerySetMappingRestartIT extends AbstractSearchRelevanceRestartUpgradeTestCase {

    private static final String QUERYSET_INDEX = "search-relevance-queryset";
    private static final String QUERYSET_ENDPOINT = "/_plugins/_search_relevance/query_sets";
    private static final String OLD_MAPPING_RESOURCE = "mappings/queryset_v0.json";
    private static final String TEST_DOC_RESOURCE = "mappings/queryset_test_document.json";
    private static final String TEST_DOC_ID = "test-queryset-bwc";

    public void testQuerySetMappingUpdate_RestartUpgrade() throws Exception {
        switch (getClusterType()) {
            case OLD:
                testOldCluster();
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

        String testDoc = IndexMappingTestHelper.readMappingResource(TEST_DOC_RESOURCE);
        IndexMappingTestHelper.insertTestDocument(client(), QUERYSET_INDEX, TEST_DOC_ID, testDoc);

        logger.info("OLD cluster: QuerySet index ready with test document");
    }

    private void testUpgradedCluster() throws Exception {
        // Verify old data survives
        Map<String, Object> oldDoc = IndexMappingTestHelper.getDocument(client(), QUERYSET_INDEX, TEST_DOC_ID, logger);
        assertNotNull("Old document should survive restart upgrade", oldDoc);
        assertNull("Old document should not have status field", oldDoc.get("status"));

        // Create via plugin API — triggers auto-migration
        Request request = new Request("PUT", QUERYSET_ENDPOINT);
        request.setJsonEntity(
            "{"
                + "\"name\": \"bwc-restart-queryset\","
                + "\"description\": \"Created after restart to test auto-migration\","
                + "\"sampling\": \"manual\","
                + "\"querySetQueries\": [{\"queryText\": \"test query\"}]"
                + "}"
        );

        Response response = client().performRequest(request);
        assertEquals("Plugin API should succeed after auto-migration", 200, response.getStatusLine().getStatusCode());

        // Verify mapping updated
        Map<String, Object> mapping = IndexMappingTestHelper.getIndexMapping(client(), QUERYSET_INDEX);
        Map<String, Object> properties = IndexMappingTestHelper.getMappingProperties(mapping);
        assertTrue("Mapping should have status field", properties.containsKey("status"));
        assertTrue("Mapping should have type field", properties.containsKey("type"));
        assertTrue("Mapping should have numberOfQueryTerms field", properties.containsKey("numberOfQueryTerms"));

        Map<String, Object> meta = IndexMappingTestHelper.getMappingMeta(mapping);
        assertEquals("Schema version should be 1", 1, ((Number) meta.get("schema_version")).intValue());

        // Old data still accessible
        oldDoc = IndexMappingTestHelper.getDocument(client(), QUERYSET_INDEX, TEST_DOC_ID, logger);
        assertNotNull("Old document should survive auto-migration", oldDoc);
        assertEquals("manual", oldDoc.get("sampling"));

        logger.info("UPGRADED cluster: Plugin auto-migration succeeded after restart");
    }
}
