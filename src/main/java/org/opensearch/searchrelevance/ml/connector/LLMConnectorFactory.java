/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */
package org.opensearch.searchrelevance.ml.connector;

/**
 * Factory class for creating LLM connector instances
 */
public class LLMConnectorFactory {

    /**
     * Creates an LLM connector instance based on the connector type
     *
     * @param type The connector type
     * @return LLMConnector instance
     * @throws IllegalArgumentException if connector type is not supported
     */
    public static LLMConnector create(ConnectorType type) {
        return switch (type) {
            case OPENAI -> new OpenAIConnector();
            case CLAUDE -> new ClaudeConnector();
            case COHERE -> new CohereConnector();
            case DEEPSEEK -> new DeepSeekConnector();
        };
    }
}
