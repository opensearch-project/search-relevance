/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */
package org.opensearch.searchrelevance.common.validator;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;

import org.opensearch.cluster.ClusterState;
import org.opensearch.cluster.metadata.IndexMetadata;
import org.opensearch.cluster.metadata.MappingMetadata;
import org.opensearch.cluster.metadata.Metadata;
import org.opensearch.cluster.service.ClusterService;
import org.opensearch.test.OpenSearchTestCase;

public class IndexValidatorTests extends OpenSearchTestCase {

    public void testCheckIndexAndMappingExists_ValidIndex() {
        ClusterService clusterService = mock(ClusterService.class);
        ClusterState clusterState = mock(ClusterState.class);
        Metadata metadata = mock(Metadata.class);
        IndexMetadata indexMetadata = mock(IndexMetadata.class);
        MappingMetadata mappingMetadata = mock(MappingMetadata.class);

        when(clusterService.state()).thenReturn(clusterState);
        when(clusterState.metadata()).thenReturn(metadata);
        when(metadata.hasIndex("my-index")).thenReturn(true);
        when(metadata.index("my-index")).thenReturn(indexMetadata);
        when(indexMetadata.mapping()).thenReturn(mappingMetadata);

        assertTrue(IndexValidator.checkIndexAndMappingExists(clusterService, "my-index"));
    }

    public void testCheckIndexAndMappingExists_IndexNotFound() {
        ClusterService clusterService = mock(ClusterService.class);
        ClusterState clusterState = mock(ClusterState.class);
        Metadata metadata = mock(Metadata.class);

        when(clusterService.state()).thenReturn(clusterState);
        when(clusterState.metadata()).thenReturn(metadata);
        when(metadata.hasIndex("missing-index")).thenReturn(false);

        assertFalse(IndexValidator.checkIndexAndMappingExists(clusterService, "missing-index"));
    }

    public void testCheckIndexAndMappingExists_NullMapping() {
        ClusterService clusterService = mock(ClusterService.class);
        ClusterState clusterState = mock(ClusterState.class);
        Metadata metadata = mock(Metadata.class);
        IndexMetadata indexMetadata = mock(IndexMetadata.class);

        when(clusterService.state()).thenReturn(clusterState);
        when(clusterState.metadata()).thenReturn(metadata);
        when(metadata.hasIndex("no-mapping")).thenReturn(true);
        when(metadata.index("no-mapping")).thenReturn(indexMetadata);
        when(indexMetadata.mapping()).thenReturn(null);

        assertFalse(IndexValidator.checkIndexAndMappingExists(clusterService, "no-mapping"));
    }

    public void testCheckIndexAndMappingExists_NullClusterService() {
        assertFalse(IndexValidator.checkIndexAndMappingExists(null, "any-index"));
    }

    public void testValidateFieldsExistInIndexMapping_AllFieldsExist() {
        MappingMetadata mappingMetadata = mock(MappingMetadata.class);
        when(mappingMetadata.sourceAsMap()).thenReturn(
            Map.of("properties", Map.of("field1", Map.of("type", "text"), "field2", Map.of("type", "keyword")))
        );

        // Should not throw
        IndexValidator.validateFieldsExistInIndexMapping(mappingMetadata, List.of("field1", "field2"));
    }

    public void testValidateFieldsExistInIndexMapping_SomeFieldsMissing() {
        MappingMetadata mappingMetadata = mock(MappingMetadata.class);
        when(mappingMetadata.sourceAsMap()).thenReturn(Map.of("properties", Map.of("field1", Map.of("type", "text"))));

        IllegalArgumentException exception = expectThrows(
            IllegalArgumentException.class,
            () -> IndexValidator.validateFieldsExistInIndexMapping(mappingMetadata, List.of("field1", "missing_field"))
        );
        assertTrue(exception.getMessage().contains("missing_field"));
        assertTrue(exception.getMessage().contains("do not exist"));
    }

    public void testValidateFieldsExistInIndexMapping_NestedField() {
        MappingMetadata mappingMetadata = mock(MappingMetadata.class);
        when(mappingMetadata.sourceAsMap()).thenReturn(
            Map.of("properties", Map.of("parent", Map.of("properties", Map.of("child", Map.of("type", "text")))))
        );

        // Should not throw
        IndexValidator.validateFieldsExistInIndexMapping(mappingMetadata, List.of("parent.child"));
    }

    public void testValidateFieldsExistInIndexMapping_NestedFieldMissing() {
        MappingMetadata mappingMetadata = mock(MappingMetadata.class);
        when(mappingMetadata.sourceAsMap()).thenReturn(
            Map.of("properties", Map.of("parent", Map.of("properties", Map.of("child", Map.of("type", "text")))))
        );

        IllegalArgumentException exception = expectThrows(
            IllegalArgumentException.class,
            () -> IndexValidator.validateFieldsExistInIndexMapping(mappingMetadata, List.of("parent.missing"))
        );
        assertTrue(exception.getMessage().contains("parent.missing"));
    }

    public void testValidateFieldsExistInIndexMapping_NoProperties() {
        MappingMetadata mappingMetadata = mock(MappingMetadata.class);
        when(mappingMetadata.sourceAsMap()).thenReturn(Map.of());

        IllegalArgumentException exception = expectThrows(
            IllegalArgumentException.class,
            () -> IndexValidator.validateFieldsExistInIndexMapping(mappingMetadata, List.of("any_field"))
        );
        assertTrue(exception.getMessage().contains("any_field"));
    }
}
