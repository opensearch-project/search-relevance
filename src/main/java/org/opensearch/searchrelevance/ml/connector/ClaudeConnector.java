/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */
package org.opensearch.searchrelevance.ml.connector;

import static org.opensearch.searchrelevance.common.MLConstants.escapeJson;

import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Claude-specific connector implementation
 */
public class ClaudeConnector implements LLMConnector {

    @Override
    public String formatPrompt(String systemContent, String userContent) {
        String combinedContent = systemContent + "\n\n" + userContent;
        return String.format(
            Locale.ROOT,
            "[{\"role\":\"user\",\"content\":[{\"type\":\"text\",\"text\":\"%s\"}]}]",
            escapeJson(combinedContent)
        );
    }

    @Override
    public String extractResponse(Map<String, ?> rawResponse) {
        List<?> content = (List<?>) rawResponse.get("content");
        if (content != null && !content.isEmpty()) {
            Map<String, ?> textContent = (Map<String, ?>) content.get(0);
            return (String) textContent.get("text");
        }
        return "";
    }

    @Override
    public ConnectorType getType() {
        return ConnectorType.CLAUDE;
    }
}
