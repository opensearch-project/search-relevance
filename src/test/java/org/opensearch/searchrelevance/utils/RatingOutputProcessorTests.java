/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */
package org.opensearch.searchrelevance.utils;

import org.opensearch.test.OpenSearchTestCase;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Unit tests for RatingOutputProcessor with focus on GPT-3.5 unstructured output handling.
 */
public class RatingOutputProcessorTests extends OpenSearchTestCase {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    public void testStructuredOutputWithRatingsArray() throws Exception {
        // GPT-4o with response_format: {"ratings": [...]}
        String response = "{\"ratings\": [{\"id\": \"doc1\", \"rating_score\": 4}, {\"id\": \"doc2\", \"rating_score\": 5}]}";
        String result = RatingOutputProcessor.sanitizeLLMResponse(response);

        JsonNode resultNode = OBJECT_MAPPER.readTree(result);
        assertTrue(resultNode.isArray());
        assertEquals(2, resultNode.size());
        assertEquals("doc1", resultNode.get(0).get("id").asString());
        assertEquals(4, resultNode.get(0).get("rating_score").asInt());
    }

    public void testDirectJsonArray() throws Exception {
        // Already an array
        String response = "[{\"id\": \"doc1\", \"rating_score\": 3}]";
        String result = RatingOutputProcessor.sanitizeLLMResponse(response);

        JsonNode resultNode = OBJECT_MAPPER.readTree(result);
        assertTrue(resultNode.isArray());
        assertEquals(1, resultNode.size());
    }

    public void testMarkdownCodeBlockWithJson() throws Exception {
        // GPT-3.5 response with markdown code block
        String response = "Here are the ratings:\n\n```json\n{\"ratings\": [{\"id\": \"doc1\", \"rating_score\": 4}]}\n```";
        String result = RatingOutputProcessor.sanitizeLLMResponse(response);

        JsonNode resultNode = OBJECT_MAPPER.readTree(result);
        assertTrue(resultNode.isArray());
        assertEquals(1, resultNode.size());
        assertEquals("doc1", resultNode.get(0).get("id").asString());
    }

    public void testMarkdownCodeBlockWithoutJsonTag() throws Exception {
        // GPT-3.5 response with markdown code block without 'json' tag
        String response = "Here are the ratings:\n\n```\n{\"ratings\": [{\"id\": \"doc1\", \"rating_score\": 5}]}\n```";
        String result = RatingOutputProcessor.sanitizeLLMResponse(response);

        JsonNode resultNode = OBJECT_MAPPER.readTree(result);
        assertTrue(resultNode.isArray());
        assertEquals(1, resultNode.size());
    }

    public void testEmbeddedJsonInText() throws Exception {
        // GPT-3.5 response with JSON embedded in prose
        String response =
            "Based on the query, here is my evaluation: {\"ratings\": [{\"id\": \"doc1\", \"rating_score\": 3}]} as requested.";
        String result = RatingOutputProcessor.sanitizeLLMResponse(response);

        JsonNode resultNode = OBJECT_MAPPER.readTree(result);
        assertTrue(resultNode.isArray());
        assertEquals(1, resultNode.size());
    }

    public void testEmbeddedJsonArray() throws Exception {
        // GPT-3.5 response with JSON array embedded in text
        String response = "The ratings are: [{\"id\": \"doc1\", \"rating_score\": 4}, {\"id\": \"doc2\", \"rating_score\": 2}]";
        String result = RatingOutputProcessor.sanitizeLLMResponse(response);

        JsonNode resultNode = OBJECT_MAPPER.readTree(result);
        assertTrue(resultNode.isArray());
        assertEquals(2, resultNode.size());
    }

    public void testComplexUnstructuredResponse() throws Exception {
        // Realistic GPT-3.5 response
        String response = "I'll rate each document based on relevance:\n\n"
            + "```json\n"
            + "{\n"
            + "  \"ratings\": [\n"
            + "    {\"id\": \"query1_doc1\", \"rating_score\": 4},\n"
            + "    {\"id\": \"query1_doc2\", \"rating_score\": 5},\n"
            + "    {\"id\": \"query1_doc3\", \"rating_score\": 2}\n"
            + "  ]\n"
            + "}\n"
            + "```\n\n"
            + "These ratings reflect the relevance of each document.";
        String result = RatingOutputProcessor.sanitizeLLMResponse(response);

        JsonNode resultNode = OBJECT_MAPPER.readTree(result);
        assertTrue(resultNode.isArray());
        assertEquals(3, resultNode.size());
        assertEquals("query1_doc1", resultNode.get(0).get("id").asString());
        assertEquals(4, resultNode.get(0).get("rating_score").asInt());
    }

    public void testEmptyResponse() throws Exception {
        String result = RatingOutputProcessor.sanitizeLLMResponse("");
        JsonNode resultNode = OBJECT_MAPPER.readTree(result);
        assertTrue(resultNode.isArray());
        assertEquals(0, resultNode.size());
    }

    public void testNullResponse() throws Exception {
        String result = RatingOutputProcessor.sanitizeLLMResponse(null);
        JsonNode resultNode = OBJECT_MAPPER.readTree(result);
        assertTrue(resultNode.isArray());
        assertEquals(0, resultNode.size());
    }

    public void testUnparseableText() throws Exception {
        // Pure text with no JSON
        String response = "This is just plain text without any JSON structure.";
        String result = RatingOutputProcessor.sanitizeLLMResponse(response);

        JsonNode resultNode = OBJECT_MAPPER.readTree(result);
        assertTrue(resultNode.isArray());
        assertEquals(0, resultNode.size());
    }

    public void testMultipleJsonObjectsSelectsFirst() throws Exception {
        // Multiple JSON objects - should select the first valid one
        String response = "{\"ratings\": [{\"id\": \"doc1\", \"rating_score\": 4}]} and also {\"other\": \"data\"}";
        String result = RatingOutputProcessor.sanitizeLLMResponse(response);

        JsonNode resultNode = OBJECT_MAPPER.readTree(result);
        assertTrue(resultNode.isArray());
        assertEquals(1, resultNode.size());
        assertEquals("doc1", resultNode.get(0).get("id").asString());
    }

    public void testArrayAppearsBeforeObject() throws Exception {
        // Array appears before object - should extract array
        String response = "Result: [{\"id\": \"doc1\", \"rating_score\": 4}] or {\"ratings\": [...]}";
        String result = RatingOutputProcessor.sanitizeLLMResponse(response);

        JsonNode resultNode = OBJECT_MAPPER.readTree(result);
        assertTrue(resultNode.isArray());
        assertEquals(1, resultNode.size());
        assertEquals("doc1", resultNode.get(0).get("id").asString());
    }

    public void testArrayWithMultipleElementsInText() throws Exception {
        // This is the scenario that was failing - array with 2 elements embedded in text
        String response =
            "Here are the results: [{\"id\": \"doc1\", \"rating_score\": 4}, {\"id\": \"doc2\", \"rating_score\": 2}] as requested";
        String result = RatingOutputProcessor.sanitizeLLMResponse(response);

        JsonNode resultNode = OBJECT_MAPPER.readTree(result);
        assertTrue(resultNode.isArray());
        assertEquals(2, resultNode.size());
        assertEquals("doc1", resultNode.get(0).get("id").asString());
        assertEquals("doc2", resultNode.get(1).get("id").asString());
    }

    public void testNestedArrayInObject() throws Exception {
        // Object with nested array - should extract the ratings array
        String response = "Text before {\"meta\": \"data\", \"ratings\": [{\"id\": \"doc1\", \"rating_score\": 5}]} text after";
        String result = RatingOutputProcessor.sanitizeLLMResponse(response);

        JsonNode resultNode = OBJECT_MAPPER.readTree(result);
        assertTrue(resultNode.isArray());
        assertEquals(1, resultNode.size());
        assertEquals("doc1", resultNode.get(0).get("id").asString());
    }

    public void testMultipleArraysSelectsFirst() throws Exception {
        // Multiple arrays - should select the first one
        String response = "First: [{\"id\": \"doc1\", \"rating_score\": 4}] Second: [{\"id\": \"doc2\", \"rating_score\": 3}]";
        String result = RatingOutputProcessor.sanitizeLLMResponse(response);

        JsonNode resultNode = OBJECT_MAPPER.readTree(result);
        assertTrue(resultNode.isArray());
        assertEquals(1, resultNode.size());
        assertEquals("doc1", resultNode.get(0).get("id").asString());
    }

    public void testObjectBeforeArrayInText() throws Exception {
        // Realistic case: Object appears first in prose, then array
        String response = "Status: {\"status\": \"ok\"}. Here are the ratings: [{\"id\": \"doc1\", \"rating_score\": 4}]";
        String result = RatingOutputProcessor.sanitizeLLMResponse(response);

        JsonNode resultNode = OBJECT_MAPPER.readTree(result);
        // Should extract the first valid JSON structure (the object),
        // and since it doesn't have ratings field, it wraps it in an array
        assertTrue(resultNode.isArray());
        // Will extract the first object and wrap it
        assertEquals(1, resultNode.size());
    }

    public void testComplexNestedStructure() throws Exception {
        // Complex structure with nested objects and arrays
        String response =
            "The LLM response:\n```json\n{\n  \"explanation\": \"analysis\",\n  \"ratings\": [\n    {\"id\": \"q1_d1\", \"rating_score\": 5},\n    {\"id\": \"q1_d2\", \"rating_score\": 3},\n    {\"id\": \"q1_d3\", \"rating_score\": 1}\n  ]\n}\n```";
        String result = RatingOutputProcessor.sanitizeLLMResponse(response);

        JsonNode resultNode = OBJECT_MAPPER.readTree(result);
        assertTrue(resultNode.isArray());
        assertEquals(3, resultNode.size());
        assertEquals("q1_d1", resultNode.get(0).get("id").asString());
        assertEquals(5, resultNode.get(0).get("rating_score").asInt());
    }

    public void testArrayWithNoRatingsKey() throws Exception {
        // Direct array without "ratings" wrapper - common GPT-3.5 format
        String response =
            "[{\"id\": \"doc1\", \"rating_score\": 4}, {\"id\": \"doc2\", \"rating_score\": 2}, {\"id\": \"doc3\", \"rating_score\": 5}]";
        String result = RatingOutputProcessor.sanitizeLLMResponse(response);

        JsonNode resultNode = OBJECT_MAPPER.readTree(result);
        assertTrue(resultNode.isArray());
        assertEquals(3, resultNode.size());
    }

    public void testMalformedJsonReturnsEmpty() throws Exception {
        // Malformed JSON should return empty array
        String response = "Text with {broken json [that doesn't close properly";
        String result = RatingOutputProcessor.sanitizeLLMResponse(response);

        JsonNode resultNode = OBJECT_MAPPER.readTree(result);
        assertTrue(resultNode.isArray());
        assertEquals(0, resultNode.size());
    }

    public void testProseWithCodeBlockContainingArray() throws Exception {
        // GPT-3.5 style response with explanation and code block
        String response = "I've evaluated each document based on relevance.\n\n"
            + "```\n"
            + "[{\"id\": \"doc1\", \"rating_score\": 0.9}, {\"id\": \"doc2\", \"rating_score\": 0.5}]\n"
            + "```\n\n"
            + "The first document is highly relevant.";
        String result = RatingOutputProcessor.sanitizeLLMResponse(response);

        JsonNode resultNode = OBJECT_MAPPER.readTree(result);
        assertTrue(resultNode.isArray());
        assertEquals(2, resultNode.size());
        assertEquals("doc1", resultNode.get(0).get("id").asString());
    }

    // ============================================
    // Tests for improved state machine - handling braces/brackets inside strings
    // ============================================

    public void testJsonWithBracesInsideStrings() throws Exception {
        // JSON object with braces inside string values - state machine should handle correctly
        String response = "{\"ratings\": [{\"id\": \"doc1\", \"comment\": \"This {has} braces\", \"rating_score\": 4}]}";
        String result = RatingOutputProcessor.sanitizeLLMResponse(response);

        JsonNode resultNode = OBJECT_MAPPER.readTree(result);
        assertTrue(resultNode.isArray());
        assertEquals(1, resultNode.size());
        assertEquals("doc1", resultNode.get(0).get("id").asString());
        assertEquals("This {has} braces", resultNode.get(0).get("comment").asString());
    }

    public void testJsonWithBracketsInsideStrings() throws Exception {
        // JSON with brackets inside string values
        String response = "[{\"id\": \"doc1\", \"title\": \"Array [1,2,3] reference\", \"rating_score\": 3}]";
        String result = RatingOutputProcessor.sanitizeLLMResponse(response);

        JsonNode resultNode = OBJECT_MAPPER.readTree(result);
        assertTrue(resultNode.isArray());
        assertEquals(1, resultNode.size());
        assertEquals("Array [1,2,3] reference", resultNode.get(0).get("title").asString());
    }

    public void testJsonWithEscapedQuotesInStrings() throws Exception {
        // JSON with escaped quotes - state machine should handle properly
        String response = "[{\"id\": \"doc1\", \"text\": \"He said \\\"hello\\\"\", \"rating_score\": 5}]";
        String result = RatingOutputProcessor.sanitizeLLMResponse(response);

        JsonNode resultNode = OBJECT_MAPPER.readTree(result);
        assertTrue(resultNode.isArray());
        assertEquals(1, resultNode.size());
        assertEquals("He said \"hello\"", resultNode.get(0).get("text").asString());
    }

    public void testJsonWithComplexEscapedContent() throws Exception {
        // JSON with multiple escape sequences and special characters
        String response = "{\"ratings\": [{\"id\": \"doc1\", \"note\": \"Path: C:\\\\Users\\\\file.txt\", \"rating_score\": 4}]}";
        String result = RatingOutputProcessor.sanitizeLLMResponse(response);

        JsonNode resultNode = OBJECT_MAPPER.readTree(result);
        assertTrue(resultNode.isArray());
        assertEquals(1, resultNode.size());
        assertEquals("Path: C:\\Users\\file.txt", resultNode.get(0).get("note").asString());
    }

    public void testJsonWithMixedQuotes() throws Exception {
        // JSON with both single and double quotes in strings (JSON standard requires double quotes for keys)
        String response = "[{\"id\": \"doc1\", \"content\": \"It's a good match\", \"rating_score\": 4}]";
        String result = RatingOutputProcessor.sanitizeLLMResponse(response);

        JsonNode resultNode = OBJECT_MAPPER.readTree(result);
        assertTrue(resultNode.isArray());
        assertEquals(1, resultNode.size());
        assertEquals("It's a good match", resultNode.get(0).get("content").asString());
    }

    // ============================================
    // Tests for different line endings (CRLF vs LF)
    // ============================================

    public void testMarkdownCodeBlockWithCRLF() throws Exception {
        // Windows-style line endings (CRLF)
        String response = "Here are the ratings:\r\n\r\n```json\r\n"
            + "{\"ratings\": [{\"id\": \"doc1\", \"rating_score\": 4}]}\r\n"
            + "```";
        String result = RatingOutputProcessor.sanitizeLLMResponse(response);

        JsonNode resultNode = OBJECT_MAPPER.readTree(result);
        assertTrue(resultNode.isArray());
        assertEquals(1, resultNode.size());
        assertEquals("doc1", resultNode.get(0).get("id").asString());
    }

    public void testJsonWithMixedLineEndings() throws Exception {
        // Mixed CRLF and LF
        String response = "Result:\n\r```\r\n[{\"id\": \"doc1\", \"rating_score\": 5}]\n```";
        String result = RatingOutputProcessor.sanitizeLLMResponse(response);

        JsonNode resultNode = OBJECT_MAPPER.readTree(result);
        assertTrue(resultNode.isArray());
        assertEquals(1, resultNode.size());
    }

    // ============================================
    // Tests for multiple code blocks and other language tags
    // ============================================

    public void testMultipleCodeBlocksSelectsFirst() throws Exception {
        // Multiple code blocks - should extract from the first one
        String response = "First block:\n```json\n[{\"id\": \"doc1\", \"rating_score\": 4}]\n```\n\n"
            + "Second block:\n```json\n[{\"id\": \"doc2\", \"rating_score\": 3}]\n```";
        String result = RatingOutputProcessor.sanitizeLLMResponse(response);

        JsonNode resultNode = OBJECT_MAPPER.readTree(result);
        assertTrue(resultNode.isArray());
        assertEquals(1, resultNode.size());
        assertEquals("doc1", resultNode.get(0).get("id").asString());
    }

    public void testCodeBlockWithPythonTag() throws Exception {
        // Code block with 'python' tag instead of 'json' - should still extract JSON
        String response = "Here's the output:\n```python\n" + "[{\"id\": \"doc1\", \"rating_score\": 4}]\n" + "```";
        String result = RatingOutputProcessor.sanitizeLLMResponse(response);

        // This will fail to extract from markdown (non-json tag), but should fall back to pattern extraction
        JsonNode resultNode = OBJECT_MAPPER.readTree(result);
        assertTrue(resultNode.isArray());
        // May be empty or may extract depending on fallback - at least should not crash
    }

    public void testCodeBlockWithJavaScriptTag() throws Exception {
        // Code block with 'javascript' tag - fallback to pattern extraction
        String response = "```javascript\n{\"ratings\": [{\"id\": \"doc1\", \"rating_score\": 5}]}\n```";
        String result = RatingOutputProcessor.sanitizeLLMResponse(response);

        // Should fall back to pattern extraction and still work
        JsonNode resultNode = OBJECT_MAPPER.readTree(result);
        assertTrue(resultNode.isArray());
    }

    public void testExplanationBeforeCodeBlock() throws Exception {
        // Realistic: Long explanation before the actual JSON
        String response = "Let me explain my reasoning for these ratings:\n\n"
            + "Document 1 appears highly relevant because it contains...\n"
            + "Document 2 is less relevant due to...\n\n"
            + "Here are my final ratings:\n\n"
            + "```json\n"
            + "{\"ratings\": [{\"id\": \"doc1\", \"rating_score\": 5}, {\"id\": \"doc2\", \"rating_score\": 2}]}\n"
            + "```";
        String result = RatingOutputProcessor.sanitizeLLMResponse(response);

        JsonNode resultNode = OBJECT_MAPPER.readTree(result);
        assertTrue(resultNode.isArray());
        assertEquals(2, resultNode.size());
        assertEquals("doc1", resultNode.get(0).get("id").asString());
    }

    // ============================================
    // Tests for inline JSON and edge cases
    // ============================================

    public void testInlineJsonWithSurroundingText() throws Exception {
        // Inline JSON with lots of surrounding prose
        String response = "After analyzing the query and documents, I believe the ratings should be "
            + "[{\"id\": \"doc1\", \"rating_score\": 4}, {\"id\": \"doc2\", \"rating_score\": 3}] "
            + "because these scores reflect the relevance accurately.";
        String result = RatingOutputProcessor.sanitizeLLMResponse(response);

        JsonNode resultNode = OBJECT_MAPPER.readTree(result);
        assertTrue(resultNode.isArray());
        assertEquals(2, resultNode.size());
    }

    public void testJsonWithNestedObjectsAndArrays() throws Exception {
        // Complex nested structure that state machine should handle
        String response =
            "{\"ratings\": [{\"id\": \"doc1\", \"details\": {\"score\": 5, \"factors\": [\"a\", \"b\"]}, \"rating_score\": 5}]}";
        String result = RatingOutputProcessor.sanitizeLLMResponse(response);

        JsonNode resultNode = OBJECT_MAPPER.readTree(result);
        assertTrue(resultNode.isArray());
        assertEquals(1, resultNode.size());
        assertEquals("doc1", resultNode.get(0).get("id").asString());
    }

    public void testMalformedJsonWithExtraComma() throws Exception {
        // Common LLM mistake: trailing comma
        String response = "[{\"id\": \"doc1\", \"rating_score\": 4,}]";
        String result = RatingOutputProcessor.sanitizeLLMResponse(response);

        // Jackson should fail to parse this, should return empty array
        JsonNode resultNode = OBJECT_MAPPER.readTree(result);
        assertTrue(resultNode.isArray());
        // Will likely be empty due to parse failure
    }

    public void testJsonWithUnicodeCharacters() throws Exception {
        // JSON with unicode characters
        String response = "[{\"id\": \"doc1\", \"title\": \"Café résumé\", \"rating_score\": 4}]";
        String result = RatingOutputProcessor.sanitizeLLMResponse(response);

        JsonNode resultNode = OBJECT_MAPPER.readTree(result);
        assertTrue(resultNode.isArray());
        assertEquals(1, resultNode.size());
        assertEquals("Café résumé", resultNode.get(0).get("title").asString());
    }

    public void testJsonArrayWithEmptyObjects() throws Exception {
        // Edge case: array with empty objects
        String response = "[{}, {\"id\": \"doc1\", \"rating_score\": 3}]";
        String result = RatingOutputProcessor.sanitizeLLMResponse(response);

        JsonNode resultNode = OBJECT_MAPPER.readTree(result);
        assertTrue(resultNode.isArray());
        assertEquals(2, resultNode.size());
    }

    public void testVeryLongJsonResponse() throws Exception {
        // Simulate a large response with many ratings
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < 100; i++) {
            if (i > 0) {
                sb.append(",");
            }
            sb.append("{\"id\": \"doc").append(i).append("\", \"rating_score\": ").append(i % 5).append("}");
        }
        sb.append("]");
        String response = sb.toString();

        String result = RatingOutputProcessor.sanitizeLLMResponse(response);

        JsonNode resultNode = OBJECT_MAPPER.readTree(result);
        assertTrue(resultNode.isArray());
        assertEquals(100, resultNode.size());
    }
}
