/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */
package org.opensearch.searchrelevance.transport.judgment;

import static org.opensearch.action.ValidateActions.addValidationError;

import java.io.IOException;

import org.opensearch.action.ActionRequest;
import org.opensearch.action.ActionRequestValidationException;
import org.opensearch.core.common.io.stream.StreamInput;
import org.opensearch.core.common.io.stream.StreamOutput;

/**
 * Transport Request for adjusting a single rating on an existing judgment in place.
 *
 * <p>Instead of resending the whole judgmentRatings set, the client submits just the one rating it
 * wants to change: the target judgment id plus the query, the docId, and the new rating value. The
 * server locates that (query, docId) entry, updates its score (moving it out of the failures list
 * if needed), and recomputes the summary counts. No model call is made.
 */
public class UpdateJudgmentRatingsRequest extends ActionRequest {
    private final String judgmentId;
    private final String query;
    private final String docId;
    private final String rating;

    /**
     * @param judgmentId - id of the judgment to update
     * @param query - the query text whose rating is being adjusted
     * @param docId - the document id whose rating is being adjusted
     * @param rating - the new rating value for the (query, docId) pair
     */
    public UpdateJudgmentRatingsRequest(String judgmentId, String query, String docId, String rating) {
        this.judgmentId = judgmentId;
        this.query = query;
        this.docId = docId;
        this.rating = rating;
    }

    /**
     * Deserialize the request from a transport stream (node-to-node).
     *
     * @param in - stream to read from
     * @throws IOException if the stream cannot be read
     */
    public UpdateJudgmentRatingsRequest(StreamInput in) throws IOException {
        super(in);
        this.judgmentId = in.readString();
        this.query = in.readString();
        this.docId = in.readString();
        this.rating = in.readString();
    }

    /**
     * Serialize the request to a transport stream (node-to-node).
     *
     * @param out - stream to write to
     * @throws IOException if the stream cannot be written
     */
    @Override
    public void writeTo(StreamOutput out) throws IOException {
        super.writeTo(out);
        out.writeString(judgmentId);
        out.writeString(query);
        out.writeString(docId);
        out.writeString(rating);
    }

    /** @return id of the judgment to update */
    public String getJudgmentId() {
        return judgmentId;
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

    /**
     * Reject the request if any of the required fields are null or empty.
     *
     * @return a validation exception if the request is invalid, otherwise null
     */
    @Override
    public ActionRequestValidationException validate() {
        ActionRequestValidationException validationException = null;
        if (judgmentId == null || judgmentId.trim().isEmpty()) {
            validationException = addValidationError("judgmentId must not be null or empty", validationException);
        }
        if (query == null || query.trim().isEmpty()) {
            validationException = addValidationError("query must not be null or empty", validationException);
        }
        if (docId == null || docId.trim().isEmpty()) {
            validationException = addValidationError("docId must not be null or empty", validationException);
        }
        if (rating == null || rating.trim().isEmpty()) {
            validationException = addValidationError("rating must not be null or empty", validationException);
        }
        return validationException;
    }
}
