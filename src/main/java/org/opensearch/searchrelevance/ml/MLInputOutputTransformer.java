/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */
package org.opensearch.searchrelevance.ml;

import static org.opensearch.searchrelevance.common.MLConstants.PARAM_MESSAGES_FIELD;
import static org.opensearch.searchrelevance.common.MLConstants.PROMPT_JSON_MESSAGES_SHELL;
import static org.opensearch.searchrelevance.common.MLConstants.PROMPT_SEARCH_RELEVANCE_SCORE_0_1_START;
import static org.opensearch.searchrelevance.common.MLConstants.PROMPT_SEARCH_RELEVANCE_SCORE_1_5_START;
import static org.opensearch.searchrelevance.common.MLConstants.PROMPT_SEARCH_RELEVANCE_SCORE_BINARY;
import static org.opensearch.searchrelevance.common.MLConstants.PROMPT_SEARCH_RELEVANCE_SCORE_END;
import static org.opensearch.searchrelevance.common.MLConstants.RESPONSE_CHOICES_FIELD;
import static org.opensearch.searchrelevance.common.MLConstants.RESPONSE_CONTENT_FIELD;
import static org.opensearch.searchrelevance.common.MLConstants.RESPONSE_MESSAGE_FIELD;
import static org.opensearch.searchrelevance.common.MLConstants.escapeJson;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

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
import org.opensearch.searchrelevance.model.LLMJudgmentRatingType;

import lombok.extern.log4j.Log4j2;

/**
 * Handles ML input/output transformations for search relevance predictions
 */
@Log4j2
public class MLInputOutputTransformer {

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

            String messages = formatMessages(searchText, referenceData, tempChunk, promptTemplate, ratingType);
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
        String testMessages = formatMessages(searchText, referenceData, testChunk, promptTemplate, ratingType);
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
        Map<String, String> parameters = new HashMap<>();
        parameters.put(PARAM_MESSAGES_FIELD, formatMessages(searchText, referenceData, hits, promptTemplate, ratingType));
        return MLInput.builder().algorithm(FunctionName.REMOTE).inputDataset(new RemoteInferenceInputDataSet(parameters)).build();
    }

    public String formatMessages(
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
            return String.format(Locale.ROOT, PROMPT_JSON_MESSAGES_SHELL, systemPrompt, escapeJson(userContent));
        } catch (IOException e) {
            log.error("Error converting hits to JSON string", e);
            throw new IllegalArgumentException("Failed to process hits", e);
        }
    }

    private static String getSystemPrompt(LLMJudgmentRatingType ratingType) {
        String systemPromptStart;
        String systemPromptEnd = PROMPT_SEARCH_RELEVANCE_SCORE_END;
        switch (ratingType) {
            case LLMJudgmentRatingType.SCORE0_1:
                systemPromptStart = PROMPT_SEARCH_RELEVANCE_SCORE_0_1_START;
                break;
            case LLMJudgmentRatingType.SCORE1_5:
                systemPromptStart = PROMPT_SEARCH_RELEVANCE_SCORE_1_5_START;
                break;
            default:
                systemPromptStart = PROMPT_SEARCH_RELEVANCE_SCORE_BINARY;
        }
        return systemPromptStart + systemPromptEnd;
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

        Map<String, ?> choices = (Map<String, ?>) ((List<?>) dataMap.get(RESPONSE_CHOICES_FIELD)).get(0);
        Map<String, ?> message = (Map<String, ?>) choices.get(RESPONSE_MESSAGE_FIELD);
        return (String) message.get(RESPONSE_CONTENT_FIELD);
    }
}
