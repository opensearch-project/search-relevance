/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */
package org.opensearch.searchrelevance.transport.abTest;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import org.apache.lucene.search.TotalHits;
import org.junit.Before;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.opensearch.action.search.SearchResponse;
import org.opensearch.action.support.ActionFilters;
import org.opensearch.core.action.ActionListener;
import org.opensearch.core.common.bytes.BytesReference;
import org.opensearch.core.rest.RestStatus;
import org.opensearch.search.SearchHit;
import org.opensearch.search.SearchHits;
import org.opensearch.searchrelevance.dao.ABTestDao;
import org.opensearch.searchrelevance.dao.SearchConfigurationDao;
import org.opensearch.searchrelevance.model.ABTest;
import org.opensearch.test.OpenSearchTestCase;
import org.opensearch.threadpool.ThreadPool;
import org.opensearch.transport.TransportService;
import org.opensearch.transport.client.Client;

public class ABTestSearchTransportActionTests extends OpenSearchTestCase {

    @Mock
    private TransportService transportService;
    @Mock
    private ActionFilters actionFilters;
    @Mock
    private ABTestDao abTestDao;
    @Mock
    private SearchConfigurationDao searchConfigurationDao;
    @Mock
    private Client client;
    @Mock
    private ThreadPool threadPool;

    private ABTestSearchTransportAction transportAction;

    @Before
    public void setup() {
        MockitoAnnotations.openMocks(this);
        transportAction = new ABTestSearchTransportAction(
            transportService,
            actionFilters,
            abTestDao,
            searchConfigurationDao,
            client,
            threadPool
        );
    }

    private SearchResponse createABTestResponse(boolean enabled) {
        Map<String, Object> source = new HashMap<>();
        source.put(ABTest.TEST_ID, "my-test");
        source.put(ABTest.SEARCH_CONFIGURATION_A, "config-a-id");
        source.put(ABTest.SEARCH_CONFIGURATION_B, "config-b-id");
        source.put(ABTest.CONFIG_A_UUID, "uuid-a");
        source.put(ABTest.CONFIG_B_UUID, "uuid-b");
        source.put(ABTest.ENABLED, enabled);

        SearchHit hit = new SearchHit(1, "my-test", Collections.emptyMap(), Collections.emptyMap());
        hit.sourceRef(BytesReference.bytes(createXContentFromMap(source)));
        SearchHits hits = new SearchHits(new SearchHit[] { hit }, new TotalHits(1, TotalHits.Relation.EQUAL_TO), 1.0f);
        SearchResponse response = mock(SearchResponse.class);
        org.mockito.Mockito.when(response.getHits()).thenReturn(hits);
        return response;
    }

    private SearchResponse createConfigResponse(String query, String index) {
        Map<String, Object> source = new HashMap<>();
        source.put("query", query);
        source.put("index", index);
        source.put("searchPipeline", "");

        SearchHit hit = new SearchHit(1, "config-id", Collections.emptyMap(), Collections.emptyMap());
        hit.sourceRef(BytesReference.bytes(createXContentFromMap(source)));
        SearchHits hits = new SearchHits(new SearchHit[] { hit }, new TotalHits(1, TotalHits.Relation.EQUAL_TO), 1.0f);
        SearchResponse response = mock(SearchResponse.class);
        org.mockito.Mockito.when(response.getHits()).thenReturn(hits);
        return response;
    }

    private org.opensearch.core.xcontent.XContentBuilder createXContentFromMap(Map<String, Object> source) {
        try {
            org.opensearch.core.xcontent.XContentBuilder builder = org.opensearch.common.xcontent.XContentFactory.jsonBuilder();
            builder.map(source);
            return builder;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Null request returns error
     */
    @SuppressWarnings("unchecked")
    public void testNullRequest() {
        ActionListener<ABTestSearchResponse> listener = mock(ActionListener.class);

        transportAction.doExecute(null, null, listener);

        ArgumentCaptor<Exception> errorCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(listener).onFailure(errorCaptor.capture());
        assertTrue(errorCaptor.getValue().getMessage().contains("cannot be null"));
    }

    /**
     * Empty params returns error
     */
    @SuppressWarnings("unchecked")
    public void testEmptyParams() {
        ABTestSearchRequest request = new ABTestSearchRequest("my-test", Collections.emptyMap());
        ActionListener<ABTestSearchResponse> listener = mock(ActionListener.class);

        transportAction.doExecute(null, request, listener);

        ArgumentCaptor<Exception> errorCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(listener).onFailure(errorCaptor.capture());
        assertTrue(errorCaptor.getValue().getMessage().contains("cannot be null"));
    }

    /**
     * Missing SearchText returns error
     */
    @SuppressWarnings("unchecked")
    public void testMissingSearchText() {
        Map<String, String> params = new HashMap<>();
        params.put("SomeOtherParam", "value");

        doAnswer(invocation -> {
            ActionListener<SearchResponse> listener = invocation.getArgument(1);
            listener.onResponse(createABTestResponse(true));
            return null;
        }).when(abTestDao).getABTest(any(String.class), any(ActionListener.class));

        doAnswer(invocation -> {
            ActionListener<SearchResponse> listener = invocation.getArgument(1);
            listener.onResponse(createConfigResponse("{\"query\":{\"match\":{\"title\":\"%SearchText%\"}}}", "prime_video"));
            return null;
        }).when(searchConfigurationDao).getSearchConfiguration(any(String.class), any(ActionListener.class));

        ABTestSearchRequest request = new ABTestSearchRequest("my-test", params);
        ActionListener<ABTestSearchResponse> listener = mock(ActionListener.class);

        transportAction.doExecute(null, request, listener);

        ArgumentCaptor<Exception> errorCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(listener).onFailure(errorCaptor.capture());
        assertTrue(errorCaptor.getValue().getMessage().contains("SearchText is required"));
    }

    /**
     * Test not found returns error
     */
    @SuppressWarnings("unchecked")
    public void testTestNotFound() {
        doAnswer(invocation -> {
            ActionListener<SearchResponse> listener = invocation.getArgument(1);
            listener.onFailure(new org.opensearch.ResourceNotFoundException("not found", RestStatus.NOT_FOUND));
            return null;
        }).when(abTestDao).getABTest(any(String.class), any(ActionListener.class));

        Map<String, String> params = new HashMap<>();
        params.put("SearchText", "spy thriller");

        ABTestSearchRequest request = new ABTestSearchRequest("non-existent", params);
        ActionListener<ABTestSearchResponse> listener = mock(ActionListener.class);

        transportAction.doExecute(null, request, listener);

        verify(listener).onFailure(any(Exception.class));
    }

    /**
     * Null testId returns error
     */
    @SuppressWarnings("unchecked")
    public void testNullTestId() {
        Map<String, String> params = new HashMap<>();
        params.put("SearchText", "spy");

        ABTestSearchRequest request = new ABTestSearchRequest(null, params);
        ActionListener<ABTestSearchResponse> listener = mock(ActionListener.class);

        transportAction.doExecute(null, request, listener);

        ArgumentCaptor<Exception> errorCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(listener).onFailure(errorCaptor.capture());
        assertTrue(errorCaptor.getValue().getMessage().contains("cannot be null"));
    }
}
