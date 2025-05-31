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
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

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
    private static final int MAX_RETRY_NUMBER = 3;
    private static final long RETRY_DELAY_MS = 1000;

    public MLAccessor(MachineLearningNodeClient mlClient) {
        this.mlClient = mlClient;
    }

    public void predict(
        String modelId,
        int tokenLimit,
        String searchText,
        String reference,
        Map<String, String> hits,
        boolean ignoreFailure,
        ActionListener<ChunkResult> progressListener  // For individual chunk
    ) {
        List<MLInput> mlInputs = getMLInputs(tokenLimit, searchText, reference, hits);
        LOGGER.info("Number of chunks: {}", mlInputs.size());

        ConcurrentMap<Integer, String> succeededChunks = new ConcurrentHashMap<>();
        ConcurrentHashMap<Integer, String> failedChunks = new ConcurrentHashMap<>();
        AtomicInteger processedChunks = new AtomicInteger(0);

        for (int i = 0; i < mlInputs.size(); i++) {
            final int chunkIndex = i;
            predictSingleChunkWithRetry(modelId, mlInputs.get(chunkIndex), chunkIndex, 0, new ActionListener<String>() {
                @Override
                public void onResponse(String response) {
                    LOGGER.info("Chunk {} processed successfully", chunkIndex);
                    String processedResponse = response.substring(1, response.length() - 1); // remove brackets
                    handleChunkCompletion(
                        chunkIndex,
                        processedResponse,
                        null,
                        mlInputs.size(),
                        succeededChunks,
                        failedChunks,
                        ignoreFailure,
                        processedChunks,
                        progressListener
                    );
                }

                @Override
                public void onFailure(Exception e) {
                    LOGGER.error("Chunk {} failed after all retries", chunkIndex, e);
                    handleChunkCompletion(
                        chunkIndex,
                        null,
                        e,
                        mlInputs.size(),
                        succeededChunks,
                        failedChunks,
                        ignoreFailure,
                        processedChunks,
                        progressListener
                    );
                }
            });
        }
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
                    LOGGER.warn("Chunk {} failed, attempt {}/{}. Retrying...", chunkIndex, retryCount + 1, MAX_RETRY_NUMBER);

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
            ActionListener.wrap(mlOutput -> listener.onResponse(extractResponseContent(mlOutput)), listener::onFailure)
        );
    }

    private List<MLInput> getMLInputs(int tokenLimit, String searchText, String reference, Map<String, String> hits) {
        List<MLInput> mlInputs = new ArrayList<>();
        Map<String, String> currentChunk = new HashMap<>();

        for (Map.Entry<String, String> entry : hits.entrySet()) {
            Map<String, String> tempChunk = new HashMap<>(currentChunk);
            tempChunk.put(entry.getKey(), entry.getValue());

            String messages = formatMessages(searchText, reference, tempChunk);
            int totalTokens = TokenizerUtil.countTokens(messages);

            if (totalTokens > tokenLimit) {
                if (currentChunk.isEmpty()) {
                    // Single entry exceeds token limit
                    LOGGER.warn("Entry with key {} causes total tokens to exceed limit of {}", entry.getKey(), tokenLimit);
                    Map<String, String> singleEntryChunk = new HashMap<>();

                    // Calculate tokens for the message with just this entry
                    Map<String, String> testChunk = new HashMap<>();
                    testChunk.put(entry.getKey(), entry.getValue());
                    String testMessages = formatMessages(searchText, reference, testChunk);
                    int excessTokens = TokenizerUtil.countTokens(testMessages) - tokenLimit;

                    // Truncate the entry value
                    int currentTokens = TokenizerUtil.countTokens(entry.getValue());
                    String truncatedValue = TokenizerUtil.truncateString(entry.getValue(), Math.max(1, currentTokens - excessTokens));
                    singleEntryChunk.put(entry.getKey(), truncatedValue);
                    mlInputs.add(createMLInput(searchText, reference, singleEntryChunk));
                } else {
                    // Current chunk is full, add it and start new chunk
                    mlInputs.add(createMLInput(searchText, reference, currentChunk));
                    currentChunk = new HashMap<>();
                    currentChunk.put(entry.getKey(), entry.getValue());
                }
            } else {
                // Can add entry to current chunk
                currentChunk.put(entry.getKey(), entry.getValue());
            }
        }

        if (!currentChunk.isEmpty()) {
            mlInputs.add(createMLInput(searchText, reference, currentChunk));
        }

        return mlInputs;
    }

    private String formatMessages(String searchText, String reference, Map<String, String> hits) {
        try {
            String hitsJson;
            try (XContentBuilder builder = XContentFactory.jsonBuilder()) {
                builder.startArray();
                for (Map.Entry<String, String> hit : hits.entrySet()) {
                    builder.startObject();
                    builder.field("id", hit.getKey());
                    builder.field("source", hit.getValue());
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
            return String.format(Locale.ROOT, PROMPT_JSON_MESSAGES_SHELL, PROMPT_SEARCH_RELEVANCE, escapeJson(userContent));
        } catch (IOException e) {
            LOGGER.error("Error converting hits to JSON string", e);
            throw new IllegalArgumentException("Failed to process hits", e);
        }
    }

    private MLInput createMLInput(String searchText, String reference, Map<String, String> hits) {
        Map<String, String> parameters = new HashMap<>();
        parameters.put(PARAM_MESSAGES_FIELD, formatMessages(searchText, reference, hits));
        return MLInput.builder().algorithm(FunctionName.REMOTE).inputDataset(new RemoteInferenceInputDataSet(parameters)).build();
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

    private void handleChunkCompletion(
        int chunkIndex,
        String response,
        Exception error,
        int totalChunks,
        ConcurrentMap<Integer, String> succeededChunks,
        ConcurrentMap<Integer, String> failedChunks,
        boolean ignoreFailure,
        AtomicInteger processedChunks,
        ActionListener<ChunkResult> progressListener
    ) {
        try {
            if (error != null) {
                String errorMessage = error.getMessage();
                failedChunks.put(chunkIndex, errorMessage);
                if (!ignoreFailure) {
                    progressListener.onFailure(error);
                    return;
                }
            } else {
                succeededChunks.put(chunkIndex, response);
            }

            int processed = processedChunks.incrementAndGet();
            boolean isLastChunk = processed == totalChunks;

            ChunkResult result = new ChunkResult(
                chunkIndex,
                totalChunks,
                isLastChunk,
                new HashMap<>(succeededChunks),
                new HashMap<>(failedChunks)
            );

            progressListener.onResponse(result);

            if (isLastChunk) {
                handleFinalStatus(result, ignoreFailure, progressListener);
            }
        } catch (Exception e) {
            LOGGER.error("Error handling chunk completion for chunk {}", chunkIndex, e);
            if (!ignoreFailure) {
                progressListener.onFailure(e);
            }
        }
    }

    private void handleFinalStatus(ChunkResult finalResult, boolean ignoreFailure, ActionListener<ChunkResult> progressListener) {
        if (finalResult.getFailedChunksCount() > 0 && !ignoreFailure) {
            String errorMessage = String.format(
                Locale.ROOT,
                "Failed to process %d out of %d chunks",
                finalResult.getFailedChunksCount(),
                finalResult.getTotalChunks()
            );
            progressListener.onFailure(new RuntimeException(errorMessage));
        } else {
            progressListener.onResponse(finalResult);
        }
    }

}
