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
 * BWC Integration Test for evaluation_result mapping update during rolling upgrade.
 *
 * Validates the schema_version 0 → 1 bump that adds first-class {@code took}:
 * 1. OLD: Create evaluation_result with schema_version 0 (no took) and insert a legacy document
 * 2. MIXED: Confirm the legacy document remains readable
 * 3. UPGRADED: Apply the v1 mapping (simulating createIndexIfAbsent on the next write),
 *    verify took is mapped as long, schema_version is 1, and the pre-upgrade document still
 *    loads without took
 *
 * Automatic migration is triggered when experiment writes call createIndexIfAbsent. That path
 * needs a full experiment, so this test updates the mapping directly the same way
 * {@link IndexMappingBWCIT} does for judgment_cache.
 */
public class EvaluationResultMappingBWCIT extends AbstractSearchRelevanceRollingUpgradeTestCase {

    private static final String EVALUATION_RESULT_INDEX = "search-relevance-evaluation-result";
    private static final String OLD_MAPPING_RESOURCE = "mappings/evaluation_result_v0.json";
    private static final String NEW_MAPPING_RESOURCE = "mappings/evaluation_result_v1.json";
    private static final String TEST_DOC_RESOURCE = "mappings/evaluation_result_test_document.json";
    private static final String TEST_DOC_ID = "test-evaluation-result-bwc";

    public void testEvaluationResultMappingUpdate_RollingUpgrade() throws Exception {
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
                    wipeOfTestResources(EVALUATION_RESULT_INDEX);
                }
                break;
            default:
                throw new IllegalStateException("Unknown cluster type: " + getClusterType());
        }
    }

    private void testOldCluster() throws Exception {
        if (!IndexMappingTestHelper.checkIndexExists(client(), EVALUATION_RESULT_INDEX, logger)) {
            String oldMapping = IndexMappingTestHelper.readMappingResource(OLD_MAPPING_RESOURCE);
            IndexMappingTestHelper.createIndexWithMapping(client(), EVALUATION_RESULT_INDEX, oldMapping, logger);
        }

        assertTrue(
            "evaluation_result index should exist",
            IndexMappingTestHelper.checkIndexExists(client(), EVALUATION_RESULT_INDEX, logger)
        );

        Map<String, Object> mapping = IndexMappingTestHelper.getIndexMapping(client(), EVALUATION_RESULT_INDEX);
        Map<String, Object> properties = IndexMappingTestHelper.getMappingProperties(mapping);
        assertNotNull("Properties should exist", properties);
        assertFalse("Old schema should NOT have took", properties.containsKey("took"));

        Map<String, Object> meta = IndexMappingTestHelper.getMappingMeta(mapping);
        assertNotNull("_meta should exist", meta);
        assertEquals("Schema version should be 0 in OLD cluster", 0, ((Number) meta.get("schema_version")).intValue());

        String testDoc = IndexMappingTestHelper.readMappingResource(TEST_DOC_RESOURCE);
        IndexMappingTestHelper.insertTestDocument(client(), EVALUATION_RESULT_INDEX, TEST_DOC_ID, testDoc);

        logger.info("OLD cluster: evaluation_result index ready with schema_version=0 and legacy document");
    }

    private void testMixedCluster() throws Exception {
        assertTrue(
            "evaluation_result index should exist in MIXED cluster",
            IndexMappingTestHelper.checkIndexExists(client(), EVALUATION_RESULT_INDEX, logger)
        );

        Map<String, Object> doc = IndexMappingTestHelper.getDocument(client(), EVALUATION_RESULT_INDEX, TEST_DOC_ID, logger);
        assertNotNull("Legacy evaluation document should be accessible in MIXED cluster", doc);
        assertEquals("legacy query", doc.get("searchText"));
        assertFalse("Pre-upgrade evaluation documents omit took", doc.containsKey("took"));

        Map<String, Object> mapping = IndexMappingTestHelper.getIndexMapping(client(), EVALUATION_RESULT_INDEX);
        Map<String, Object> meta = IndexMappingTestHelper.getMappingMeta(mapping);
        Map<String, Object> properties = IndexMappingTestHelper.getMappingProperties(mapping);
        int currentVersion = meta != null ? ((Number) meta.get("schema_version")).intValue() : -1;
        boolean hasTook = properties != null && properties.containsKey("took");
        logger.info("MIXED cluster: schema_version={}, hasTook={}", currentVersion, hasTook);
    }

    private void testUpgradedCluster() throws Exception {
        assertTrue(
            "evaluation_result index should exist after upgrade",
            IndexMappingTestHelper.checkIndexExists(client(), EVALUATION_RESULT_INDEX, logger)
        );

        Map<String, Object> oldDoc = IndexMappingTestHelper.getDocument(client(), EVALUATION_RESULT_INDEX, TEST_DOC_ID, logger);
        assertNotNull("Legacy evaluation document should survive upgrade", oldDoc);
        assertFalse("Pre-upgrade evaluation documents omit took", oldDoc.containsKey("took"));

        // Simulate createIndexIfAbsent mapping upgrade (schema_version 0 → 1 adds took).
        String newMapping = IndexMappingTestHelper.readMappingResource(NEW_MAPPING_RESOURCE);
        IndexMappingTestHelper.updateMapping(client(), EVALUATION_RESULT_INDEX, newMapping, logger);
        IndexMappingTestHelper.waitForMappingUpdate(client(), EVALUATION_RESULT_INDEX, new String[] { "took" }, 30, logger);

        Map<String, Object> mapping = IndexMappingTestHelper.getIndexMapping(client(), EVALUATION_RESULT_INDEX);
        Map<String, Object> properties = IndexMappingTestHelper.getMappingProperties(mapping);
        assertNotNull("Properties should exist after upgrade", properties);
        assertTrue("Mapping should have took after upgrade", properties.containsKey("took"));
        @SuppressWarnings("unchecked")
        Map<String, Object> took = (Map<String, Object>) properties.get("took");
        assertEquals("took should be mapped as long", "long", took.get("type"));

        Map<String, Object> meta = IndexMappingTestHelper.getMappingMeta(mapping);
        assertNotNull("Mapping should have _meta", meta);
        assertEquals("Schema version should be 1 after upgrade", 1, ((Number) meta.get("schema_version")).intValue());

        oldDoc = IndexMappingTestHelper.getDocument(client(), EVALUATION_RESULT_INDEX, TEST_DOC_ID, logger);
        assertNotNull("Legacy evaluation document should still be accessible after mapping update", oldDoc);
        assertEquals("legacy query", oldDoc.get("searchText"));
        assertFalse("Pre-upgrade evaluation documents still omit took after mapping update", oldDoc.containsKey("took"));

        logger.info("UPGRADED cluster: evaluation_result mapping has took, schema_version=1, legacy docs preserved");
    }
}
