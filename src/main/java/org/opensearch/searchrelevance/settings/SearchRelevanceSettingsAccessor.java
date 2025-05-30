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

import lombok.Getter;

/**
 * Class handles exposing settings related to search relevance and manages callbacks when the settings change
 */
public class SearchRelevanceSettingsAccessor {
    @Getter
    private volatile boolean isWorkbenchEnabled;

    /**
     * Constructor, registers callbacks to update settings
     * @param clusterService
     * @param settings
     */
    @Inject
    public SearchRelevanceSettingsAccessor(ClusterService clusterService, Settings settings) {
        isWorkbenchEnabled = SearchRelevanceSettings.SEARCH_RELEVANCE_WORKBENCH_ENABLED.get(settings);
        registerSettingsCallbacks(clusterService);
    }

    private void registerSettingsCallbacks(ClusterService clusterService) {
        clusterService.getClusterSettings().addSettingsUpdateConsumer(SearchRelevanceSettings.SEARCH_RELEVANCE_WORKBENCH_ENABLED, value -> {
            isWorkbenchEnabled = value;
        });
    }
}
