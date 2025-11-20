/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */
package org.opensearch.searchrelevance.ml.connector;

/**
 * Enum representing different LLM connector types
 */
public enum ConnectorType {
    OPENAI("openai"),
    CLAUDE("claude"),
    COHERE("cohere"),
    DEEPSEEK("deepseek");

    private final String value;

    ConnectorType(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }

    @Override
    public String toString() {
        return value;
    }
}
