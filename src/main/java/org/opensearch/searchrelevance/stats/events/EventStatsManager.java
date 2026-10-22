/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */
package org.opensearch.searchrelevance.stats.events;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;

import org.opensearch.searchrelevance.settings.SearchRelevanceSettingsAccessor;
import org.opensearch.searchrelevance.stats.common.StatSnapshot;

import lombok.NoArgsConstructor;

/**
 * Singleton manager class for event stats, used to increment and store event stat related data
 */
@NoArgsConstructor
public class EventStatsManager {
    private static EventStatsManager INSTANCE;
    private SearchRelevanceSettingsAccessor settingsAccessor;

    /**
     * Initializes dependencies for the EventStats manager
     * @param settingsAccessor
     */
    public void initialize(SearchRelevanceSettingsAccessor settingsAccessor) {
        this.settingsAccessor = settingsAccessor;
    }

    /**
     * Returns the singleton instance of EventStatsManager.
     * Creates a new instance with default settings if one doesn't exist.
     *
     * @return The singleton instance of EventStatsManager
     */
    public static EventStatsManager instance() {
        if (INSTANCE == null) {
            INSTANCE = new EventStatsManager();
        }
        return INSTANCE;
    }

    /**
     * Static helper to increments the counter for a specified event statistic on the singleton
     *
     * @param eventStatName The name of the event stat to increment
     */
    public static void increment(EventStatName eventStatName) {
        instance().inc(eventStatName);
    }

    /**
     *  Instance level method to increment the counter for a specified event statistic.
     *  Treated as a NOOP if stats are disabled
     *
     * @param eventStatName The name of the event stat to increment
     */
    public void inc(EventStatName eventStatName) {
        if (settingsAccessor.isStatsEnabled()) {
            eventStatName.getEventStat().increment();
        }
    }

    /**
     * Retrieves snapshots of specified event statistics.
     *
     * @param statsToRetrieve Set of event stat names to retrieve data for
     * @return Map of event stat names to their current snapshots
     */
    public Map<EventStatName, TimestampedEventStatSnapshot> getTimestampedEventStatSnapshots(EnumSet<EventStatName> statsToRetrieve) {
        // Filter stats based on passed in collection
        Map<EventStatName, TimestampedEventStatSnapshot> eventStatsDataMap = new HashMap<>();
        for (EventStatName statName : statsToRetrieve) {
            if (statName.getStatType() == EventStatType.TIMESTAMPED_EVENT_COUNTER) {
                StatSnapshot<?> snapshot = statName.getEventStat().getStatSnapshot();
                if (snapshot instanceof TimestampedEventStatSnapshot) {
                    // Get event data snapshot
                    eventStatsDataMap.put(statName, (TimestampedEventStatSnapshot) snapshot);
                }
            }
        }
        return eventStatsDataMap;
    }

    /**
     * Resets all statistics counters to their initial state.
     * Called when stats_enabled cluster setting is toggled off
     */
    public void reset() {
        for (EventStatName statName : EventStatName.values()) {
            statName.getEventStat().reset();
        }
    }
}
