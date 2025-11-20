/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */
package org.opensearch.searchrelevance.indices;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Set;

import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.opensearch.cluster.ClusterChangedEvent;
import org.opensearch.cluster.ClusterState;
import org.opensearch.cluster.block.ClusterBlocks;
import org.opensearch.core.action.ActionListener;
import org.opensearch.test.OpenSearchTestCase;

public class SearchRelevanceMappingUpdateListenerTests extends OpenSearchTestCase {

    @Mock
    private SearchRelevanceIndicesManager indicesManager;

    @Mock
    private ClusterChangedEvent event;

    @Mock
    private ClusterState clusterState;

    @Mock
    private ClusterBlocks clusterBlocks;

    private AutoCloseable openMocks;
    private SearchRelevanceMappingUpdateListener listener;

    @Override
    public void setUp() throws Exception {
        super.setUp();
        openMocks = MockitoAnnotations.openMocks(this);
        listener = new SearchRelevanceMappingUpdateListener(indicesManager);

        when(event.state()).thenReturn(clusterState);
        when(clusterState.blocks()).thenReturn(clusterBlocks);
    }

    @Override
    public void tearDown() throws Exception {
        super.tearDown();
        openMocks.close();
    }

    public void testClusterChangedWhenNotClusterManager() {
        when(event.localNodeClusterManager()).thenReturn(false);

        listener.clusterChanged(event);

        // Should not call createOrUpdateIndex when not cluster manager
        verify(indicesManager, never()).createOrUpdateIndex(any(), any());
    }

    public void testClusterChangedWhenClusterHasGlobalBlocks() {
        when(event.localNodeClusterManager()).thenReturn(true);
        when(clusterBlocks.global()).thenReturn(Set.of(mock(org.opensearch.cluster.block.ClusterBlock.class)));

        listener.clusterChanged(event);

        // Should not call createOrUpdateIndex when cluster has global blocks
        verify(indicesManager, never()).createOrUpdateIndex(any(), any());
    }

    public void testClusterChangedWhenReadyFirstTime() {
        when(event.localNodeClusterManager()).thenReturn(true);
        when(clusterBlocks.global()).thenReturn(Set.of());

        listener.clusterChanged(event);

        // Should call createOrUpdateIndex for all SearchRelevanceIndices enum values
        int expectedCallCount = SearchRelevanceIndices.values().length;
        verify(indicesManager, times(expectedCallCount)).createOrUpdateIndex(any(SearchRelevanceIndices.class), any());
    }

    public void testClusterChangedOnlyRunsOnce() {
        when(event.localNodeClusterManager()).thenReturn(true);
        when(clusterBlocks.global()).thenReturn(Set.of());

        // Call twice
        listener.clusterChanged(event);
        listener.clusterChanged(event);

        // Should only call createOrUpdateIndex once for all indices
        int expectedCallCount = SearchRelevanceIndices.values().length;
        verify(indicesManager, times(expectedCallCount)).createOrUpdateIndex(any(SearchRelevanceIndices.class), any());
    }

    public void testClusterChangedCallsForAllIndices() {
        when(event.localNodeClusterManager()).thenReturn(true);
        when(clusterBlocks.global()).thenReturn(Set.of());

        listener.clusterChanged(event);

        // Verify that createOrUpdateIndex is called for each index type
        for (SearchRelevanceIndices index : SearchRelevanceIndices.values()) {
            verify(indicesManager, times(1)).createOrUpdateIndex(eq(index), any(ActionListener.class));
        }
    }
}
