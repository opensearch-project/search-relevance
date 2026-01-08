/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */
package org.opensearch.searchrelevance.ubi;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Map;

import org.opensearch.cluster.ClusterState;
import org.opensearch.cluster.metadata.IndexMetadata;
import org.opensearch.cluster.metadata.MappingMetadata;
import org.opensearch.cluster.metadata.Metadata;
import org.opensearch.cluster.service.ClusterService;
import org.opensearch.test.OpenSearchTestCase;

public class UbiValidatorTests extends OpenSearchTestCase {

    public void testCheckUbiQueriesIndexExistsWithValidFields() {
        ClusterService clusterService = mock(ClusterService.class);
        ClusterState clusterState = mock(ClusterState.class);
        Metadata metadata = mock(Metadata.class);
        IndexMetadata indexMetadata = mock(IndexMetadata.class);
        MappingMetadata mappingMetadata = mock(MappingMetadata.class);

        when(clusterService.state()).thenReturn(clusterState);
        when(clusterState.metadata()).thenReturn(metadata);
        when(metadata.hasIndex("ubi_queries")).thenReturn(true);
        when(metadata.index("ubi_queries")).thenReturn(indexMetadata);
        when(indexMetadata.mapping()).thenReturn(mappingMetadata);
        when(mappingMetadata.sourceAsMap()).thenReturn(Map.of("properties", Map.of("user_query", Map.of("type", "text"))));

        assertTrue(UbiValidator.checkUbiQueriesIndexExists(clusterService, "ubi_queries"));
    }

    public void testCheckUbiQueriesIndexExistsWithMissingFields() {
        ClusterService clusterService = mock(ClusterService.class);
        ClusterState clusterState = mock(ClusterState.class);
        Metadata metadata = mock(Metadata.class);
        IndexMetadata indexMetadata = mock(IndexMetadata.class);
        MappingMetadata mappingMetadata = mock(MappingMetadata.class);

        when(clusterService.state()).thenReturn(clusterState);
        when(clusterState.metadata()).thenReturn(metadata);
        when(metadata.hasIndex("ubi_queries")).thenReturn(true);
        when(metadata.index("ubi_queries")).thenReturn(indexMetadata);
        when(indexMetadata.mapping()).thenReturn(mappingMetadata);
        when(mappingMetadata.sourceAsMap()).thenReturn(Map.of("properties", Map.of()));

        assertFalse(UbiValidator.checkUbiQueriesIndexExists(clusterService, "ubi_queries"));
    }

    public void testCheckUbiEventsIndexExistsWithValidFields() {
        ClusterService clusterService = mock(ClusterService.class);
        ClusterState clusterState = mock(ClusterState.class);
        Metadata metadata = mock(Metadata.class);
        IndexMetadata indexMetadata = mock(IndexMetadata.class);
        MappingMetadata mappingMetadata = mock(MappingMetadata.class);

        when(clusterService.state()).thenReturn(clusterState);
        when(clusterState.metadata()).thenReturn(metadata);
        when(metadata.hasIndex("ubi_events")).thenReturn(true);
        when(metadata.index("ubi_events")).thenReturn(indexMetadata);
        when(indexMetadata.mapping()).thenReturn(mappingMetadata);
        when(mappingMetadata.sourceAsMap()).thenReturn(
            Map.of(
                "properties",
                Map.of(
                    "query_id",
                    Map.of("type", "keyword"),
                    "action_name",
                    Map.of("type", "keyword"),
                    "event_attributes",
                    Map.of("properties", Map.of("object", Map.of("properties", Map.of("object_id", Map.of("type", "keyword")))))
                )
            )
        );

        assertTrue(UbiValidator.checkUbiEventsIndexExists(clusterService, "ubi_events"));
    }

    public void testCheckUbiEventsIndexExistsWithMissingFields() {
        ClusterService clusterService = mock(ClusterService.class);
        ClusterState clusterState = mock(ClusterState.class);
        Metadata metadata = mock(Metadata.class);
        IndexMetadata indexMetadata = mock(IndexMetadata.class);
        MappingMetadata mappingMetadata = mock(MappingMetadata.class);

        when(clusterService.state()).thenReturn(clusterState);
        when(clusterState.metadata()).thenReturn(metadata);
        when(metadata.hasIndex("ubi_events")).thenReturn(true);
        when(metadata.index("ubi_events")).thenReturn(indexMetadata);
        when(indexMetadata.mapping()).thenReturn(mappingMetadata);
        when(mappingMetadata.sourceAsMap()).thenReturn(Map.of("properties", Map.of("query_id", Map.of("type", "keyword"))));

        assertFalse(UbiValidator.checkUbiEventsIndexExists(clusterService, "ubi_events"));
    }

    public void testCheckUbiQueriesIndexNotExist() {
        ClusterService clusterService = mock(ClusterService.class);
        ClusterState clusterState = mock(ClusterState.class);
        Metadata metadata = mock(Metadata.class);

        when(clusterService.state()).thenReturn(clusterState);
        when(clusterState.metadata()).thenReturn(metadata);
        when(metadata.hasIndex("ubi_queries")).thenReturn(false);

        assertFalse(UbiValidator.checkUbiQueriesIndexExists(clusterService, "ubi_queries"));
    }

    public void testCheckUbiEventsIndexNotExist() {
        ClusterService clusterService = mock(ClusterService.class);
        ClusterState clusterState = mock(ClusterState.class);
        Metadata metadata = mock(Metadata.class);

        when(clusterService.state()).thenReturn(clusterState);
        when(clusterState.metadata()).thenReturn(metadata);
        when(metadata.hasIndex("ubi_events")).thenReturn(false);

        assertFalse(UbiValidator.checkUbiEventsIndexExists(clusterService, "ubi_events"));
    }
}
