/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */
package org.opensearch.searchrelevance.model;

import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import org.opensearch.common.xcontent.XContentFactory;
import org.opensearch.common.xcontent.XContentHelper;
import org.opensearch.common.xcontent.XContentType;
import org.opensearch.core.common.bytes.BytesReference;
import org.opensearch.core.xcontent.ToXContent;
import org.opensearch.core.xcontent.XContentBuilder;
import org.opensearch.test.OpenSearchTestCase;

/**
 * Tests for {@link QuerySetEntry}, covering new format, legacy format parsing,
 * backward compatibility, and edge cases.
 */
public class QuerySetEntryTests extends OpenSearchTestCase {

    // ============================================
    // fromStoredMap — New Format Tests
    // ============================================

    public void testFromStoredMap_NewFormat_WithCustomFields() {
        Map<String, Object> entryMap = new HashMap<>();
        entryMap.put("queryText", "red shoes");
        Map<String, Object> customFields = new HashMap<>();
        customFields.put("referenceAnswer", "High quality leather shoes");
        customFields.put("category", "footwear");
        entryMap.put("customFields", customFields);

        QuerySetEntry entry = QuerySetEntry.fromStoredMap(entryMap);

        assertEquals("red shoes", entry.queryText());
        assertEquals(2, entry.customFields().size());
        assertEquals("High quality leather shoes", entry.customFields().get("referenceAnswer"));
        assertEquals("footwear", entry.customFields().get("category"));
    }

    public void testFromStoredMap_NewFormat_EmptyCustomFields() {
        Map<String, Object> entryMap = new HashMap<>();
        entryMap.put("queryText", "What is C#?");
        entryMap.put("customFields", Collections.emptyMap());

        QuerySetEntry entry = QuerySetEntry.fromStoredMap(entryMap);

        // Must NOT trigger legacy parsing — queryText must be preserved as-is
        assertEquals("What is C#?", entry.queryText());
        assertTrue(entry.customFields().isEmpty());
    }

    public void testFromStoredMap_NewFormat_NullCustomFields() {
        Map<String, Object> entryMap = new HashMap<>();
        entryMap.put("queryText", "What is C#?");
        entryMap.put("customFields", null);

        QuerySetEntry entry = QuerySetEntry.fromStoredMap(entryMap);

        // customFields key is present (even though null) — must NOT trigger legacy parsing
        assertEquals("What is C#?", entry.queryText());
        assertTrue(entry.customFields().isEmpty());
    }

    public void testFromStoredMap_NewFormat_HashInQueryText_NoLegacyParsing() {
        // Critical edge case: new-format doc with # in queryText and customFields key present
        Map<String, Object> entryMap = new HashMap<>();
        entryMap.put("queryText", "C# programming guide");
        entryMap.put("customFields", Collections.emptyMap());

        QuerySetEntry entry = QuerySetEntry.fromStoredMap(entryMap);

        assertEquals("C# programming guide", entry.queryText());
        assertTrue(entry.customFields().isEmpty());
    }

    public void testFromStoredMap_NewFormat_QueryTextOnly_NoCustomFieldsKey() {
        // New-format doc with just queryText and no customFields key at all
        // This could be a legacy doc OR a new doc that somehow lost the key.
        // Without the key, legacy parsing is attempted — but for plain text
        // without #, it returns the queryText unchanged.
        Map<String, Object> entryMap = new HashMap<>();
        entryMap.put("queryText", "simple query");

        QuerySetEntry entry = QuerySetEntry.fromStoredMap(entryMap);

        assertEquals("simple query", entry.queryText());
        assertTrue(entry.customFields().isEmpty());
    }

    // ============================================
    // fromStoredMap — Legacy Format Tests
    // ============================================

    public void testFromStoredMap_LegacyFormat_JsonCustomFields() {
        // Legacy format: queryText#{\"key\":\"value\"}
        Map<String, Object> entryMap = new HashMap<>();
        entryMap.put("queryText", "red shoes#{\"referenceAnswer\":\"leather shoes\",\"color\":\"red\"}");

        QuerySetEntry entry = QuerySetEntry.fromStoredMap(entryMap);

        assertEquals("red shoes", entry.queryText());
        assertEquals(2, entry.customFields().size());
        assertEquals("leather shoes", entry.customFields().get("referenceAnswer"));
        assertEquals("red", entry.customFields().get("color"));
    }

    public void testFromStoredMap_LegacyFormat_PlainReferenceAnswer() {
        // Legacy format: queryText#referenceAnswer
        Map<String, Object> entryMap = new HashMap<>();
        entryMap.put("queryText", "What is OpenSearch?#OpenSearch is a search suite");

        QuerySetEntry entry = QuerySetEntry.fromStoredMap(entryMap);

        assertEquals("What is OpenSearch?", entry.queryText());
        assertEquals(1, entry.customFields().size());
        assertEquals("OpenSearch is a search suite", entry.customFields().get("referenceAnswer"));
    }

    public void testFromStoredMap_LegacyFormat_TrailingHash() {
        // Legacy format with trailing # but no content after it
        Map<String, Object> entryMap = new HashMap<>();
        entryMap.put("queryText", "test query#");

        QuerySetEntry entry = QuerySetEntry.fromStoredMap(entryMap);

        // No content after # — no legacy parsing triggered, original queryText preserved
        assertEquals("test query#", entry.queryText());
        assertTrue(entry.customFields().isEmpty());
    }

    public void testFromStoredMap_LegacyFormat_HashOnly() {
        Map<String, Object> entryMap = new HashMap<>();
        entryMap.put("queryText", "#");

        QuerySetEntry entry = QuerySetEntry.fromStoredMap(entryMap);

        // Single # with nothing after — no parsing
        assertEquals("#", entry.queryText());
        assertTrue(entry.customFields().isEmpty());
    }

    public void testFromStoredMap_LegacyFormat_InvalidJson() {
        // After # looks like JSON but isn't valid — falls through to plain text
        Map<String, Object> entryMap = new HashMap<>();
        entryMap.put("queryText", "query#{not valid json}");

        QuerySetEntry entry = QuerySetEntry.fromStoredMap(entryMap);

        // Starts with { and ends with } but fails JSON parse — falls to plain text
        assertEquals("query", entry.queryText());
        assertEquals("{not valid json}", entry.customFields().get("referenceAnswer"));
    }

    // ============================================
    // fromStoredMap — Null / Empty Input
    // ============================================

    public void testFromStoredMap_NullQueryText() {
        Map<String, Object> entryMap = new HashMap<>();
        entryMap.put("queryText", null);

        QuerySetEntry entry = QuerySetEntry.fromStoredMap(entryMap);

        assertNull(entry.queryText());
        assertTrue(entry.customFields().isEmpty());
    }

    public void testFromStoredMap_EmptyQueryText() {
        Map<String, Object> entryMap = new HashMap<>();
        entryMap.put("queryText", "");

        QuerySetEntry entry = QuerySetEntry.fromStoredMap(entryMap);

        assertEquals("", entry.queryText());
        assertTrue(entry.customFields().isEmpty());
    }

    // ============================================
    // toXContent — Serialization Tests
    // ============================================

    public void testToXContent_AlwaysWritesCustomFieldsKey() throws IOException {
        // Even with empty customFields, the key must be written for disambiguation
        QuerySetEntry entry = new QuerySetEntry("test query", Collections.emptyMap());

        XContentBuilder builder = XContentFactory.jsonBuilder();
        entry.toXContent(builder, ToXContent.EMPTY_PARAMS);

        Map<String, Object> serialized = XContentHelper.convertToMap(BytesReference.bytes(builder), false, XContentType.JSON).v2();

        assertTrue("customFields key must always be present in serialized output", serialized.containsKey("customFields"));
        assertEquals("test query", serialized.get("queryText"));
    }

    public void testToXContent_WithCustomFields() throws IOException {
        Map<String, String> customFields = new HashMap<>();
        customFields.put("referenceAnswer", "answer text");
        QuerySetEntry entry = new QuerySetEntry("my query", customFields);

        XContentBuilder builder = XContentFactory.jsonBuilder();
        entry.toXContent(builder, ToXContent.EMPTY_PARAMS);

        Map<String, Object> serialized = XContentHelper.convertToMap(BytesReference.bytes(builder), false, XContentType.JSON).v2();

        assertEquals("my query", serialized.get("queryText"));
        Map<String, Object> cf = (Map<String, Object>) serialized.get("customFields");
        assertNotNull(cf);
        assertEquals("answer text", cf.get("referenceAnswer"));
    }

    // ============================================
    // Round-trip: toXContent → fromStoredMap
    // ============================================

    public void testRoundTrip_WithCustomFields() throws IOException {
        Map<String, String> customFields = new HashMap<>();
        customFields.put("referenceAnswer", "OpenSearch is great");
        customFields.put("category", "tech");
        QuerySetEntry original = new QuerySetEntry("What is OpenSearch?", customFields);

        // Serialize
        XContentBuilder builder = XContentFactory.jsonBuilder();
        original.toXContent(builder, ToXContent.EMPTY_PARAMS);
        Map<String, Object> serialized = XContentHelper.convertToMap(BytesReference.bytes(builder), false, XContentType.JSON).v2();

        // Deserialize
        QuerySetEntry roundTripped = QuerySetEntry.fromStoredMap(serialized);

        assertEquals(original.queryText(), roundTripped.queryText());
        assertEquals(original.customFields().size(), roundTripped.customFields().size());
        assertEquals(original.customFields().get("referenceAnswer"), roundTripped.customFields().get("referenceAnswer"));
        assertEquals(original.customFields().get("category"), roundTripped.customFields().get("category"));
    }

    public void testRoundTrip_EmptyCustomFields() throws IOException {
        QuerySetEntry original = new QuerySetEntry("What is C#?", Collections.emptyMap());

        XContentBuilder builder = XContentFactory.jsonBuilder();
        original.toXContent(builder, ToXContent.EMPTY_PARAMS);
        Map<String, Object> serialized = XContentHelper.convertToMap(BytesReference.bytes(builder), false, XContentType.JSON).v2();

        QuerySetEntry roundTripped = QuerySetEntry.fromStoredMap(serialized);

        // Critical: queryText with # must survive the round trip unmodified
        assertEquals("What is C#?", roundTripped.queryText());
        assertTrue(roundTripped.customFields().isEmpty());
    }

    // ============================================
    // customFields() immutability
    // ============================================

    public void testCustomFields_ReturnsUnmodifiableMap() {
        Map<String, String> customFields = new HashMap<>();
        customFields.put("key", "value");
        QuerySetEntry entry = new QuerySetEntry("query", customFields);

        expectThrows(UnsupportedOperationException.class, () -> entry.customFields().put("new", "value"));
    }

    // ============================================
    // Builder Tests
    // ============================================

    public void testBuilder_WithCustomFields() {
        Map<String, String> customFields = Map.of("key", "value");
        QuerySetEntry entry = QuerySetEntry.Builder.builder().queryText("test").customFields(customFields).build();

        assertEquals("test", entry.queryText());
        assertEquals("value", entry.customFields().get("key"));
    }

    public void testBuilder_NullCustomFields() {
        QuerySetEntry entry = QuerySetEntry.Builder.builder().queryText("test").customFields(null).build();

        assertEquals("test", entry.queryText());
        assertTrue(entry.customFields().isEmpty());
    }

    public void testBuilder_FromExistingEntry() {
        Map<String, String> customFields = Map.of("k", "v");
        QuerySetEntry original = new QuerySetEntry("q", customFields);
        QuerySetEntry copy = QuerySetEntry.Builder.builder(original).build();

        assertEquals(original.queryText(), copy.queryText());
        assertEquals(original.customFields(), copy.customFields());
    }
}
