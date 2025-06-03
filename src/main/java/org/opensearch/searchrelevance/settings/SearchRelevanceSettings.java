/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */
package org.opensearch.searchrelevance.settings;

import org.opensearch.common.settings.Setting;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/**
 * Class defines settings specific to search-relevance plugin
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class SearchRelevanceSettings {

    /**
     * Gates the functionality of search relevance workbench
     * By defaulted, we enable the functionalities
     */
    public static final String SEARCH_RELEVANCE_WORKBENCH_ENABLED_KEY = "plugins.search_relevance.workbench_enabled";
    public static final Setting<Boolean> SEARCH_RELEVANCE_WORKBENCH_ENABLED = Setting.boolSetting(
        SEARCH_RELEVANCE_WORKBENCH_ENABLED_KEY,
        false,
        Setting.Property.NodeScope,
        Setting.Property.Dynamic
    );

}
