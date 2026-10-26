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
    LLM_QUERY_SET("llm_query_set"),
    MANUAL_QUERY_SET("manual_query_set"),
    UBI_QUERY_SET("ubi_query_set");

    private final String value;

    QuerySetType(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }

    public static QuerySetType fromString(String type) {
        for (QuerySetType t : values()) {
            if (t.value.equalsIgnoreCase(type) || t.name().equalsIgnoreCase(type)) {
                return t;
            }
        }
        return null;
    }
}
