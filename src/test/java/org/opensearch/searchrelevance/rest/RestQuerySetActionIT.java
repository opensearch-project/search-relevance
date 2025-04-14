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
 * Integration tests for RestPutQuerySetAction, RestGetQuerySetAction and RestDeleteQuerySetAction
 */
public class RestQuerySetActionIT extends SearchRelevanceRestTestCase {

    public void testQuerySetCRUDOperations() throws Exception {
        // 1. put a query set
        String name = "test_name";
        String description = "test_description";
        String requestBody = createQuerySetRequestBody(name, description);

        Response putResponse = makeRequest("PUT", QUERY_SETS_ENDPOINT, requestBody);
        assertEquals(RestStatus.OK.getStatus(), putResponse.getStatusLine().getStatusCode());

        // parse the response to get the query_set_id
        Map<String, Object> putResponseMap = entityAsMap(putResponse);
        String querySetId = (String) putResponseMap.get("query_set_id");
        assertNotNull("Query set ID should not be null", querySetId);

        // force index refresh to ensure the document is searchable
        makeRequest("POST", "/_refresh", null);

        // 2. get the query set by query_set_id
        Response getResponse = makeRequest("GET", QUERY_SETS_ENDPOINT + "/" + querySetId, null);
        assertEquals(RestStatus.OK.getStatus(), getResponse.getStatusLine().getStatusCode());

        Map<String, Object> getResponseMap = entityAsMap(getResponse);
        assertNotNull("Get response should not be null", getResponseMap);

        // 3. list all query sets
        Response listResponse = makeRequest("GET", QUERY_SETS_ENDPOINT, null);
        assertEquals(RestStatus.OK.getStatus(), listResponse.getStatusLine().getStatusCode());

        // 4. delete the query set by query_set_id
        Response deleteResponse = makeRequest("DELETE", QUERY_SETS_ENDPOINT + "/" + querySetId, null);
        assertEquals(RestStatus.OK.getStatus(), deleteResponse.getStatusLine().getStatusCode());

        // force index refresh to ensure the document is searchable
        makeRequest("POST", "/_refresh", null);

        // 5. validate the query set got deleted
        try {
            makeRequest("GET", QUERY_SETS_ENDPOINT + "/" + querySetId, null);
            fail("Expected ResponseException for deleted document");
        } catch (ResponseException e) {
            assertEquals(RestStatus.NOT_FOUND.getStatus(), e.getResponse().getStatusLine().getStatusCode());
        }
    }

    protected static String createQuerySetRequestBody(String name, String description) throws JsonProcessingException {
        Map<String, Object> requestMap = new HashMap<>();
        requestMap.put("name", name);
        requestMap.put("description", description);
        requestMap.put("sampling", "manual");
        requestMap.put("querySetQueries", "apple, banana");
        return OBJECT_MAPPER.writeValueAsString(requestMap);
    }
}
