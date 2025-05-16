/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */
package org.opensearch.searchrelevance.model.builder;

import org.opensearch.action.search.SearchRequest;
import org.opensearch.test.OpenSearchTestCase;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

public class SearchRequestBuilderTests extends OpenSearchTestCase {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    void testQueryParsing() throws Exception {
        // Given
        String query =
            "{\"_source\": {\"exclude\": [\"passage_embedding\"]},\"query\": {\"hybrid\": {\"queries\": [{\"match\": {\"category\": \"sister\"}},{\"match\": {\"title\": {\"query\": \"my friend\"}}}]}},\"search_pipeline\" : {\"description\": \"Post processor for hybrid search\",\"phase_results_processors\": [{\"normalization-processor\": {\"normalization\": {\"technique\": \"min_max\"},\"combination\": {\"technique\": \"arithmetic_mean\",\"parameters\": {\"weights\": [0.7,0.3]}}}}]}}";

        // When
        String pipelineBody = SearchRequestBuilder.fetchPipelineBody(query);
        String[] excludedFields = SearchRequestBuilder.fetchExcludingFields(query);
        String queryBody = SearchRequestBuilder.fetchQueryBody(query);

        // Then
        // Verify pipeline body
        assertNotNull(pipelineBody);
        JsonNode pipelineNode = OBJECT_MAPPER.readTree(pipelineBody);
        assertEquals("Post processor for hybrid search", pipelineNode.get("description").asText());
        assertTrue(pipelineNode.has("phase_results_processors"));

        // Verify excluded fields
        assertNotNull(excludedFields);
        assertEquals(1, excludedFields.length);
        assertEquals("passage_embedding", excludedFields[0]);

        // Verify query body
        assertNotNull(queryBody);
        JsonNode queryNode = OBJECT_MAPPER.readTree(queryBody);
        assertTrue(queryNode.has("hybrid"));
        JsonNode hybridNode = queryNode.get("hybrid");
        assertTrue(hybridNode.has("queries"));
        assertEquals(2, hybridNode.get("queries").size());
    }

    void testBuildSearchRequest() {
        // Given
        String index = "test_index";
        String query =
            "{\"_source\": {\"exclude\": [\"passage_embedding\"]},\"query\": {\"hybrid\": {\"queries\": [{\"match\": {\"category\": \"sister\"}},{\"match\": {\"title\": {\"query\": \"my friend\"}}}]}}}";
        String queryText = "test query";
        String searchPipeline = "test_pipeline";
        int size = 10;

        // When
        SearchRequest searchRequest = SearchRequestBuilder.buildSearchRequest(index, query, queryText, searchPipeline, size);

        // Then
        assertNotNull(searchRequest);
        assertEquals(index, searchRequest.indices()[0]);
        assertEquals(searchPipeline, searchRequest.pipeline());
        assertEquals(size, searchRequest.source().size());
    }
}
