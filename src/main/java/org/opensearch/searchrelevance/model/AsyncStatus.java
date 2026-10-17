/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */
package org.opensearch.searchrelevance.model;

public enum AsyncStatus {
    PROCESSING,
    COMPLETED,
    TIMEOUT,
    ERROR,
    // A retry of a previously completed judgment is running. Distinct from PROCESSING (initial
    // generation) so that a died initial generation is never retried with an incomplete doc list.
    RETRYING
}
