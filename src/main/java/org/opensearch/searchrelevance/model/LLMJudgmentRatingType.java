/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */
package org.opensearch.searchrelevance.model;

import java.io.IOException;
import java.util.Arrays;
import java.util.stream.Collectors;

import org.opensearch.core.common.io.stream.StreamInput;
import org.opensearch.core.common.io.stream.StreamOutput;
import org.opensearch.core.common.io.stream.Writeable;

public enum LLMJudgmentRatingType implements Writeable {
    SCORE0_1,
    RELEVANT_IRRELEVANT;

    @Override
    public void writeTo(StreamOutput out) throws IOException {
        out.writeEnum(this);
    }

    public static LLMJudgmentRatingType readFromStream(StreamInput in) throws IOException {
        return in.readEnum(LLMJudgmentRatingType.class);
    }

    /**
     * Get a comma-separated string of all valid rating type values.
     * @return String containing all valid enum values
     */
    public static String getValidValues() {
        return Arrays.stream(LLMJudgmentRatingType.values()).map(Enum::name).collect(Collectors.joining(", "));
    }
}
