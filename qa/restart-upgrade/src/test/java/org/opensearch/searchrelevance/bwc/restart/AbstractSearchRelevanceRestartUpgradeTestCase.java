/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */
package org.opensearch.searchrelevance.bwc.restart;

import java.util.Locale;

import org.junit.Before;
import org.opensearch.common.settings.Settings;
import org.opensearch.test.rest.OpenSearchRestTestCase;

/**
 * Base class for Search Relevance BWC restart upgrade tests.
 *
 * In restart upgrade tests:
 * - OLD cluster: All nodes run the old version
 * - UPGRADED cluster: All nodes are upgraded simultaneously (full cluster restart)
 *
 * Unlike rolling upgrade, there is no MIXED cluster state.
 */
public abstract class AbstractSearchRelevanceRestartUpgradeTestCase extends OpenSearchRestTestCase {

    protected static final String TEST_INDEX_PREFIX = "bwc-restart-";

    /**
     * Cluster types for restart upgrade tests
     */
    protected enum ClusterType {
        OLD,
        UPGRADED;

        public static ClusterType parse(String value) {
            switch (value) {
                case "old_cluster":
                    return OLD;
                case "upgraded_cluster":
                    return UPGRADED;
                default:
                    throw new IllegalArgumentException("Unknown cluster type: " + value);
            }
        }
    }

    @Before
    public void setupTestEnvironment() throws Exception {
        logger.info("Cluster type: {}", getClusterType());
        logger.info("BWC version: {}", getBWCVersion());
    }

    /**
     * Returns the cluster type based on the system property set by the test framework.
     */
    protected ClusterType getClusterType() {
        String clusterType = System.getProperty("tests.rest.bwcsuite_cluster");
        if (clusterType == null) {
            throw new IllegalStateException("tests.rest.bwcsuite_cluster system property is not set");
        }
        return ClusterType.parse(clusterType);
    }

    /**
     * Returns whether this is the old cluster.
     */
    protected boolean isOldCluster() {
        return "true".equals(System.getProperty("tests.is_old_cluster"));
    }

    /**
     * Returns the BWC version being tested.
     */
    protected String getBWCVersion() {
        return System.getProperty("tests.plugin_bwc_version", "3.3.0-SNAPSHOT");
    }

    /**
     * Generates an index name for the test based on test method name.
     */
    protected String getIndexNameForTest() {
        return TEST_INDEX_PREFIX + getTestName().toLowerCase(Locale.ROOT);
    }

    /**
     * Generates a query set name for the test based on test method name.
     */
    protected String getQuerySetNameForTest() {
        return "bwc-restart-queryset-" + getTestName().toLowerCase(Locale.ROOT);
    }

    /**
     * Generates a judgment name for the test based on test method name.
     */
    protected String getJudgmentNameForTest() {
        return "bwc-restart-judgment-" + getTestName().toLowerCase(Locale.ROOT);
    }

    /**
     * Generates a search configuration name for the test based on test method name.
     */
    protected String getSearchConfigNameForTest() {
        return "bwc-restart-searchconfig-" + getTestName().toLowerCase(Locale.ROOT);
    }

    @Override
    protected Settings restClientSettings() {
        // Increase timeout for BWC tests as cluster operations may be slow
        return Settings.builder().put(super.restClientSettings()).put(CLIENT_SOCKET_TIMEOUT, "120s").build();
    }

    @Override
    protected boolean preserveIndicesUponCompletion() {
        return true;
    }

    @Override
    protected boolean preserveClusterUponCompletion() {
        return true;
    }

    @Override
    protected boolean preserveReposUponCompletion() {
        return true;
    }

    @Override
    protected boolean preserveTemplatesUponCompletion() {
        return true;
    }
}
