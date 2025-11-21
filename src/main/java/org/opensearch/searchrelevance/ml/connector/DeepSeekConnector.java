/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */
package org.opensearch.searchrelevance.ml.connector;

/**
 * DeepSeek-specific connector implementation (uses OpenAI-compatible format)
 */
public class DeepSeekConnector extends OpenAIConnector {

    @Override
    public ConnectorType getType() {
        return ConnectorType.DEEPSEEK;
    }

    @Override
    public String getMessageParameterName() {
        return "messages"; // DeepSeek uses plural "messages"
    }
}
