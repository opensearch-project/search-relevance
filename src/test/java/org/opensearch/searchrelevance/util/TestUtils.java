/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */
package org.opensearch.searchrelevance.util;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Map;

import org.opensearch.common.xcontent.XContentHelper;
import org.opensearch.core.common.bytes.BytesReference;
import org.opensearch.core.xcontent.XContentBuilder;
import org.opensearch.searchrelevance.settings.SearchRelevanceSettingsAccessor;
import org.opensearch.searchrelevance.stats.events.EventStatsManager;

public class TestUtils {
    /**
     * Convert an xContentBuilder to a map
     * @param xContentBuilder to produce map from
     * @return Map from xContentBuilder
     */
    public static Map<String, Object> xContentBuilderToMap(XContentBuilder xContentBuilder) {
        return XContentHelper.convertToMap(BytesReference.bytes(xContentBuilder), true, xContentBuilder.contentType()).v2();
    }

    /**
     * Initializes static EventStatsManager with correct mocks
     */
    public static void initializeEventStatsManager() {
        SearchRelevanceSettingsAccessor settingsAccessor = mock(SearchRelevanceSettingsAccessor.class);
        EventStatsManager.instance().reset();
        when(settingsAccessor.isStatsEnabled()).thenReturn(true);
        when(settingsAccessor.isWorkbenchEnabled()).thenReturn(true);
        EventStatsManager.instance().initialize(settingsAccessor);
    }

}
