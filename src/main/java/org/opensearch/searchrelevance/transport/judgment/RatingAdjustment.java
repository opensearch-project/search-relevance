/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */
package org.opensearch.searchrelevance.transport.judgment;

import java.io.IOException;

import org.opensearch.core.common.io.stream.StreamInput;
import org.opensearch.core.common.io.stream.StreamOutput;
import org.opensearch.core.common.io.stream.Writeable;

/**
 * A single rating adjustment applied to an existing judgment: the query, the document id, and the
 * new rating value for that (query, docId) pair.
 *
 * <p>One update request carries a list of these so several ratings can be corrected in one call
 * (for example, fixing multiple failed docs after a partial LLM run).
 */
public class RatingAdjustment implements Writeable {
    private final String query;
    private final String docId;
    private final String rating;

    /**
     * @param query - the query text whose rating is being adjusted
     * @param docId - the document id whose rating is being adjusted
     * @param rating - the new rating value for the (query, docId) pair
     */
    public RatingAdjustment(String query, String docId, String rating) {
        this.query = query;
        this.docId = docId;
        this.rating = rating;
    }

    /**
     * Deserialize a single adjustment from a transport stream (node-to-node).
     *
     * @param in - stream to read from
     * @throws IOException if the stream cannot be read
     */
    public RatingAdjustment(StreamInput in) throws IOException {
        this.query = in.readString();
        this.docId = in.readString();
        this.rating = in.readString();
    }

    /**
     * Serialize a single adjustment to a transport stream (node-to-node).
     *
     * @param out - stream to write to
     * @throws IOException if the stream cannot be written
     */
    @Override
    public void writeTo(StreamOutput out) throws IOException {
        out.writeString(query);
        out.writeString(docId);
        out.writeString(rating);
    }

    /** @return the query text whose rating is being adjusted */
    public String getQuery() {
        return query;
    }

    /** @return the document id whose rating is being adjusted */
    public String getDocId() {
        return docId;
    }

    /** @return the new rating value */
    public String getRating() {
        return rating;
    }

    /** @return true if any of the three fields is null or blank */
    public boolean isIncomplete() {
        return query == null
            || query.trim().isEmpty()
            || docId == null
            || docId.trim().isEmpty()
            || rating == null
            || rating.trim().isEmpty();
    }
}
