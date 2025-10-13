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

    /**
     * ML response field names
     */
    public static final String RESPONSE_CHOICES_FIELD = "choices";
    public static final String RESPONSE_MESSAGE_FIELD = "message";
    public static final String RESPONSE_CONTENT_FIELD = "content";

    /**
     * LLM defaulted token limits
     */
    public static final Integer DEFAULTED_TOKEN_LIMIT = 4000;
    public static final Integer MAXIMUM_TOKEN_LIMIT = 500000;
    public static final Integer MINIMUM_TOKEN_LIMIT = 1000;

    /**
     * Prompt strings that specific for llm-as-a-judge use case.
     * TODO: need benchmark for final prompt definition.
     */
    public static final String PROMPT_SEARCH_RELEVANCE_SCORE_1_5_START = escapeJson(
        "You are an expert search relevance rater. Your task is to evaluate the relevance between search query and results with these criteria:\n"
            + "- Score 5: Perfect match, highly relevant\n"
            + "- Score 4: Very relevant with minor variations\n"
            + "- Score 3: Moderately relevant\n"
            + "- Score 2: Slightly relevant\n"
            + "- Score 1: Completely irrelevant\n"
    );

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

    /**
     * Sanitize LLM response without rating type validation (backward compatibility).
     * @deprecated Use {@link RatingOutputProcessor#sanitizeLLMResponse(String)} instead
     * @param response The raw LLM response
     * @return Sanitized JSON array string
     */
    @Deprecated
    public static String sanitizeLLMResponse(String response) {
        return RatingOutputProcessor.sanitizeLLMResponse(response);
    }

    /**
     * Sanitize LLM response and optionally validate ratings based on rating type.
     * @deprecated Use {@link RatingOutputProcessor#sanitizeLLMResponse(String, LLMJudgmentRatingType)} instead
     * @param response The raw LLM response
     * @param ratingType The expected rating type (nullable for backward compatibility)
     * @return Sanitized JSON array string
     */
    @Deprecated
    public static String sanitizeLLMResponse(String response, LLMJudgmentRatingType ratingType) {
        return RatingOutputProcessor.sanitizeLLMResponse(response, ratingType);
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
