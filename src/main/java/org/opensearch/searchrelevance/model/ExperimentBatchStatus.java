/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */
package org.opensearch.searchrelevance.model;

/**
 * Enum representing the aggregate status of a batch of experiment variants
 * within a search configuration during experiment execution.
 */
public enum ExperimentBatchStatus {
    /**
     * All experiment variants in the batch have completed successfully
     */
    SUCCESS,

    /**
     * Some experiment variants succeeded while others failed
     */
    PARTIAL_SUCCESS,

    /**
     * All experiment variants in the batch have failed
     */
    ALL_FAILED
}
