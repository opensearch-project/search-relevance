/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */
package org.opensearch.searchrelevance.common;

/**
 * Plugin constants that shared cross the project.
 */
public class PluginConstants {
    private PluginConstants() {}

    /** The transport action name prefix */
    public static final String TRANSPORT_ACTION_NAME_PREFIX = "cluster:admin/opensearch/search_relevance/";
    /** The base URI for this plugin's rest actions */
    public static final String SEARCH_RELEVANCE_BASE_URI = "/_plugins/search_relevance";

    /** The URI for this plugin's queryset rest actions */
    public static final String QUERYSETS_URL = SEARCH_RELEVANCE_BASE_URI + "/query_sets";
    /** The URI for this plugin's experiments rest actions */
    public static final String EXPERIMENTS_URI = SEARCH_RELEVANCE_BASE_URI + "/experiments";
    /** The URI for this plugin's judgments rest actions */
    public static final String JUDGMENTS_URL = SEARCH_RELEVANCE_BASE_URI + "/judgments";
    /** The URI for this plugin's search configurations rest actions */
    public static final String SEARCH_CONFIGURATIONS_URL = SEARCH_RELEVANCE_BASE_URI + "/search_configurations";

    /** The URI PARAMS placeholders */
    public static final String DOCUMENT_ID = "id";
    public static final String QUERY_TEXT = "query_text";

    /** Use %SearchText% to represent wildcard in queryBody and also refer to the text in the search bar */
    public static final String WILDCARD_QUERY_TEXT = "%SearchText%";

    /**
     * Indices constants
     */
    public static final String QUERY_SET_INDEX = ".plugins-search-relevance-queryset";
    public static final String QUERY_SET_INDEX_MAPPING = "mappings/queryset.json";
    public static final String SEARCH_CONFIGURATION_INDEX = ".plugins-search-relevance-search-config";
    public static final String SEARCH_CONFIGURATION_INDEX_MAPPING = "mappings/search_configuration.json";
    public static final String EXPERIMENT_INDEX = ".plugins-search-relevance-experiment";
    public static final String EXPERIMENT_INDEX_MAPPING = "mappings/experiment.json";
    public static final String JUDGMENT_INDEX = ".plugins-search-relevance-judgment";
    public static final String JUDGMENT_INDEX_MAPPING = "mappings/judgment.json";
    public static final String EVALUATION_RESULT_INDEX = ".plugins-search-relevance-evaluation-result";
    public static final String EVALUATION_RESULT_INDEX_MAPPING = "mappings/evaluation_result.json";

    /**
     * UBI
     */
    public static final String UBI_QUERIES_INDEX = "ubi_queries";
    public static final String USER_QUERY_FIELD = "user_query";

}
