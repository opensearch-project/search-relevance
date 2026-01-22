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
import static org.opensearch.searchrelevance.common.PluginConstants.EXPERIMENT_VARIANT_INDEX;
import static org.opensearch.searchrelevance.common.PluginConstants.EXPERIMENT_VARIANT_INDEX_MAPPING;
import static org.opensearch.searchrelevance.common.PluginConstants.JUDGMENT_CACHE_INDEX;
import static org.opensearch.searchrelevance.common.PluginConstants.JUDGMENT_CACHE_INDEX_MAPPING;
import static org.opensearch.searchrelevance.common.PluginConstants.JUDGMENT_INDEX;
import static org.opensearch.searchrelevance.common.PluginConstants.JUDGMENT_INDEX_MAPPING;
import static org.opensearch.searchrelevance.common.PluginConstants.QUERY_SET_INDEX;
import static org.opensearch.searchrelevance.common.PluginConstants.QUERY_SET_INDEX_MAPPING;
import static org.opensearch.searchrelevance.common.PluginConstants.SCHEDULED_EXPERIMENT_HISTORY_INDEX;
import static org.opensearch.searchrelevance.common.PluginConstants.SCHEDULED_EXPERIMENT_HISTORY_INDEX_MAPPING;
import static org.opensearch.searchrelevance.common.PluginConstants.SCHEDULED_JOBS_INDEX;
import static org.opensearch.searchrelevance.common.PluginConstants.SCHEDULED_JOBS_INDEX_MAPPING;
import static org.opensearch.searchrelevance.common.PluginConstants.SEARCH_CONFIGURATION_INDEX;
import static org.opensearch.searchrelevance.common.PluginConstants.SEARCH_CONFIGURATION_INDEX_MAPPING;
import static org.opensearch.searchrelevance.indices.SearchRelevanceIndicesManager.getIndexMappings;

import java.io.IOException;
import java.util.Map;
import java.util.Objects;

import org.opensearch.common.xcontent.XContentHelper;
import org.opensearch.common.xcontent.XContentType;
import org.opensearch.core.rest.RestStatus;
import org.opensearch.searchrelevance.exception.SearchRelevanceException;

import lombok.Getter;

@Getter
public enum SearchRelevanceIndices {
    /**
     * Query Set Index
     */
    QUERY_SET(QUERY_SET_INDEX, QUERY_SET_INDEX_MAPPING, false),

    /**
     * Experiment Index
     */
    EXPERIMENT(EXPERIMENT_INDEX, EXPERIMENT_INDEX_MAPPING, true),

    /**
     * Search Configuration Index
     */
    SEARCH_CONFIGURATION(SEARCH_CONFIGURATION_INDEX, SEARCH_CONFIGURATION_INDEX_MAPPING, false),

    /**
     * Judgment Index
     */
    JUDGMENT(JUDGMENT_INDEX, JUDGMENT_INDEX_MAPPING, false),

    /**
     * Evaluation Result Index
     */
    EVALUATION_RESULT(EVALUATION_RESULT_INDEX, EVALUATION_RESULT_INDEX_MAPPING, false),

    /**
     * Judgment Cache Index
     */
    JUDGMENT_CACHE(JUDGMENT_CACHE_INDEX, JUDGMENT_CACHE_INDEX_MAPPING, false),

    /**
     * Experiment Variant Index
     */
    EXPERIMENT_VARIANT(EXPERIMENT_VARIANT_INDEX, EXPERIMENT_VARIANT_INDEX_MAPPING, false),

    /**
     * Scheduled Jobs Index
     */
    SCHEDULED_JOBS(SCHEDULED_JOBS_INDEX, SCHEDULED_JOBS_INDEX_MAPPING, false),

    /**
     * Scheduled Experiment History Index
     */
    SCHEDULED_EXPERIMENT_HISTORY(SCHEDULED_EXPERIMENT_HISTORY_INDEX, SCHEDULED_EXPERIMENT_HISTORY_INDEX_MAPPING, false);

    /**
     * Key for schema_version in the _meta section of index mappings
     */
    public static final String META_SCHEMA_VERSION_KEY = "schema_version";

    private final String indexName;
    private final String mapping;
    private final boolean isProtected;
    private final int schemaVersion;

    SearchRelevanceIndices(String indexName, String mappingPath, boolean isProtected) {
        this.indexName = Objects.requireNonNull(indexName, "Index name cannot be null.");
        this.mapping = loadMapping(mappingPath);
        this.isProtected = isProtected;
        this.schemaVersion = parseSchemaVersionFromMapping(this.mapping);
    }

    private String loadMapping(String mappingPath) {
        try {
            return getIndexMappings(mappingPath);
        } catch (IOException e) {
            throw new SearchRelevanceException("Failed to load mapping under path: " + mappingPath, e, RestStatus.INTERNAL_SERVER_ERROR);
        }
    }

    /**
     * Parse the schema_version from the _meta field in the mapping JSON.
     * This ensures the schema version is defined in a single place (the JSON file).
     * All mapping JSON files MUST have _meta.schema_version defined.
     * @param mappingJson the mapping JSON string
     * @return the schema version
     * @throws SearchRelevanceException if schema_version is not found in the mapping
     */
    private int parseSchemaVersionFromMapping(String mappingJson) {
        try {
            Map<String, Object> mappingMap = XContentHelper.convertToMap(XContentType.JSON.xContent(), mappingJson, false);
            Object metaObj = mappingMap.get("_meta");
            if (!(metaObj instanceof Map<?, ?>)) {
                throw new SearchRelevanceException(
                    "Mapping JSON for index [" + indexName + "] must have _meta.schema_version defined",
                    RestStatus.INTERNAL_SERVER_ERROR
                );
            }
            Map<?, ?> meta = (Map<?, ?>) metaObj;
            Object versionObj = meta.get(META_SCHEMA_VERSION_KEY);
            if (versionObj instanceof Number) {
                return ((Number) versionObj).intValue();
            }
            throw new SearchRelevanceException(
                "Mapping JSON for index [" + indexName + "] must have _meta.schema_version defined as a number",
                RestStatus.INTERNAL_SERVER_ERROR
            );
        } catch (Exception e) {
            throw new SearchRelevanceException(
                "Failed to parse schema_version from mapping JSON for index: " + indexName,
                e,
                RestStatus.INTERNAL_SERVER_ERROR
            );
        }
    }
}
