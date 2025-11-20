/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */
package org.opensearch.searchrelevance.ml.connector;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import java.util.List;
import java.util.Map;

import org.opensearch.test.OpenSearchTestCase;

public class LLMConnectorTests extends OpenSearchTestCase {

    public void testOpenAIConnector() {
        LLMConnector connector = new OpenAIConnector();

        // Test connector type
        assertEquals(ConnectorType.OPENAI, connector.getType());

        // Test prompt formatting
        String formatted = connector.formatPrompt("System prompt", "User message");
        assertEquals("[{\"role\":\"system\",\"content\":\"System prompt\"},{\"role\":\"user\",\"content\":\"User message\"}]", formatted);

        // Test response extraction
        Map<String, Object> response = Map.of("choices", List.of(Map.of("message", Map.of("content", "AI response"))));
        String extracted = connector.extractResponse(response);
        assertEquals("AI response", extracted);
    }

    public void testClaudeConnector() {
        LLMConnector connector = new ClaudeConnector();

        // Test connector type
        assertEquals(ConnectorType.CLAUDE, connector.getType());

        // Test prompt formatting
        String formatted = connector.formatPrompt("System prompt", "User message");
        assertEquals("[{\"role\":\"user\",\"content\":[{\"type\":\"text\",\"text\":\"System prompt\\n\\nUser message\"}]}]", formatted);

        // Test response extraction
        Map<String, Object> response = Map.of("content", List.of(Map.of("text", "Claude response")));
        String extracted = connector.extractResponse(response);
        assertEquals("Claude response", extracted);
    }

    public void testCohereConnector() {
        LLMConnector connector = new CohereConnector();

        // Test connector type
        assertEquals(ConnectorType.COHERE, connector.getType());

        // Test prompt formatting
        String formatted = connector.formatPrompt("System prompt", "User message");
        assertEquals("System prompt\\n\\nUser message", formatted);

        // Test response extraction
        Map<String, Object> response = Map.of("text", "Cohere response");
        String extracted = connector.extractResponse(response);
        assertEquals("Cohere response", extracted);
    }

    public void testConnectorFactory() {
        // Test all connector types
        LLMConnector openai = LLMConnectorFactory.create(ConnectorType.OPENAI);
        assertNotNull(openai);
        assertEquals(ConnectorType.OPENAI, openai.getType());

        LLMConnector claude = LLMConnectorFactory.create(ConnectorType.CLAUDE);
        assertNotNull(claude);
        assertEquals(ConnectorType.CLAUDE, claude.getType());

        LLMConnector cohere = LLMConnectorFactory.create(ConnectorType.COHERE);
        assertNotNull(cohere);
        assertEquals(ConnectorType.COHERE, cohere.getType());

        LLMConnector deepseek = LLMConnectorFactory.create(ConnectorType.DEEPSEEK);
        assertNotNull(deepseek);
        assertEquals(ConnectorType.DEEPSEEK, deepseek.getType()); // DeepSeek returns correct type
    }

    public void testJsonEscaping() {
        LLMConnector connector = new OpenAIConnector();

        // Test with special characters that need escaping
        String formatted = connector.formatPrompt("System \"quoted\" text", "User's message with \n newline");
        assertEquals(
            "[{\"role\":\"system\",\"content\":\"System \\\"quoted\\\" text\"},{\"role\":\"user\",\"content\":\"User's message with \\n newline\"}]",
            formatted
        );
    }

    public void testEmptyClaudeResponse() {
        LLMConnector connector = new ClaudeConnector();

        // Test empty content array
        Map<String, Object> emptyResponse = Map.of("content", List.of());
        String extracted = connector.extractResponse(emptyResponse);
        assertEquals("", extracted);

        // Test null content
        Map<String, Object> nullResponse = Map.of("other", "value");
        String extractedNull = connector.extractResponse(nullResponse);
        assertEquals("", extractedNull);
    }

    public void testDeepSeekConnector() {
        LLMConnector connector = LLMConnectorFactory.create(ConnectorType.DEEPSEEK);

        // Test connector type (should return DEEPSEEK)
        assertEquals(ConnectorType.DEEPSEEK, connector.getType());

        // Test prompt formatting (same as OpenAI format)
        String formatted = connector.formatPrompt("System prompt", "User message");
        assertEquals("[{\"role\":\"system\",\"content\":\"System prompt\"},{\"role\":\"user\",\"content\":\"User message\"}]", formatted);

        // Test response extraction (same as OpenAI format)
        Map<String, Object> response = Map.of("choices", List.of(Map.of("message", Map.of("content", "DeepSeek response"))));
        String extracted = connector.extractResponse(response);
        assertEquals("DeepSeek response", extracted);
    }
}
