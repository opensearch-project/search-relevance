/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */
package org.opensearch.searchrelevance.bwc.restart;

import java.util.Locale;

import org.opensearch.common.settings.Settings;
import org.opensearch.test.rest.OpenSearchRestTestCase;

/**
 * Base class for Search Relevance BWC (Backward Compatibility) tests during full cluster restart upgrades.
 * Provides common utilities and cluster state management for testing compatibility across versions.
 *
 * Unlike rolling upgrades, restart upgrades shut down all nodes at once and restart them
 * with the new version. This tests a different upgrade path that some users may take.
 */
public abstract class AbstractSearchRelevanceRestartUpgradeTestCase extends OpenSearchRestTestCase {

    private static final String OLD_CLUSTER = "old_cluster";
    private static final String UPGRADED_CLUSTER = "upgraded_cluster";

    /**
     * Enum representing the different cluster states during a restart upgrade.
     * Unlike rolling upgrades, there is no MIXED state - the cluster goes directly
     * from OLD to UPGRADED.
     */
    protected enum ClusterType {
        OLD,
        UPGRADED;

        public static ClusterType instance(String value) {
            switch (value) {
                case OLD_CLUSTER:
                    return OLD;
                case UPGRADED_CLUSTER:
                    return UPGRADED;
                default:
                    throw new IllegalArgumentException("unknown cluster type: " + value);
            }
        }
    }

    /**
     * Gets the current cluster type based on system properties.
     * This determines which phase of the restart upgrade the test is currently executing.
     *
     * @return The current ClusterType (OLD or UPGRADED)
     */
    protected ClusterType getClusterType() {
        return ClusterType.instance(System.getProperty("tests.rest.bwcsuite_cluster"));
    }

    /**
     * Customizes REST client settings to accommodate restart upgrade scenarios.
     * Increases socket timeout to handle delays during cluster transitions.
     *
     * @return Settings with extended client socket timeout
     */
    @Override
    protected final Settings restClientSettings() {
        return Settings.builder().put(super.restClientSettings()).put(OpenSearchRestTestCase.CLIENT_SOCKET_TIMEOUT, "120s").build();
    }

    /**
     * Gets the index name for the test with a prefix to identify BWC test resources.
     *
     * @return Index name prefixed with "search-relevance-bwc-restart-"
     */
    protected String getIndexNameForTest() {
        return String.format(Locale.ROOT, "search-relevance-bwc-restart-%s", getTestName().toLowerCase(Locale.ROOT));
    }

    /**
     * Gets the query set name for the test with a prefix to identify BWC test resources.
     *
     * @return Query set name prefixed with "bwc-restart-queryset-"
     */
    protected String getQuerySetNameForTest() {
        return String.format(Locale.ROOT, "bwc-restart-queryset-%s", getTestName().toLowerCase(Locale.ROOT));
    }

    /**
     * Gets the judgment name for the test with a prefix to identify BWC test resources.
     *
     * @return Judgment name prefixed with "bwc-restart-judgment-"
     */
    protected String getJudgmentNameForTest() {
        return String.format(Locale.ROOT, "bwc-restart-judgment-%s", getTestName().toLowerCase(Locale.ROOT));
    }

    /**
     * Gets the search configuration name for the test with a prefix to identify BWC test resources.
     *
     * @return Search configuration name prefixed with "bwc-restart-search-config-"
     */
    protected String getSearchConfigNameForTest() {
        return String.format(Locale.ROOT, "bwc-restart-search-config-%s", getTestName().toLowerCase(Locale.ROOT));
    }

    /**
     * Gets the BWC (backward compatible) version being tested.
     * This is the older version that we're upgrading from.
     *
     * @return The BWC version string
     */
    protected String getBWCVersion() {
        return System.getProperty("tests.plugin_bwc_version");
    }

    /**
     * Preserves indices created during tests across restart upgrade phases.
     * This is essential for BWC testing where data created in OLD cluster
     * must be accessible in UPGRADED cluster phase.
     *
     * @return true to preserve indices between test phases
     */
    @Override
    protected boolean preserveIndicesUponCompletion() {
        return true;
    }

    @Override
    public boolean preserveClusterUponCompletion() {
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
