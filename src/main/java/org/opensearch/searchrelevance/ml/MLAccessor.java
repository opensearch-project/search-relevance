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
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.opensearch.common.xcontent.XContentFactory;
import org.opensearch.core.action.ActionListener;
import org.opensearch.core.common.util.CollectionUtils;
import org.opensearch.core.xcontent.XContentBuilder;
import org.opensearch.ml.client.MachineLearningNodeClient;
import org.opensearch.ml.common.FunctionName;
import org.opensearch.ml.common.dataset.remote.RemoteInferenceInputDataSet;
import org.opensearch.ml.common.input.MLInput;
import org.opensearch.ml.common.output.MLOutput;
import org.opensearch.ml.common.output.model.ModelTensor;
import org.opensearch.ml.common.output.model.ModelTensorOutput;
import org.opensearch.ml.common.output.model.ModelTensors;

/**
 * This is a ml-commons accessor that will call predict API and process ml input/output.
 */
public class MLAccessor {
    private MachineLearningNodeClient mlClient;

    private static final Logger LOGGER = LogManager.getLogger(MLAccessor.class);

    public MLAccessor(MachineLearningNodeClient mlClient) {
        this.mlClient = mlClient;
    }

    public void predict(
        String modelId,
        String searchText,
        String reference,
        List<Map<String, String>> hits,
        ActionListener<String> listener
    ) {
        MLInput mlInput = getMLInput(searchText, reference, hits);
        mlClient.predict(
            modelId,
            mlInput,
            ActionListener.wrap(mlOutput -> listener.onResponse(extractResponseContent(mlOutput)), listener::onFailure)
        );
    }

    private MLInput getMLInput(String searchText, String reference, List<Map<String, String>> hits) {
        Map<String, String> parameters = new HashMap<>();
        try {
            // Use XContentBuilder to create JSON string. JSON serialization/deserialization through Jackson needs to use reflection to
            // access class members, which is restricted by the security policy
            String hitsJson;
            try (XContentBuilder builder = XContentFactory.jsonBuilder()) {
                builder.startArray();
                for (Map<String, String> hit : hits) {
                    builder.startObject();
                    for (Map.Entry<String, String> entry : hit.entrySet()) {
                        builder.field(entry.getKey(), entry.getValue());
                    }
                    builder.endObject();
                }
                builder.endArray();
                hitsJson = builder.toString();
            }
            String userContent;
            if (Objects.isNull(reference) || reference.isEmpty()) {
                userContent = String.format(Locale.ROOT, INPUT_FORMAT_SEARCH, searchText, hitsJson);
            } else {
                userContent = String.format(Locale.ROOT, INPUT_FORMAT_SEARCH_WITH_REFERENCE, searchText, reference, hitsJson);
            }
            String messages = String.format(Locale.ROOT, PROMPT_JSON_MESSAGES_SHELL, PROMPT_SEARCH_RELEVANCE, escapeJson(userContent));

            parameters.put(PARAM_MESSAGES_FIELD, messages);
            return MLInput.builder().algorithm(FunctionName.REMOTE).inputDataset(new RemoteInferenceInputDataSet(parameters)).build();
        } catch (IOException e) {
            LOGGER.error("Error converting hits to JSON string", e);
            throw new IllegalArgumentException("Failed to process hits", e);
        }
    }

    private String extractResponseContent(MLOutput mlOutput) {
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
        String content = (String) message.get(RESPONSE_CONTENT_FIELD);
        return content;
    }
}
