/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */
package org.opensearch.searchrelevance.model;

/**
 * Enum representing the origin type of a query set.
 */
public enum QuerySetType {
    LLM_QUERY_SET,
    MANUAL_QUERY_SET,
    UBI_QUERY_SET
}
