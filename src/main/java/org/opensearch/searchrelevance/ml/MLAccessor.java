/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */
package org.opensearch.searchrelevance.ml;

import static org.opensearch.searchrelevance.common.MLConstants.INPUT_FORMAT_WITHOUT_REFERENCE;
import static org.opensearch.searchrelevance.common.MLConstants.INPUT_FORMAT_WITH_REFERENCE;
import static org.opensearch.searchrelevance.common.MLConstants.PARAM_MESSAGES_FIELD;
import static org.opensearch.searchrelevance.common.MLConstants.PROMPT_JSON_MESSAGES_SHELL;
import static org.opensearch.searchrelevance.common.MLConstants.PROMPT_WITHOUT_REFERENCE;
import static org.opensearch.searchrelevance.common.MLConstants.PROMPT_WITH_REFERENCE;
import static org.opensearch.searchrelevance.common.MLConstants.RESPONSE_FIELD;

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

    public void predict(String modelId, String question, String context, String reference, ActionListener<String> listener) {
        MLInput mlInput = getMLInput(question, context, reference);
        mlClient.predict(
            modelId,
            mlInput,
            ActionListener.wrap(mlOutput -> listener.onResponse(extractResponseContent(mlOutput)), listener::onFailure)
        );
    }

    private MLInput getMLInput(String question, String context, String reference) {
        Map<String, String> parameters = new HashMap<>();

        String messagesJson;
        if (Objects.isNull(reference) || reference.isEmpty()) {
            String inputs = String.format(Locale.ROOT, INPUT_FORMAT_WITHOUT_REFERENCE, question, context);
            LOGGER.debug("building mlInput without reference. inputs: {}", inputs);
            messagesJson = String.format(Locale.ROOT, PROMPT_JSON_MESSAGES_SHELL, PROMPT_WITHOUT_REFERENCE, inputs);
        } else {
            String inputs = String.format(Locale.ROOT, INPUT_FORMAT_WITH_REFERENCE, question, context, reference);
            LOGGER.debug("building mlInput with reference. inputs: {}", inputs);
            messagesJson = String.format(Locale.ROOT, PROMPT_JSON_MESSAGES_SHELL, PROMPT_WITH_REFERENCE, inputs);
        }

        parameters.put(PARAM_MESSAGES_FIELD, messagesJson);
        return MLInput.builder().algorithm(FunctionName.REMOTE).inputDataset(new RemoteInferenceInputDataSet(parameters)).build();
    }

    private String extractResponseContent(MLOutput mlOutput) throws IOException {
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

        XContentBuilder builder = XContentFactory.jsonBuilder();
        builder.startObject();
        builder.field(RESPONSE_FIELD, dataMap);
        builder.endObject();
        return builder.toString();
    }
}
