/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */
package org.opensearch.searchrelevance.ml;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.opensearch.core.action.ActionListener;

/**
 * Context for tracking chunk processing state during ML prediction
 */
public class ChunkProcessingContext {
    private static final Logger LOGGER = LogManager.getLogger(ChunkProcessingContext.class);

    private final ConcurrentMap<Integer, String> succeededChunks = new ConcurrentHashMap<>();
    private final ConcurrentMap<Integer, String> failedChunks = new ConcurrentHashMap<>();
    private final AtomicInteger processedChunks = new AtomicInteger(0);
    private final int totalChunks;
    private final ActionListener<ChunkResult> progressListener;

    public ChunkProcessingContext(int totalChunks, ActionListener<ChunkResult> progressListener) {
        this.totalChunks = totalChunks;
        this.progressListener = progressListener;
    }

    public void handleSuccess(int chunkIndex, String response) {
        succeededChunks.put(chunkIndex, response);
        completeChunk(chunkIndex);
    }

    public void handleFailure(int chunkIndex, Exception error) {
        failedChunks.put(chunkIndex, error.getMessage());
        completeChunk(chunkIndex);
    }

    private void completeChunk(int chunkIndex) {
        try {
            int processed = processedChunks.incrementAndGet();
            boolean isLastChunk = processed == totalChunks;

            ChunkResult result = new ChunkResult(chunkIndex, totalChunks, isLastChunk, succeededChunks, failedChunks);

            progressListener.onResponse(result);

            // Always report results, let caller decide how to handle failures
        } catch (Exception e) {
            LOGGER.error("Error handling chunk completion for chunk {}", chunkIndex, e);
            progressListener.onFailure(e);
        }
    }
}
