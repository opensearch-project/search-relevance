/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */
package org.opensearch.searchrelevance.common;

/**
 * Evaluation metrics related constants.
 */
public class MetricsConstants {
    private MetricsConstants() {}

    public static final String METRICS_FIELD_NAME = "metrics";
    public static final String METRICS_QUERY_TEXT_FIELD_NAME = "queryTexts";
    public static final String METRICS_INDEX_AND_QUERIES_FIELD_NAME = "indexAndQueries";

    /**
     * pariwise comparison field names
     */
    public static final String METRICS_PAIRWISE_COMPARISON_FIELD_NAME = "pairwiseComparison";
    public static final String PAIRWISE_FIELD_NAME_A = "0";
    public static final String PAIRWISE_FIELD_NAME_B = "1";

    /**
     * metadata map fields
     */
    public static final String JUDGMENT_IDS = "judgmentIds";
    public static final String MODEL_ID = "modelId";
}
