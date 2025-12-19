/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */
package org.opensearch.searchrelevance.common;

import java.util.Locale;
import java.util.Map;

import org.opensearch.searchrelevance.model.LLMJudgmentRatingType;

/**
 * ML related constants.
 */
public class MLConstants {

    private MLConstants() {}

    /**
     * ML input field names
     */
    public static final String PARAM_MESSAGES_FIELD = "messages";
    public static final String PROMPT_TEMPLATE = "promptTemplate";
    public static final String LLM_JUDGMENT_RATING_TYPE = "llmJudgmentRatingType";
    public static final String OVERWRITE_CACHE = "overwriteCache";

    /**
     * Prompt template placeholder names.
     * These are the special variables that can be used in custom prompt templates.
     */
    public static final String PLACEHOLDER_QUERY_TEXT = "queryText";
    public static final String PLACEHOLDER_SEARCH_TEXT = "searchText";
    public static final String PLACEHOLDER_HITS = "hits";
    public static final String PLACEHOLDER_RESULTS = "results";
    public static final String PLACEHOLDER_REFERENCE = "reference";
    public static final String PLACEHOLDER_REFERENCE_ANSWER = "referenceAnswer";

    /**
     * Default prompt template for LLM judgments (simple format without reference data)
     */
    public static final String DEFAULT_PROMPT_TEMPLATE = "SearchText: {{searchText}}; Hits: {{hits}}";

    /**
     * ML response field names
     */
    public static final String RESPONSE_CHOICES_FIELD = "choices";
    public static final String RESPONSE_MESSAGE_FIELD = "message";
    public static final String RESPONSE_CONTENT_FIELD = "content";

    /**
     * LLM RELEVANT/IRRELEVANT String
     */
    public static final String RELEVANT_DECISION_STRING = "RELEVANT";
    public static final String IRRELEVANT_DECISION_STRING = "IRRELEVANT";

    /**
     * LLM defaulted token limits
     */
    public static final Integer DEFAULTED_TOKEN_LIMIT = 4000;
    public static final Integer MAXIMUM_TOKEN_LIMIT = 500000;
    public static final Integer MINIMUM_TOKEN_LIMIT = 1000;

    public static final String PROMPT_SEARCH_RELEVANCE_SCORE_0_1_START = escapeJson(
        "You are an expert search relevance rater. Your task is to evaluate the relevance between search query and results with these criteria:\n"
            + "- Score 1.0: Perfect match, highly relevant\n"
            + "- Score 0.7-0.9: Very relevant with minor variations\n"
            + "- Score 0.4-0.6: Moderately relevant\n"
            + "- Score 0.1-0.3: Slightly relevant\n"
            + "- Score 0.0: Completely irrelevant\n"
    );

    public static final String PROMPT_SEARCH_RELEVANCE_SCORE_BINARY = escapeJson(
        "You are an expert search relevance rater. Your task is to evaluate the relevance between search query and results with these criteria:\n"
            + "RELEVANT: Perfect match, highly relevant\n"
            + "IRRELEVANT: Completely irrelevant\n"
    );

    public static final String PROMPT_SEARCH_RELEVANCE_SCORE_END = escapeJson(
        "\nEvaluate based on: exact matches, semantic relevance, and overall context between the SearchText and content in Hits.\n"
            + "When a reference is provided, evaluate based on the relevance to both SearchText and its reference.\n\n"
            + "IMPORTANT: You MUST include a rating for EVERY hit provided.\n\n"
            + "Return ONLY a JSON object in this EXACT format:\n"
            + "{\"ratings\": [{\"id\": \"doc_id_here\", \"rating_score\": <score/RELEVANT/IRRELEVANT>}]}\n"
            + "Do not include any explanation, commentary, or markdown formatting. Return only the JSON object."
    );

    /**
     * JSON Schema definitions for OpenAI structured output.
     * These schemas enforce the output format at the model level.
     */
    public static final String RATING_SCORE_NUMERIC_SCHEMA = "{"
        + "\"type\":\"object\","
        + "\"properties\":{"
        + "\"id\":{\"type\":\"string\"},"
        + "\"rating_score\":{\"type\":\"number\"}"
        + "},"
        + "\"required\":[\"id\",\"rating_score\"],"
        + "\"additionalProperties\":false"
        + "}";

    public static final String RATING_SCORE_BINARY_SCHEMA = "{"
        + "\"type\":\"object\","
        + "\"properties\":{"
        + "\"id\":{\"type\":\"string\"},"
        + "\"rating_score\":{\"type\":\"string\",\"enum\":[\"RELEVANT\",\"IRRELEVANT\"]}"
        + "},"
        + "\"required\":[\"id\",\"rating_score\"],"
        + "\"additionalProperties\":false"
        + "}";

    public static final String RESPONSE_FORMAT_TEMPLATE = "{"
        + "\"type\":\"json_schema\","
        + "\"json_schema\":{"
        + "\"name\":\"rating_response\","
        + "\"strict\":true,"
        + "\"schema\":{"
        + "\"type\":\"object\","
        + "\"properties\":{"
        + "\"ratings\":{"
        + "\"type\":\"array\","
        + "\"items\":%s"
        + "}"
        + "},"
        + "\"required\":[\"ratings\"],"
        + "\"additionalProperties\":false"
        + "}"
        + "}"
        + "}";

    public static final String PROMPT_JSON_MESSAGES_SHELL = "[{\"role\":\"system\",\"content\":\"%s\"},"
        + "{\"role\":\"user\",\"content\":\"%s\"}]";
    public static final String PROMPT_JSON_MESSAGES_WITH_SCHEMA_SHELL = "{"
        + "\"messages\":[{\"role\":\"system\",\"content\":\"%s\"},{\"role\":\"user\",\"content\":\"%s\"}],"
        + "\"response_format\":%s"
        + "}";
    public static final String INPUT_FORMAT_SEARCH = "SearchText - %s; Hits - %s";
    public static final String INPUT_FORMAT_SEARCH_WITH_REFERENCE = "SearchText: %s; Reference: %s; Hits: %s";

    public static String escapeJson(String str) {
        if (str == null) {
            return "";
        }
        return str.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r").replace("\t", "\\t");
    }

    /**
     * Get the appropriate response format schema based on rating type.
     * @param ratingType The rating type to get the schema for
     * @return The complete response_format JSON string with the appropriate schema
     */
    public static String getResponseFormatSchema(LLMJudgmentRatingType ratingType) {
        String itemSchema;
        if (ratingType == LLMJudgmentRatingType.RELEVANT_IRRELEVANT) {
            itemSchema = RATING_SCORE_BINARY_SCHEMA;
        } else {
            itemSchema = RATING_SCORE_NUMERIC_SCHEMA;
        }
        return String.format(Locale.ROOT, RESPONSE_FORMAT_TEMPLATE, itemSchema);
    }

    public static int validateTokenLimit(Map<String, Object> source) {
        if (!source.containsKey("tokenLimit")) {
            return DEFAULTED_TOKEN_LIMIT;
        }

        Object tokenLimitObj = source.get("tokenLimit");
        int tokenLimit;

        try {
            if (tokenLimitObj instanceof String) {
                tokenLimit = Integer.parseInt((String) tokenLimitObj);
            } else if (tokenLimitObj instanceof Number) {
                tokenLimit = ((Number) tokenLimitObj).intValue();
            } else {
                throw new IllegalArgumentException(
                    "Invalid tokenLimit type. Expected numeric value or string, got: " + tokenLimitObj.getClass().getSimpleName()
                );
            }

            // Validate range
            if (tokenLimit < MINIMUM_TOKEN_LIMIT || tokenLimit > MAXIMUM_TOKEN_LIMIT) {
                throw new IllegalArgumentException(
                    String.format(
                        Locale.ROOT,
                        "TokenLimit must be between %d and %d, got: %d",
                        MINIMUM_TOKEN_LIMIT,
                        MAXIMUM_TOKEN_LIMIT,
                        tokenLimit
                    )
                );
            }

            return tokenLimit;
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid tokenLimit value. Expected numeric value, got: " + tokenLimitObj);
        }
    }

}
