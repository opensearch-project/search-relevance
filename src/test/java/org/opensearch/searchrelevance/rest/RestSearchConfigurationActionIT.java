/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */
package org.opensearch.searchrelevance.rest;

import java.util.HashMap;
import java.util.Map;

import org.opensearch.client.Response;
import org.opensearch.client.ResponseException;
import org.opensearch.core.rest.RestStatus;
import org.opensearch.searchrelevance.plugin.SearchRelevanceRestTestCase;

import com.fasterxml.jackson.core.JsonProcessingException;

/**
 * Integration tests for RestPutSearchConfigurationAction, RestGetSearchConfigurationAction and RestDeleteSearchConfigurationAction
 */
public class RestSearchConfigurationActionIT extends SearchRelevanceRestTestCase {

    public void testSearchConfigurationCRUDOperations() throws Exception {
        // 1. put a search configuration
        String name = "test_name";
        String index = "sample_index";
        String query = "{\"query\": {\n\"match_all\": {}}}";
        String requestBody = createSearchConfigurationRequestBody(name, index, query);

        Response putResponse = makeRequest("PUT", SEARCH_CONFIGURATIONS_ENDPOINT, requestBody);
        assertEquals(RestStatus.OK.getStatus(), putResponse.getStatusLine().getStatusCode());

        // parse the response to get the search_configuration_id
        Map<String, Object> putResponseMap = entityAsMap(putResponse);
        String searchConfigurationId = (String) putResponseMap.get("search_configuration_id");
        assertNotNull("Search configuration ID should not be null", searchConfigurationId);

        // force index refresh to ensure the document is searchable
        makeRequest("POST", "/_refresh", null);

        // 2. get the query set by query_set_id
        Response getResponse = makeRequest("GET", SEARCH_CONFIGURATIONS_ENDPOINT + "/" + searchConfigurationId, null);
        assertEquals(RestStatus.OK.getStatus(), getResponse.getStatusLine().getStatusCode());

        Map<String, Object> getResponseMap = entityAsMap(getResponse);
        assertNotNull("Get response should not be null", getResponseMap);

        // 3. list all query sets
        Response listResponse = makeRequest("GET", SEARCH_CONFIGURATIONS_ENDPOINT, null);
        assertEquals(RestStatus.OK.getStatus(), listResponse.getStatusLine().getStatusCode());

        // 4. delete the query set by query_set_id
        Response deleteResponse = makeRequest("DELETE", SEARCH_CONFIGURATIONS_ENDPOINT + "/" + searchConfigurationId, null);
        assertEquals(RestStatus.OK.getStatus(), deleteResponse.getStatusLine().getStatusCode());

        // force index refresh to ensure the document is searchable
        makeRequest("POST", "/_refresh", null);

        // 5. validate the query set got deleted
        try {
            makeRequest("GET", SEARCH_CONFIGURATIONS_ENDPOINT + "/" + searchConfigurationId, null);
            fail("Expected ResponseException for deleted document");
        } catch (ResponseException e) {
            assertEquals(RestStatus.NOT_FOUND.getStatus(), e.getResponse().getStatusLine().getStatusCode());
        }
    }

    protected static String createSearchConfigurationRequestBody(String name, String index, String query) throws JsonProcessingException {
        Map<String, Object> requestMap = new HashMap<>();
        requestMap.put("name", name);
        requestMap.put("index", index);
        requestMap.put("query", query);
        requestMap.put("searchPipeline", "");
        return OBJECT_MAPPER.writeValueAsString(requestMap);
    }
}
