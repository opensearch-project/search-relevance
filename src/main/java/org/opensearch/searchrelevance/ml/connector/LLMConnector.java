/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */
package org.opensearch.searchrelevance.ml.connector;

import java.util.Map;

/**
 * Interface for LLM connector implementations that handle different LLM providers
 */
public interface LLMConnector {

    /**
     * Formats the prompt according to the specific LLM provider's requirements
     *
     * @param systemContent The system message content
     * @param userContent The user message content
     * @return Formatted prompt string ready for the LLM API
     */
    String formatPrompt(String systemContent, String userContent);

    /**
     * Extracts the response text from the raw LLM API response
     *
     * @param rawResponse The raw response map from the LLM API
     * @return Extracted response text
     */
    String extractResponse(Map<String, ?> rawResponse);

    /**
     * Returns the connector type
     *
     * @return The ConnectorType enum value
     */
    ConnectorType getType();

    /**
     * Returns the parameter name used for messages in ML input
     *
     * @return The parameter name (e.g., "messages" for Claude/OpenAI, "message" for Cohere)
     */
    String getMessageParameterName();
}
