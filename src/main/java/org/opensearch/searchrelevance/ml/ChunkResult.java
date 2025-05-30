/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */
package org.opensearch.searchrelevance.ml;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

// Helper class to carry chunk result information
public class ChunkResult {
    private final int chunkIndex;
    private final int totalChunks;
    private final boolean isLastChunk;

    private final Map<Integer, String> succeededChunks;
    private final Map<Integer, String> failedChunks;

    // constructor for individual chunk result
    public ChunkResult(int chunkIndex, String response, String error, int totalChunks, boolean isLastChunk) {
        this.chunkIndex = chunkIndex;
        this.totalChunks = totalChunks;
        this.isLastChunk = isLastChunk;
        this.succeededChunks = new HashMap<>();
        this.failedChunks = new HashMap<>();
        if (error != null) {
            this.failedChunks.put(chunkIndex, error);
        } else {
            this.succeededChunks.put(chunkIndex, response);
        }
    }

    // Constructor for aggregated results
    public ChunkResult(
        int chunkIndex,
        int totalChunks,
        boolean isLastChunk,
        Map<Integer, String> succeededChunks,
        Map<Integer, String> failedChunks
    ) {
        this.chunkIndex = chunkIndex;
        this.totalChunks = totalChunks;
        this.isLastChunk = isLastChunk;
        this.succeededChunks = new HashMap<>(succeededChunks);
        this.failedChunks = new HashMap<>(failedChunks);
    }

    public int getSuccessfulChunksCount() {
        return succeededChunks.size();
    }

    public int getFailedChunksCount() {
        return failedChunks.size();
    }

    public Map<Integer, String> getSucceededChunks() {
        return Collections.unmodifiableMap(succeededChunks);
    }

    public Map<Integer, String> getFailedChunks() {
        return Collections.unmodifiableMap(failedChunks);
    }

    public boolean isLastChunk() {
        return isLastChunk;
    }

    public int getTotalChunks() {
        return totalChunks;
    }

}
