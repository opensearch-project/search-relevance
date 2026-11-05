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
 * BWC Integration Test for experiment mapping update during rolling upgrade.
 *
 * Validates the schema_version 1 → 2 bump that adds experiment-level {@code metrics}:
 * 1. OLD: Create experiment with schema_version 1 (no metrics) and insert a legacy document
 * 2. MIXED: Confirm the legacy document remains readable
 * 3. UPGRADED: Apply the v2 mapping (simulating createIndexIfAbsent on the next write),
 *    verify metrics is mapped as nested, schema_version is 2, and the pre-upgrade document
 *    still loads without metrics
 */
public class ExperimentMappingBWCIT extends AbstractSearchRelevanceRollingUpgradeTestCase {

    private static final String EXPERIMENT_INDEX = ".plugins-search-relevance-experiment";
    private static final String OLD_MAPPING_RESOURCE = "mappings/experiment_v1.json";
    private static final String NEW_MAPPING_RESOURCE = "mappings/experiment_v2.json";
    private static final String TEST_DOC_RESOURCE = "mappings/experiment_test_document.json";
    private static final String TEST_DOC_ID = "test-experiment-bwc";

    public void testExperimentMappingUpdate_RollingUpgrade() throws Exception {
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
                    wipeOfTestResources(EXPERIMENT_INDEX);
                }
                break;
            default:
                throw new IllegalStateException("Unknown cluster type: " + getClusterType());
        }
    }

    private void testOldCluster() throws Exception {
        if (!IndexMappingTestHelper.checkIndexExists(client(), EXPERIMENT_INDEX, logger)) {
            String oldMapping = IndexMappingTestHelper.readMappingResource(OLD_MAPPING_RESOURCE);
            IndexMappingTestHelper.createIndexWithMapping(client(), EXPERIMENT_INDEX, oldMapping, logger);
        }

        assertTrue("experiment index should exist", IndexMappingTestHelper.checkIndexExists(client(), EXPERIMENT_INDEX, logger));

        Map<String, Object> mapping = IndexMappingTestHelper.getIndexMapping(client(), EXPERIMENT_INDEX);
        Map<String, Object> properties = IndexMappingTestHelper.getMappingProperties(mapping);
        assertNotNull("Properties should exist", properties);
        assertFalse("Old schema should NOT have metrics", properties.containsKey("metrics"));

        Map<String, Object> meta = IndexMappingTestHelper.getMappingMeta(mapping);
        assertNotNull("_meta should exist", meta);
        assertEquals("Schema version should be 1 in OLD cluster", 1, ((Number) meta.get("schema_version")).intValue());

        String testDoc = IndexMappingTestHelper.readMappingResource(TEST_DOC_RESOURCE);
        IndexMappingTestHelper.insertTestDocument(client(), EXPERIMENT_INDEX, TEST_DOC_ID, testDoc);

        logger.info("OLD cluster: experiment index ready with schema_version=1 and legacy document");
    }

    private void testMixedCluster() throws Exception {
        assertTrue(
            "experiment index should exist in MIXED cluster",
            IndexMappingTestHelper.checkIndexExists(client(), EXPERIMENT_INDEX, logger)
        );

        Map<String, Object> doc = IndexMappingTestHelper.getDocument(client(), EXPERIMENT_INDEX, TEST_DOC_ID, logger);
        assertNotNull("Legacy experiment document should be accessible in MIXED cluster", doc);
        assertEquals("PAIRWISE_COMPARISON", doc.get("type"));
        assertFalse("Pre-upgrade experiment documents omit metrics", doc.containsKey("metrics"));

        Map<String, Object> mapping = IndexMappingTestHelper.getIndexMapping(client(), EXPERIMENT_INDEX);
        Map<String, Object> meta = IndexMappingTestHelper.getMappingMeta(mapping);
        Map<String, Object> properties = IndexMappingTestHelper.getMappingProperties(mapping);
        int currentVersion = meta != null ? ((Number) meta.get("schema_version")).intValue() : -1;
        boolean hasMetrics = properties != null && properties.containsKey("metrics");
        logger.info("MIXED cluster: schema_version={}, hasMetrics={}", currentVersion, hasMetrics);
    }

    private void testUpgradedCluster() throws Exception {
        assertTrue(
            "experiment index should exist after upgrade",
            IndexMappingTestHelper.checkIndexExists(client(), EXPERIMENT_INDEX, logger)
        );

        Map<String, Object> oldDoc = IndexMappingTestHelper.getDocument(client(), EXPERIMENT_INDEX, TEST_DOC_ID, logger);
        assertNotNull("Legacy experiment document should survive upgrade", oldDoc);
        assertFalse("Pre-upgrade experiment documents omit metrics", oldDoc.containsKey("metrics"));

        String newMapping = IndexMappingTestHelper.readMappingResource(NEW_MAPPING_RESOURCE);
        IndexMappingTestHelper.updateMapping(client(), EXPERIMENT_INDEX, newMapping, logger);
        IndexMappingTestHelper.waitForMappingUpdate(client(), EXPERIMENT_INDEX, new String[] { "metrics" }, 30, logger);

        Map<String, Object> mapping = IndexMappingTestHelper.getIndexMapping(client(), EXPERIMENT_INDEX);
        Map<String, Object> properties = IndexMappingTestHelper.getMappingProperties(mapping);
        assertNotNull("Properties should exist after upgrade", properties);
        assertTrue("Mapping should have metrics after upgrade", properties.containsKey("metrics"));
        @SuppressWarnings("unchecked")
        Map<String, Object> metrics = (Map<String, Object>) properties.get("metrics");
        assertEquals("metrics should be mapped as nested", "nested", metrics.get("type"));

        Map<String, Object> meta = IndexMappingTestHelper.getMappingMeta(mapping);
        assertNotNull("Mapping should have _meta", meta);
        assertEquals("Schema version should be 2 after upgrade", 2, ((Number) meta.get("schema_version")).intValue());

        oldDoc = IndexMappingTestHelper.getDocument(client(), EXPERIMENT_INDEX, TEST_DOC_ID, logger);
        assertNotNull("Legacy experiment document should still be accessible after mapping update", oldDoc);
        assertEquals("legacy-queryset", oldDoc.get("querySetId"));
        assertFalse("Pre-upgrade experiment documents still omit metrics after mapping update", oldDoc.containsKey("metrics"));

        logger.info("UPGRADED cluster: experiment mapping has metrics, schema_version=2, legacy docs preserved");
    }
}
