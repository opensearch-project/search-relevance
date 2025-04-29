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

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

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
        String content = userMessage.get("content").asText();

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
}
