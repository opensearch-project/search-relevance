/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */
package org.opensearch.searchrelevance.settings;

import org.opensearch.common.settings.Setting;
import org.opensearch.common.unit.TimeValue;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/**
 * Class defines settings specific to search-relevance plugin
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class SearchRelevanceSettings {

    /**
     * Gates the functionality of search relevance workbench
     * By defaulted, we disable the functionalities
     */
    public static final String SEARCH_RELEVANCE_WORKBENCH_ENABLED_KEY = "plugins.search_relevance.workbench_enabled";
    public static final Setting<Boolean> SEARCH_RELEVANCE_WORKBENCH_ENABLED = Setting.boolSetting(
        SEARCH_RELEVANCE_WORKBENCH_ENABLED_KEY,
        true,
        Setting.Property.NodeScope,
        Setting.Property.Dynamic
    );

    /**
     * Enables or disables the Stats API and event stat collection.
     * If stats API is called when stats are disabled, the response will 403.
     * Event stat increment calls are also treated as no-ops.
     */
    public static final Setting<Boolean> SEARCH_RELEVANCE_STATS_ENABLED = Setting.boolSetting(
        "plugins.search_relevance.stats_enabled",
        true,
        Setting.Property.NodeScope,
        Setting.Property.Dynamic
    );

    /**
     * Gates the maximum limit of search relevance query set size
     * The defaultValue is 1,000
     */
    public static final String SEARCH_RELEVANCE_QUERY_SET_MAX_LIMIT_KEY = "plugins.search_relevance.query_set.maximum";
    public static final Setting<Integer> SEARCH_RELEVANCE_QUERY_SET_MAX_LIMIT = Setting.intSetting(
        SEARCH_RELEVANCE_QUERY_SET_MAX_LIMIT_KEY,
        1000,
        Setting.Property.NodeScope,
        Setting.Property.Dynamic
    );

    /**
     * Enables or disables the scheduled experiment feature. When disabled,
     * Any experiments that were already scheduled will still run, but any
     * updates to the scheduled experiments or any new scheduled experiment requests
     * will be denied.
     */
    public static final Setting<Boolean> SEARCH_RELEVANCE_SCHEDULED_EXPERIMENTS_ENABLED = Setting.boolSetting(
        "plugins.search_relevance.scheduled_experiments_enabled",
        true,
        Setting.Property.NodeScope,
        Setting.Property.Dynamic
    );

    /**
     * When a search evaluation is scheduled to run, there will be an amount of
     * time to wait before automatically cancelling that experiment.
     */
    public static final Setting<TimeValue> SEARCH_RELEVANCE_SCHEDULED_EXPERIMENTS_TIMEOUT = Setting.positiveTimeSetting(
        "plugins.search_relevance.scheduled_experiments_timeout",
        TimeValue.timeValueMinutes(60),
        Setting.Property.NodeScope,
        Setting.Property.Dynamic
    );

    /**
     * The time between the runs of a search experiment scheduled will impact the load
     * on the thread pool and the the CPU of the cluster. The minimum space between the
     * times the jobs are run will be greater than or equal to the minimum
     * interval defined here.
     */
    public static final Setting<TimeValue> SEARCH_RELEVANCE_SCHEDULED_EXPERIMENTS_MINIMUM_INTERVAL = Setting.positiveTimeSetting(
        "plugins.search_relevance.scheduled_experiments_minimum_interval",
        TimeValue.timeValueSeconds(1),
        Setting.Property.NodeScope,
        Setting.Property.Dynamic
    );
}
