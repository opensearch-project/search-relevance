/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */
package org.opensearch.searchrelevance.judgments;

import java.io.IOException;
import java.util.List;

import org.opensearch.core.xcontent.ToXContentObject;
import org.opensearch.core.xcontent.XContentBuilder;
import org.opensearch.searchrelevance.ml.connector.ConnectorType;
import org.opensearch.searchrelevance.model.LLMJudgmentRatingType;
import org.opensearch.searchrelevance.model.SearchConfiguration;

import lombok.Builder;
import lombok.Getter;

/**
 * Context object to hold LLM judgment parameters
 */
@Getter
@Builder
public class LlmJudgmentContext implements ToXContentObject {
    public static final String MODEL_ID = "modelId";
    public static final String SIZE = "size";
    public static final String TOKEN_LIMIT = "tokenLimit";
    public static final String CONTEXT_FIELDS = "contextFields";
    public static final String SEARCH_CONFIGURATIONS = "searchConfigurations";
    public static final String IGNORE_FAILURE = "ignoreFailure";
    public static final String PROMPT_TEMPLATE = "promptTemplate";
    public static final String RATING_TYPE = "ratingType";
    public static final String OVERWRITE_CACHE = "overwriteCache";
    public static final String CONNECTOR_TYPE = "connectorType";
    public static final String RATE_LIMIT = "rateLimit";

    private final String modelId;
    private final int size;
    private final int tokenLimit;
    private final List<String> contextFields;
    private final List<SearchConfiguration> searchConfigurations;
    private final boolean ignoreFailure;
    private final String promptTemplate;
    private final LLMJudgmentRatingType ratingType;
    private final boolean overwriteCache;
    private final ConnectorType connectorType;
    private final long rateLimit; // milliseconds between requests

    @Override
    public XContentBuilder toXContent(XContentBuilder builder, Params params) throws IOException {
        builder.startObject();
        builder.field(MODEL_ID, modelId);
        builder.field(SIZE, size);
        builder.field(TOKEN_LIMIT, tokenLimit);
        if (contextFields != null) {
            builder.field(CONTEXT_FIELDS, contextFields);
        }
        if (searchConfigurations != null) {
            builder.field(SEARCH_CONFIGURATIONS, searchConfigurations);
        }
        builder.field(IGNORE_FAILURE, ignoreFailure);
        if (promptTemplate != null) {
            builder.field(PROMPT_TEMPLATE, promptTemplate);
        }
        // Always include ratingType, use default if null
        String ratingTypeValue = (ratingType != null) ? ratingType.name() : LLMJudgmentRatingType.SCORE0_1.name();
        builder.field(RATING_TYPE, ratingTypeValue);
        builder.field(OVERWRITE_CACHE, overwriteCache);
        if (connectorType != null) {
            builder.field(CONNECTOR_TYPE, connectorType.name());
        }
        builder.field(RATE_LIMIT, rateLimit);
        return builder.endObject();
    }
}
