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
    public static final String SEARCH_RELEVANCE_BASE_URI = "/_plugins/_search_relevance";

    /** The URI for this plugin's queryset rest actions */
    public static final String QUERYSETS_URL = SEARCH_RELEVANCE_BASE_URI + "/query_sets";
    /** The URI for this plugin's experiments rest actions */
    public static final String EXPERIMENTS_URI = SEARCH_RELEVANCE_BASE_URI + "/experiments";
    /** The URI for this plugin's judgments rest actions */
    public static final String JUDGMENTS_URL = SEARCH_RELEVANCE_BASE_URI + "/judgments";
    /** The URI for this plugin's search configurations rest actions */
    public static final String SEARCH_CONFIGURATIONS_URL = SEARCH_RELEVANCE_BASE_URI + "/search_configurations";
    /** The URI for this plugin's scheduled experiments rest actions */
    public static final String SCHEDULED_EXPERIMENT_URL = EXPERIMENTS_URI + "/schedule";
    /** The URI for initializing the UBI indices */
    public static final String INITIALIZE_URL = "/_plugins/ubi/initialize";

    /** The URI PARAMS placeholders */
    public static final String DOCUMENT_ID = "id";
    public static final String QUERY_TEXT = "query_text";

    /** Use %SearchText% to represent wildcard in queryBody and also refer to the text in the search bar */
    public static final String WILDCARD_QUERY_TEXT = "%SearchText%";

    /**
     * Indices constants
     */
    public static final String QUERY_SET_INDEX = "search-relevance-queryset";
    public static final String QUERY_SET_INDEX_MAPPING = "mappings/queryset.json";
    public static final String SEARCH_CONFIGURATION_INDEX = "search-relevance-search-config";
    public static final String SEARCH_CONFIGURATION_INDEX_MAPPING = "mappings/search_configuration.json";
    public static final String EXPERIMENT_INDEX = ".plugins-search-relevance-experiment";
    public static final String EXPERIMENT_INDEX_MAPPING = "mappings/experiment.json";
    public static final String JUDGMENT_INDEX = "search-relevance-judgment";
    public static final String JUDGMENT_INDEX_MAPPING = "mappings/judgment.json";
    public static final String EVALUATION_RESULT_INDEX = "search-relevance-evaluation-result";
    public static final String EVALUATION_RESULT_INDEX_MAPPING = "mappings/evaluation_result.json";
    public static final String JUDGMENT_CACHE_INDEX = ".plugins-search-relevance-judgment-cache";
    public static final String JUDGMENT_CACHE_INDEX_MAPPING = "mappings/judgment_cache.json";
    public static final String EXPERIMENT_VARIANT_INDEX = "search-relevance-experiment-variant";
    public static final String EXPERIMENT_VARIANT_INDEX_MAPPING = "mappings/experiment_variant.json";
    public static final String SCHEDULED_JOBS_INDEX = ".search-relevance-scheduled-experiment-jobs";
    public static final String SCHEDULED_JOBS_INDEX_MAPPING = "mappings/scheduled_job.json";
    public static final String SCHEDULED_EXPERIMENT_HISTORY_INDEX = ".search-relevance-scheduled-experiment-history";
    public static final String SCHEDULED_EXPERIMENT_HISTORY_INDEX_MAPPING = "mappings/scheduled_experiment_history.json";

    /**
     * UBI
     */
    public static final String UBI_QUERIES_INDEX = "ubi_queries";
    public static final String USER_QUERY_FIELD = "user_query";
    public static final String UBI_EVENTS_INDEX = "ubi_events";

    public static final String CLICK_MODEL = "clickModel";
    public static final String NAX_RANK = "maxRank";
    public static final String START_DATE = "startDate";
    public static final String END_DATE = "endDate";

    /**
     * Rest Input Field Names
     */
    public static final String NAME = "name";
    public static final String DESCRIPTION = "description";
    public static final String TYPE = "type";
    public static final String SAMPLING = "sampling";
    public static final String QUERY_SET_SIZE = "querySetSize";
    public static final String QUERY_SET_QUERIES = "querySetQueries";
    public static final String INDEX = "index";
    public static final String QUERY = "query";
    public static final String SEARCH_PIPELINE = "searchPipeline";
    public static final String SIZE = "size";
    public static final String QUERYSET_ID = "querySetId";
    public static final String EXPERIMENT_ID = "experimentId";
    public static final String SEARCH_CONFIGURATION_LIST = "searchConfigurationList";
    public static final String JUDGMENT_LIST = "judgmentList";
    public static final String CRON_EXPRESSION = "cronExpression";

    public static final String JUDGMENT_RATINGS = "judgmentRatings";
    public static final String CONTEXT_FIELDS = "contextFields";
    public static final String IGNORE_FAILURE = "ignoreFailure";

    public static final int DEFAULTED_QUERY_SET_SIZE = 10;
    public static final String MANUAL = "manual";
}
