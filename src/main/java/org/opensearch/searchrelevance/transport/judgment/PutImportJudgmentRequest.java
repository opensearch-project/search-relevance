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
import java.util.Map;

import org.opensearch.action.ActionRequestValidationException;
import org.opensearch.core.common.io.stream.StreamInput;
import org.opensearch.core.common.io.stream.StreamOutput;
import org.opensearch.searchrelevance.exception.SearchRelevanceException;
import org.opensearch.searchrelevance.model.JudgmentType;
import org.opensearch.searchrelevance.utils.ImportJudgmentRatingValidator;

import reactor.util.annotation.NonNull;

public class PutImportJudgmentRequest extends PutJudgmentRequest {
    private List<Map<String, Object>> judgmentScores;

    public PutImportJudgmentRequest(
        @NonNull JudgmentType type,
        @NonNull String name,
        @NonNull String description,
        @NonNull List<Map<String, Object>> judgmentScores
    ) {
        super(type, name, description);
        this.judgmentScores = judgmentScores;
    }

    public PutImportJudgmentRequest(StreamInput in) throws IOException {
        super(in);
        this.judgmentScores = in.readList(StreamInput::readMap);
    }

    @Override
    public ActionRequestValidationException validate() {
        if (judgmentScores == null) {
            return addValidationError("judgmentRatings must be a list", null);
        }
        for (Map<String, Object> queryJudgment : judgmentScores) {
            if (queryJudgment == null || !(queryJudgment.get("ratings") instanceof List<?> ratings)) {
                return addValidationError("each judgment must have a list of ratings", null);
            }
            for (Object entry : ratings) {
                if (!(entry instanceof Map<?, ?> rating)) {
                    return addValidationError("each rating must be an object", null);
                }
                try {
                    ImportJudgmentRatingValidator.validate(rating.get("rating"), String.valueOf(queryJudgment.get("query")));
                } catch (SearchRelevanceException e) {
                    return addValidationError(e.getMessage(), null);
                }
            }
        }
        return null;
    }

    @Override
    public void writeTo(StreamOutput out) throws IOException {
        super.writeTo(out);
        out.writeCollection(judgmentScores, StreamOutput::writeMap);
    }

    public List<Map<String, Object>> getJudgmentRatings() {
        return judgmentScores;
    }

}
