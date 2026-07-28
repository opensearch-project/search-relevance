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
import java.util.List;

import org.opensearch.action.ActionRequest;
import org.opensearch.action.ActionRequestValidationException;
import org.opensearch.core.common.io.stream.StreamInput;
import org.opensearch.core.common.io.stream.StreamOutput;

/**
 * Transport Request for adjusting one or more ratings on an existing judgment in place.
 *
 * <p>Instead of resending the whole judgmentRatings set, the client submits just the ratings it
 * wants to change: the target judgment id plus a list of {@link RatingAdjustment}s, each carrying a
 * query, a docId, and the new rating value. For every adjustment the server locates that
 * (query, docId) entry, updates its score (moving it out of the failures list if needed, or
 * overwriting an already-rated doc), then recomputes the summary counts once. No model call is made.
 * All adjustments are applied together under one optimistic-concurrency write, so either all land or
 * none do.
 */
public class UpdateJudgmentRatingsRequest extends ActionRequest {
    private final String judgmentId;
    private final List<RatingAdjustment> adjustments;

    /**
     * @param judgmentId - id of the judgment to update
     * @param adjustments - the rating adjustments to apply, each a (query, docId, rating) triple
     */
    public UpdateJudgmentRatingsRequest(String judgmentId, List<RatingAdjustment> adjustments) {
        this.judgmentId = judgmentId;
        this.adjustments = adjustments;
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
        this.adjustments = in.readList(RatingAdjustment::new);
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
        out.writeList(adjustments);
    }

    /** @return id of the judgment to update */
    public String getJudgmentId() {
        return judgmentId;
    }

    /** @return the rating adjustments to apply */
    public List<RatingAdjustment> getAdjustments() {
        return adjustments;
    }

    /**
     * Reject the request if the id is missing, the adjustments list is empty, or any adjustment is
     * missing a field.
     *
     * @return a validation exception if the request is invalid, otherwise null
     */
    @Override
    public ActionRequestValidationException validate() {
        ActionRequestValidationException validationException = null;
        if (judgmentId == null || judgmentId.trim().isEmpty()) {
            validationException = addValidationError("judgmentId must not be null or empty", validationException);
        }
        if (adjustments == null || adjustments.isEmpty()) {
            validationException = addValidationError("at least one rating adjustment is required", validationException);
        } else {
            for (RatingAdjustment adjustment : adjustments) {
                if (adjustment.isIncomplete()) {
                    validationException = addValidationError(
                        "each adjustment must have a non-empty query, docId and rating",
                        validationException
                    );
                    break;
                }
            }
        }
        return validationException;
    }
}
