/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */
package org.opensearch.searchrelevance.model.builder;

import static org.opensearch.searchrelevance.common.PluginConstants.WILDCARD_QUERY_TEXT;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.opensearch.action.search.SearchRequest;
import org.opensearch.search.builder.SearchSourceBuilder;
import org.opensearch.test.OpenSearchTestCase;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

public class SearchRequestBuilderTests extends OpenSearchTestCase {

    private static final String TEST_INDEX = "test_index";
    private static final String TEST_QUERY_TEXT = "test_query";
    private static final String TEST_PIPELINE = "test_pipeline";
    private static final int TEST_SIZE = 10;

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static Map<String, Object> parseJsonToMap(String json) throws Exception {
        return MAPPER.readValue(json, new TypeReference<Map<String, Object>>() {
        });
    }

    @SuppressWarnings("unchecked")
    private static String extractWrappedRescoreQueryBase64(Map<String, Object> sourceMap) {
        Object rescore = sourceMap.get("rescore");
        if (rescore instanceof Map) {
            Map<String, Object> rescoreMap = (Map<String, Object>) rescore;
            Object queryObj = rescoreMap.get("query");
            if (queryObj instanceof Map) {
                Map<String, Object> queryMap = (Map<String, Object>) queryObj;
                Object rescoreQuery = queryMap.get("rescore_query");
                if (rescoreQuery instanceof Map) {
                    Map<String, Object> rq = (Map<String, Object>) rescoreQuery;
                    Object wrapperObj = rq.get("wrapper");
                    if (wrapperObj instanceof Map) {
                        return (String) ((Map<String, Object>) wrapperObj).get("query");
                    }
                }
            }
        } else if (rescore instanceof List) {
            List<?> list = (List<?>) rescore;
            if (list.isEmpty() == false) {
                Object first = list.get(0);
                if (first instanceof Map) {
                    Map<String, Object> rescoreMap = (Map<String, Object>) first;
                    Object queryObj = rescoreMap.get("query");
                    if (queryObj instanceof Map) {
                        Map<String, Object> queryMap = (Map<String, Object>) queryObj;
                        Object rescoreQuery = queryMap.get("rescore_query");
                        if (rescoreQuery instanceof Map) {
                            Map<String, Object> rq = (Map<String, Object>) rescoreQuery;
                            Object wrapperObj = rq.get("wrapper");
                            if (wrapperObj instanceof Map) {
                                return (String) ((Map<String, Object>) wrapperObj).get("query");
                            }
                        }
                    }
                }
            }
        }
        return null;
    }

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

    // -------------------------
    // Contract tests for rescore.rescore_query wrapper preprocessing
    // -------------------------

    public void testBuildSearchRequest_whenRescoreQueryIsObject_thenWrappedWithBase64() throws Exception {
        String query = "{"
            + "\"query\":{\"match\":{\"title\":\""
            + WILDCARD_QUERY_TEXT
            + "\"}},"
            + "\"rescore\":{\"query\":{\"rescore_query\":{\"sltr\":{\"model\":\"m1\",\"params\":{\"keywords\":\"abc\"}}}}}"
            + "}";

        SearchRequest searchRequest = SearchRequestBuilder.buildSearchRequest(TEST_INDEX, query, TEST_QUERY_TEXT, null, TEST_SIZE);
        assertNotNull(searchRequest);
        SearchSourceBuilder sourceBuilder = searchRequest.source();
        assertNotNull(sourceBuilder);

        Map<String, Object> sourceMap = parseJsonToMap(sourceBuilder.toString());
        String base64 = extractWrappedRescoreQueryBase64(sourceMap);
        assertNotNull("rescore.rescore_query should be wrapped with base64 payload", base64);

        String decoded = new String(Base64.getDecoder().decode(base64), StandardCharsets.UTF_8);
        Map<String, Object> decodedMap = parseJsonToMap(decoded);
        assertTrue("Decoded rescore_query should contain sltr", decodedMap.containsKey("sltr"));
        @SuppressWarnings("unchecked")
        Map<String, Object> sltr = (Map<String, Object>) decodedMap.get("sltr");
        assertEquals("m1", sltr.get("model"));
    }

    public void testBuildSearchRequest_whenRescoreIsArray_thenFirstEntryRescoreQueryWrapped() throws Exception {
        String query = "{"
            + "\"query\":{\"match\":{\"title\":\""
            + WILDCARD_QUERY_TEXT
            + "\"}},"
            + "\"rescore\":[{\"query\":{\"rescore_query\":{\"sltr\":{\"model\":\"m2\",\"params\":{\"keywords\":\"xyz\"}}}}}]"
            + "}";

        SearchRequest searchRequest = SearchRequestBuilder.buildSearchRequest(TEST_INDEX, query, TEST_QUERY_TEXT, null, TEST_SIZE);
        assertNotNull(searchRequest);
        SearchSourceBuilder sourceBuilder = searchRequest.source();
        assertNotNull(sourceBuilder);

        Map<String, Object> sourceMap = parseJsonToMap(sourceBuilder.toString());
        String base64 = extractWrappedRescoreQueryBase64(sourceMap);
        assertNotNull("rescore[0].rescore_query should be wrapped with base64 payload", base64);

        String decoded = new String(Base64.getDecoder().decode(base64), StandardCharsets.UTF_8);
        Map<String, Object> decodedMap = parseJsonToMap(decoded);
        assertTrue(decodedMap.containsKey("sltr"));
        @SuppressWarnings("unchecked")
        Map<String, Object> sltr = (Map<String, Object>) decodedMap.get("sltr");
        assertEquals("m2", sltr.get("model"));
    }

    public void testBuildRequestForHybridSearch_whenRescoreQueryPresent_thenWrappedWithBase64() throws Exception {
        // Minimal valid hybrid query (2 sub-queries) plus rescore with sltr
        String hybridQuery = "{"
            + "\"_source\":{\"exclude\":[\"passage_embedding\"]},"
            + "\"query\":{\"hybrid\":{\"queries\":["
            + "{\"match\":{\"name\":\""
            + WILDCARD_QUERY_TEXT
            + "\"}},"
            + "{\"match\":{\"name\":{\"query\":\""
            + WILDCARD_QUERY_TEXT
            + "\"}}}"
            + "]}},"
            + "\"rescore\":{\"query\":{\"rescore_query\":{\"sltr\":{\"model\":\"m3\",\"params\":{\"keywords\":\"hyb\"}}}}}"
            + "}";

        Map<String, Object> temporarySearchPipeline = Map.of(); // allowed empty
        SearchRequest searchRequest = SearchRequestBuilder.buildRequestForHybridSearch(
            TEST_INDEX,
            hybridQuery,
            temporarySearchPipeline,
            TEST_QUERY_TEXT,
            TEST_SIZE
        );
        assertNotNull(searchRequest);
        SearchSourceBuilder sourceBuilder = searchRequest.source();
        assertNotNull(sourceBuilder);

        Map<String, Object> sourceMap = parseJsonToMap(sourceBuilder.toString());
        String base64 = extractWrappedRescoreQueryBase64(sourceMap);
        assertNotNull("hybrid path should wrap rescore.rescore_query with base64 payload", base64);

        String decoded = new String(Base64.getDecoder().decode(base64), StandardCharsets.UTF_8);
        Map<String, Object> decodedMap = parseJsonToMap(decoded);
        assertTrue(decodedMap.containsKey("sltr"));
        @SuppressWarnings("unchecked")
        Map<String, Object> sltr = (Map<String, Object>) decodedMap.get("sltr");
        assertEquals("m3", sltr.get("model"));
    }
}
