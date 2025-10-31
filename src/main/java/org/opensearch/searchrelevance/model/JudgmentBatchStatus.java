/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */
package org.opensearch.searchrelevance.model;

/**
 * Enum representing the aggregate status of a batch of judgment generation tasks
 * within a query processing during LLM judgment execution.
 */
public enum JudgmentBatchStatus {
    /**
     * All judgment generation tasks in the batch have completed successfully
     */
    SUCCESS,

    /**
     * Some judgment tasks succeeded while others failed
     */
    PARTIAL_SUCCESS,

    /**
     * All judgment generation tasks in the batch have failed
     */
    ALL_FAILED,

    /**
     * Processing was terminated early due to critical failure (ignoreFailure=false)
     */
    TERMINATED
}
