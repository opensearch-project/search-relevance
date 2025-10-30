/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */
package org.opensearch.searchrelevance.common;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Unit tests for RatingOutputProcessor with focus on GPT-3.5 unstructured output handling.
 */
public class RatingOutputProcessorTests {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Test
    public void testStructuredOutputWithRatingsArray() throws Exception {
        // GPT-4o with response_format: {"ratings": [...]}
        String response = "{\"ratings\": [{\"id\": \"doc1\", \"rating_score\": 4}, {\"id\": \"doc2\", \"rating_score\": 5}]}";
        String result = RatingOutputProcessor.sanitizeLLMResponse(response);

        JsonNode resultNode = OBJECT_MAPPER.readTree(result);
        assertTrue(resultNode.isArray());
        assertEquals(2, resultNode.size());
        assertEquals("doc1", resultNode.get(0).get("id").asText());
        assertEquals(4, resultNode.get(0).get("rating_score").asInt());
    }

    @Test
    public void testDirectJsonArray() throws Exception {
        // Already an array
        String response = "[{\"id\": \"doc1\", \"rating_score\": 3}]";
        String result = RatingOutputProcessor.sanitizeLLMResponse(response);

        JsonNode resultNode = OBJECT_MAPPER.readTree(result);
        assertTrue(resultNode.isArray());
        assertEquals(1, resultNode.size());
    }

    @Test
    public void testMarkdownCodeBlockWithJson() throws Exception {
        // GPT-3.5 response with markdown code block
        String response = "Here are the ratings:\n\n```json\n{\"ratings\": [{\"id\": \"doc1\", \"rating_score\": 4}]}\n```";
        String result = RatingOutputProcessor.sanitizeLLMResponse(response);

        JsonNode resultNode = OBJECT_MAPPER.readTree(result);
        assertTrue(resultNode.isArray());
        assertEquals(1, resultNode.size());
        assertEquals("doc1", resultNode.get(0).get("id").asText());
    }

    @Test
    public void testMarkdownCodeBlockWithoutJsonTag() throws Exception {
        // GPT-3.5 response with markdown code block without 'json' tag
        String response = "Here are the ratings:\n\n```\n{\"ratings\": [{\"id\": \"doc1\", \"rating_score\": 5}]}\n```";
        String result = RatingOutputProcessor.sanitizeLLMResponse(response);

        JsonNode resultNode = OBJECT_MAPPER.readTree(result);
        assertTrue(resultNode.isArray());
        assertEquals(1, resultNode.size());
    }

    @Test
    public void testEmbeddedJsonInText() throws Exception {
        // GPT-3.5 response with JSON embedded in prose
        String response =
            "Based on the query, here is my evaluation: {\"ratings\": [{\"id\": \"doc1\", \"rating_score\": 3}]} as requested.";
        String result = RatingOutputProcessor.sanitizeLLMResponse(response);

        JsonNode resultNode = OBJECT_MAPPER.readTree(result);
        assertTrue(resultNode.isArray());
        assertEquals(1, resultNode.size());
    }

    @Test
    public void testEmbeddedJsonArray() throws Exception {
        // GPT-3.5 response with JSON array embedded in text
        String response = "The ratings are: [{\"id\": \"doc1\", \"rating_score\": 4}, {\"id\": \"doc2\", \"rating_score\": 2}]";
        String result = RatingOutputProcessor.sanitizeLLMResponse(response);

        JsonNode resultNode = OBJECT_MAPPER.readTree(result);
        assertTrue(resultNode.isArray());
        assertEquals(2, resultNode.size());
    }

    @Test
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
        assertEquals("query1_doc1", resultNode.get(0).get("id").asText());
        assertEquals(4, resultNode.get(0).get("rating_score").asInt());
    }

    @Test
    public void testEmptyResponse() throws Exception {
        String result = RatingOutputProcessor.sanitizeLLMResponse("");
        JsonNode resultNode = OBJECT_MAPPER.readTree(result);
        assertTrue(resultNode.isArray());
        assertEquals(0, resultNode.size());
    }

    @Test
    public void testNullResponse() throws Exception {
        String result = RatingOutputProcessor.sanitizeLLMResponse(null);
        JsonNode resultNode = OBJECT_MAPPER.readTree(result);
        assertTrue(resultNode.isArray());
        assertEquals(0, resultNode.size());
    }

    @Test
    public void testUnparseableText() throws Exception {
        // Pure text with no JSON
        String response = "This is just plain text without any JSON structure.";
        String result = RatingOutputProcessor.sanitizeLLMResponse(response);

        JsonNode resultNode = OBJECT_MAPPER.readTree(result);
        assertTrue(resultNode.isArray());
        assertEquals(0, resultNode.size());
    }

    @Test
    public void testMultipleJsonObjectsSelectsFirst() throws Exception {
        // Multiple JSON objects - should select the first valid one
        String response = "{\"ratings\": [{\"id\": \"doc1\", \"rating_score\": 4}]} and also {\"other\": \"data\"}";
        String result = RatingOutputProcessor.sanitizeLLMResponse(response);

        JsonNode resultNode = OBJECT_MAPPER.readTree(result);
        assertTrue(resultNode.isArray());
        assertEquals(1, resultNode.size());
        assertEquals("doc1", resultNode.get(0).get("id").asText());
    }

    @Test
    public void testArrayAppearsBeforeObject() throws Exception {
        // Array appears before object - should extract array
        String response = "Result: [{\"id\": \"doc1\", \"rating_score\": 4}] or {\"ratings\": [...]}";
        String result = RatingOutputProcessor.sanitizeLLMResponse(response);

        JsonNode resultNode = OBJECT_MAPPER.readTree(result);
        assertTrue(resultNode.isArray());
        assertEquals(1, resultNode.size());
        assertEquals("doc1", resultNode.get(0).get("id").asText());
    }

    @Test
    public void testArrayWithMultipleElementsInText() throws Exception {
        // This is the scenario that was failing - array with 2 elements embedded in text
        String response =
            "Here are the results: [{\"id\": \"doc1\", \"rating_score\": 4}, {\"id\": \"doc2\", \"rating_score\": 2}] as requested";
        String result = RatingOutputProcessor.sanitizeLLMResponse(response);

        JsonNode resultNode = OBJECT_MAPPER.readTree(result);
        assertTrue(resultNode.isArray());
        assertEquals(2, resultNode.size());
        assertEquals("doc1", resultNode.get(0).get("id").asText());
        assertEquals("doc2", resultNode.get(1).get("id").asText());
    }

    @Test
    public void testNestedArrayInObject() throws Exception {
        // Object with nested array - should extract the ratings array
        String response = "Text before {\"meta\": \"data\", \"ratings\": [{\"id\": \"doc1\", \"rating_score\": 5}]} text after";
        String result = RatingOutputProcessor.sanitizeLLMResponse(response);

        JsonNode resultNode = OBJECT_MAPPER.readTree(result);
        assertTrue(resultNode.isArray());
        assertEquals(1, resultNode.size());
        assertEquals("doc1", resultNode.get(0).get("id").asText());
    }

    @Test
    public void testMultipleArraysSelectsFirst() throws Exception {
        // Multiple arrays - should select the first one
        String response = "First: [{\"id\": \"doc1\", \"rating_score\": 4}] Second: [{\"id\": \"doc2\", \"rating_score\": 3}]";
        String result = RatingOutputProcessor.sanitizeLLMResponse(response);

        JsonNode resultNode = OBJECT_MAPPER.readTree(result);
        assertTrue(resultNode.isArray());
        assertEquals(1, resultNode.size());
        assertEquals("doc1", resultNode.get(0).get("id").asText());
    }

    @Test
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

    @Test
    public void testComplexNestedStructure() throws Exception {
        // Complex structure with nested objects and arrays
        String response =
            "The LLM response:\n```json\n{\n  \"explanation\": \"analysis\",\n  \"ratings\": [\n    {\"id\": \"q1_d1\", \"rating_score\": 5},\n    {\"id\": \"q1_d2\", \"rating_score\": 3},\n    {\"id\": \"q1_d3\", \"rating_score\": 1}\n  ]\n}\n```";
        String result = RatingOutputProcessor.sanitizeLLMResponse(response);

        JsonNode resultNode = OBJECT_MAPPER.readTree(result);
        assertTrue(resultNode.isArray());
        assertEquals(3, resultNode.size());
        assertEquals("q1_d1", resultNode.get(0).get("id").asText());
        assertEquals(5, resultNode.get(0).get("rating_score").asInt());
    }

    @Test
    public void testArrayWithNoRatingsKey() throws Exception {
        // Direct array without "ratings" wrapper - common GPT-3.5 format
        String response =
            "[{\"id\": \"doc1\", \"rating_score\": 4}, {\"id\": \"doc2\", \"rating_score\": 2}, {\"id\": \"doc3\", \"rating_score\": 5}]";
        String result = RatingOutputProcessor.sanitizeLLMResponse(response);

        JsonNode resultNode = OBJECT_MAPPER.readTree(result);
        assertTrue(resultNode.isArray());
        assertEquals(3, resultNode.size());
    }

    @Test
    public void testMalformedJsonReturnsEmpty() throws Exception {
        // Malformed JSON should return empty array
        String response = "Text with {broken json [that doesn't close properly";
        String result = RatingOutputProcessor.sanitizeLLMResponse(response);

        JsonNode resultNode = OBJECT_MAPPER.readTree(result);
        assertTrue(resultNode.isArray());
        assertEquals(0, resultNode.size());
    }

    @Test
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
        assertEquals("doc1", resultNode.get(0).get("id").asText());
    }
}
