/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */
package org.opensearch.searchrelevance.ubi;

import static org.opensearch.searchrelevance.common.PluginConstants.UBI_EVENTS_INDEX;
import static org.opensearch.searchrelevance.common.PluginConstants.UBI_QUERIES_INDEX;

import org.opensearch.cluster.service.ClusterService;

public class UbiValidator {

    /**
     * Checks if both UBI indices exist in the cluster
     * @param clusterService opensearch cluster instance
     * @return true if both indices exist, false otherwise
     */
    public static boolean checkUbiIndicesExist(ClusterService clusterService) {
        if (clusterService == null) {
            return false;
        }

        boolean queriesIndexExists = clusterService.state().metadata().hasIndex(UBI_QUERIES_INDEX);
        boolean eventsIndexExists = clusterService.state().metadata().hasIndex(UBI_EVENTS_INDEX);

        return queriesIndexExists && eventsIndexExists;
    }
}
