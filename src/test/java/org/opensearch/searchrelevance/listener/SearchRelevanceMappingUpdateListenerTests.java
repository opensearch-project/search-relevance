/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */
package org.opensearch.searchrelevance.listener;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;

import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.opensearch.cluster.ClusterChangedEvent;
import org.opensearch.cluster.ClusterState;
import org.opensearch.cluster.RestoreInProgress;
import org.opensearch.searchrelevance.indices.SearchRelevanceIndices;
import org.opensearch.searchrelevance.indices.SearchRelevanceIndicesManager;
import org.opensearch.snapshots.Snapshot;
import org.opensearch.snapshots.SnapshotId;
import org.opensearch.test.OpenSearchTestCase;
import org.opensearch.threadpool.ThreadPool;

public class SearchRelevanceMappingUpdateListenerTests extends OpenSearchTestCase {

    @Mock
    private SearchRelevanceIndicesManager indicesManager;
    @Mock
    private ThreadPool threadPool;
    @Mock
    private ExecutorService executorService;
    @Mock
    private ClusterChangedEvent event;
    @Mock
    private ClusterState currentState;
    @Mock
    private ClusterState previousState;

    private AutoCloseable openMocks;
    private SearchRelevanceMappingUpdateListener listener;

    @Override
    public void setUp() throws Exception {
        super.setUp();
        openMocks = MockitoAnnotations.openMocks(this);

        when(threadPool.generic()).thenReturn(executorService);
        when(event.state()).thenReturn(currentState);
        when(event.previousState()).thenReturn(previousState);

        listener = new SearchRelevanceMappingUpdateListener(indicesManager, threadPool);
    }

    @Override
    public void tearDown() throws Exception {
        super.tearDown();
        openMocks.close();
    }

    public void testClusterChangedSkipsWhenNotClusterManager() {
        when(event.localNodeClusterManager()).thenReturn(false);

        listener.clusterChanged(event);

        verify(currentState, never()).custom(RestoreInProgress.TYPE);
    }

    public void testClusterChangedSkipsWhenNoRestoreInProgress() {
        when(event.localNodeClusterManager()).thenReturn(true);
        when(currentState.custom(RestoreInProgress.TYPE)).thenReturn(null);

        listener.clusterChanged(event);

        verify(threadPool, never()).generic();
    }

    public void testClusterChangedProcessesCompletedRestore() {
        when(event.localNodeClusterManager()).thenReturn(true);

        // Create a completed restore entry for a search relevance index
        String indexName = SearchRelevanceIndices.QUERY_SET.getIndexName();
        Snapshot snapshot = new Snapshot("test-repo", new SnapshotId("test-snapshot", "uuid-1"));
        RestoreInProgress.Entry entry = new RestoreInProgress.Entry(
            "restore-uuid",
            snapshot,
            RestoreInProgress.State.SUCCESS,
            List.of(indexName),
            Collections.emptyMap()
        );
        RestoreInProgress restoreInProgress = new RestoreInProgress.Builder().add(entry).build();

        when(currentState.custom(RestoreInProgress.TYPE)).thenReturn(restoreInProgress);
        when(previousState.custom(RestoreInProgress.TYPE)).thenReturn(null);

        // Capture the runnable submitted to the executor
        ArgumentCaptor<Runnable> runnableCaptor = ArgumentCaptor.forClass(Runnable.class);

        listener.clusterChanged(event);

        verify(threadPool).generic();
        verify(executorService).execute(runnableCaptor.capture());

        // Execute the captured runnable to trigger the mapping update
        runnableCaptor.getValue().run();

        verify(indicesManager).updateMappingIfExistsSync(SearchRelevanceIndices.QUERY_SET);
    }

    public void testClusterChangedSkipsNonSearchRelevanceIndices() {
        when(event.localNodeClusterManager()).thenReturn(true);

        // Create a completed restore entry for a non-search-relevance index
        String indexName = "some-other-index";
        Snapshot snapshot = new Snapshot("test-repo", new SnapshotId("test-snapshot", "uuid-1"));
        RestoreInProgress.Entry entry = new RestoreInProgress.Entry(
            "restore-uuid",
            snapshot,
            RestoreInProgress.State.SUCCESS,
            List.of(indexName),
            Collections.emptyMap()
        );
        RestoreInProgress restoreInProgress = new RestoreInProgress.Builder().add(entry).build();

        when(currentState.custom(RestoreInProgress.TYPE)).thenReturn(restoreInProgress);
        when(previousState.custom(RestoreInProgress.TYPE)).thenReturn(null);

        listener.clusterChanged(event);

        // Should not submit any task since index is not a search relevance index
        verify(executorService, never()).execute(any(Runnable.class));
    }

    public void testClusterChangedSkipsInProgressRestore() {
        when(event.localNodeClusterManager()).thenReturn(true);

        // Create an in-progress restore entry
        String indexName = SearchRelevanceIndices.QUERY_SET.getIndexName();
        Snapshot snapshot = new Snapshot("test-repo", new SnapshotId("test-snapshot", "uuid-1"));
        RestoreInProgress.Entry entry = new RestoreInProgress.Entry(
            "restore-uuid",
            snapshot,
            RestoreInProgress.State.STARTED,
            List.of(indexName),
            Collections.emptyMap()
        );
        RestoreInProgress restoreInProgress = new RestoreInProgress.Builder().add(entry).build();

        when(currentState.custom(RestoreInProgress.TYPE)).thenReturn(restoreInProgress);
        when(previousState.custom(RestoreInProgress.TYPE)).thenReturn(null);

        listener.clusterChanged(event);

        // Should not submit any task since restore is still in progress
        verify(executorService, never()).execute(any(Runnable.class));
    }

    public void testClusterChangedSkipsAlreadyProcessedRestore() {
        when(event.localNodeClusterManager()).thenReturn(true);

        // Create a completed restore entry
        String indexName = SearchRelevanceIndices.QUERY_SET.getIndexName();
        Snapshot snapshot = new Snapshot("test-repo", new SnapshotId("test-snapshot", "uuid-1"));
        RestoreInProgress.Entry entry = new RestoreInProgress.Entry(
            "restore-uuid",
            snapshot,
            RestoreInProgress.State.SUCCESS,
            List.of(indexName),
            Collections.emptyMap()
        );
        RestoreInProgress restoreInProgress = new RestoreInProgress.Builder().add(entry).build();

        // Same restore was already in previous state with same status
        when(currentState.custom(RestoreInProgress.TYPE)).thenReturn(restoreInProgress);
        when(previousState.custom(RestoreInProgress.TYPE)).thenReturn(restoreInProgress);

        listener.clusterChanged(event);

        // Should not submit any task since restore was already processed
        verify(executorService, never()).execute(any(Runnable.class));
    }

    public void testClusterChangedHandlesMappingUpdateException() {
        when(event.localNodeClusterManager()).thenReturn(true);

        String indexName = SearchRelevanceIndices.QUERY_SET.getIndexName();
        Snapshot snapshot = new Snapshot("test-repo", new SnapshotId("test-snapshot", "uuid-1"));
        RestoreInProgress.Entry entry = new RestoreInProgress.Entry(
            "restore-uuid",
            snapshot,
            RestoreInProgress.State.SUCCESS,
            List.of(indexName),
            Collections.emptyMap()
        );
        RestoreInProgress restoreInProgress = new RestoreInProgress.Builder().add(entry).build();

        when(currentState.custom(RestoreInProgress.TYPE)).thenReturn(restoreInProgress);
        when(previousState.custom(RestoreInProgress.TYPE)).thenReturn(null);

        // Mock exception during mapping update
        when(indicesManager.updateMappingIfExistsSync(any(SearchRelevanceIndices.class))).thenThrow(
            new RuntimeException("Mapping update failed")
        );

        ArgumentCaptor<Runnable> runnableCaptor = ArgumentCaptor.forClass(Runnable.class);

        listener.clusterChanged(event);

        verify(executorService).execute(runnableCaptor.capture());

        // Execute the runnable - should not throw, exception should be caught and logged
        runnableCaptor.getValue().run();

        // Verify mapping update was attempted
        verify(indicesManager).updateMappingIfExistsSync(SearchRelevanceIndices.QUERY_SET);
    }
}
