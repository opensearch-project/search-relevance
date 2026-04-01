/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */
package org.opensearch.searchrelevance.util;

import static org.mockito.Mockito.*;

import org.junit.Before;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.opensearch.cluster.ClusterState;
import org.opensearch.cluster.metadata.Metadata;
import org.opensearch.cluster.service.ClusterService;
import org.opensearch.searchrelevance.utils.ReferenceValidationUtil;
import org.opensearch.test.OpenSearchTestCase;

public class ReferenceValidationUtilTests extends OpenSearchTestCase {

    @Mock
    private ClusterService clusterService;
    @Mock
    private ClusterState clusterState;
    @Mock
    private Metadata metadata;

    @Before
    public void setup() {
        MockitoAnnotations.openMocks(this);
        when(clusterService.state()).thenReturn(clusterState);
        when(clusterState.metadata()).thenReturn(metadata);
    }

    public void testValidateIndexExists_Success() {
        when(metadata.hasIndex("test-index")).thenReturn(true);

        ReferenceValidationUtil.ValidationResult result = ReferenceValidationUtil.validateIndexExists(clusterService, "test-index");

        assertTrue(result.isValid());
        assertNull(result.getErrorMessage());
    }

    public void testValidateIndexExists_IndexNotFound() {
        when(metadata.hasIndex("missing-index")).thenReturn(false);

        ReferenceValidationUtil.ValidationResult result = ReferenceValidationUtil.validateIndexExists(clusterService, "missing-index");

        assertFalse(result.isValid());
        assertEquals("Index [missing-index] does not exist", result.getErrorMessage());
    }

}
