/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */
package org.opensearch.searchrelevance.bwc.rolling;

import java.util.Locale;

import org.opensearch.common.settings.Settings;
import org.opensearch.test.rest.OpenSearchRestTestCase;

/**
 * Base class for Search Relevance BWC (Backward Compatibility) tests during rolling upgrades.
 * Provides common utilities and cluster state management for testing compatibility across versions.
 */
public abstract class AbstractSearchRelevanceRollingUpgradeTestCase extends OpenSearchRestTestCase {

    private static final String OLD_CLUSTER = "old_cluster";
    private static final String MIXED_CLUSTER = "mixed_cluster";
    private static final String UPGRADED_CLUSTER = "upgraded_cluster";

    /**
     * Enum representing the different cluster states during a rolling upgrade.
     */
    protected enum ClusterType {
        OLD,
        MIXED,
        UPGRADED;

        public static ClusterType instance(String value) {
            switch (value) {
                case OLD_CLUSTER:
                    return OLD;
                case MIXED_CLUSTER:
                    return MIXED;
                case UPGRADED_CLUSTER:
                    return UPGRADED;
                default:
                    throw new IllegalArgumentException("unknown cluster type: " + value);
            }
        }
    }

    /**
     * Gets the current cluster type based on system properties.
     * This determines which phase of the rolling upgrade the test is currently executing.
     *
     * @return The current ClusterType (OLD, MIXED, or UPGRADED)
     */
    protected ClusterType getClusterType() {
        return ClusterType.instance(System.getProperty("tests.rest.bwcsuite_cluster"));
    }

    /**
     * Customizes REST client settings to accommodate rolling upgrade scenarios.
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
     * @return Index name prefixed with "search-relevance-bwc-"
     */
    protected String getIndexNameForTest() {
        return String.format(Locale.ROOT, "search-relevance-bwc-%s", getTestName().toLowerCase(Locale.ROOT));
    }

    /**
     * Gets the query set name for the test with a prefix to identify BWC test resources.
     *
     * @return Query set name prefixed with "bwc-queryset-"
     */
    protected String getQuerySetNameForTest() {
        return String.format(Locale.ROOT, "bwc-queryset-%s", getTestName().toLowerCase(Locale.ROOT));
    }

    /**
     * Gets the judgment name for the test with a prefix to identify BWC test resources.
     *
     * @return Judgment name prefixed with "bwc-judgment-"
     */
    protected String getJudgmentNameForTest() {
        return String.format(Locale.ROOT, "bwc-judgment-%s", getTestName().toLowerCase(Locale.ROOT));
    }

    /**
     * Gets the search configuration name for the test with a prefix to identify BWC test resources.
     *
     * @return Search configuration name prefixed with "bwc-search-config-"
     */
    protected String getSearchConfigNameForTest() {
        return String.format(Locale.ROOT, "bwc-search-config-%s", getTestName().toLowerCase(Locale.ROOT));
    }

    /**
     * Checks if this is the first round of the mixed cluster phase.
     * During rolling upgrades, the mixed phase has multiple rounds as nodes are upgraded one by one.
     *
     * @return true if this is the first mixed cluster round, false otherwise
     */
    protected boolean isFirstMixedRound() {
        return Boolean.parseBoolean(System.getProperty("tests.rest.first_round", "false"));
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
     * Preserves indices created during tests across rolling upgrade phases.
     * This is essential for BWC testing where data created in OLD cluster
     * must be accessible in MIXED and UPGRADED cluster phases.
     *
     * @return true to preserve indices between test phases
     */
    @Override
    protected boolean preserveIndicesUponCompletion() {
        return true;
    }

    @Override
    public boolean preserveClusterUponCompletion() {
        // Otherwise, the cluster setting to enable ml-common is reset and the model is undeployed
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
