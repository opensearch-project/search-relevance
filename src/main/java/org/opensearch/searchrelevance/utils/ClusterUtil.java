/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */
package org.opensearch.searchrelevance.utils;

import org.opensearch.Version;
import org.opensearch.cluster.service.ClusterService;

import lombok.AllArgsConstructor;

@AllArgsConstructor
public class ClusterUtil {
    private ClusterService clusterService;

    /**
     * Return minimal OpenSearch version based on all nodes currently discoverable in the cluster
     * @return minimal installed OpenSearch version, default to Version.CURRENT which is typically the latest version
     */
    public Version getClusterMinVersion() {
        return this.clusterService.state().getNodes().getMinNodeVersion();
    }
}
