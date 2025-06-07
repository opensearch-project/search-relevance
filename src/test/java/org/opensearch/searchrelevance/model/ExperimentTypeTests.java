/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */
package org.opensearch.searchrelevance.model;

import org.opensearch.test.OpenSearchTestCase;

public class ExperimentTypeTests extends OpenSearchTestCase {

    public void testAllExperimentTypes() {
        // Verify all expected experiment types exist
        ExperimentType[] types = ExperimentType.values();

        assertTrue("PAIRWISE_COMPARISON should exist", containsType(types, ExperimentType.PAIRWISE_COMPARISON));
        assertTrue("POINTWISE_EVALUATION should exist", containsType(types, ExperimentType.POINTWISE_EVALUATION));
        assertTrue("HYBRID_OPTIMIZER should exist", containsType(types, ExperimentType.HYBRID_OPTIMIZER));
        assertTrue("POINTWISE_EVALUATION_IMPORT should exist", containsType(types, ExperimentType.POINTWISE_EVALUATION_IMPORT));
    }

    public void testNewImportExperimentType() {
        // Test the new import experiment type specifically
        ExperimentType importType = ExperimentType.POINTWISE_EVALUATION_IMPORT;
        assertNotNull("Import experiment type should not be null", importType);
        assertEquals("Import experiment type name should match", "POINTWISE_EVALUATION_IMPORT", importType.name());
    }

    public void testExperimentTypeValueOf() {
        // Test valueOf functionality for all types including the new one
        assertEquals(ExperimentType.PAIRWISE_COMPARISON, ExperimentType.valueOf("PAIRWISE_COMPARISON"));
        assertEquals(ExperimentType.POINTWISE_EVALUATION, ExperimentType.valueOf("POINTWISE_EVALUATION"));
        assertEquals(ExperimentType.HYBRID_OPTIMIZER, ExperimentType.valueOf("HYBRID_OPTIMIZER"));
        assertEquals(ExperimentType.POINTWISE_EVALUATION_IMPORT, ExperimentType.valueOf("POINTWISE_EVALUATION_IMPORT"));
    }

    public void testExperimentTypeOrdinals() {
        // Verify ordinals are consistent (important for serialization)
        ExperimentType[] types = ExperimentType.values();

        assertEquals("Should have 4 experiment types", 4, types.length);

        // Verify the new type is the last one added
        assertEquals("POINTWISE_EVALUATION_IMPORT should be last", ExperimentType.POINTWISE_EVALUATION_IMPORT, types[types.length - 1]);
    }

    public void testInvalidExperimentType() {
        // Test that invalid experiment type throws exception
        assertThrows(
            "Should throw IllegalArgumentException for invalid type",
            IllegalArgumentException.class,
            () -> ExperimentType.valueOf("INVALID_TYPE")
        );
    }

    private boolean containsType(ExperimentType[] types, ExperimentType target) {
        for (ExperimentType type : types) {
            if (type == target) {
                return true;
            }
        }
        return false;
    }
}
