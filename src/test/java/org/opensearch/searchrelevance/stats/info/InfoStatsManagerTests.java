/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */
package org.opensearch.searchrelevance.stats.info;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

import org.junit.Before;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.opensearch.searchrelevance.settings.SearchRelevanceSettingsAccessor;
import org.opensearch.searchrelevance.stats.common.StatSnapshot;
import org.opensearch.test.OpenSearchTestCase;

public class InfoStatsManagerTests extends OpenSearchTestCase {
    @Mock
    private SearchRelevanceSettingsAccessor mockSettingsAccessor;

    private InfoStatsManager infoStatsManager;

    @Before
    public void setup() {
        MockitoAnnotations.openMocks(this);
        infoStatsManager = new InfoStatsManager(mockSettingsAccessor);
    }

    public void test_getStats_returnsAllStats() {
        Map<InfoStatName, StatSnapshot<?>> stats = infoStatsManager.getStats(EnumSet.allOf(InfoStatName.class));
        Set<InfoStatName> allStatNames = EnumSet.allOf(InfoStatName.class);

        assertEquals(allStatNames, stats.keySet());
    }

    public void test_getStats_returnsFilteredStats() {
        Map<InfoStatName, StatSnapshot<?>> stats = infoStatsManager.getStats(EnumSet.of(InfoStatName.CLUSTER_VERSION));

        assertEquals(1, stats.size());
        assertTrue(stats.containsKey(InfoStatName.CLUSTER_VERSION));
        assertNotNull(((SettableInfoStatSnapshot<?>) stats.get(InfoStatName.CLUSTER_VERSION)).getValue());
    }
}
