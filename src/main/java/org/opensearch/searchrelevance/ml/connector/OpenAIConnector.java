/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */
package org.opensearch.searchrelevance.ml.connector;

import static org.opensearch.searchrelevance.common.MLConstants.PROMPT_JSON_MESSAGES_SHELL;
import static org.opensearch.searchrelevance.common.MLConstants.RESPONSE_CHOICES_FIELD;
import static org.opensearch.searchrelevance.common.MLConstants.RESPONSE_CONTENT_FIELD;
import static org.opensearch.searchrelevance.common.MLConstants.RESPONSE_MESSAGE_FIELD;
import static org.opensearch.searchrelevance.common.MLConstants.escapeJson;

import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * OpenAI-specific connector implementation
 */
public class OpenAIConnector implements LLMConnector {

    @Override
    public String formatPrompt(String systemContent, String userContent) {
        return String.format(Locale.ROOT, PROMPT_JSON_MESSAGES_SHELL, escapeJson(systemContent), escapeJson(userContent));
    }

    @Override
    public String extractResponse(Map<String, ?> rawResponse) {
        Map<String, ?> choices = (Map<String, ?>) ((List<?>) rawResponse.get(RESPONSE_CHOICES_FIELD)).get(0);
        Map<String, ?> message = (Map<String, ?>) choices.get(RESPONSE_MESSAGE_FIELD);
        return (String) message.get(RESPONSE_CONTENT_FIELD);
    }

    @Override
    public ConnectorType getType() {
        return ConnectorType.OPENAI;
    }

    @Override
    public String getMessageParameterName() {
        return "messages"; // OpenAI uses plural "messages"
    }
}
