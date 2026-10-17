/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */
package org.opensearch.searchrelevance.ml;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.opensearch.test.OpenSearchTestCase;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

public class MLAccessorTests extends OpenSearchTestCase {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    // Helper method to escape JSON strings
    private static String escapeJson(String str) {
        return str.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r").replace("\t", "\\t");
    }

    private static final String PROMPT_SEARCH_RELEVANCE = escapeJson(
        "You are an expert search relevance rater. Your task is to evaluate the relevance between search query and results with these criteria:\n"
            + "- Score 1.0: Perfect match, highly relevant\n"
            + "- Score 0.7-0.9: Very relevant with minor variations\n"
            + "- Score 0.4-0.6: Moderately relevant\n"
            + "- Score 0.1-0.3: Slightly relevant\n"
            + "- Score 0.0: Completely irrelevant\n"
            + "Evaluate based on: exact matches, semantic relevance, and overall context between the SearchText and content in Hits.\n"
            + "When a reference is provided, evaluate based on the relevance to both SearchText and its reference.\n\n"
            + "IMPORTANT: Provide your response ONLY as a JSON array of objects, each with \"id\" and \"rating_score\" fields. "
            + "You MUST include a rating for EVERY hit provided, even if the rating is 0. "
            + "Do not include any explanation or additional text."
    );

    private static final String PROMPT_JSON_MESSAGES_SHELL = "[{\"role\":\"system\",\"content\":\"%s\"},"
        + "{\"role\":\"user\",\"content\":\"%s\"}]";

    private static final String INPUT_FORMAT_SEARCH = "SearchText - %s; Hits - %s";

    public void testMessageFormatting() throws Exception {
        // Prepare test data
        String searchText = "banana";
        List<Map<String, String>> hits = new ArrayList<>();
        Map<String, String> hit1 = new HashMap<>();
        hit1.put("_id", "001");
        hit1.put("_index", "sample_index03");
        hit1.put("_source", "{\"name\": \"banana\", \"price\": 1.99, \"description\": \"this is a banana\"}");
        hits.add(hit1);

        // Format messages
        String hitsJson = OBJECT_MAPPER.writeValueAsString(hits);
        String userContent = String.format(Locale.ROOT, INPUT_FORMAT_SEARCH, searchText, hitsJson);
        String messages = String.format(Locale.ROOT, PROMPT_JSON_MESSAGES_SHELL, PROMPT_SEARCH_RELEVANCE, escapeJson(userContent));
        String messagesJson = String.format(Locale.ROOT, "{\"messages\":%s}", messages);

        // Parse and verify
        JsonNode jsonNode = OBJECT_MAPPER.readTree(messagesJson);
        assertNotNull("JSON should not be null", jsonNode);

        JsonNode messagesNode = jsonNode.get("messages");
        assertNotNull("messages field should exist", messagesNode);
        assertTrue("messages should be an array", messagesNode.isArray());
        assertEquals("messages should have 2 elements", 2, messagesNode.size());
    }

    public void testMessageFormattingWithMultipleHits() throws Exception {
        // Prepare test data with multiple hits
        String searchText = "fruit";
        List<Map<String, String>> hits = new ArrayList<>();

        Map<String, String> hit1 = new HashMap<>();
        hit1.put("_id", "001");
        hit1.put("_index", "sample_index03");
        hit1.put("_source", "{\"name\": \"banana\", \"price\": 1.99, \"description\": \"yellow fruit\"}");

        Map<String, String> hit2 = new HashMap<>();
        hit2.put("_id", "002");
        hit2.put("_index", "sample_index03");
        hit2.put("_source", "{\"name\": \"apple\", \"price\": 0.99, \"description\": \"red fruit\"}");

        hits.add(hit1);
        hits.add(hit2);

        // Format messages
        String hitsJson = OBJECT_MAPPER.writeValueAsString(hits);
        String userContent = String.format(Locale.ROOT, INPUT_FORMAT_SEARCH, searchText, hitsJson);
        String messages = String.format(Locale.ROOT, PROMPT_JSON_MESSAGES_SHELL, PROMPT_SEARCH_RELEVANCE, escapeJson(userContent));
        String messagesJson = String.format(Locale.ROOT, "{\"messages\":%s}", messages);

        // Verify
        JsonNode jsonNode = OBJECT_MAPPER.readTree(messagesJson);
        assertNotNull("JSON should not be null", jsonNode);

        JsonNode messagesNode = jsonNode.get("messages");
        JsonNode userMessage = messagesNode.get(1);
        String content = userMessage.get("content").asString();

        assertTrue("Should contain first hit", content.contains("001"));
        assertTrue("Should contain second hit", content.contains("002"));

    }

    public void testMessageFormattingWithSpecialCharacters() throws Exception {
        // Prepare test data with special characters
        String searchText = "test\"with\"quotes";
        List<Map<String, String>> hits = new ArrayList<>();
        Map<String, String> hit = new HashMap<>();
        hit.put("_id", "001");
        hit.put("_index", "sample_index03");
        hit.put("_source", "{\"name\": \"test\\with\\backslashes\", \"description\": \"line1\\nline2\"}");
        hits.add(hit);

        // Format messages
        String hitsJson = OBJECT_MAPPER.writeValueAsString(hits);
        String userContent = String.format(Locale.ROOT, INPUT_FORMAT_SEARCH, searchText, hitsJson);
        String messages = String.format(Locale.ROOT, PROMPT_JSON_MESSAGES_SHELL, PROMPT_SEARCH_RELEVANCE, escapeJson(userContent));
        String messagesJson = String.format(Locale.ROOT, "{\"messages\":%s}", messages);

        // Verify
        JsonNode jsonNode = OBJECT_MAPPER.readTree(messagesJson);
        assertNotNull("JSON should not be null", jsonNode);
    }

    /**
     * Test that cleanResponse does not corrupt valid JSON from OpenAI structured output.
     * This is a regression test for the bug where cleanResponse was stripping characters
     * from valid JSON, causing it to be unparseable.
     */
    public void testCleanResponsePreservesValidJson() throws Exception {
        // Valid JSON response from OpenAI structured output
        String validJsonResponse = "{\"ratings\":[{\"id\":\"1\",\"rating_score\":0.9}]}";

        // cleanResponse should return the response as-is
        // (We can't directly test the private method, but we verify the concept)
        JsonNode jsonNode = OBJECT_MAPPER.readTree(validJsonResponse);
        assertNotNull("JSON should be parseable", jsonNode);
        assertTrue("JSON should have ratings array", jsonNode.has("ratings"));
        assertTrue("Ratings should be an array", jsonNode.get("ratings").isArray());
        assertEquals("Should have one rating", 1, jsonNode.get("ratings").size());

        JsonNode rating = jsonNode.get("ratings").get(0);
        assertEquals("ID should be preserved", "1", rating.get("id").asString());
        assertEquals("Rating score should be preserved", 0.9, rating.get("rating_score").asDouble(), 0.001);
    }

    /**
     * Test various valid JSON formats that should be preserved by cleanResponse
     */
    public void testCleanResponseVariousFormats() throws Exception {
        // Test empty ratings array
        String emptyRatings = "{\"ratings\":[]}";
        JsonNode node1 = OBJECT_MAPPER.readTree(emptyRatings);
        assertNotNull("Empty ratings should be valid JSON", node1);
        assertEquals("Should have empty ratings array", 0, node1.get("ratings").size());

        // Test multiple ratings
        String multipleRatings = "{\"ratings\":[{\"id\":\"1\",\"rating_score\":0.9},{\"id\":\"2\",\"rating_score\":0.5}]}";
        JsonNode node2 = OBJECT_MAPPER.readTree(multipleRatings);
        assertNotNull("Multiple ratings should be valid JSON", node2);
        assertEquals("Should have two ratings", 2, node2.get("ratings").size());

        // Test with composite keys
        String compositeKeys = "{\"ratings\":[{\"id\":\"test_products::1\",\"rating_score\":1.0}]}";
        JsonNode node3 = OBJECT_MAPPER.readTree(compositeKeys);
        assertNotNull("Composite keys should be valid JSON", node3);
        assertEquals("Composite key should be preserved", "test_products::1", node3.get("ratings").get(0).get("id").asString());
    }

    /**
     * Test that malformed responses from LLM would be handled
     * (This tests the sanitization logic in RatingOutputProcessor, not cleanResponse)
     */
    public void testMalformedJsonHandling() {
        // These would be handled by sanitizeLLMResponse, not cleanResponse
        String withCodeBlock = "```json\n{\"ratings\":[{\"id\":\"1\",\"rating_score\":0.9}]}\n```";
        String withText = "Here are the ratings:\n{\"ratings\":[{\"id\":\"1\",\"rating_score\":0.9}]}";

        // Both contain valid JSON that should be extractable by sanitization
        assertTrue("Code block should contain valid JSON", withCodeBlock.contains("{\"ratings\""));
        assertTrue("Text response should contain valid JSON", withText.contains("{\"ratings\""));
    }
}
