/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */
package org.opensearch.searchrelevance.metrics;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.lucene.search.TotalHits;
import org.mockito.ArgumentCaptor;
import org.opensearch.action.search.SearchRequest;
import org.opensearch.action.search.SearchResponse;
import org.opensearch.cluster.service.ClusterService;
import org.opensearch.common.settings.Settings;
import org.opensearch.common.xcontent.XContentFactory;
import org.opensearch.core.action.ActionListener;
import org.opensearch.core.common.bytes.BytesArray;
import org.opensearch.core.common.bytes.BytesReference;
import org.opensearch.core.xcontent.NamedXContentRegistry;
import org.opensearch.search.SearchHit;
import org.opensearch.search.SearchHits;
import org.opensearch.search.SearchModule;
import org.opensearch.searchrelevance.dao.EvaluationResultDao;
import org.opensearch.searchrelevance.dao.ExperimentVariantDao;
import org.opensearch.searchrelevance.dao.JudgmentDao;
import org.opensearch.searchrelevance.model.SearchConfigurationDetails;
import org.opensearch.searchrelevance.model.builder.SearchRequestBuilder;
import org.opensearch.test.OpenSearchTestCase;
import org.opensearch.transport.client.Client;

public class MetricsHelperTests extends OpenSearchTestCase {

    private ClusterService clusterService;
    private Client client;
    private JudgmentDao judgmentDao;
    private EvaluationResultDao evaluationResultDao;
    private ExperimentVariantDao experimentVariantDao;
    private MetricsHelper metricsHelper;

    @Override
    public void setUp() throws Exception {
        super.setUp();
        clusterService = mock(ClusterService.class);
        client = mock(Client.class);
        judgmentDao = mock(JudgmentDao.class);
        evaluationResultDao = mock(EvaluationResultDao.class);
        experimentVariantDao = mock(ExperimentVariantDao.class);
        metricsHelper = new MetricsHelper(clusterService, client, judgmentDao, evaluationResultDao, experimentVariantDao);

        // Initialize SearchRequestBuilder NamedXContentRegistry for tests
        NamedXContentRegistry reg = new NamedXContentRegistry(
            new SearchModule(Settings.EMPTY, java.util.Collections.emptyList()).getNamedXContents()
        );
        SearchRequestBuilder.initialize(reg);
    }

    public void testProcessPairwiseMetricsWithPipeline() {
        // Prepare test data
        String queryText = "test query";
        int size = 10;

        Map<String, SearchConfigurationDetails> searchConfigurations = new HashMap<>();
        searchConfigurations.put(
            "config1",
            SearchConfigurationDetails.builder()
                .index("index1")
                .query("{\"query\":{\"match\":{\"title\":\"%SearchText%\"}}}")
                .pipeline("pipeline1")
                .build()
        );
        searchConfigurations.put(
            "config2",
            SearchConfigurationDetails.builder()
                .index("index2")
                .query("{\"query\":{\"match\":{\"description\":\"%SearchText%\"}}}")
                .pipeline("pipeline2")
                .build()
        );

        // Mock search response
        SearchResponse mockResponse = createMockSearchResponse("doc1", "doc2", "doc3");

        // Capture the search request to verify pipeline is set correctly
        ArgumentCaptor<SearchRequest> requestCaptor = ArgumentCaptor.forClass(SearchRequest.class);
        ArgumentCaptor<ActionListener<SearchResponse>> listenerCaptor = ArgumentCaptor.forClass(ActionListener.class);

        // Mock client.search to capture requests and respond
        doAnswer(invocation -> {
            ActionListener<SearchResponse> listener = invocation.getArgument(1);
            listener.onResponse(mockResponse);
            return null;
        }).when(client).search(requestCaptor.capture(), listenerCaptor.capture());

        // Execute the method
        ActionListener<Map<String, Object>> resultListener = new ActionListener<>() {
            @Override
            public void onResponse(Map<String, Object> result) {
                assertNotNull(result);
                assertNotNull(result.get("snapshots"));
                assertNotNull(result.get("metrics"));
            }

            @Override
            public void onFailure(Exception e) {
                fail("Test should not fail: " + e.getMessage());
            }
        };

        metricsHelper.processPairwiseMetrics(queryText, searchConfigurations, size, resultListener);

        // Verify that search was called twice with correct pipelines
        verify(client, times(2)).search(any(SearchRequest.class), any(ActionListener.class));

        List<SearchRequest> capturedRequests = requestCaptor.getAllValues();
        assertEquals(2, capturedRequests.size());

        // Verify pipeline is set correctly for both requests
        SearchRequest request1 = capturedRequests.stream()
            .filter(r -> r.indices()[0].equals("index1"))
            .findFirst()
            .orElseThrow(() -> new AssertionError("Request for index1 not found"));
        assertEquals("pipeline1", request1.pipeline());

        SearchRequest request2 = capturedRequests.stream()
            .filter(r -> r.indices()[0].equals("index2"))
            .findFirst()
            .orElseThrow(() -> new AssertionError("Request for index2 not found"));
        assertEquals("pipeline2", request2.pipeline());
    }

    public void testProcessPairwiseMetricsWithNullPipeline() {
        // Prepare test data with null pipeline
        String queryText = "test query";
        int size = 10;

        Map<String, SearchConfigurationDetails> searchConfigurations = new HashMap<>();
        searchConfigurations.put(
            "config1",
            SearchConfigurationDetails.builder()
                .index("index1")
                .query("{\"query\":{\"match\":{\"title\":\"%SearchText%\"}}}")
                .pipeline(null)
                .build()
        );

        // Mock search response
        SearchResponse mockResponse = createMockSearchResponse("doc1", "doc2");

        // Capture the search request
        ArgumentCaptor<SearchRequest> requestCaptor = ArgumentCaptor.forClass(SearchRequest.class);
        ArgumentCaptor<ActionListener<SearchResponse>> listenerCaptor = ArgumentCaptor.forClass(ActionListener.class);

        doAnswer(invocation -> {
            ActionListener<SearchResponse> listener = invocation.getArgument(1);
            listener.onResponse(mockResponse);
            return null;
        }).when(client).search(requestCaptor.capture(), listenerCaptor.capture());

        // Execute the method
        ActionListener<Map<String, Object>> resultListener = mock(ActionListener.class);
        metricsHelper.processPairwiseMetrics(queryText, searchConfigurations, size, resultListener);

        // Verify that null pipeline is handled correctly
        verify(client, times(1)).search(any(SearchRequest.class), any(ActionListener.class));

        SearchRequest capturedRequest = requestCaptor.getValue();
        assertEquals("index1", capturedRequest.indices()[0]);
        // When pipeline is null, the request should not have a pipeline set
        assertNull(capturedRequest.pipeline());
    }

    public void testProcessPairwiseMetricsWithEmptyPipeline() {
        // Prepare test data with empty pipeline
        String queryText = "test query";
        int size = 10;

        Map<String, SearchConfigurationDetails> searchConfigurations = new HashMap<>();
        searchConfigurations.put(
            "config1",
            SearchConfigurationDetails.builder()
                .index("index1")
                .query("{\"query\":{\"match\":{\"title\":\"%SearchText%\"}}}")
                .pipeline("")
                .build()
        );

        // Mock search response
        SearchResponse mockResponse = createMockSearchResponse("doc1");

        // Capture the search request
        ArgumentCaptor<SearchRequest> requestCaptor = ArgumentCaptor.forClass(SearchRequest.class);
        ArgumentCaptor<ActionListener<SearchResponse>> listenerCaptor = ArgumentCaptor.forClass(ActionListener.class);

        doAnswer(invocation -> {
            ActionListener<SearchResponse> listener = invocation.getArgument(1);
            listener.onResponse(mockResponse);
            return null;
        }).when(client).search(requestCaptor.capture(), listenerCaptor.capture());

        // Execute the method
        ActionListener<Map<String, Object>> resultListener = mock(ActionListener.class);
        metricsHelper.processPairwiseMetrics(queryText, searchConfigurations, size, resultListener);

        // Verify that empty pipeline is handled correctly
        verify(client, times(1)).search(any(SearchRequest.class), any(ActionListener.class));

        SearchRequest capturedRequest = requestCaptor.getValue();
        assertEquals("index1", capturedRequest.indices()[0]);
        assertNull(capturedRequest.pipeline());
    }

    public void testProcessEvaluationMetricsWithPipeline() {
        // Prepare test data
        String queryText = "test query";
        int size = 10;
        List<String> judgmentIds = Arrays.asList("judgment1");

        Map<String, List<String>> indexAndQueries = new HashMap<>();
        indexAndQueries.put("config1", Arrays.asList("index1", "{\"query\":{\"match\":{\"title\":\"%SearchText%\"}}}", "pipeline1"));

        // Mock judgment response
        SearchResponse judgmentResponse = createMockJudgmentResponse(queryText);
        doAnswer(invocation -> {
            ActionListener<SearchResponse> listener = invocation.getArgument(1);
            listener.onResponse(judgmentResponse);
            return null;
        }).when(judgmentDao).getJudgment(any(String.class), any(ActionListener.class));

        // Mock search response
        SearchResponse mockResponse = createMockSearchResponse("doc1", "doc2");

        // Capture the search request
        ArgumentCaptor<SearchRequest> requestCaptor = ArgumentCaptor.forClass(SearchRequest.class);

        doAnswer(invocation -> {
            ActionListener<SearchResponse> listener = invocation.getArgument(1);
            listener.onResponse(mockResponse);
            return null;
        }).when(client).search(requestCaptor.capture(), any(ActionListener.class));

        // Mock evaluation result dao
        doAnswer(invocation -> {
            ActionListener<Void> listener = invocation.getArgument(1);
            listener.onResponse(null);
            return null;
        }).when(evaluationResultDao).putEvaluationResult(any(), any(ActionListener.class));

        // Execute the method
        ActionListener<Map<String, Object>> resultListener = mock(ActionListener.class);
        metricsHelper.processEvaluationMetrics(queryText, indexAndQueries, size, judgmentIds, resultListener, null);

        // Verify pipeline is passed correctly
        verify(client, times(1)).search(
            argThat(request -> "index1".equals(request.indices()[0]) && "pipeline1".equals(request.pipeline())),
            any(ActionListener.class)
        );
    }

    public void testProcessEvaluationMetricsWithEmptyPipeline() {
        // Prepare test data with empty pipeline
        String queryText = "test query";
        int size = 10;
        List<String> judgmentIds = Arrays.asList("judgment1");

        Map<String, List<String>> indexAndQueries = new HashMap<>();
        indexAndQueries.put("config1", Arrays.asList("index1", "{\"query\":{\"match\":{\"title\":\"%SearchText%\"}}}", ""));

        // Mock judgment response
        SearchResponse judgmentResponse = createMockJudgmentResponse(queryText);
        doAnswer(invocation -> {
            ActionListener<SearchResponse> listener = invocation.getArgument(1);
            listener.onResponse(judgmentResponse);
            return null;
        }).when(judgmentDao).getJudgment(any(String.class), any(ActionListener.class));

        // Mock search response
        SearchResponse mockResponse = createMockSearchResponse("doc1", "doc2");

        // Capture the search request
        ArgumentCaptor<SearchRequest> requestCaptor = ArgumentCaptor.forClass(SearchRequest.class);

        doAnswer(invocation -> {
            ActionListener<SearchResponse> listener = invocation.getArgument(1);
            listener.onResponse(mockResponse);
            return null;
        }).when(client).search(requestCaptor.capture(), any(ActionListener.class));

        // Mock evaluation result dao
        doAnswer(invocation -> {
            ActionListener<Void> listener = invocation.getArgument(1);
            listener.onResponse(null);
            return null;
        }).when(evaluationResultDao).putEvaluationResult(any(), any(ActionListener.class));

        // Execute the method
        ActionListener<Map<String, Object>> resultListener = mock(ActionListener.class);
        metricsHelper.processEvaluationMetrics(queryText, indexAndQueries, size, judgmentIds, resultListener, null);

        // Verify empty pipeline is handled correctly
        verify(client, times(1)).search(
            argThat(request -> "index1".equals(request.indices()[0]) && request.pipeline() == null),
            any(ActionListener.class)
        );
    }

    private SearchResponse createMockSearchResponse(String... docIds) {
        SearchResponse response = mock(SearchResponse.class);

        SearchHit[] searchHits = new SearchHit[docIds.length];
        for (int i = 0; i < docIds.length; i++) {
            searchHits[i] = new SearchHit(i + 1, docIds[i], Map.of(), Map.of());
            searchHits[i].sourceRef(new BytesArray("{}"));
        }

        SearchHits hits = new SearchHits(searchHits, new TotalHits(docIds.length, TotalHits.Relation.EQUAL_TO), 1.0f);

        when(response.getHits()).thenReturn(hits);
        return response;
    }

    private SearchResponse createMockJudgmentResponse(String queryText) {
        SearchResponse response = mock(SearchResponse.class);

        Map<String, Object> sourceMap = new HashMap<>();
        List<Map<String, Object>> judgmentRatings = Arrays.asList(
            Map.of(
                "query",
                queryText,
                "ratings",
                Arrays.asList(Map.of("docId", "doc1", "rating", "5"), Map.of("docId", "doc2", "rating", "3"))
            )
        );
        sourceMap.put("judgmentRatings", judgmentRatings);

        SearchHit hit = new SearchHit(1, "judgment1", Map.of(), Map.of());
        try {
            BytesReference sourceBytes = BytesReference.bytes(XContentFactory.jsonBuilder().map(sourceMap));
            hit.sourceRef(sourceBytes);
        } catch (Exception e) {
            throw new RuntimeException("Failed to create judgment response", e);
        }

        SearchHits hits = new SearchHits(new SearchHit[] { hit }, new TotalHits(1, TotalHits.Relation.EQUAL_TO), 1.0f);

        when(response.getHits()).thenReturn(hits);
        return response;
    }
}
