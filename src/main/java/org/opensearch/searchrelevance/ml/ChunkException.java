/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */
package org.opensearch.searchrelevance.ml;

// Custom exception for chunk failures
public class ChunkException extends RuntimeException {

    private final int chunkIndex;
    private final int totalChunks;

    public ChunkException(int chunkIndex, Throwable cause, int totalChunks) {
        super("Failed to process chunk " + chunkIndex + " of " + totalChunks, cause);
        this.chunkIndex = chunkIndex;
        this.totalChunks = totalChunks;
    }

    public int getChunkIndex() {
        return chunkIndex;
    }

    public int getTotalChunks() {
        return totalChunks;
    }
}
