/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */
package org.opensearch.searchrelevance.indices;

import static org.opensearch.searchrelevance.common.PluginConstants.EVALUATION_RESULT_INDEX;
import static org.opensearch.searchrelevance.common.PluginConstants.EVALUATION_RESULT_INDEX_MAPPING;
import static org.opensearch.searchrelevance.common.PluginConstants.EXPERIMENT_INDEX;
import static org.opensearch.searchrelevance.common.PluginConstants.EXPERIMENT_INDEX_MAPPING;
import static org.opensearch.searchrelevance.common.PluginConstants.JUDGMENT_INDEX;
import static org.opensearch.searchrelevance.common.PluginConstants.JUDGMENT_INDEX_MAPPING;
import static org.opensearch.searchrelevance.common.PluginConstants.QUERY_SET_INDEX;
import static org.opensearch.searchrelevance.common.PluginConstants.QUERY_SET_INDEX_MAPPING;
import static org.opensearch.searchrelevance.common.PluginConstants.SEARCH_CONFIGURATION_INDEX;
import static org.opensearch.searchrelevance.common.PluginConstants.SEARCH_CONFIGURATION_INDEX_MAPPING;
import static org.opensearch.searchrelevance.indices.SearchRelevanceIndicesManager.getIndexMappings;

import java.io.IOException;
import java.util.Objects;

import org.opensearch.core.rest.RestStatus;
import org.opensearch.searchrelevance.exception.SearchRelevanceException;

public enum SearchRelevanceIndices {
    /**
     * Query Set Index
     */
    QUERY_SET(QUERY_SET_INDEX, QUERY_SET_INDEX_MAPPING),

    /**
     * Experiment Index
     */
    EXPERIMENT(EXPERIMENT_INDEX, EXPERIMENT_INDEX_MAPPING),

    /**
     * Search Configuration Index
     */
    SEARCH_CONFIGURATION(SEARCH_CONFIGURATION_INDEX, SEARCH_CONFIGURATION_INDEX_MAPPING),

    /**
     * Judgment Index
     */
    JUDGMENT(JUDGMENT_INDEX, JUDGMENT_INDEX_MAPPING),

    /**
     * Evaluation Result Index
     */
    EVALUATION_RESULT(EVALUATION_RESULT_INDEX, EVALUATION_RESULT_INDEX_MAPPING);

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
            throw new SearchRelevanceException("Failed to load mapping under path: " + mappingPath, e, RestStatus.INTERNAL_SERVER_ERROR);
        }
    }

    public String getIndexName() {
        return indexName;
    }

    public String getMapping() {
        return mapping;
    }

}
