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
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import org.opensearch.core.action.ActionListener;
import org.opensearch.ml.client.MachineLearningNodeClient;
import org.opensearch.ml.common.input.MLInput;

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
        String reference,
        Map<String, String> hits,
        ActionListener<ChunkResult> progressListener
    ) {
        List<MLInput> mlInputs = transformer.createMLInputs(tokenLimit, searchText, reference, hits);
        log.info("Number of chunks: {}", mlInputs.size());

        ChunkProcessingContext context = new ChunkProcessingContext(mlInputs.size(), progressListener);

        for (int i = 0; i < mlInputs.size(); i++) {
            processChunk(modelId, mlInputs.get(i), i, context);
        }
    }

    private void processChunk(String modelId, MLInput mlInput, int chunkIndex, ChunkProcessingContext context) {
        predictSingleChunkWithRetry(modelId, mlInput, chunkIndex, 0, ActionListener.wrap(response -> {
            log.info("Chunk {} processed successfully", chunkIndex);
            String processedResponse = cleanResponse(response);
            context.handleSuccess(chunkIndex, processedResponse);
        }, e -> {
            log.error("Chunk {} failed after all retries", chunkIndex, e);
            context.handleFailure(chunkIndex, e);
        }));
    }

    private String cleanResponse(String response) {
        return response.substring(1, response.length() - 1); // remove brackets
    }

    private void predictSingleChunkWithRetry(
        String modelId,
        MLInput mlInput,
        int chunkIndex,
        int retryCount,
        ActionListener<String> chunkListener
    ) {
        predictSingleChunk(modelId, mlInput, new ActionListener<String>() {
            @Override
            public void onResponse(String response) {
                chunkListener.onResponse(response);
            }

            @Override
            public void onFailure(Exception e) {
                if (retryCount < MAX_RETRY_NUMBER) {
                    log.warn("Chunk {} failed, attempt {}/{}. Retrying...", chunkIndex, retryCount + 1, MAX_RETRY_NUMBER);

                    long delay = RETRY_DELAY_MS * (long) Math.pow(2, retryCount);
                    scheduleRetry(() -> predictSingleChunkWithRetry(modelId, mlInput, chunkIndex, retryCount + 1, chunkListener), delay);
                } else {
                    chunkListener.onFailure(e);
                }
            }
        });
    }

    private void scheduleRetry(Runnable runnable, long delayMs) {
        CompletableFuture.delayedExecutor(delayMs, TimeUnit.MILLISECONDS).execute(runnable);
    }

    public void predictSingleChunk(String modelId, MLInput mlInput, ActionListener<String> listener) {
        mlClient.predict(
            modelId,
            mlInput,
            ActionListener.wrap(mlOutput -> listener.onResponse(transformer.extractResponseContent(mlOutput)), listener::onFailure)
        );
    }

}
