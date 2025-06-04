/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */
package org.opensearch.searchrelevance.stats.info;

import static org.opensearch.searchrelevance.util.TestUtils.xContentBuilderToMap;

import java.io.IOException;
import java.util.Map;

import org.opensearch.common.xcontent.json.JsonXContent;
import org.opensearch.core.xcontent.ToXContent;
import org.opensearch.core.xcontent.XContentBuilder;
import org.opensearch.searchrelevance.stats.common.StatSnapshot;
import org.opensearch.test.OpenSearchTestCase;

public class SettableInfoStatSnapshotTests extends OpenSearchTestCase {

    private static final InfoStatName STAT_NAME = InfoStatName.CLUSTER_VERSION;
    private static final String SETTABLE_VALUE = "test-value";

    public void test_constructorWithoutValue() {
        SettableInfoStatSnapshot<String> snapshot = new SettableInfoStatSnapshot<>(STAT_NAME);
        assertNull(snapshot.getValue());
    }

    public void test_constructorWithValue() {
        SettableInfoStatSnapshot<String> snapshot = new SettableInfoStatSnapshot<>(STAT_NAME, SETTABLE_VALUE);
        assertEquals(SETTABLE_VALUE, snapshot.getValue());
    }

    public void test_setValueUpdates() {
        SettableInfoStatSnapshot<String> snapshot = new SettableInfoStatSnapshot<>(STAT_NAME);
        snapshot.setValue("new-value");
        assertEquals("new-value", snapshot.getValue());
    }

    public void test_toXContent() throws IOException {
        SettableInfoStatSnapshot<String> snapshot = new SettableInfoStatSnapshot<>(STAT_NAME, SETTABLE_VALUE);
        XContentBuilder builder = JsonXContent.contentBuilder();
        snapshot.toXContent(builder, ToXContent.EMPTY_PARAMS);

        Map<String, Object> responseMap = xContentBuilderToMap(builder);

        assertEquals(SETTABLE_VALUE, responseMap.get(StatSnapshot.VALUE_FIELD));
        assertEquals(STAT_NAME.getStatType().getTypeString(), responseMap.get(StatSnapshot.STAT_TYPE_FIELD));
    }
}
