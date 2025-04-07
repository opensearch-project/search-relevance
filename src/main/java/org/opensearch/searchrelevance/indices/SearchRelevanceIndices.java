/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */
package org.opensearch.searchrelevance.indices;

import static org.opensearch.searchrelevance.common.PluginConstants.QUERY_SET_INDEX;
import static org.opensearch.searchrelevance.common.PluginConstants.QUERY_SET_INDEX_MAPPING;
import static org.opensearch.searchrelevance.indices.SearchRelevanceIndicesManager.getIndexMappings;

import java.io.IOException;
import java.util.Objects;

public enum SearchRelevanceIndices {
    /**
     * Query Set Index
     */
    QUERY_SET(QUERY_SET_INDEX, QUERY_SET_INDEX_MAPPING);

    private final String indexName;
    private final String mapping;

    SearchRelevanceIndices(String indexName, String mappingPath) {
        this.indexName = Objects.requireNonNull(indexName, "Index name cannot be null.");
        this.mapping = loadMapping(mappingPath);
    }

    private String loadMapping(String mappingPath) {
        try {
            return getIndexMappings(mappingPath);
        } catch (IOException e) {
            throw new IllegalArgumentException("Failed to load mapping under path: " + mappingPath, e);
        }
    }

    public String getIndexName() {
        return indexName;
    }

    public String getMapping() {
        return mapping;
    }

}
