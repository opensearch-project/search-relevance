/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */
package org.opensearch.searchrelevance.utils;

import java.util.Map;

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

    // ============================================
    // parseQueryTextWithCustomInput Tests
    // ============================================

    public void testParseQueryTextWithCustomInput_QueryOnly() {
        // Test with only query text, no reference data
        String input = "What is OpenSearch?";
        Map<String, String> result = ParserUtils.parseQueryTextWithCustomInput(input);

        assertEquals("Query text should be parsed", "What is OpenSearch?", result.get("queryText"));
        assertEquals("Should only contain queryText", 1, result.size());
    }

    public void testParseQueryTextWithCustomInput_JsonFormat() {
        // Test current JSON format: queryText#{"key1":"value1","key2":"value2"}
        String input = "What is OpenSearch?#{\"referenceAnswer\":\"OpenSearch is a search suite\",\"category\":\"technology\"}";
        Map<String, String> result = ParserUtils.parseQueryTextWithCustomInput(input);

        assertEquals("Query text should be parsed", "What is OpenSearch?", result.get("queryText"));
        assertEquals("Reference answer should be parsed", "OpenSearch is a search suite", result.get("referenceAnswer"));
        assertEquals("Category should be parsed", "technology", result.get("category"));
        assertEquals("Should contain queryText, referenceAnswer, and category", 3, result.size());
    }

    public void testParseQueryTextWithCustomInput_JsonFormatMultipleFields() {
        // Test JSON format with multiple custom fields
        String input =
            "red shoes#{\"referenceAnswer\":\"High quality leather shoes\",\"color\":\"red\",\"brand\":\"Nike\",\"price\":\"120\"}";
        Map<String, String> result = ParserUtils.parseQueryTextWithCustomInput(input);

        assertEquals("Query text should be parsed", "red shoes", result.get("queryText"));
        assertEquals("Reference answer should be parsed", "High quality leather shoes", result.get("referenceAnswer"));
        assertEquals("Color should be parsed", "red", result.get("color"));
        assertEquals("Brand should be parsed", "Nike", result.get("brand"));
        assertEquals("Price should be parsed", "120", result.get("price"));
        assertEquals("Should contain 5 entries", 5, result.size());
    }

    public void testParseQueryTextWithCustomInput_LegacyPlainFormat() {
        // Test legacy plain format: queryText#referenceAnswer
        String input = "What is OpenSearch?#OpenSearch is a community-driven, open source search and analytics suite";
        Map<String, String> result = ParserUtils.parseQueryTextWithCustomInput(input);

        assertEquals("Query text should be parsed", "What is OpenSearch?", result.get("queryText"));
        assertEquals(
            "Reference answer should be parsed",
            "OpenSearch is a community-driven, open source search and analytics suite",
            result.get("referenceAnswer")
        );
        assertEquals("Should contain queryText and referenceAnswer", 2, result.size());
    }

    public void testParseQueryTextWithCustomInput_EmptyReferenceContent() {
        // Test with delimiter but empty content after it
        String input = "test query#";
        Map<String, String> result = ParserUtils.parseQueryTextWithCustomInput(input);

        assertEquals("Query text should be parsed", "test query", result.get("queryText"));
        assertEquals("Should only contain queryText", 1, result.size());
    }

    public void testParseQueryTextWithCustomInput_JsonFormatWithSpecialCharacters() {
        // Test JSON format with special characters in values (colons, quotes, etc.)
        String input = "test query#{\"url\":\"https://example.com:8080\",\"description\":\"Product with \\\"quotes\\\"\"}";
        Map<String, String> result = ParserUtils.parseQueryTextWithCustomInput(input);

        assertEquals("Query text should be parsed", "test query", result.get("queryText"));
        assertEquals("URL with colons should be parsed", "https://example.com:8080", result.get("url"));
        assertEquals("Description with quotes should be parsed", "Product with \"quotes\"", result.get("description"));
        assertEquals("Should contain 3 entries", 3, result.size());
    }

    // ============================================
    // QuerySetEntry Format Integration Tests
    // ============================================

    public void testQuerySetEntry_OldFormat_SingleReferenceAnswer() {
        // Test old QuerySetEntry format: "queryText#referenceAnswer"
        // This simulates the legacy format where queryText contains both query and reference answer
        String querySetEntry = "What is OpenSearch?#OpenSearch is a community-driven, open source search and analytics suite";

        Map<String, String> result = ParserUtils.parseQueryTextWithCustomInput(querySetEntry);

        assertEquals("Query text should be extracted", "What is OpenSearch?", result.get("queryText"));
        assertEquals(
            "Reference answer should be extracted",
            "OpenSearch is a community-driven, open source search and analytics suite",
            result.get("referenceAnswer")
        );
        assertEquals("Should contain queryText and referenceAnswer", 2, result.size());

        // Verify this can be used for ML processing
        String queryText = result.remove("queryText");
        Map<String, String> referenceData = result; // Remaining entries are reference data

        assertEquals("Query text should be ready for ML", "What is OpenSearch?", queryText);
        assertEquals("Reference data should contain referenceAnswer", 1, referenceData.size());
        assertTrue("Reference data should have referenceAnswer key", referenceData.containsKey("referenceAnswer"));
    }

    public void testQuerySetEntry_JsonFormat_MultipleCustomFields() {
        // Test new QuerySetEntry format from PutQuerySetTransportAction (JSON format)
        String querySetEntry =
            "red shoes#{\"referenceAnswer\":\"High quality red leather shoes\",\"color\":\"red\",\"brand\":\"Nike\",\"price\":\"120\"}";

        Map<String, String> result = ParserUtils.parseQueryTextWithCustomInput(querySetEntry);

        assertEquals("Query text should be extracted", "red shoes", result.get("queryText"));
        assertEquals("Reference answer should be extracted", "High quality red leather shoes", result.get("referenceAnswer"));
        assertEquals("Color should be extracted", "red", result.get("color"));
        assertEquals("Brand should be extracted", "Nike", result.get("brand"));
        assertEquals("Price should be extracted", "120", result.get("price"));
        assertEquals("Should contain all fields", 5, result.size());

        // Verify this can be used for ML processing
        String queryText = result.remove("queryText");
        Map<String, String> referenceData = result; // Remaining entries are reference data

        assertEquals("Query text should be ready for ML", "red shoes", queryText);
        assertEquals("Reference data should contain all custom fields", 4, referenceData.size());
        assertTrue("Reference data should have referenceAnswer", referenceData.containsKey("referenceAnswer"));
        assertTrue("Reference data should have color", referenceData.containsKey("color"));
        assertTrue("Reference data should have brand", referenceData.containsKey("brand"));
        assertTrue("Reference data should have price", referenceData.containsKey("price"));
    }

    public void testQuerySetEntry_JsonFormat_OnlyReferenceAnswer() {
        // Test JSON format with only referenceAnswer (no other custom fields)
        String querySetEntry = "What is OpenSearch?#{\"referenceAnswer\":\"OpenSearch is a search and analytics suite\"}";

        Map<String, String> result = ParserUtils.parseQueryTextWithCustomInput(querySetEntry);

        assertEquals("Query text should be extracted", "What is OpenSearch?", result.get("queryText"));
        assertEquals("Reference answer should be extracted", "OpenSearch is a search and analytics suite", result.get("referenceAnswer"));
        assertEquals("Should contain queryText and referenceAnswer", 2, result.size());

        // Verify this can be used for ML processing
        String queryText = result.remove("queryText");
        Map<String, String> referenceData = result;

        assertEquals("Query text should be ready for ML", "What is OpenSearch?", queryText);
        assertEquals("Reference data should contain only referenceAnswer", 1, referenceData.size());
    }

    public void testQuerySetEntry_JsonFormat_NoReferenceAnswerOnlyCustomFields() {
        // Test JSON format with custom fields but no referenceAnswer
        String querySetEntry = "test query#{\"category\":\"technology\",\"expectedScore\":\"0.9\",\"difficulty\":\"medium\"}";

        Map<String, String> result = ParserUtils.parseQueryTextWithCustomInput(querySetEntry);

        assertEquals("Query text should be extracted", "test query", result.get("queryText"));
        assertEquals("Category should be extracted", "technology", result.get("category"));
        assertEquals("Expected score should be extracted", "0.9", result.get("expectedScore"));
        assertEquals("Difficulty should be extracted", "medium", result.get("difficulty"));
        assertFalse("Should not have referenceAnswer", result.containsKey("referenceAnswer"));
        assertEquals("Should contain queryText and 3 custom fields", 4, result.size());

        // Verify this can be used for ML processing
        String queryText = result.remove("queryText");
        Map<String, String> referenceData = result;

        assertEquals("Query text should be ready for ML", "test query", queryText);
        assertEquals("Reference data should contain custom fields", 3, referenceData.size());
        assertFalse("Reference data should not have referenceAnswer", referenceData.containsKey("referenceAnswer"));
    }

    public void testQuerySetEntry_OldFormat_EmptyReferenceAnswer() {
        // Test old format with empty reference answer
        String querySetEntry = "What is OpenSearch?#";

        Map<String, String> result = ParserUtils.parseQueryTextWithCustomInput(querySetEntry);

        assertEquals("Query text should be extracted", "What is OpenSearch?", result.get("queryText"));
        assertEquals("Should only contain queryText", 1, result.size());

        // Verify this can be used for ML processing
        String queryText = result.remove("queryText");
        Map<String, String> referenceData = result;

        assertEquals("Query text should be ready for ML", "What is OpenSearch?", queryText);
        assertTrue("Reference data should be empty", referenceData.isEmpty());
    }

    public void testQuerySetEntry_NoDelimiter_QueryOnly() {
        // Test entry with no delimiter (just query text)
        String querySetEntry = "What is OpenSearch?";

        Map<String, String> result = ParserUtils.parseQueryTextWithCustomInput(querySetEntry);

        assertEquals("Query text should be extracted", "What is OpenSearch?", result.get("queryText"));
        assertEquals("Should only contain queryText", 1, result.size());

        // Verify this can be used for ML processing
        String queryText = result.remove("queryText");
        Map<String, String> referenceData = result;

        assertEquals("Query text should be ready for ML", "What is OpenSearch?", queryText);
        assertTrue("Reference data should be empty", referenceData.isEmpty());
    }

    public void testQuerySetEntry_BackwardCompatibility_LegacyToJson() {
        // Test that legacy plain format and new JSON format both work
        String legacyFormatEntry = "test query#expected answer";
        String jsonFormatEntry = "test query#{\"referenceAnswer\":\"expected answer\"}";

        Map<String, String> legacyResult = ParserUtils.parseQueryTextWithCustomInput(legacyFormatEntry);
        Map<String, String> jsonResult = ParserUtils.parseQueryTextWithCustomInput(jsonFormatEntry);

        // Both should extract the same queryText
        assertEquals("Query text should match", legacyResult.get("queryText"), jsonResult.get("queryText"));

        // Both should have referenceAnswer
        assertEquals("Both should have referenceAnswer", legacyResult.get("referenceAnswer"), jsonResult.get("referenceAnswer"));

        // Both should have the same size
        assertEquals("Both should have same number of entries", legacyResult.size(), jsonResult.size());
    }

    public void testQuerySetEntry_JsonFormat_RealWorldExample() {
        // Test real-world example from PutQuerySetTransportAction (JSON format)
        String querySetEntry =
            "red leather shoes#{\"referenceAnswer\":\"High quality red leather shoes with rubber sole and comfortable insole\","
                + "\"expectedRelevanceScore\":\"0.95\","
                + "\"productCategory\":\"footwear\","
                + "\"targetAudience\":\"adults\","
                + "\"priceRange\":\"premium\"}";

        Map<String, String> result = ParserUtils.parseQueryTextWithCustomInput(querySetEntry);

        // Verify all fields are extracted
        assertEquals("Query text should be extracted", "red leather shoes", result.get("queryText"));
        assertEquals(
            "Reference answer should be extracted",
            "High quality red leather shoes with rubber sole and comfortable insole",
            result.get("referenceAnswer")
        );
        assertEquals("Expected score should be extracted", "0.95", result.get("expectedRelevanceScore"));
        assertEquals("Category should be extracted", "footwear", result.get("productCategory"));
        assertEquals("Target audience should be extracted", "adults", result.get("targetAudience"));
        assertEquals("Price range should be extracted", "premium", result.get("priceRange"));
        assertEquals("Should contain all 6 fields", 6, result.size());

        // Verify this can be used for ML processing and UserPromptFactory
        String queryText = result.remove("queryText");
        Map<String, String> referenceData = result;

        assertEquals("Query text should be ready", "red leather shoes", queryText);
        assertEquals("Reference data should have 5 custom fields", 5, referenceData.size());

        // All these fields can now be used in UserPromptFactory with template variables
        assertTrue("Should have all fields for template replacement", referenceData.containsKey("referenceAnswer"));
        assertTrue("Should have expectedRelevanceScore", referenceData.containsKey("expectedRelevanceScore"));
        assertTrue("Should have productCategory", referenceData.containsKey("productCategory"));
        assertTrue("Should have targetAudience", referenceData.containsKey("targetAudience"));
        assertTrue("Should have priceRange", referenceData.containsKey("priceRange"));
    }
}
