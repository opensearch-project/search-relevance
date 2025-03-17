/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */
package org.opensearch.searchrelevance.common;

public class Constants {
    /** The transport action name prefix */
    public static final String TRANSPORT_ACTION_NAME_PREFIX = "cluster:admin/opensearch/search_relevance/";
    /** The base URI for this plugin's rest actions */
    public static final String SEARCH_RELEVANCE_BASE_URI = "/_plugins/search_relevance";
    /** The URI for this plugin's queryset rest actions */
    public static final String QUERYSET_URI = SEARCH_RELEVANCE_BASE_URI + "/queryset";
    /** Field name for queryset Id, the document Id of the indexed use case template */
    public static final String QUERYSET_ID = "queryset_id";
}
