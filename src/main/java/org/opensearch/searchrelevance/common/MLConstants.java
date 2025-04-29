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

    /**
     * ML input field names
     */
    public static final String PARAM_MESSAGES_FIELD = "messages";

    /**
     * ML response field names
     */
    public static final String RESPONSE_CHOICES_FIELD = "choices";
    public static final String RESPONSE_MESSAGE_FIELD = "message";
    public static final String RESPONSE_CONTENT_FIELD = "content";

    /**
     * Prompt strings that specific for llm-as-a-judge use case.
     * TODO: need benchmark for final prompt definition.
     */
    public static final String PROMPT_SEARCH_RELEVANCE = escapeJson(
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
    public static final String PROMPT_JSON_MESSAGES_SHELL = "[{\"role\":\"system\",\"content\":\"%s\"},"
        + "{\"role\":\"user\",\"content\":\"%s\"}]";
    public static final String INPUT_FORMAT_SEARCH = "SearchText - %s; Hits - %s";
    public static final String INPUT_FORMAT_SEARCH_WITH_REFERENCE = "SearchText: %s; Reference: %s; Hits: %s";

    public static String escapeJson(String str) {
        if (str == null) {
            return "";
        }
        return str.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r").replace("\t", "\\t");
    }
}
