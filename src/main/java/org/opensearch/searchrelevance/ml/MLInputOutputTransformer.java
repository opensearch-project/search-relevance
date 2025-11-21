/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */
package org.opensearch.searchrelevance.ml;

import static org.opensearch.searchrelevance.common.MLConstants.PROMPT_SEARCH_RELEVANCE_SCORE_0_1_START;
import static org.opensearch.searchrelevance.common.MLConstants.PROMPT_SEARCH_RELEVANCE_SCORE_BINARY;
import static org.opensearch.searchrelevance.common.MLConstants.PROMPT_SEARCH_RELEVANCE_SCORE_END;
import static org.opensearch.searchrelevance.common.MLConstants.RATING_SCORE_BINARY_SCHEMA;
import static org.opensearch.searchrelevance.common.MLConstants.RATING_SCORE_NUMERIC_SCHEMA;
import static org.opensearch.searchrelevance.common.MLConstants.RESPONSE_FORMAT_TEMPLATE;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.opensearch.common.xcontent.XContentFactory;
import org.opensearch.core.common.util.CollectionUtils;
import org.opensearch.core.xcontent.XContentBuilder;
import org.opensearch.ml.common.FunctionName;
import org.opensearch.ml.common.dataset.remote.RemoteInferenceInputDataSet;
import org.opensearch.ml.common.input.MLInput;
import org.opensearch.ml.common.output.MLOutput;
import org.opensearch.ml.common.output.model.ModelTensor;
import org.opensearch.ml.common.output.model.ModelTensorOutput;
import org.opensearch.ml.common.output.model.ModelTensors;
import org.opensearch.searchrelevance.ml.connector.LLMConnector;
import org.opensearch.searchrelevance.ml.connector.OpenAIConnector;
import org.opensearch.searchrelevance.model.LLMJudgmentRatingType;

/**
 * Handles ML input/output transformations for search relevance predictions
 */
public class MLInputOutputTransformer {
    private static final Logger log = LogManager.getLogger(MLInputOutputTransformer.class);

    private final LLMConnector connector;

    public MLInputOutputTransformer() {
        // Default to OpenAI for backward compatibility
        this.connector = new OpenAIConnector();
    }

    public MLInputOutputTransformer(LLMConnector connector) {
        this.connector = connector;
    }

    public List<MLInput> createMLInputs(
        int tokenLimit,
        String searchText,
        Map<String, String> referenceData,
        Map<String, String> hits,
        String promptTemplate,
        LLMJudgmentRatingType ratingType
    ) {
        List<MLInput> mlInputs = new ArrayList<>();
        Map<String, String> currentChunk = new HashMap<>();

        for (Map.Entry<String, String> entry : hits.entrySet()) {
            Map<String, String> tempChunk = new HashMap<>(currentChunk);
            tempChunk.put(entry.getKey(), entry.getValue());

            String messages = buildMessagesArray(searchText, referenceData, tempChunk, promptTemplate, ratingType);
            int totalTokens = TokenizerUtil.countTokens(messages);

            if (totalTokens > tokenLimit) {
                if (currentChunk.isEmpty()) {
                    mlInputs.add(handleOversizedEntry(entry, searchText, referenceData, tokenLimit, promptTemplate, ratingType));
                } else {
                    mlInputs.add(createMLInput(searchText, referenceData, currentChunk, promptTemplate, ratingType));
                    currentChunk = new HashMap<>();
                    currentChunk.put(entry.getKey(), entry.getValue());
                }
            } else {
                currentChunk.put(entry.getKey(), entry.getValue());
            }
        }

        if (!currentChunk.isEmpty()) {
            mlInputs.add(createMLInput(searchText, referenceData, currentChunk, promptTemplate, ratingType));
        }

        return mlInputs;
    }

    private MLInput handleOversizedEntry(
        Map.Entry<String, String> entry,
        String searchText,
        Map<String, String> referenceData,
        int tokenLimit,
        String promptTemplate,
        LLMJudgmentRatingType ratingType
    ) {
        log.warn("Entry with key {} causes total tokens to exceed limit of {}", entry.getKey(), tokenLimit);

        Map<String, String> testChunk = Map.of(entry.getKey(), entry.getValue());
        String testMessages = buildMessagesArray(searchText, referenceData, testChunk, promptTemplate, ratingType);
        int excessTokens = TokenizerUtil.countTokens(testMessages) - tokenLimit;

        int currentTokens = TokenizerUtil.countTokens(entry.getValue());
        String truncatedValue = TokenizerUtil.truncateString(entry.getValue(), Math.max(1, currentTokens - excessTokens));

        Map<String, String> singleEntryChunk = Map.of(entry.getKey(), truncatedValue);
        return createMLInput(searchText, referenceData, singleEntryChunk, promptTemplate, ratingType);
    }

    public MLInput createMLInput(
        String searchText,
        Map<String, String> referenceData,
        Map<String, String> hits,
        String promptTemplate,
        LLMJudgmentRatingType ratingType
    ) {
        return createMLInput(searchText, referenceData, hits, promptTemplate, ratingType, true);
    }

    /**
     * Creates MLInput with optional response_format parameter.
     * Some models (like GPT-3.5) don't support response_format, so we can disable it for fallback.
     *
     * @param includeResponseFormat If true, includes response_format parameter; if false, excludes it
     */
    public MLInput createMLInput(
        String searchText,
        Map<String, String> referenceData,
        Map<String, String> hits,
        String promptTemplate,
        LLMJudgmentRatingType ratingType,
        boolean includeResponseFormat
    ) {
        Map<String, String> parameters = new HashMap<>();
        String messagesArray = buildMessagesArray(searchText, referenceData, hits, promptTemplate, ratingType);

        // Use connector-specific parameter name
        String paramName = connector.getMessageParameterName();
        parameters.put(paramName, messagesArray);

        // Only add response_format if requested (for models that support it)
        if (includeResponseFormat) {
            String responseFormat = getResponseFormat(ratingType);
            parameters.put("response_format", responseFormat);
        }

        return MLInput.builder().algorithm(FunctionName.REMOTE).inputDataset(new RemoteInferenceInputDataSet(parameters)).build();
    }

    private String buildMessagesArray(
        String searchText,
        Map<String, String> referenceData,
        Map<String, String> hits,
        String promptTemplate,
        LLMJudgmentRatingType ratingType
    ) {
        try {
            String hitsJson = buildHitsJson(hits);
            String userContent = UserPromptFactory.buildUserContent(searchText, referenceData, hitsJson, promptTemplate);
            String systemPrompt = getSystemPrompt(ratingType);
            return connector.formatPrompt(systemPrompt, userContent);
        } catch (IOException e) {
            log.error("Error converting hits to JSON string", e);
            throw new IllegalArgumentException("Failed to process hits", e);
        }
    }

    private static String getSystemPrompt(LLMJudgmentRatingType ratingType) {
        String systemPromptStart;
        String systemPromptEnd = PROMPT_SEARCH_RELEVANCE_SCORE_END;

        // Handle null ratingType with default
        if (ratingType == null) {
            ratingType = LLMJudgmentRatingType.SCORE0_1;
        }

        switch (ratingType) {
            case SCORE0_1:
                systemPromptStart = PROMPT_SEARCH_RELEVANCE_SCORE_0_1_START;
                break;
            default:
                systemPromptStart = PROMPT_SEARCH_RELEVANCE_SCORE_BINARY;
        }
        return systemPromptStart + systemPromptEnd;
    }

    private static String getResponseFormat(LLMJudgmentRatingType ratingType) {
        // Handle null ratingType with default
        if (ratingType == null) {
            ratingType = LLMJudgmentRatingType.SCORE0_1;
        }

        String schema;
        switch (ratingType) {
            case SCORE0_1:
                schema = RATING_SCORE_NUMERIC_SCHEMA;
                break;
            case RELEVANT_IRRELEVANT:
                schema = RATING_SCORE_BINARY_SCHEMA;
                break;
            default:
                schema = RATING_SCORE_NUMERIC_SCHEMA;
        }
        return String.format(Locale.ROOT, RESPONSE_FORMAT_TEMPLATE, schema);
    }

    private String buildHitsJson(Map<String, String> hits) throws IOException {
        try (XContentBuilder builder = XContentFactory.jsonBuilder()) {
            builder.startArray();
            for (Map.Entry<String, String> hit : hits.entrySet()) {
                builder.startObject();
                builder.field("id", hit.getKey());
                builder.field("source", hit.getValue());
                builder.endObject();
            }
            builder.endArray();
            return builder.toString();
        }
    }

    public String extractResponseContent(MLOutput mlOutput) {
        if (!(mlOutput instanceof ModelTensorOutput)) {
            throw new IllegalArgumentException("Expected ModelTensorOutput, but got " + mlOutput.getClass().getSimpleName());
        }

        ModelTensorOutput modelTensorOutput = (ModelTensorOutput) mlOutput;
        List<ModelTensors> tensorOutputList = modelTensorOutput.getMlModelOutputs();

        if (CollectionUtils.isEmpty(tensorOutputList) || CollectionUtils.isEmpty(tensorOutputList.get(0).getMlModelTensors())) {
            throw new IllegalStateException(
                "Empty model result produced. Expected at least [1] tensor output and [1] model tensor, but got [0]"
            );
        }

        ModelTensor tensor = tensorOutputList.get(0).getMlModelTensors().get(0);
        Map<String, ?> dataMap = tensor.getDataAsMap();

        return connector.extractResponse(dataMap);
    }
}
