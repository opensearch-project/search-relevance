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
import org.opensearch.common.unit.TimeValue;
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
    @Getter
    private volatile boolean isScheduledExperimentsEnabled;
    @Getter
    private volatile TimeValue scheduledExperimentsTimeout;
    @Getter
    private volatile TimeValue scheduledExperimentsMinimumInterval;

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
        isScheduledExperimentsEnabled = SearchRelevanceSettings.SEARCH_RELEVANCE_SCHEDULED_EXPERIMENTS_ENABLED.get(settings);
        scheduledExperimentsTimeout = SearchRelevanceSettings.SEARCH_RELEVANCE_SCHEDULED_EXPERIMENTS_TIMEOUT.get(settings);
        scheduledExperimentsMinimumInterval = SearchRelevanceSettings.SEARCH_RELEVANCE_SCHEDULED_EXPERIMENTS_MINIMUM_INTERVAL.get(settings);
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

        clusterService.getClusterSettings()
            .addSettingsUpdateConsumer(SearchRelevanceSettings.SEARCH_RELEVANCE_SCHEDULED_EXPERIMENTS_ENABLED, value -> {
                isScheduledExperimentsEnabled = value;
            });

        clusterService.getClusterSettings()
            .addSettingsUpdateConsumer(SearchRelevanceSettings.SEARCH_RELEVANCE_SCHEDULED_EXPERIMENTS_TIMEOUT, value -> {
                scheduledExperimentsTimeout = value;
            });

        clusterService.getClusterSettings()
            .addSettingsUpdateConsumer(SearchRelevanceSettings.SEARCH_RELEVANCE_SCHEDULED_EXPERIMENTS_MINIMUM_INTERVAL, value -> {
                scheduledExperimentsMinimumInterval = value;
            });
    }
}
