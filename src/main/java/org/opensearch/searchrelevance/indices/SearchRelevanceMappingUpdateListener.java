/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */
package org.opensearch.searchrelevance.indices;

import java.util.concurrent.atomic.AtomicBoolean;

import org.opensearch.cluster.ClusterChangedEvent;
import org.opensearch.cluster.ClusterStateListener;
import org.opensearch.core.action.ActionListener;

import lombok.extern.log4j.Log4j2;

/**
 * Listener that updates search relevance index mappings when the cluster becomes ready.
 * This handles both node startup and snapshot restore scenarios.
 */
@Log4j2
public class SearchRelevanceMappingUpdateListener implements ClusterStateListener {

    private final SearchRelevanceIndicesManager indicesManager;
    private final AtomicBoolean initialized = new AtomicBoolean(false);

    public SearchRelevanceMappingUpdateListener(SearchRelevanceIndicesManager indicesManager) {
        this.indicesManager = indicesManager;
    }

    @Override
    public void clusterChanged(ClusterChangedEvent event) {
        // Only process once when the cluster becomes ready
        if (!event.localNodeClusterManager()) {
            return;
        }

        // Check if cluster has global blocks that would prevent operations
        if (!event.state().blocks().global().isEmpty()) {
            return;
        }

        // Only run once per node lifecycle
        if (!initialized.compareAndSet(false, true)) {
            return;
        }

        log.info("Cluster is ready, checking and updating search relevance index mappings");
        updateAllMappings();
    }

    /**
     * Update mappings for all search relevance indices
     */
    private void updateAllMappings() {
        for (SearchRelevanceIndices index : SearchRelevanceIndices.values()) {
            indicesManager.createOrUpdateIndex(index, new ActionListener<>() {
                @Override
                public void onResponse(Void response) {
                    log.debug("Successfully processed index [{}]", index.getIndexName());
                }

                @Override
                public void onFailure(Exception e) {
                    log.error("Failed to process index [{}]", index.getIndexName(), e);
                }
            });
        }
    }
}
