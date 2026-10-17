/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */
package org.opensearch.searchrelevance.stats.events;

import static org.mockito.Mockito.when;

import java.util.EnumSet;
import java.util.Map;

import org.junit.Before;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.opensearch.searchrelevance.settings.SearchRelevanceSettingsAccessor;
import org.opensearch.test.OpenSearchTestCase;

public class EventStatsManagerTests extends OpenSearchTestCase {
    private static final EventStatName STAT_NAME = EventStatName.LLM_JUDGMENT_RATING_GENERATIONS;

    @Mock
    private SearchRelevanceSettingsAccessor mockSettingsAccessor;

    private EventStatsManager eventStatsManager;

    @Before
    public void setup() {
        MockitoAnnotations.openMocks(this);
        eventStatsManager = new EventStatsManager();
        eventStatsManager.initialize(mockSettingsAccessor);
    }

    public void test_increment() {
        when(mockSettingsAccessor.isStatsEnabled()).thenReturn(true);

        EventStat originalStat = STAT_NAME.getEventStat();
        long originalValue = originalStat.getValue();

        eventStatsManager.inc(STAT_NAME);

        long newValue = originalStat.getValue();
        assertEquals(originalValue + 1, newValue);
    }

    public void test_incrementWhenStatsDisabled() {
        when(mockSettingsAccessor.isStatsEnabled()).thenReturn(false);

        EventStat originalStat = STAT_NAME.getEventStat();
        long originalValue = originalStat.getValue();

        eventStatsManager.inc(STAT_NAME);

        long newValue = originalStat.getValue();
        assertEquals(originalValue, newValue);
    }

    public void test_getTimestampedEventStatSnapshots() {
        Map<EventStatName, TimestampedEventStatSnapshot> result = eventStatsManager.getTimestampedEventStatSnapshots(EnumSet.of(STAT_NAME));

        assertEquals(1, result.size());
        assertNotNull(result.get(STAT_NAME));
    }

    public void test_getTimestampedEventStatSnapshotsReturnsEmptyMap() {
        Map<EventStatName, TimestampedEventStatSnapshot> result = eventStatsManager.getTimestampedEventStatSnapshots(
            EnumSet.noneOf(EventStatName.class)
        );

        assertTrue(result.isEmpty());
    }

    public void test_reset() {
        when(mockSettingsAccessor.isStatsEnabled()).thenReturn(true);
        EventStat originalStat = STAT_NAME.getEventStat();
        long originalValue = originalStat.getValue();
        eventStatsManager.inc(STAT_NAME);
        eventStatsManager.inc(STAT_NAME);
        eventStatsManager.inc(STAT_NAME);

        long newValue = originalStat.getValue();
        assertEquals(originalValue + 3, newValue);

        eventStatsManager.reset();

        newValue = originalStat.getValue();
        assertEquals(newValue, 0);
    }

    public void test_singletonInstanceCreation() {
        // Test that multiple calls return same instance
        EventStatsManager instance1 = EventStatsManager.instance();
        EventStatsManager instance2 = EventStatsManager.instance();

        assertNotNull(instance1);
        assertSame(instance1, instance2);
    }
}
