/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */
package org.opensearch.searchrelevance.bwc.restart;

import static org.opensearch.searchrelevance.bwc.IndexMappingTestHelper.BWC_CLUSTER_TYPE_PROPERTY;
import static org.opensearch.searchrelevance.bwc.IndexMappingTestHelper.BWC_VERSION_PROPERTY;
import static org.opensearch.searchrelevance.bwc.IndexMappingTestHelper.CLIENT_TIMEOUT_VALUE;
import static org.opensearch.searchrelevance.bwc.IndexMappingTestHelper.OLD_CLUSTER;
import static org.opensearch.searchrelevance.bwc.IndexMappingTestHelper.RESTART_UPGRADE_BWC_PREFIX;
import static org.opensearch.searchrelevance.bwc.IndexMappingTestHelper.RESTART_UPGRADE_JUDGMENT_PREFIX;
import static org.opensearch.searchrelevance.bwc.IndexMappingTestHelper.RESTART_UPGRADE_QUERYSET_PREFIX;
import static org.opensearch.searchrelevance.bwc.IndexMappingTestHelper.RESTART_UPGRADE_SEARCH_CONFIG_PREFIX;
import static org.opensearch.searchrelevance.bwc.IndexMappingTestHelper.UPGRADED_CLUSTER;

import java.util.Locale;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.opensearch.common.settings.Settings;
import org.opensearch.searchrelevance.bwc.IndexMappingTestHelper;
import org.opensearch.test.rest.OpenSearchRestTestCase;

/**
 * Base class for Search Relevance BWC (Backward Compatibility) tests during full cluster restart upgrades.
 * Provides common utilities and cluster state management for testing compatibility across versions.
 *
 * Unlike rolling upgrades, restart upgrades shut down all nodes at once and restart them
 * with the new version. This tests a different upgrade path that some users may take.
 */
public abstract class AbstractSearchRelevanceRestartUpgradeTestCase extends OpenSearchRestTestCase {

    protected static final Logger logger = LogManager.getLogger(AbstractSearchRelevanceRestartUpgradeTestCase.class);

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
        return ClusterType.instance(System.getProperty(BWC_CLUSTER_TYPE_PROPERTY));
    }

    /**
     * Customizes REST client settings to accommodate restart upgrade scenarios.
     * Increases socket timeout to handle delays during cluster transitions.
     *
     * @return Settings with extended client socket timeout
     */
    @Override
    protected final Settings restClientSettings() {
        return Settings.builder()
            .put(super.restClientSettings())
            .put(OpenSearchRestTestCase.CLIENT_SOCKET_TIMEOUT, CLIENT_TIMEOUT_VALUE)
            .build();
    }

    /**
     * Gets the index name for the test with a prefix to identify BWC test resources.
     *
     * @return Index name prefixed with restart upgrade BWC prefix
     */
    protected String getIndexNameForTest() {
        return String.format(Locale.ROOT, "%s%s", RESTART_UPGRADE_BWC_PREFIX, getTestName().toLowerCase(Locale.ROOT));
    }

    /**
     * Gets the query set name for the test with a prefix to identify BWC test resources.
     *
     * @return Query set name prefixed with restart upgrade query set prefix
     */
    protected String getQuerySetNameForTest() {
        return String.format(Locale.ROOT, "%s%s", RESTART_UPGRADE_QUERYSET_PREFIX, getTestName().toLowerCase(Locale.ROOT));
    }

    /**
     * Gets the judgment name for the test with a prefix to identify BWC test resources.
     *
     * @return Judgment name prefixed with restart upgrade judgment prefix
     */
    protected String getJudgmentNameForTest() {
        return String.format(Locale.ROOT, "%s%s", RESTART_UPGRADE_JUDGMENT_PREFIX, getTestName().toLowerCase(Locale.ROOT));
    }

    /**
     * Gets the search configuration name for the test with a prefix to identify BWC test resources.
     *
     * @return Search configuration name prefixed with restart upgrade search config prefix
     */
    protected String getSearchConfigNameForTest() {
        return String.format(Locale.ROOT, "%s%s", RESTART_UPGRADE_SEARCH_CONFIG_PREFIX, getTestName().toLowerCase(Locale.ROOT));
    }

    /**
     * Gets the BWC (backward compatible) version being tested.
     * This is the older version that we're upgrading from.
     *
     * @return The BWC version string
     */
    protected String getBWCVersion() {
        return System.getProperty(BWC_VERSION_PROPERTY);
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

    /**
     * Cleans up test resources after test completion.
     * This method should be called in a finally block to ensure cleanup happens
     * even if the test fails.
     *
     * @param indexName The name of the index to delete, or null to skip
     */
    protected void wipeOfTestResources(final String indexName) {
        if (indexName != null) {
            IndexMappingTestHelper.deleteIndex(client(), indexName, logger);
        }
    }
}
