/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */
package org.opensearch.searchrelevance.common;

/**
 * ML related constants.
 */
public class MLConstants {
    private MLConstants() {}

    public static final String RESPONSE_FIELD = "response";
    public static final String PARAM_MESSAGES_FIELD = "messages";

    /**
     * Prompt strings that specific for llm-as-a-judge use case.
     * TODO: need benchmark for final prompt definition.
     */
    public static final String PROMPT_WITHOUT_REFERENCE = "You are a helpful assistant to judge if context answered the question.";
    public static final String PROMPT_WITH_REFERENCE =
        "You are a helpful assistant to judge if context answered the question according to the reference.";
    public static final String PROMPT_JSON_MESSAGES_SHELL =
        "[{\"role\":\"system\",\"content\":\"%s And give a relevance_score ranged from 0 to 1.\"},"
            + "{\"role\":\"user\",\"content\":\"%s\"}]";
    public static final String INPUT_FORMAT_WITHOUT_REFERENCE = "question: %s; content: %s";
    public static final String INPUT_FORMAT_WITH_REFERENCE = "question: %s; content: %s; reference: %s";

}
