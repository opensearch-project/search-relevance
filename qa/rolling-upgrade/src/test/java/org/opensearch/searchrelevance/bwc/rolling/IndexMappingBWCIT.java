/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */
package org.opensearch.searchrelevance.bwc.rolling;

import static org.opensearch.searchrelevance.bwc.IndexMappingTestHelper.JUDGMENT_CACHE_INDEX;
import static org.opensearch.searchrelevance.bwc.IndexMappingTestHelper.TEST_DOC_ID;

import java.util.Map;

import org.opensearch.searchrelevance.bwc.IndexMappingTestHelper;

/**
 * BWC (Backward Compatibility) Integration Test for Index Mapping Updates.
 *
 * This test validates that index mappings are automatically updated when the plugin version
 * is upgraded and the schema_version in the mapping JSON has been bumped.
 *
 * Test scenario:
 * 1. OLD cluster: Manually create judgment_cache index with old schema (version 0, no modelId/encodedPromptTemplate)
 * 2. MIXED cluster: Validate index still accessible, check if mapping updated
 * 3. UPGRADED cluster: Verify mapping has new fields (modelId, encodedPromptTemplate) and schema_version=1
 */
public class IndexMappingBWCIT extends AbstractSearchRelevanceRollingUpgradeTestCase {

    /**
     * Main BWC test for Index Mapping Updates during rolling upgrade.
     * Tests that:
     * - OLD: Index is created with old mapping (schema_version 0, no modelId/encodedPromptTemplate)
     * - MIXED: Mapping update may happen when upgraded node becomes cluster manager
     * - UPGRADED: All nodes have updated mapping with new fields and schema_version=1
     */
    public void testIndexMappingUpdate_RollingUpgrade() throws Exception {
        switch (getClusterType()) {
            case OLD:
                testCreateIndexWithOldMappingInOldCluster();
                break;
            case MIXED:
                testValidateIndexInMixedCluster();
                break;
            case UPGRADED:
                try {
                    testValidateMappingUpdateInUpgradedCluster();
                } finally {
                    wipeOfTestResources(JUDGMENT_CACHE_INDEX);
                }
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
        String oldMapping = IndexMappingTestHelper.getOldMapping();
        IndexMappingTestHelper.createIndexWithMapping(client(), JUDGMENT_CACHE_INDEX, oldMapping, logger);

        // Verify the index was created
        assertTrue("Judgment cache index should exist", IndexMappingTestHelper.checkIndexExists(client(), JUDGMENT_CACHE_INDEX, logger));

        // Verify the mapping has old schema
        Map<String, Object> mapping = IndexMappingTestHelper.getIndexMapping(client(), JUDGMENT_CACHE_INDEX);
        assertNotNull("Mapping should exist", mapping);

        Map<String, Object> properties = IndexMappingTestHelper.getMappingProperties(mapping);
        assertNotNull("Properties should exist", properties);

        // Verify old schema doesn't have the new fields
        assertFalse("Old schema should NOT have modelId", properties.containsKey("modelId"));
        assertFalse("Old schema should NOT have encodedPromptTemplate", properties.containsKey("encodedPromptTemplate"));

        // Verify schema version is 0
        Map<String, Object> meta = IndexMappingTestHelper.getMappingMeta(mapping);
        assertNotNull("_meta should exist", meta);
        assertEquals("Schema version should be 0 in OLD cluster", 0, ((Number) meta.get("schema_version")).intValue());

        // Insert a test document to ensure the index has data
        String testDocument = IndexMappingTestHelper.getTestDocument();
        IndexMappingTestHelper.insertTestDocument(client(), JUDGMENT_CACHE_INDEX, TEST_DOC_ID, testDocument);

        logger.info("OLD cluster: Created judgment_cache index with schema_version=0");
    }

    /**
     * MIXED cluster: Validate the index is still accessible.
     * The mapping may or may not be updated depending on cluster manager election.
     */
    private void testValidateIndexInMixedCluster() throws Exception {
        // Verify the index still exists
        assertTrue(
            "Judgment cache index should exist in MIXED cluster",
            IndexMappingTestHelper.checkIndexExists(client(), JUDGMENT_CACHE_INDEX, logger)
        );

        // Verify data is still accessible
        Map<String, Object> doc = IndexMappingTestHelper.getDocument(client(), JUDGMENT_CACHE_INDEX, TEST_DOC_ID, logger);
        assertNotNull("Test document should be accessible in MIXED cluster", doc);

        // Log current mapping state
        Map<String, Object> mapping = IndexMappingTestHelper.getIndexMapping(client(), JUDGMENT_CACHE_INDEX);
        Map<String, Object> meta = IndexMappingTestHelper.getMappingMeta(mapping);
        Map<String, Object> properties = IndexMappingTestHelper.getMappingProperties(mapping);

        int currentVersion = meta != null ? ((Number) meta.get("schema_version")).intValue() : -1;
        boolean hasNewFields = properties != null && properties.containsKey("modelId");

        logger.info("MIXED cluster: schema_version={}, hasNewFields={}", currentVersion, hasNewFields);
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
        assertTrue(
            "Judgment cache index should exist after upgrade",
            IndexMappingTestHelper.checkIndexExists(client(), JUDGMENT_CACHE_INDEX, logger)
        );

        // Verify old data is still accessible
        Map<String, Object> doc = IndexMappingTestHelper.getDocument(client(), JUDGMENT_CACHE_INDEX, TEST_DOC_ID, logger);
        assertNotNull("Test document should still be accessible after upgrade", doc);

        // Update the mapping directly to add new fields (simulates what createIndexIfAbsent does)
        String newMapping = IndexMappingTestHelper.getNewMapping();
        IndexMappingTestHelper.updateMapping(client(), JUDGMENT_CACHE_INDEX, newMapping, logger);

        // Wait for mapping update to complete
        IndexMappingTestHelper.waitForMappingUpdate(
            client(),
            JUDGMENT_CACHE_INDEX,
            new String[] { "modelId", "encodedPromptTemplate" },
            30,
            logger
        );

        Map<String, Object> mapping = IndexMappingTestHelper.getIndexMapping(client(), JUDGMENT_CACHE_INDEX);
        assertNotNull("Mapping should exist in UPGRADED cluster", mapping);

        Map<String, Object> properties = IndexMappingTestHelper.getMappingProperties(mapping);
        assertNotNull("Properties should exist in UPGRADED cluster", properties);

        // Verify new fields exist in the mapping
        assertTrue("Mapping should have modelId field after upgrade", properties.containsKey("modelId"));
        assertTrue("Mapping should have encodedPromptTemplate field after upgrade", properties.containsKey("encodedPromptTemplate"));

        // Verify schema version has been updated to 1
        Map<String, Object> meta = IndexMappingTestHelper.getMappingMeta(mapping);
        assertNotNull("Mapping should have _meta field", meta);
        assertEquals("Schema version should be 1 after upgrade", 1, ((Number) meta.get("schema_version")).intValue());

        // Verify old data is still accessible after mapping update
        doc = IndexMappingTestHelper.getDocument(client(), JUDGMENT_CACHE_INDEX, TEST_DOC_ID, logger);
        assertNotNull("Test document should still be accessible after mapping update", doc);

        logger.info("UPGRADED cluster: Mapping updated with new fields, schema_version=1, old data preserved");
    }
}
