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

/**
 * Singleton holder for cluster-level information (e.g. the minimum node version).
 *
 * <p>Modeled on neural-search's {@code NeuralSearchClusterUtil}: it is initialized once from the
 * plugin's {@code createComponents} and then accessed statically via {@link #instance()}. The
 * static access lets code paths that have no dependency-injection hook — notably transport request
 * serialization ({@code writeTo}/{@code StreamInput}) — read the cluster's minimum node version to
 * gate backward-incompatible changes during a rolling upgrade.
 */
public class ClusterUtil {

    private static ClusterUtil INSTANCE;

    private ClusterService clusterService;

    private ClusterUtil() {}

    /**
     * @return the singleton instance
     */
    public static synchronized ClusterUtil instance() {
        if (INSTANCE == null) {
            INSTANCE = new ClusterUtil();
        }
        return INSTANCE;
    }

    /**
     * Injects the cluster service. Must be called once during plugin initialization before any
     * call to {@link #getClusterMinVersion()}.
     *
     * @param clusterService the cluster service
     */
    public void initialize(final ClusterService clusterService) {
        this.clusterService = clusterService;
    }

    /**
     * Return minimal OpenSearch version based on all nodes currently discoverable in the cluster
     * @return minimal installed OpenSearch version, default to Version.CURRENT which is typically the latest version
     */
    public Version getClusterMinVersion() {
        return this.clusterService.state().getNodes().getMinNodeVersion();
    }
}
