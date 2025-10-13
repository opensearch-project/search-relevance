/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */
package org.opensearch.searchrelevance.model;

import java.io.IOException;

import org.opensearch.core.common.io.stream.StreamInput;
import org.opensearch.core.common.io.stream.StreamOutput;
import org.opensearch.core.common.io.stream.Writeable;

public enum LLMJudgmentRatingType implements Writeable {
    SCORE0_1,
    SCORE1_5,
    RELEVANT_IRRELEVANT;

    @Override
    public void writeTo(StreamOutput out) throws IOException {
        out.writeEnum(this);
    }

    public static LLMJudgmentRatingType readFromStream(StreamInput in) throws IOException {
        return in.readEnum(LLMJudgmentRatingType.class);
    }
}
