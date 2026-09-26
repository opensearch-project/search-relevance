/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */
package org.opensearch.searchrelevance.utils;

import org.opensearch.action.search.SearchResponse;

/**
 * Helpers for OpenSearch cluster query latency ({@code SearchResponse.getTook()}).
 */
public final class SearchTookMs {
    private SearchTookMs() {}

    /**
     * Cluster query time in milliseconds from {@link SearchResponse#getTook()}.
     * This is OpenSearch {@code took} only — not plugin queue time, judgment scoring,
     * or Dashboards round-trip.
     *
     * @return millis, or {@code null} when the response or its took value is missing
     */
    public static Long from(SearchResponse response) {
        if (response == null || response.getTook() == null) {
            return null;
        }
        return response.getTook().millis();
    }
}
