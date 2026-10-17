/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */
package org.opensearch.searchrelevance.utils;

import org.opensearch.test.OpenSearchTestCase;

/**
 * Unit tests for ParserUtils
 */
public class ParserUtilsTests extends OpenSearchTestCase {

    /**
     * Test getDocIdFromCompositeKey with standard composite key format (index::docId)
     */
    public void testGetDocIdFromCompositeKeyWithCompositeFormat() {
        String compositeKey = "test_products::123";
        String docId = ParserUtils.getDocIdFromCompositeKey(compositeKey);
        assertEquals("Should extract docId from composite key", "123", docId);
    }

    /**
     * Test getDocIdFromCompositeKey with multiple :: separators
     * Note: split("::") without limit splits on all occurrences,
     * so this extracts the second element, not everything after first ::
     */
    public void testGetDocIdFromCompositeKeyWithMultipleSeparators() {
        String compositeKey = "index::with::colons::docId123";
        String docId = ParserUtils.getDocIdFromCompositeKey(compositeKey);
        // split("::") returns ["index", "with", "colons", "docId123"], so [1] = "with"
        assertEquals("Should extract second element", "with", docId);
    }

    /**
     * Test getDocIdFromCompositeKey with plain docId (no ::)
     * This is a regression test for the bug where LLM returns plain docIds
     * instead of composite keys, causing ArrayIndexOutOfBoundsException
     */
    public void testGetDocIdFromCompositeKeyWithPlainDocId() {
        String plainDocId = "123";
        String docId = ParserUtils.getDocIdFromCompositeKey(plainDocId);
        assertEquals("Should return plain docId as-is", "123", docId);
    }

    /**
     * Test getDocIdFromCompositeKey with various plain docId formats
     */
    public void testGetDocIdFromCompositeKeyVariousPlainFormats() {
        // Numeric docId
        assertEquals("1", ParserUtils.getDocIdFromCompositeKey("1"));

        // Alphanumeric docId
        assertEquals("abc123", ParserUtils.getDocIdFromCompositeKey("abc123"));

        // UUID-like docId
        assertEquals("550e8400-e29b-41d4-a716-446655440000", ParserUtils.getDocIdFromCompositeKey("550e8400-e29b-41d4-a716-446655440000"));

        // DocId with hyphens (but no ::)
        assertEquals("doc-123-456", ParserUtils.getDocIdFromCompositeKey("doc-123-456"));
    }

    /**
     * Test getDocIdFromCompositeKey with edge cases
     */
    public void testGetDocIdFromCompositeKeyEdgeCases() {
        // DocId with special characters
        String specialChars = "index::doc_id-123.test";
        String result3 = ParserUtils.getDocIdFromCompositeKey(specialChars);
        assertEquals("Should preserve special characters", "doc_id-123.test", result3);

        // DocId with numbers
        String withNumbers = "products::12345";
        String result4 = ParserUtils.getDocIdFromCompositeKey(withNumbers);
        assertEquals("Should extract numeric docId", "12345", result4);
    }

    /**
     * Test combinedIndexAndDocId creates proper composite keys
     */
    public void testCombinedIndexAndDocId() {
        String compositeKey = ParserUtils.combinedIndexAndDocId("test_index", "doc123");
        assertEquals("Should create composite key with :: separator", "test_index::doc123", compositeKey);

        // Verify round-trip
        String extractedDocId = ParserUtils.getDocIdFromCompositeKey(compositeKey);
        assertEquals("Should extract original docId", "doc123", extractedDocId);
    }

    /**
     * Test combinedIndexAndDocId with special characters
     */
    public void testCombinedIndexAndDocIdWithSpecialChars() {
        String compositeKey = ParserUtils.combinedIndexAndDocId("my-index_123", "doc-456.test");
        assertEquals("Should handle special characters", "my-index_123::doc-456.test", compositeKey);

        String extractedDocId = ParserUtils.getDocIdFromCompositeKey(compositeKey);
        assertEquals("Should extract docId with special chars", "doc-456.test", extractedDocId);
    }
}
