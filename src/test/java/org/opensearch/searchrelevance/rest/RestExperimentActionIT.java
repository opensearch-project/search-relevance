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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.opensearch.client.Response;
import org.opensearch.core.rest.RestStatus;
import org.opensearch.searchrelevance.model.QueryWithReference;
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
        String query = "{\"query\": {\n\"match_all\": {}}}";
        String searchConfigRequestBody = createSearchConfigurationRequestBody(searchConfigName, index, query);

        Response putSearchConfigResponse = makeRequest("PUT", SEARCH_CONFIGURATIONS_ENDPOINT, searchConfigRequestBody);
        assertEquals(RestStatus.OK.getStatus(), putSearchConfigResponse.getStatusLine().getStatusCode());

        // parse the response to get the search_configuration_id
        Map<String, Object> searchConfigPutResponseMap = entityAsMap(putSearchConfigResponse);
        String searchConfigurationId = (String) searchConfigPutResponseMap.get("search_configuration_id");
        assertNotNull("Search configuration ID should not be null", searchConfigurationId);

        // repeat to build pairwise search configuration
        String searchConfigName2 = "test_name2";
        String searchConfigRequestBody2 = createSearchConfigurationRequestBody(searchConfigName2, index, query);
        Response putSearchConfigResponse2 = makeRequest("PUT", SEARCH_CONFIGURATIONS_ENDPOINT, searchConfigRequestBody2);
        assertEquals(RestStatus.OK.getStatus(), putSearchConfigResponse2.getStatusLine().getStatusCode());
        Map<String, Object> searchConfigPutResponseMap2 = entityAsMap(putSearchConfigResponse2);
        String searchConfigurationId2 = (String) searchConfigPutResponseMap2.get("search_configuration_id");

        // 1. put an experiment
        List<String> searchConfigurationList = List.of(searchConfigurationId, searchConfigurationId2);
        String pairwiseRequestBody = createPairwiseRequestBody(querySetId, searchConfigurationList);

        Response putResponse = makeRequest("PUT", EXPERIMENTS_ENDPOINT, pairwiseRequestBody);
        assertEquals(RestStatus.OK.getStatus(), putResponse.getStatusLine().getStatusCode());

        // parse the response to get the experiment_id
        Map<String, Object> putResponseMap = entityAsMap(putResponse);
        String experimentId = (String) putResponseMap.get("experiment_id");
        assertNotNull("Experiment ID should not be null", experimentId);

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
    }

    private String createPairwiseRequestBody(String querySetId, List<String> searchConfigurationList) throws JsonProcessingException {
        Map<String, Object> requestMap = new HashMap<>();
        requestMap.put("querySetId", querySetId);
        requestMap.put("searchConfigurationList", searchConfigurationList);
        requestMap.put("size", 10);
        requestMap.put("type", "PAIRWISE_COMPARISON");
        return OBJECT_MAPPER.writeValueAsString(requestMap);
    }

    private List<QueryWithReference> getQuerySetQueries() {
        List<QueryWithReference> querySetQueries = new ArrayList<>();
        querySetQueries.add(new QueryWithReference("apple", ""));
        querySetQueries.add(new QueryWithReference("banana", ""));
        return querySetQueries;
    }
}
