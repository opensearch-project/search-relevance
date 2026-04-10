/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */
package org.opensearch.searchrelevance.bwc.rolling;

import java.util.Map;

import org.opensearch.searchrelevance.bwc.IndexMappingTestHelper;

/**
 * BWC Integration Test for QuerySet index mapping updates during rolling upgrade.
 *
 * Test scenario:
 * 1. OLD cluster: Create queryset index with old schema (version 0, no status/type/numberOfQueryTerms)
 * 2. MIXED cluster: Validate index still accessible, old data preserved
 * 3. UPGRADED cluster: Verify mapping can be updated with new fields and old data survives
 */
public class QuerySetMappingBWCIT extends AbstractSearchRelevanceRollingUpgradeTestCase {

    private static final String QUERYSET_INDEX = "search-relevance-queryset";
    private static final String OLD_MAPPING_RESOURCE = "mappings/queryset_v0.json";
    private static final String NEW_MAPPING_RESOURCE = "mappings/queryset_v1.json";
    private static final String TEST_DOC_RESOURCE = "mappings/queryset_test_document.json";
    private static final String TEST_DOC_ID = "test-queryset-bwc";

    public void testQuerySetMappingUpdate_RollingUpgrade() throws Exception {
        switch (getClusterType()) {
            case OLD:
                testCreateQuerySetIndexInOldCluster();
                break;
            case MIXED:
                testValidateQuerySetInMixedCluster();
                break;
            case UPGRADED:
                try {
                    testValidateQuerySetMappingInUpgradedCluster();
                } finally {
                    wipeOfTestResources(QUERYSET_INDEX);
                }
                break;
            default:
                throw new IllegalStateException("Unknown cluster type: " + getClusterType());
        }
    }

    private void testCreateQuerySetIndexInOldCluster() throws Exception {
        String oldMapping = IndexMappingTestHelper.readMappingResource(OLD_MAPPING_RESOURCE);
        IndexMappingTestHelper.createIndexWithMapping(client(), QUERYSET_INDEX, oldMapping, logger);

        assertTrue("QuerySet index should exist", IndexMappingTestHelper.checkIndexExists(client(), QUERYSET_INDEX, logger));

        Map<String, Object> mapping = IndexMappingTestHelper.getIndexMapping(client(), QUERYSET_INDEX);
        Map<String, Object> properties = IndexMappingTestHelper.getMappingProperties(mapping);
        assertNotNull("Properties should exist", properties);
        assertFalse("Old schema should NOT have status", properties.containsKey("status"));
        assertFalse("Old schema should NOT have type", properties.containsKey("type"));
        assertFalse("Old schema should NOT have numberOfQueryTerms", properties.containsKey("numberOfQueryTerms"));

        Map<String, Object> meta = IndexMappingTestHelper.getMappingMeta(mapping);
        assertEquals("Schema version should be 0", 0, ((Number) meta.get("schema_version")).intValue());

        String testDoc = IndexMappingTestHelper.readMappingResource(TEST_DOC_RESOURCE);
        IndexMappingTestHelper.insertTestDocument(client(), QUERYSET_INDEX, TEST_DOC_ID, testDoc);

        logger.info("OLD cluster: Created queryset index with schema_version=0");
    }

    private void testValidateQuerySetInMixedCluster() throws Exception {
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

    private void testValidateQuerySetMappingInUpgradedCluster() throws Exception {
        assertTrue("QuerySet index should exist after upgrade", IndexMappingTestHelper.checkIndexExists(client(), QUERYSET_INDEX, logger));

        // Verify old data is still accessible without new fields
        Map<String, Object> doc = IndexMappingTestHelper.getDocument(client(), QUERYSET_INDEX, TEST_DOC_ID, logger);
        assertNotNull("Old document should still be accessible", doc);
        assertEquals("manual", doc.get("sampling"));
        assertNull("Old document should not have status field", doc.get("status"));
        assertNull("Old document should not have type field", doc.get("type"));

        // Update mapping to add new fields
        String newMapping = IndexMappingTestHelper.readMappingResource(NEW_MAPPING_RESOURCE);
        IndexMappingTestHelper.updateMapping(client(), QUERYSET_INDEX, newMapping, logger);

        IndexMappingTestHelper.waitForMappingUpdate(
            client(),
            QUERYSET_INDEX,
            new String[] { "status", "type", "numberOfQueryTerms" },
            30,
            logger
        );

        Map<String, Object> mapping = IndexMappingTestHelper.getIndexMapping(client(), QUERYSET_INDEX);
        Map<String, Object> properties = IndexMappingTestHelper.getMappingProperties(mapping);
        assertTrue("Mapping should have status field", properties.containsKey("status"));
        assertTrue("Mapping should have type field", properties.containsKey("type"));
        assertTrue("Mapping should have numberOfQueryTerms field", properties.containsKey("numberOfQueryTerms"));

        Map<String, Object> meta = IndexMappingTestHelper.getMappingMeta(mapping);
        assertEquals("Schema version should be 1", 1, ((Number) meta.get("schema_version")).intValue());

        // Verify old data still accessible after mapping update
        doc = IndexMappingTestHelper.getDocument(client(), QUERYSET_INDEX, TEST_DOC_ID, logger);
        assertNotNull("Old document should survive mapping update", doc);
        assertEquals("manual", doc.get("sampling"));

        logger.info("UPGRADED cluster: QuerySet mapping updated to schema_version=1, old data preserved");
    }
}
