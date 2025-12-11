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
 * Listener that updates search relevance index mappings when indices are restored from snapshots.
 * This ensures that restored indices get the latest mapping definitions.
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
