/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */
package org.opensearch.searchrelevance.listener;

import java.util.Set;
import java.util.stream.Collectors;

import org.opensearch.cluster.ClusterChangedEvent;
import org.opensearch.cluster.ClusterStateListener;
import org.opensearch.cluster.RestoreInProgress;
import org.opensearch.searchrelevance.indices.SearchRelevanceIndices;
import org.opensearch.searchrelevance.indices.SearchRelevanceIndicesManager;
import org.opensearch.threadpool.ThreadPool;

import lombok.extern.log4j.Log4j2;

/**
 * Listener that updates search relevance index mappings in response to cluster events:
 * 1. When this node becomes the cluster manager - updates all existing indices
 * 2. When indices are restored from snapshots - updates restored indices
 *
 * This ensures that indices always have the latest mapping definitions after upgrades
 * or snapshot restores.
 */
@Log4j2
public class SearchRelevanceMappingUpdateListener implements ClusterStateListener {

    private final SearchRelevanceIndicesManager indicesManager;
    private final ThreadPool threadPool;

    // Set of search relevance index names for quick lookup
    private static final Set<String> SEARCH_RELEVANCE_INDEX_NAMES = java.util.Arrays.stream(SearchRelevanceIndices.values())
        .map(SearchRelevanceIndices::getIndexName)
        .collect(Collectors.toSet());

    public SearchRelevanceMappingUpdateListener(SearchRelevanceIndicesManager indicesManager, ThreadPool threadPool) {
        this.indicesManager = indicesManager;
        this.threadPool = threadPool;
    }

    @Override
    public void clusterChanged(ClusterChangedEvent event) {
        // Only process on cluster manager node
        if (!event.localNodeClusterManager()) {
            return;
        }

        // Check if this node just became cluster manager
        if (!event.previousState().nodes().isLocalNodeElectedClusterManager()) {
            log.info("This node just became cluster manager, scheduling mapping updates for all existing indices");
            updateAllExistingIndicesMappings();
        }

        // Handle snapshot restore events
        handleSnapshotRestore(event);
    }

    /**
     * Updates mappings for all existing search relevance indices.
     * Called when this node becomes cluster manager.
     *
     * We delay the mapping update slightly to allow the cluster state to fully propagate
     * after a node becomes cluster manager, especially important during restart upgrades
     * when all nodes restart simultaneously.
     */
    private void updateAllExistingIndicesMappings() {
        // Schedule with a small delay to allow cluster state to stabilize
        threadPool.generic().execute(() -> {
            try {
                // Wait for cluster state to stabilize after becoming cluster manager
                // This is important during restart upgrades when all nodes restart simultaneously
                Thread.sleep(5000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.warn("Interrupted while waiting for cluster state to stabilize");
                return;
            }

            int updatedCount = 0;
            int skippedCount = 0;
            for (SearchRelevanceIndices index : SearchRelevanceIndices.values()) {
                try {
                    log.info("Checking mapping update for index [{}] after becoming cluster manager", index.getIndexName());
                    Object result = indicesManager.updateMappingIfExistsSync(index);
                    if (result != null) {
                        log.info("Successfully updated mapping for index [{}]", index.getIndexName());
                        updatedCount++;
                    } else {
                        log.debug("Index [{}] does not exist, skipping mapping update", index.getIndexName());
                        skippedCount++;
                    }
                } catch (Exception e) {
                    log.error("Failed to update mapping for index [{}] after becoming cluster manager", index.getIndexName(), e);
                }
            }
            log.info(
                "Completed mapping updates for search relevance indices: {} updated, {} skipped (not exist)",
                updatedCount,
                skippedCount
            );
        });
    }

    /**
     * Handles snapshot restore events by updating mappings for restored search relevance indices.
     */
    private void handleSnapshotRestore(ClusterChangedEvent event) {
        RestoreInProgress restoreInProgress = event.state().custom(RestoreInProgress.TYPE);
        if (restoreInProgress == null) {
            return;
        }

        // Check for completed restore operations
        RestoreInProgress previousRestore = event.previousState().custom(RestoreInProgress.TYPE);

        for (RestoreInProgress.Entry entry : restoreInProgress) {
            // Skip if this restore was already in the previous state with the same status
            if (previousRestore != null && isRestoreUnchanged(previousRestore, entry)) {
                continue;
            }

            // Process completed restores
            if (entry.state() == RestoreInProgress.State.SUCCESS) {
                processRestoredIndices(entry);
            }
        }
    }

    private boolean isRestoreUnchanged(RestoreInProgress previousRestore, RestoreInProgress.Entry currentEntry) {
        for (RestoreInProgress.Entry prevEntry : previousRestore) {
            if (prevEntry.uuid().equals(currentEntry.uuid()) && prevEntry.state() == currentEntry.state()) {
                return true;
            }
        }
        return false;
    }

    private void processRestoredIndices(RestoreInProgress.Entry entry) {
        for (String indexName : entry.indices()) {
            if (SEARCH_RELEVANCE_INDEX_NAMES.contains(indexName)) {
                log.info("Detected restored search relevance index [{}], scheduling mapping update", indexName);
                updateMappingForRestoredIndex(indexName);
            }
        }
    }

    private void updateMappingForRestoredIndex(String indexName) {
        // Find the corresponding SearchRelevanceIndices enum
        SearchRelevanceIndices index = findIndexByName(indexName);
        if (index == null) {
            log.warn("Could not find SearchRelevanceIndices enum for index [{}]", indexName);
            return;
        }

        // Execute mapping update in generic thread pool to avoid blocking cluster state processing
        threadPool.generic().execute(() -> {
            try {
                log.info("Updating mapping for restored index [{}]", indexName);
                indicesManager.updateMappingIfExistsSync(index);
                log.info("Successfully updated mapping for restored index [{}]", indexName);
            } catch (Exception e) {
                log.error("Failed to update mapping for restored index [{}]", indexName, e);
            }
        });
    }

    private SearchRelevanceIndices findIndexByName(String indexName) {
        for (SearchRelevanceIndices index : SearchRelevanceIndices.values()) {
            if (index.getIndexName().equals(indexName)) {
                return index;
            }
        }
        return null;
    }
}
