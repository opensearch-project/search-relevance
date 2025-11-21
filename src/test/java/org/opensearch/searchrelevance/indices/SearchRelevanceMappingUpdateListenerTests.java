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

import java.util.List;

import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.opensearch.cluster.ClusterChangedEvent;
import org.opensearch.cluster.ClusterState;
import org.opensearch.cluster.RestoreInProgress;
import org.opensearch.core.action.ActionListener;
import org.opensearch.test.OpenSearchTestCase;

public class SearchRelevanceMappingUpdateListenerTests extends OpenSearchTestCase {

    @Mock
    private SearchRelevanceIndicesManager indicesManager;

    @Mock
    private ClusterChangedEvent event;

    @Mock
    private ClusterState clusterState;

    private AutoCloseable openMocks;
    private SearchRelevanceMappingUpdateListener listener;

    @Override
    public void setUp() throws Exception {
        super.setUp();
        openMocks = MockitoAnnotations.openMocks(this);
        listener = new SearchRelevanceMappingUpdateListener(indicesManager);

        when(event.state()).thenReturn(clusterState);
    }

    @Override
    public void tearDown() throws Exception {
        super.tearDown();
        openMocks.close();
    }

    public void testClusterChangedWhenNotClusterManager() {
        when(event.localNodeClusterManager()).thenReturn(false);

        listener.clusterChanged(event);

        // Should not call updateMappingIfExists when not cluster manager
        verify(indicesManager, never()).updateMappingIfExists(any(), any());
    }

    public void testClusterChangedWhenNoRestoreInProgress() {
        when(event.localNodeClusterManager()).thenReturn(true);
        when(clusterState.custom(RestoreInProgress.TYPE, RestoreInProgress.EMPTY)).thenReturn(RestoreInProgress.EMPTY);

        listener.clusterChanged(event);

        // Should not call updateMappingIfExists when no restore in progress
        verify(indicesManager, never()).updateMappingIfExists(any(), any());
    }

    public void testClusterChangedWhenRestoreInProgressButNotSuccess() {
        when(event.localNodeClusterManager()).thenReturn(true);

        // Create a restore entry that is not successful
        RestoreInProgress.Entry entry = mock(RestoreInProgress.Entry.class);
        when(entry.state()).thenReturn(RestoreInProgress.State.STARTED);
        when(entry.indices()).thenReturn(List.of(".plugins-search-relevance-experiment"));

        RestoreInProgress restoreInProgress = mock(RestoreInProgress.class);
        when(restoreInProgress.iterator()).thenReturn(List.of(entry).iterator());
        when(clusterState.custom(RestoreInProgress.TYPE, RestoreInProgress.EMPTY)).thenReturn(restoreInProgress);

        listener.clusterChanged(event);

        // Should not call updateMappingIfExists when restore is not successful
        verify(indicesManager, never()).updateMappingIfExists(any(), any());
    }

    public void testClusterChangedWhenRestoreSuccessfulForSearchRelevanceIndex() {
        when(event.localNodeClusterManager()).thenReturn(true);

        // Create a successful restore entry for experiment index
        RestoreInProgress.Entry entry = mock(RestoreInProgress.Entry.class);
        when(entry.state()).thenReturn(RestoreInProgress.State.SUCCESS);
        when(entry.indices()).thenReturn(List.of(".plugins-search-relevance-experiment"));

        RestoreInProgress restoreInProgress = mock(RestoreInProgress.class);
        when(restoreInProgress.iterator()).thenReturn(List.of(entry).iterator());
        when(clusterState.custom(RestoreInProgress.TYPE, RestoreInProgress.EMPTY)).thenReturn(restoreInProgress);

        listener.clusterChanged(event);

        // Should call updateMappingIfExists for the experiment index
        verify(indicesManager, times(1)).updateMappingIfExists(eq(SearchRelevanceIndices.EXPERIMENT), any(ActionListener.class));
    }

    public void testClusterChangedWhenRestoreSuccessfulForMultipleIndices() {
        when(event.localNodeClusterManager()).thenReturn(true);

        // Create a successful restore entry for multiple search relevance indices
        RestoreInProgress.Entry entry = mock(RestoreInProgress.Entry.class);
        when(entry.state()).thenReturn(RestoreInProgress.State.SUCCESS);
        when(entry.indices()).thenReturn(
            List.of(
                ".plugins-search-relevance-experiment",
                ".plugins-search-relevance-judgment-cache",
                "other-index" // This should be ignored
            )
        );

        RestoreInProgress restoreInProgress = mock(RestoreInProgress.class);
        when(restoreInProgress.iterator()).thenReturn(List.of(entry).iterator());
        when(clusterState.custom(RestoreInProgress.TYPE, RestoreInProgress.EMPTY)).thenReturn(restoreInProgress);

        listener.clusterChanged(event);

        // Should call updateMappingIfExists for both search relevance indices
        verify(indicesManager, times(1)).updateMappingIfExists(eq(SearchRelevanceIndices.EXPERIMENT), any(ActionListener.class));
        verify(indicesManager, times(1)).updateMappingIfExists(eq(SearchRelevanceIndices.JUDGMENT_CACHE), any(ActionListener.class));
    }

    public void testClusterChangedWhenRestoreSuccessfulForNonSearchRelevanceIndex() {
        when(event.localNodeClusterManager()).thenReturn(true);

        // Create a successful restore entry for a non-search-relevance index
        RestoreInProgress.Entry entry = mock(RestoreInProgress.Entry.class);
        when(entry.state()).thenReturn(RestoreInProgress.State.SUCCESS);
        when(entry.indices()).thenReturn(List.of("other-index"));

        RestoreInProgress restoreInProgress = mock(RestoreInProgress.class);
        when(restoreInProgress.iterator()).thenReturn(List.of(entry).iterator());
        when(clusterState.custom(RestoreInProgress.TYPE, RestoreInProgress.EMPTY)).thenReturn(restoreInProgress);

        listener.clusterChanged(event);

        // Should not call updateMappingIfExists for non-search-relevance indices
        verify(indicesManager, never()).updateMappingIfExists(any(), any());
    }
}
