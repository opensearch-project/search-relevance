/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */
package org.opensearch.searchrelevance.rest;

import static org.opensearch.searchrelevance.rest.RestQuerySetActionIT.createQuerySetRequestBody;
import static org.opensearch.searchrelevance.rest.RestSearchConfigurationActionIT.createSearchConfigurationRequestBody;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.opensearch.client.Response;
import org.opensearch.client.ResponseException;
import org.opensearch.core.rest.RestStatus;
import org.opensearch.searchrelevance.plugin.SearchRelevanceRestTestCase;

import com.fasterxml.jackson.core.JsonProcessingException;

/**
 * Integration tests for RestPutExperimentAction, RestGetExperimentAction and RestDeleteExperimentAction
 */
public class RestExperimentActionIT extends SearchRelevanceRestTestCase {

    public void testExperimentCRUDOperations() throws Exception {
        // create a sample index
        makeRequest("PUT", "/sample_index", null);

        // create a query set and get the query_set_id
        String querysetName = "test_queryset_name";
        String querysetDescription = "test_queryset_description";
        String querySetRequestBody = createQuerySetRequestBody(querysetName, querysetDescription);
        Response putQuerySetResponse = makeRequest("PUT", QUERY_SETS_ENDPOINT, querySetRequestBody);
        assertEquals(RestStatus.OK.getStatus(), putQuerySetResponse.getStatusLine().getStatusCode());

        // parse the response to get the query_set_id
        Map<String, Object> querySetPutResponseMap = entityAsMap(putQuerySetResponse);
        String querySetId = (String) querySetPutResponseMap.get("query_set_id");
        assertNotNull("Query set ID should not be null", querySetId);

        // create a search configuration and get the search_configuration_id
        String searchConfigName = "test_name";
        String index = "sample_index";
        String queryBody = "{\"match_all\":{}}";
        String searchConfigRequestBody = createSearchConfigurationRequestBody(searchConfigName, index, queryBody);

        Response putSearchConfigResponse = makeRequest("PUT", SEARCH_CONFIGURATIONS_ENDPOINT, searchConfigRequestBody);
        assertEquals(RestStatus.OK.getStatus(), putSearchConfigResponse.getStatusLine().getStatusCode());

        // parse the response to get the search_configuration_id
        Map<String, Object> searchConfigPutResponseMap = entityAsMap(putSearchConfigResponse);
        String searchConfigurationId = (String) searchConfigPutResponseMap.get("search_configuration_id");
        assertNotNull("Search configuration ID should not be null", searchConfigurationId);

        // 1. put an experiment
        List<String> searchConfigurationList = List.of(searchConfigurationId);
        String requestBody = createRequestBody(querySetId, searchConfigurationList);

        Response putResponse = makeRequest("PUT", EXPERIMENTS_ENDPOINT, requestBody);
        assertEquals(RestStatus.OK.getStatus(), putResponse.getStatusLine().getStatusCode());

        // parse the response to get the experiment_id
        Map<String, Object> putResponseMap = entityAsMap(putResponse);
        String experimentId = (String) putResponseMap.get("experiment_id");
        assertNotNull("Experiment ID should not be null", querySetId);

        // force index refresh to ensure the document is searchable
        makeRequest("POST", "/_refresh", null);

        // 2. get the experiment by experiment_id
        Response getResponse = makeRequest("GET", EXPERIMENTS_ENDPOINT + "/" + experimentId, null);
        assertEquals(RestStatus.OK.getStatus(), getResponse.getStatusLine().getStatusCode());

        Map<String, Object> getResponseMap = entityAsMap(getResponse);
        assertNotNull("Get response should not be null", getResponseMap);

        // 3. list all experiments
        Response listResponse = makeRequest("GET", EXPERIMENTS_ENDPOINT, null);
        assertEquals(RestStatus.OK.getStatus(), listResponse.getStatusLine().getStatusCode());

        // 4. delete the experiment by experiment_id
        Response deleteResponse = makeRequest("DELETE", EXPERIMENTS_ENDPOINT + "/" + experimentId, null);
        assertEquals(RestStatus.OK.getStatus(), deleteResponse.getStatusLine().getStatusCode());

        // force index refresh to ensure the document is searchable
        makeRequest("POST", "/_refresh", null);

        // 5. validate the experiment got deleted
        try {
            makeRequest("GET", EXPERIMENTS_ENDPOINT + "/" + experimentId, null);
            fail("Expected ResponseException for deleted document");
        } catch (ResponseException e) {
            assertEquals(RestStatus.NOT_FOUND.getStatus(), e.getResponse().getStatusLine().getStatusCode());
        }
    }

    private String createRequestBody(String querySetId, List<String> searchConfigurationList) throws JsonProcessingException {
        Map<String, Object> requestMap = new HashMap<>();
        requestMap.put("querySetId", querySetId);
        requestMap.put("searchConfigurationList", searchConfigurationList);
        requestMap.put("k", 10);
        return OBJECT_MAPPER.writeValueAsString(requestMap);
    }
}
