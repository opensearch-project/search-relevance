/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */
package org.opensearch.searchrelevance.transport.judgment;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import org.opensearch.action.StepListener;
import org.opensearch.action.index.IndexResponse;
import org.opensearch.action.support.ActionFilters;
import org.opensearch.action.support.HandledTransportAction;
import org.opensearch.cluster.service.ClusterService;
import org.opensearch.common.inject.Inject;
import org.opensearch.core.action.ActionListener;
import org.opensearch.core.common.util.CollectionUtils;
import org.opensearch.core.rest.RestStatus;
import org.opensearch.ml.client.MachineLearningNodeClient;
import org.opensearch.ml.common.FunctionName;
import org.opensearch.ml.common.dataset.MLInputDataset;
import org.opensearch.ml.common.dataset.remote.RemoteInferenceInputDataSet;
import org.opensearch.ml.common.input.MLInput;
import org.opensearch.ml.common.output.MLOutput;
import org.opensearch.ml.common.output.model.ModelTensor;
import org.opensearch.ml.common.output.model.ModelTensorOutput;
import org.opensearch.ml.common.output.model.ModelTensors;
import org.opensearch.searchrelevance.dao.JudgmentDao;
import org.opensearch.searchrelevance.exception.SearchRelevanceException;
import org.opensearch.searchrelevance.model.Judgment;
import org.opensearch.searchrelevance.utils.TimeUtils;
import org.opensearch.tasks.Task;
import org.opensearch.transport.TransportService;

public class PutJudgmentTransportAction extends HandledTransportAction<PutJudgmentRequest, IndexResponse> {
    private final ClusterService clusterService;
    private final JudgmentDao judgmentDao;
    private MachineLearningNodeClient mlClient;

    @Inject
    public PutJudgmentTransportAction(
        ClusterService clusterService,
        TransportService transportService,
        ActionFilters actionFilters,
        JudgmentDao judgmentDao,
        MachineLearningNodeClient mlClient
    ) {
        super(PutJudgmentAction.NAME, transportService, actionFilters, PutJudgmentRequest::new);
        this.clusterService = clusterService;
        this.judgmentDao = judgmentDao;
        this.mlClient = mlClient;
    }

    @Override
    protected void doExecute(Task task, PutJudgmentRequest request, ActionListener<IndexResponse> listener) {
        if (request == null) {
            listener.onFailure(new SearchRelevanceException("Request cannot be null", RestStatus.BAD_REQUEST));
            return;
        }
        String id = UUID.randomUUID().toString();
        String timestamp = TimeUtils.getTimestamp();
        MLInput mlInput = getMLInput(request.getQuestion(), request.getContext(), request.getReference());

        // Execute ML prediction
        mlClient.predict(request.getModelId(), mlInput, ActionListener.wrap(mlOutput -> {
            // Extract the response content
            String response = extractResponseContent(mlOutput);

            // Create index if it doesn't exist
            StepListener<Void> createIndexStep = new StepListener<>();
            judgmentDao.createIndexIfAbsent(createIndexStep);

            createIndexStep.whenComplete(v -> {
                Judgment judgment = new Judgment(id, timestamp, response);
                judgmentDao.putJudgement(judgment, listener);
            }, listener::onFailure);
        }, listener::onFailure));
    }

    private MLInput getMLInput(String question, String context, String reference) {
        Map<String, String> parameters = new HashMap<>();

        // Construct the messages as a JSON string with explicit Locale.ROOT
        String messagesJson = String.format(
            Locale.ROOT,
            "[{\"role\":\"system\",\"content\":\"You are a helpful assistant to judge if context answered the question. And give a relevance_score ranged from 0 to 1.\"},"
                + "{\"role\":\"user\",\"content\":\"questions - %s; context - %s\"}]",
            question,
            context
        );

        parameters.put("messages", messagesJson);

        MLInputDataset inputDataSet = new RemoteInferenceInputDataSet(parameters);

        return MLInput.builder().algorithm(FunctionName.REMOTE).inputDataset(inputDataSet).build();
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

        // Navigate through the nested structure
        @SuppressWarnings("unchecked")
        Map<String, Object> inferenceResults = (Map<String, Object>) ((List<?>) dataMap.get("inference_results")).get(0);
        @SuppressWarnings("unchecked")
        Map<String, Object> output = (Map<String, Object>) ((List<?>) inferenceResults.get("output")).get(0);
        @SuppressWarnings("unchecked")
        Map<String, Object> dataAsMap = (Map<String, Object>) output.get("dataAsMap");
        @SuppressWarnings("unchecked")
        Map<String, Object> choice = (Map<String, Object>) ((List<?>) dataAsMap.get("choices")).get(0);
        @SuppressWarnings("unchecked")
        Map<String, Object> message = (Map<String, Object>) choice.get("message");

        return (String) message.get("content");
    }

}
