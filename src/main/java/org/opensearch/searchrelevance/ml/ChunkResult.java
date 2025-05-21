/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */
package org.opensearch.searchrelevance.ml;

// Helper class to carry chunk result information
public class ChunkResult {
    private final int chunkIndex;
    private final String response;
    private final int totalChunks;
    private final boolean isLastChunk;

    public ChunkResult(int chunkIndex, String response, int totalChunks, boolean isLastChunk) {
        this.chunkIndex = chunkIndex;
        this.response = response;
        this.totalChunks = totalChunks;
        this.isLastChunk = isLastChunk;
    }

    public int getChunkIndex() {
        return chunkIndex;
    }

    public String getResponse() {
        return response;
    }

    public int getTotalChunks() {
        return totalChunks;
    }

    public boolean isLastChunk() {
        return isLastChunk;
    }

}
