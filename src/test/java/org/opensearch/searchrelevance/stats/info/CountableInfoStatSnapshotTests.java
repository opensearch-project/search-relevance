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

public class CountableInfoStatSnapshotTests extends OpenSearchTestCase {
    private static final InfoStatName STAT_NAME = InfoStatName.CLUSTER_VERSION;

    public void test_increment() {
        CountableInfoStatSnapshot snapshot = new CountableInfoStatSnapshot(STAT_NAME);
        assertEquals(0L, snapshot.getValue().longValue());
        snapshot.incrementBy(5L);
        assertEquals(5L, snapshot.getValue().longValue());
        snapshot.incrementBy(3L);
        assertEquals(8L, snapshot.getValue().longValue());
    }

    public void test_toXContent() throws IOException {
        CountableInfoStatSnapshot snapshot = new CountableInfoStatSnapshot(STAT_NAME);
        snapshot.incrementBy(8675309L);

        XContentBuilder builder = JsonXContent.contentBuilder();
        snapshot.toXContent(builder, ToXContent.EMPTY_PARAMS);

        Map<String, Object> responseMap = xContentBuilderToMap(builder);

        assertEquals(8675309, responseMap.get(StatSnapshot.VALUE_FIELD));
        assertEquals(STAT_NAME.getStatType().getTypeString(), responseMap.get(StatSnapshot.STAT_TYPE_FIELD));
    }
}
