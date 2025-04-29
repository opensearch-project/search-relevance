/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */
package org.opensearch.searchrelevance.metrics.judgments;

import java.util.Map;
import java.util.Set;

import org.opensearch.core.action.ActionListener;

public interface JudgmentsProcessor {

    /**
     * function to process docId to judgment_score map for the given queryText.
     * @param metadata - metadata for processing, e.g: llm -> modelId; ubi -> judgmentIds
     * @param unionHits - a union of Hits returned from all search configs for the given queryText.
     * @param queryText - the given queryText.
     * @param listener - async action.
     * @return a docId to judgment_score mapping.
     */
    Map<String, Double> processJudgments(
        Map<String, Object> metadata,
        Set<Map<String, String>> unionHits,
        String queryText,
        ActionListener<Map<String, Object>> listener
    );

}
