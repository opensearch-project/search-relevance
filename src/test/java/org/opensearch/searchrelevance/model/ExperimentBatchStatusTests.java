/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */
package org.opensearch.searchrelevance.model;

import org.opensearch.test.OpenSearchTestCase;

/**
 * Tests for {@link ExperimentBatchStatus}
 */
public class ExperimentBatchStatusTests extends OpenSearchTestCase {

    public void testEnumValues() {
        ExperimentBatchStatus[] values = ExperimentBatchStatus.values();
        assertEquals(3, values.length);

        // Check all expected values exist
        assertEquals(ExperimentBatchStatus.SUCCESS, ExperimentBatchStatus.valueOf("SUCCESS"));
        assertEquals(ExperimentBatchStatus.PARTIAL_SUCCESS, ExperimentBatchStatus.valueOf("PARTIAL_SUCCESS"));
        assertEquals(ExperimentBatchStatus.ALL_FAILED, ExperimentBatchStatus.valueOf("ALL_FAILED"));
    }

    public void testEnumStringRepresentation() {
        assertEquals("SUCCESS", ExperimentBatchStatus.SUCCESS.name());
        assertEquals("PARTIAL_SUCCESS", ExperimentBatchStatus.PARTIAL_SUCCESS.name());
        assertEquals("ALL_FAILED", ExperimentBatchStatus.ALL_FAILED.name());
    }
}
