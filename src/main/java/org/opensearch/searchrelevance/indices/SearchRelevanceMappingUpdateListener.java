/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */
package org.opensearch.searchrelevance.indices;

import org.opensearch.cluster.ClusterChangedEvent;
import org.opensearch.cluster.ClusterStateListener;
import org.opensearch.cluster.RestoreInProgress;
import org.opensearch.core.action.ActionListener;

import lombok.extern.log4j.Log4j2;

/**
 * Listener that updates search relevance index mappings when indices are restored from snapshots.
 * This only updates mappings for indices that were restored - it does not create new indices.
 */
@Log4j2
public class SearchRelevanceMappingUpdateListener implements ClusterStateListener {

    private final SearchRelevanceIndicesManager indicesManager;

    public SearchRelevanceMappingUpdateListener(SearchRelevanceIndicesManager indicesManager) {
        this.indicesManager = indicesManager;
    }

    @Override
    public void clusterChanged(ClusterChangedEvent event) {
        // Only process on cluster manager node
        if (!event.localNodeClusterManager()) {
            return;
        }

        // Check for completed snapshot restore operations
        RestoreInProgress restoreInProgress = event.state().custom(RestoreInProgress.TYPE, RestoreInProgress.EMPTY);
        if (restoreInProgress == null) {
            return;
        }

        for (RestoreInProgress.Entry entry : restoreInProgress) {
            // Only process successfully completed restores
            if (!RestoreInProgress.State.SUCCESS.equals(entry.state())) {
                continue;
            }

            // Check if any search relevance indices were restored
            for (SearchRelevanceIndices index : SearchRelevanceIndices.values()) {
                String indexName = index.getIndexName();
                if (entry.indices().contains(indexName)) {
                    updateMappingForRestoredIndex(index);
                }
            }
        }
    }

    /**
     * Update mapping for a restored index
     */
    private void updateMappingForRestoredIndex(SearchRelevanceIndices index) {
        indicesManager.updateMappingIfExists(index, new ActionListener<>() {
            @Override
            public void onResponse(org.opensearch.action.support.clustermanager.AcknowledgedResponse response) {
                // Mapping updated successfully
            }

            @Override
            public void onFailure(Exception e) {
                log.error("Failed to update mapping for restored index [{}]", index.getIndexName(), e);
            }
        });
    }
}
