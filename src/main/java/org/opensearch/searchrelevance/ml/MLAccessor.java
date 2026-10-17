/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */
package org.opensearch.searchrelevance.ml;

import java.util.List;
import java.util.Map;

import org.opensearch.core.action.ActionListener;
import org.opensearch.ml.client.MachineLearningNodeClient;
import org.opensearch.ml.common.dataset.remote.RemoteInferenceInputDataSet;
import org.opensearch.ml.common.input.MLInput;
import org.opensearch.searchrelevance.model.LLMJudgmentRatingType;
import org.opensearch.searchrelevance.utils.RatingOutputProcessor;

import lombok.extern.log4j.Log4j2;

/**
 * This is a ml-commons accessor that will call predict API and process ml input/output.
 */
@Log4j2
public class MLAccessor {
    private final MachineLearningNodeClient mlClient;
    private final MLInputOutputTransformer transformer;

    public MLAccessor(MachineLearningNodeClient mlClient) {
        this.mlClient = mlClient;
        this.transformer = new MLInputOutputTransformer();
    }

    public void predict(
        String modelId,
        int tokenLimit,
        String searchText,
        Map<String, String> referenceData,
        Map<String, String> hits,
        String promptTemplate,
        LLMJudgmentRatingType ratingType,
        ActionListener<ChunkResult> progressListener
    ) {
        log.debug(
            "DEBUG: MLAccessor.predict called with modelId: {}, searchText: {}, hits count: {}, ratingType: {}",
            modelId,
            searchText,
            hits.size(),
            ratingType
        );
        List<MLInput> mlInputs = transformer.createMLInputs(tokenLimit, searchText, referenceData, hits, promptTemplate, ratingType);
        log.info("Number of chunks: {}", mlInputs.size());
        log.debug("DEBUG: Created {} MLInput chunks", mlInputs.size());

        ChunkProcessingContext context = new ChunkProcessingContext(mlInputs.size(), progressListener);

        for (int i = 0; i < mlInputs.size(); i++) {
            processChunk(modelId, mlInputs.get(i), i, context);
        }
    }

    private void processChunk(String modelId, MLInput mlInput, int chunkIndex, ChunkProcessingContext context) {
        // Transient-error retries (throttling, 5xx, timeouts) are handled by the ml-commons connector's client_config.
        predictSingleChunk(modelId, mlInput, ActionListener.wrap(response -> {
            log.info("Chunk {} processed successfully", chunkIndex);
            context.handleSuccess(chunkIndex, cleanResponse(response));
        }, e -> {
            log.error("Chunk {} failed", chunkIndex, e);
            context.handleFailure(chunkIndex, e);
        }));
    }

    private String cleanResponse(String response) {
        // Handle both structured (with response_format) and unstructured responses
        return RatingOutputProcessor.sanitizeLLMResponse(response);
    }

    public void predictSingleChunk(String modelId, MLInput mlInput, ActionListener<String> listener) {
        log.debug("DEBUG: predictSingleChunk called with modelId: {}", modelId);
        RemoteInferenceInputDataSet dataset = (RemoteInferenceInputDataSet) mlInput.getInputDataset();
        Map<String, String> params = dataset.getParameters();
        log.debug(
            "DEBUG: MLInput parameters - has response_format: {}, has messages: {}",
            params.containsKey("response_format"),
            params.containsKey("messages")
        );
        mlClient.predict(modelId, mlInput, ActionListener.wrap(mlOutput -> {
            log.debug("DEBUG: ML prediction succeeded, extracting response content");
            listener.onResponse(transformer.extractResponseContent(mlOutput));
        }, e -> {
            log.debug("DEBUG: ML prediction failed with error: {}", e.getMessage());
            listener.onFailure(e);
        }));
    }

}
