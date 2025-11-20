/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */
package org.opensearch.searchrelevance.ml.connector;

import static org.opensearch.searchrelevance.common.MLConstants.escapeJson;

import java.util.Map;

/**
 * Cohere-specific connector implementation
 */
public class CohereConnector implements LLMConnector {

    @Override
    public String formatPrompt(String systemContent, String userContent) {
        return escapeJson(systemContent) + "\\n\\n" + escapeJson(userContent);
    }

    @Override
    public String extractResponse(Map<String, ?> rawResponse) {
        return (String) rawResponse.get("text");
    }

    @Override
    public ConnectorType getType() {
        return ConnectorType.COHERE;
    }
}
