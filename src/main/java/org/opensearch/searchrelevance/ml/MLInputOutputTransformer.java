/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */
package org.opensearch.searchrelevance.ml;

import static org.opensearch.searchrelevance.common.MLConstants.INPUT_FORMAT_SEARCH;
import static org.opensearch.searchrelevance.common.MLConstants.INPUT_FORMAT_SEARCH_WITH_REFERENCE;
import static org.opensearch.searchrelevance.common.MLConstants.PARAM_MESSAGES_FIELD;
import static org.opensearch.searchrelevance.common.MLConstants.PROMPT_JSON_MESSAGES_SHELL;
import static org.opensearch.searchrelevance.common.MLConstants.PROMPT_SEARCH_RELEVANCE;
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
import java.util.Objects;

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

import lombok.extern.log4j.Log4j2;

/**
 * Handles ML input/output transformations for search relevance predictions
 */
@Log4j2
public class MLInputOutputTransformer {

    public List<MLInput> createMLInputs(int tokenLimit, String searchText, String reference, Map<String, String> hits) {
        List<MLInput> mlInputs = new ArrayList<>();
        Map<String, String> currentChunk = new HashMap<>();

        for (Map.Entry<String, String> entry : hits.entrySet()) {
            Map<String, String> tempChunk = new HashMap<>(currentChunk);
            tempChunk.put(entry.getKey(), entry.getValue());

            String messages = formatMessages(searchText, reference, tempChunk);
            int totalTokens = TokenizerUtil.countTokens(messages);

            if (totalTokens > tokenLimit) {
                if (currentChunk.isEmpty()) {
                    mlInputs.add(handleOversizedEntry(entry, searchText, reference, tokenLimit));
                } else {
                    mlInputs.add(createMLInput(searchText, reference, currentChunk));
                    currentChunk = new HashMap<>();
                    currentChunk.put(entry.getKey(), entry.getValue());
                }
            } else {
                currentChunk.put(entry.getKey(), entry.getValue());
            }
        }

        if (!currentChunk.isEmpty()) {
            mlInputs.add(createMLInput(searchText, reference, currentChunk));
        }

        return mlInputs;
    }

    private MLInput handleOversizedEntry(Map.Entry<String, String> entry, String searchText, String reference, int tokenLimit) {
        log.warn("Entry with key {} causes total tokens to exceed limit of {}", entry.getKey(), tokenLimit);

        Map<String, String> testChunk = Map.of(entry.getKey(), entry.getValue());
        String testMessages = formatMessages(searchText, reference, testChunk);
        int excessTokens = TokenizerUtil.countTokens(testMessages) - tokenLimit;

        int currentTokens = TokenizerUtil.countTokens(entry.getValue());
        String truncatedValue = TokenizerUtil.truncateString(entry.getValue(), Math.max(1, currentTokens - excessTokens));

        Map<String, String> singleEntryChunk = Map.of(entry.getKey(), truncatedValue);
        return createMLInput(searchText, reference, singleEntryChunk);
    }

    public MLInput createMLInput(String searchText, String reference, Map<String, String> hits) {
        Map<String, String> parameters = new HashMap<>();
        parameters.put(PARAM_MESSAGES_FIELD, formatMessages(searchText, reference, hits));
        return MLInput.builder().algorithm(FunctionName.REMOTE).inputDataset(new RemoteInferenceInputDataSet(parameters)).build();
    }

    public String formatMessages(String searchText, String reference, Map<String, String> hits) {
        try {
            String hitsJson = buildHitsJson(hits);
            String userContent = buildUserContent(searchText, reference, hitsJson);
            return String.format(Locale.ROOT, PROMPT_JSON_MESSAGES_SHELL, PROMPT_SEARCH_RELEVANCE, escapeJson(userContent));
        } catch (IOException e) {
            log.error("Error converting hits to JSON string", e);
            throw new IllegalArgumentException("Failed to process hits", e);
        }
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

    private String buildUserContent(String searchText, String reference, String hitsJson) {
        if (Objects.isNull(reference) || reference.isEmpty()) {
            return String.format(Locale.ROOT, INPUT_FORMAT_SEARCH, searchText, hitsJson);
        } else {
            return String.format(Locale.ROOT, INPUT_FORMAT_SEARCH_WITH_REFERENCE, searchText, reference, hitsJson);
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
