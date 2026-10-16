/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */
package org.opensearch.searchrelevance.model;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.opensearch.common.xcontent.XContentFactory;
import org.opensearch.common.xcontent.XContentHelper;
import org.opensearch.common.xcontent.XContentType;
import org.opensearch.core.common.bytes.BytesReference;
import org.opensearch.core.xcontent.ToXContent;
import org.opensearch.core.xcontent.XContentBuilder;
import org.opensearch.searchrelevance.judgments.JudgmentDataTransformer;
import org.opensearch.test.OpenSearchTestCase;

public class JudgmentTests extends OpenSearchTestCase {

    @SuppressWarnings("unchecked")
    private Map<String, Object> serialize(List<Map<String, Object>> judgmentRatings) throws IOException {
        Judgment judgment = new Judgment(
            "id",
            "2024-01-01T00:00:00Z",
            "name",
            AsyncStatus.COMPLETED,
            JudgmentType.LLM_JUDGMENT,
            Map.of(),
            judgmentRatings
        );
        XContentBuilder builder = XContentFactory.jsonBuilder();
        judgment.toXContent(builder, ToXContent.EMPTY_PARAMS);
        return XContentHelper.convertToMap(BytesReference.bytes(builder), false, XContentType.JSON).v2();
    }

    private Map<String, Object> entry(String query, List<Map<String, Object>> ratings) {
        Map<String, Object> entry = new HashMap<>();
        entry.put("query", query);
        entry.put("ratings", ratings);
        return entry;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> firstEntry(Map<String, Object> serialized) {
        List<Map<String, Object>> judgmentRatings = (List<Map<String, Object>>) serialized.get("judgmentRatings");
        assertEquals(1, judgmentRatings.size());
        return judgmentRatings.get(0);
    }

    @SuppressWarnings("unchecked")
    public void testToXContentWritesRatings() throws IOException {
        Map<String, Object> entry = entry("laptop", List.of(Map.of("docId", "A", "rating", "2"), Map.of("docId", "B", "rating", "1")));

        Map<String, Object> outEntry = firstEntry(serialize(List.of(entry)));

        assertEquals("laptop", outEntry.get("query"));
        List<Map<String, Object>> ratings = (List<Map<String, Object>>) outEntry.get("ratings");
        assertEquals(2, ratings.size());
        assertFalse("no failures key when none present", outEntry.containsKey("failures"));
    }

    @SuppressWarnings("unchecked")
    public void testToXContentWritesFailuresWhenPresent() throws IOException {
        Map<String, Object> entry = entry("laptop", List.of(Map.of("docId", "A", "rating", "2")));
        entry.put("failures", List.of(Map.of("docId", "C")));

        Map<String, Object> outEntry = firstEntry(serialize(List.of(entry)));

        List<Map<String, Object>> failures = (List<Map<String, Object>>) outEntry.get("failures");
        assertNotNull(failures);
        assertEquals(1, failures.size());
        assertEquals("C", failures.get(0).get("docId"));
    }

    public void testToXContentOmitsFailuresWhenEmptyList() throws IOException {
        Map<String, Object> entry = entry("laptop", List.of(Map.of("docId", "A", "rating", "2")));
        entry.put("failures", List.of());

        Map<String, Object> outEntry = firstEntry(serialize(List.of(entry)));

        assertFalse("empty failures list should not be written", outEntry.containsKey("failures"));
    }

    public void testToXContentDoesNotPersistFailureReason() throws IOException {
        // The failure reason is a transient field used only for the metadata overview; it must not
        // be written onto the stored judgment entry.
        Map<String, Object> entry = entry("laptop", List.of());
        entry.put("failures", List.of(Map.of("docId", "A")));
        entry.put(JudgmentDataTransformer.RESULT_FAILURE_REASON, "model timed out");

        Map<String, Object> outEntry = firstEntry(serialize(List.of(entry)));

        assertFalse(outEntry.containsKey(JudgmentDataTransformer.RESULT_FAILURE_REASON));
        assertEquals(1, ((List<?>) outEntry.get("failures")).size());
    }

    public void testToXContentAllDocsFailed() throws IOException {
        // A fully failed query: no ratings, every sent doc listed as a failure.
        Map<String, Object> entry = entry("laptop", List.of());
        entry.put("failures", List.of(Map.of("docId", "A"), Map.of("docId", "B")));

        Map<String, Object> outEntry = firstEntry(serialize(List.of(entry)));

        assertTrue(((List<?>) outEntry.get("ratings")).isEmpty());
        assertEquals(2, ((List<?>) outEntry.get("failures")).size());
    }
}
