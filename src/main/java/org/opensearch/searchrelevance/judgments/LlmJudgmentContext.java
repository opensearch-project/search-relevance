/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */
package org.opensearch.searchrelevance.judgments;

import java.util.List;

import org.opensearch.searchrelevance.ml.connector.ConnectorType;
import org.opensearch.searchrelevance.model.LLMJudgmentRatingType;
import org.opensearch.searchrelevance.model.SearchConfiguration;

/**
 * Context object to hold LLM judgment parameters
 */
public class LlmJudgmentContext {
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

    private LlmJudgmentContext(Builder builder) {
        this.modelId = builder.modelId;
        this.size = builder.size;
        this.tokenLimit = builder.tokenLimit;
        this.contextFields = builder.contextFields;
        this.searchConfigurations = builder.searchConfigurations;
        this.ignoreFailure = builder.ignoreFailure;
        this.promptTemplate = builder.promptTemplate;
        this.ratingType = builder.ratingType;
        this.overwriteCache = builder.overwriteCache;
        this.connectorType = builder.connectorType;
    }

    public static Builder builder() {
        return new Builder();
    }

    public String getModelId() {
        return modelId;
    }

    public int getSize() {
        return size;
    }

    public int getTokenLimit() {
        return tokenLimit;
    }

    public List<String> getContextFields() {
        return contextFields;
    }

    public List<SearchConfiguration> getSearchConfigurations() {
        return searchConfigurations;
    }

    public boolean isIgnoreFailure() {
        return ignoreFailure;
    }

    public String getPromptTemplate() {
        return promptTemplate;
    }

    public LLMJudgmentRatingType getRatingType() {
        return ratingType;
    }

    public boolean isOverwriteCache() {
        return overwriteCache;
    }

    public ConnectorType getConnectorType() {
        return connectorType;
    }

    public static class Builder {
        private String modelId;
        private int size;
        private int tokenLimit;
        private List<String> contextFields;
        private List<SearchConfiguration> searchConfigurations;
        private boolean ignoreFailure;
        private String promptTemplate;
        private LLMJudgmentRatingType ratingType;
        private boolean overwriteCache;
        private ConnectorType connectorType;

        public Builder modelId(String modelId) {
            this.modelId = modelId;
            return this;
        }

        public Builder size(int size) {
            this.size = size;
            return this;
        }

        public Builder tokenLimit(int tokenLimit) {
            this.tokenLimit = tokenLimit;
            return this;
        }

        public Builder contextFields(List<String> contextFields) {
            this.contextFields = contextFields;
            return this;
        }

        public Builder searchConfigurations(List<SearchConfiguration> searchConfigurations) {
            this.searchConfigurations = searchConfigurations;
            return this;
        }

        public Builder ignoreFailure(boolean ignoreFailure) {
            this.ignoreFailure = ignoreFailure;
            return this;
        }

        public Builder promptTemplate(String promptTemplate) {
            this.promptTemplate = promptTemplate;
            return this;
        }

        public Builder ratingType(LLMJudgmentRatingType ratingType) {
            this.ratingType = ratingType;
            return this;
        }

        public Builder overwriteCache(boolean overwriteCache) {
            this.overwriteCache = overwriteCache;
            return this;
        }

        public Builder connectorType(ConnectorType connectorType) {
            this.connectorType = connectorType;
            return this;
        }

        public LlmJudgmentContext build() {
            return new LlmJudgmentContext(this);
        }
    }
}
