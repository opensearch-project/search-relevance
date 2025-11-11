/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */
package org.opensearch.searchrelevance.ml;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

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

    private static final int MAX_RETRY_NUMBER = 3;
    private static final long RETRY_DELAY_MS = 1000;

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
        processChunkWithFallback(modelId, mlInput, chunkIndex, false, context);
    }

    private void processChunkWithFallback(
        String modelId,
        MLInput mlInput,
        int chunkIndex,
        boolean triedWithoutResponseFormat,
        ChunkProcessingContext context
    ) {
        predictSingleChunkWithRetry(modelId, mlInput, chunkIndex, 0, triedWithoutResponseFormat, ActionListener.wrap(response -> {
            log.info("Chunk {} processed successfully", chunkIndex);
            String processedResponse = cleanResponse(response);

            // Check if parsing failed (empty ratings array) and we haven't tried without response_format yet
            if ("[]".equals(processedResponse) && !triedWithoutResponseFormat) {
                log.warn(
                    "Chunk {} returned empty ratings with response_format. Retrying without response_format for GPT-3.5 compatibility...",
                    chunkIndex
                );
                // Create new MLInput without response_format and retry
                MLInput mlInputWithoutFormat = recreateMLInputWithoutResponseFormat(mlInput);
                scheduleRetry(() -> processChunkWithFallback(modelId, mlInputWithoutFormat, chunkIndex, true, context), RETRY_DELAY_MS);
            } else {
                context.handleSuccess(chunkIndex, processedResponse);
            }
        }, e -> {
            log.error("Chunk {} failed after all retries", chunkIndex, e);
            context.handleFailure(chunkIndex, e);
        }));
    }

    private String cleanResponse(String response) {
        // Use sanitizeLLMResponse to handle both structured (with response_format) and unstructured responses
        // For GPT-4o with response_format: extracts {"ratings": [...]}
        // For GPT-3.5 without response_format: parses and sanitizes unstructured JSON
        return RatingOutputProcessor.sanitizeLLMResponse(response);
    }

    /**
     * Retries prediction with automatic fallback to non-structured output.
     * First tries with response_format, then falls back to without response_format if it fails.
     *
     * @param triedWithoutResponseFormat Tracks if we've already tried without response_format
     */
    private void predictSingleChunkWithRetry(
        String modelId,
        MLInput mlInput,
        int chunkIndex,
        int retryCount,
        boolean triedWithoutResponseFormat,
        ActionListener<String> chunkListener
    ) {
        predictSingleChunk(modelId, mlInput, new ActionListener<String>() {
            @Override
            public void onResponse(String response) {
                log.debug(
                    "DEBUG: Chunk {} received response (length: {}). First 200 chars: {}",
                    chunkIndex,
                    response.length(),
                    response.substring(0, Math.min(200, response.length()))
                );
                chunkListener.onResponse(response);
            }

            @Override
            public void onFailure(Exception e) {
                log.debug(
                    "DEBUG: Chunk {} failed with error: {}. triedWithoutResponseFormat: {}, retryCount: {}",
                    chunkIndex,
                    e.getMessage(),
                    triedWithoutResponseFormat,
                    retryCount
                );
                // If we haven't tried without response_format yet, try that first before regular retries
                if (!triedWithoutResponseFormat) {
                    log.warn(
                        "Chunk {} failed with response_format. Retrying without response_format for GPT-3.5 compatibility...",
                        chunkIndex
                    );
                    log.debug("DEBUG: Creating MLInput without response_format for chunk {}", chunkIndex);

                    // Create new MLInput without response_format
                    MLInput mlInputWithoutFormat = recreateMLInputWithoutResponseFormat(mlInput);

                    long delay = RETRY_DELAY_MS;
                    scheduleRetry(
                        () -> predictSingleChunkWithRetry(modelId, mlInputWithoutFormat, chunkIndex, 0, true, chunkListener),
                        delay
                    );
                } else if (retryCount < MAX_RETRY_NUMBER) {
                    log.warn("Chunk {} failed, attempt {}/{}. Retrying...", chunkIndex, retryCount + 1, MAX_RETRY_NUMBER);

                    long delay = RETRY_DELAY_MS * (long) Math.pow(2, retryCount);
                    scheduleRetry(
                        () -> predictSingleChunkWithRetry(modelId, mlInput, chunkIndex, retryCount + 1, true, chunkListener),
                        delay
                    );
                } else {
                    chunkListener.onFailure(e);
                }
            }
        });
    }

    /**
     * Recreates MLInput without response_format parameter for models that don't support it (e.g., GPT-3.5).
     */
    private MLInput recreateMLInputWithoutResponseFormat(MLInput originalInput) {
        // Extract the parameters from the original input and rebuild without response_format
        RemoteInferenceInputDataSet originalDataSet = (RemoteInferenceInputDataSet) originalInput.getInputDataset();
        Map<String, String> originalParams = originalDataSet.getParameters();

        // Create new parameters map without response_format
        Map<String, String> newParams = new HashMap<>();
        for (Map.Entry<String, String> entry : originalParams.entrySet()) {
            if (!"response_format".equals(entry.getKey())) {
                newParams.put(entry.getKey(), entry.getValue());
            }
        }

        return MLInput.builder().algorithm(originalInput.getAlgorithm()).inputDataset(new RemoteInferenceInputDataSet(newParams)).build();
    }

    private void scheduleRetry(Runnable runnable, long delayMs) {
        CompletableFuture.delayedExecutor(delayMs, TimeUnit.MILLISECONDS).execute(runnable);
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
