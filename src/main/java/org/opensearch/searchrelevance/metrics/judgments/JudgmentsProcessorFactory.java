/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */
package org.opensearch.searchrelevance.metrics.judgments;

import org.opensearch.searchrelevance.ml.MLAccessor;
import org.opensearch.searchrelevance.model.ExperimentType;

public class JudgmentsProcessorFactory {
    private final MLAccessor mlAccessor;

    public JudgmentsProcessorFactory(MLAccessor mlAccessor) {
        this.mlAccessor = mlAccessor;
    }

    public JudgmentsProcessor getProcessor(ExperimentType type) {
        return switch (type) {
            case LLM_EVALUATION -> new LlmJudgmentsProcessor(mlAccessor);
            case UBI_EVALUATION -> new UbiJudgmentsProcessor();
            default -> throw new IllegalArgumentException("Unsupported experiment type: " + type);
        };
    }
}
