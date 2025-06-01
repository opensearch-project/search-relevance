/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */
package org.opensearch.searchrelevance.model.builder;

import static org.opensearch.searchrelevance.common.PluginConstants.WILDCARD_QUERY_TEXT;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.opensearch.action.search.SearchRequest;
import org.opensearch.search.builder.SearchSourceBuilder;
import org.opensearch.test.OpenSearchTestCase;

public class SearchRequestBuilderTests extends OpenSearchTestCase {

    private static final String TEST_INDEX = "test_index";
    private static final String TEST_QUERY_TEXT = "test_query";
    private static final String TEST_PIPELINE = "test_pipeline";
    private static final int TEST_SIZE = 10;

    public void testBuildSearchRequestSimpleQuery() {
        String simpleQuery = "{\"query\":{\"match\":{\"title\":\"" + WILDCARD_QUERY_TEXT + "\"}}}";

        SearchRequest searchRequest = SearchRequestBuilder.buildSearchRequest(
            TEST_INDEX,
            simpleQuery,
            TEST_QUERY_TEXT,
            TEST_PIPELINE,
            TEST_SIZE
        );

        assertNotNull("SearchRequest should not be null", searchRequest);
        assertEquals("Index should match", TEST_INDEX, searchRequest.indices()[0]);
        assertEquals("Pipeline should match", TEST_PIPELINE, searchRequest.pipeline());

        SearchSourceBuilder sourceBuilder = searchRequest.source();
        assertNotNull("SearchSourceBuilder should not be null", sourceBuilder);
        assertEquals("Size should match", TEST_SIZE, sourceBuilder.size());
    }

    public void testBuildSearchRequestHybridQuery() {
        String hybridQuery =
            "{\"_source\":{\"exclude\":[\"passage_embedding\"]},\"query\":{\"hybrid\":{\"queries\":[{\"match\":{\"name\":\""
                + WILDCARD_QUERY_TEXT
                + "\"}},{\"match\":{\"name\":{\"query\":\""
                + WILDCARD_QUERY_TEXT
                + "\"}}}]}},\"search_pipeline\":{\"description\":\"Post processor for hybrid search\","
                + "\"phase_results_processors\":[{\"normalization-processor\":{\"normalization\":{\"technique\":\"min_max\"},\"combination\":"
                + "{\"technique\":\"arithmetic_mean\",\"parameters\":{\"weights\":[0.7,0.3]}}}}]}}";
        SearchRequest searchRequest = SearchRequestBuilder.buildSearchRequest(TEST_INDEX, hybridQuery, TEST_QUERY_TEXT, null, TEST_SIZE);

        assertNotNull("SearchRequest should not be null", searchRequest);
        assertEquals("Index should match", TEST_INDEX, searchRequest.indices()[0]);

        SearchSourceBuilder sourceBuilder = searchRequest.source();
        assertNotNull("SearchSourceBuilder should not be null", sourceBuilder);
        assertEquals("Size should match", TEST_SIZE, sourceBuilder.size());
    }

    public void testHybridQuerySearchConfiguration_whenQuerySourceWithGenericHybridQuery_thenSuccess() {
        String hybridQuery =
            "{\"_source\":{\"exclude\":[\"passage_embedding\"]},\"query\":{\"hybrid\":{\"queries\":[{\"match\":{\"name\":\""
                + WILDCARD_QUERY_TEXT
                + "\"}},{\"match\":{\"name\":{\"query\":\""
                + WILDCARD_QUERY_TEXT
                + "\"}}}]}}}";

        Map<String, Object> normalizationProcessorConfig = new HashMap<>(
            Map.of(
                "normalization",
                new HashMap<String, Object>(Map.of("technique", "min_max")),
                "combination",
                new HashMap<String, Object>(Map.of("technique", "arithmetic_mean"))
            )
        );
        Map<String, Object> phaseProcessorObject = new HashMap<>(Map.of("normalization-processor", normalizationProcessorConfig));
        Map<String, Object> temporarySearchPipeline = new HashMap<>();
        temporarySearchPipeline.put("phase_results_processors", List.of(phaseProcessorObject));

        SearchRequest searchRequest = SearchRequestBuilder.buildRequestForHybridSearch(
            TEST_INDEX,
            hybridQuery,
            temporarySearchPipeline,
            TEST_QUERY_TEXT,
            TEST_SIZE
        );
        assertNotNull("SearchRequest should not be null", searchRequest);
        assertEquals("Index should match", TEST_INDEX, searchRequest.indices()[0]);

        SearchSourceBuilder sourceBuilder = searchRequest.source();
        assertNotNull("SearchSourceBuilder should not be null", sourceBuilder);
        assertEquals("Size should match", TEST_SIZE, sourceBuilder.size());

        assertNotNull(sourceBuilder.searchPipelineSource());
        Map<String, Object> searchPipelineSource = sourceBuilder.searchPipelineSource();
        assertFalse(searchPipelineSource.isEmpty());
        assertTrue(searchPipelineSource.containsKey("phase_results_processors"));
        assertEquals(1, ((List<?>) searchPipelineSource.get("phase_results_processors")).size());
    }

    public void testHybridQuerySearchConfiguration_whenQuerySourceHasTemporaryPipeline_thenFail() {
        String hybridQuery =
            "{\"_source\":{\"exclude\":[\"passage_embedding\"]},\"query\":{\"hybrid\":{\"queries\":[{\"match\":{\"name\":\""
                + WILDCARD_QUERY_TEXT
                + "\"}},{\"match\":{\"name\":{\"query\":\""
                + WILDCARD_QUERY_TEXT
                + "\"}}}]}},\"search_pipeline\":{\"description\":\"Post processor for hybrid search\","
                + "\"phase_results_processors\":[{\"normalization-processor\":{\"normalization\":{\"technique\":\"min_max\"},\"combination\":"
                + "{\"technique\":\"arithmetic_mean\",\"parameters\":{\"weights\":[0.7,0.3]}}}}]}}";
        IllegalArgumentException exception = expectThrows(
            IllegalArgumentException.class,
            () -> SearchRequestBuilder.buildRequestForHybridSearch(TEST_INDEX, hybridQuery, Map.of(), TEST_QUERY_TEXT, TEST_SIZE)
        );
        assertEquals("search pipeline is not allowed in search request", exception.getMessage());
    }

    public void testBuildSearchRequestInvalidJson() {
        String invalidQuery = "{\"query\":invalid}";
        assertThrows(
            "Should throw IOException for invalid JSON format",
            IllegalArgumentException.class,
            () -> SearchRequestBuilder.buildSearchRequest(TEST_INDEX, invalidQuery, TEST_QUERY_TEXT, null, TEST_SIZE)
        );
    }

    public void testHybridQuerySearchConfiguration_whenLessThenTwoSubQueries_thenFail() {
        String hybridQuery =
            "{\"_source\":{\"exclude\":[\"passage_embedding\"]},\"query\":{\"hybrid\":{\"queries\":[{\"match\":{\"name\":\""
                + WILDCARD_QUERY_TEXT
                + "\"}}]}}}";
        IllegalArgumentException exception = expectThrows(
            IllegalArgumentException.class,
            () -> SearchRequestBuilder.buildRequestForHybridSearch(TEST_INDEX, hybridQuery, Map.of(), TEST_QUERY_TEXT, TEST_SIZE)
        );
        assertEquals("invalid hybrid query: expected exactly [2] sub-queries but found [1]", exception.getMessage());
    }
}
