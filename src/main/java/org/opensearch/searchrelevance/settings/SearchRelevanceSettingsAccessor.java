/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */
package org.opensearch.searchrelevance.settings;

import org.opensearch.cluster.service.ClusterService;
import org.opensearch.common.inject.Inject;
import org.opensearch.common.settings.Settings;
import org.opensearch.searchrelevance.stats.events.EventStatsManager;

import lombok.Getter;

/**
 * Class handles exposing settings related to search relevance and manages callbacks when the settings change
 */
public class SearchRelevanceSettingsAccessor {
    @Getter
    private volatile boolean isWorkbenchEnabled;
    @Getter
    private volatile boolean isStatsEnabled;
    @Getter
    private volatile int maxQuerySetAllowed;

    /**
     * Constructor, registers callbacks to update settings
     * @param clusterService
     * @param settings
     */
    @Inject
    public SearchRelevanceSettingsAccessor(ClusterService clusterService, Settings settings) {
        isWorkbenchEnabled = SearchRelevanceSettings.SEARCH_RELEVANCE_WORKBENCH_ENABLED.get(settings);
        isStatsEnabled = SearchRelevanceSettings.SEARCH_RELEVANCE_STATS_ENABLED.get(settings);
        maxQuerySetAllowed = SearchRelevanceSettings.SEARCH_RELEVANCE_QUERY_SET_MAX_LIMIT.get(settings);
        registerSettingsCallbacks(clusterService);
    }

    private void registerSettingsCallbacks(ClusterService clusterService) {
        clusterService.getClusterSettings().addSettingsUpdateConsumer(SearchRelevanceSettings.SEARCH_RELEVANCE_WORKBENCH_ENABLED, value -> {
            isWorkbenchEnabled = value;
        });

        clusterService.getClusterSettings().addSettingsUpdateConsumer(SearchRelevanceSettings.SEARCH_RELEVANCE_STATS_ENABLED, value -> {
            // If stats are being toggled off, clear and reset all stats
            if (isStatsEnabled && (value == false)) {
                EventStatsManager.instance().reset();
            }
            isStatsEnabled = value;
        });

        clusterService.getClusterSettings()
            .addSettingsUpdateConsumer(SearchRelevanceSettings.SEARCH_RELEVANCE_QUERY_SET_MAX_LIMIT, value -> {
                maxQuerySetAllowed = value;
            });
    }
}
