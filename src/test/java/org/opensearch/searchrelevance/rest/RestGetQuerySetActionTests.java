/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */
package org.opensearch.searchrelevance.rest;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import org.mockito.ArgumentCaptor;
import org.opensearch.action.search.SearchResponse;
import org.opensearch.common.xcontent.json.JsonXContent;
import org.opensearch.core.action.ActionListener;
import org.opensearch.core.rest.RestStatus;
import org.opensearch.rest.BytesRestResponse;
import org.opensearch.rest.RestRequest;
import org.opensearch.searchrelevance.plugin.SearchRelevanceRestTestCase;
import org.opensearch.searchrelevance.transport.OpenSearchDocRequest;
import org.opensearch.searchrelevance.transport.queryset.GetQuerySetAction;

public class RestGetQuerySetActionTests extends SearchRelevanceRestTestCase {

    private RestGetQuerySetAction restGetQuerySetAction;

    @Override
    public void setUp() throws Exception {
        super.setUp();
        restGetQuerySetAction = new RestGetQuerySetAction(settingsAccessor);
        // Setup channel mock
        when(channel.newBuilder()).thenReturn(JsonXContent.contentBuilder());
        when(channel.newErrorBuilder()).thenReturn(JsonXContent.contentBuilder());
    }

    public void testPrepareRequest_WorkbenchDisabled() throws Exception {
        // Setup
        when(settingsAccessor.isWorkbenchEnabled()).thenReturn(false);
        RestRequest request = createGetRestRequestWithParams("query_sets", null, new HashMap<>());
        when(channel.request()).thenReturn(request);

        // Execute
        restGetQuerySetAction.handleRequest(request, channel, client);

        // Verify
        ArgumentCaptor<BytesRestResponse> responseCaptor = ArgumentCaptor.forClass(BytesRestResponse.class);
        verify(channel).sendResponse(responseCaptor.capture());
        assertEquals(RestStatus.FORBIDDEN, responseCaptor.getValue().status());
    }

    public void testGetSpecificQuerySet_Success() throws Exception {
        // Setup
        when(settingsAccessor.isWorkbenchEnabled()).thenReturn(true);
        RestRequest request = createGetRestRequestWithParams("query_sets", "test_querySetId", new HashMap<>());
        when(channel.request()).thenReturn(request);

        // Mock search response
        SearchResponse mockResponse = mock(SearchResponse.class);
        when(mockResponse.status()).thenReturn(RestStatus.OK);

        doAnswer(invocation -> {
            ActionListener<SearchResponse> listener = invocation.getArgument(2);
            listener.onResponse(mockResponse);
            return null;
        }).when(client).execute(eq(GetQuerySetAction.INSTANCE), any(OpenSearchDocRequest.class), any());

        // Execute
        restGetQuerySetAction.handleRequest(request, channel, client);

        // Verify
        verify(client).execute(eq(GetQuerySetAction.INSTANCE), any(OpenSearchDocRequest.class), any());
        verify(channel).sendResponse(any(BytesRestResponse.class));
    }

    public void testListQuerySets_Success() throws Exception {
        // Setup
        when(settingsAccessor.isWorkbenchEnabled()).thenReturn(true);
        RestRequest request = createGetRestRequestWithParams("query_sets", "test_querySetId", new HashMap<>());
        when(channel.request()).thenReturn(request);

        // Mock search response
        SearchResponse mockResponse = mock(SearchResponse.class);
        when(mockResponse.status()).thenReturn(RestStatus.OK);

        doAnswer(invocation -> {
            ActionListener<SearchResponse> listener = invocation.getArgument(2);
            listener.onResponse(mockResponse);
            return null;
        }).when(client).execute(eq(GetQuerySetAction.INSTANCE), any(OpenSearchDocRequest.class), any());

        // Execute
        restGetQuerySetAction.handleRequest(request, channel, client);

        // Verify
        verify(client).execute(eq(GetQuerySetAction.INSTANCE), any(OpenSearchDocRequest.class), any());
        verify(channel).sendResponse(any(BytesRestResponse.class));
    }

    public void testGetQuerySet_Failure() throws Exception {
        // Setup
        when(settingsAccessor.isWorkbenchEnabled()).thenReturn(true);
        RestRequest request = createGetRestRequestWithParams("query_sets", null, new HashMap<>());
        when(channel.request()).thenReturn(request);

        doAnswer(invocation -> {
            ActionListener<SearchResponse> listener = invocation.getArgument(2);
            listener.onFailure(new IOException("Test exception"));
            return null;
        }).when(client).execute(eq(GetQuerySetAction.INSTANCE), any(OpenSearchDocRequest.class), any());

        // Execute
        restGetQuerySetAction.handleRequest(request, channel, client);

        // Verify
        ArgumentCaptor<BytesRestResponse> responseCaptor = ArgumentCaptor.forClass(BytesRestResponse.class);
        verify(channel).sendResponse(responseCaptor.capture());
        assertEquals(RestStatus.INTERNAL_SERVER_ERROR, responseCaptor.getValue().status());
    }

    public void testSearchParams() throws Exception {
        // Setup
        when(settingsAccessor.isWorkbenchEnabled()).thenReturn(true);
        Map<String, String> params = new HashMap<>();

        RestRequest request = createGetRestRequestWithParams("query_sets", null, params);
        when(channel.request()).thenReturn(request);

        ArgumentCaptor<OpenSearchDocRequest> requestCaptor = ArgumentCaptor.forClass(OpenSearchDocRequest.class);

        // Mock search response
        SearchResponse mockResponse = mock(SearchResponse.class);
        when(mockResponse.status()).thenReturn(RestStatus.OK);

        doAnswer(invocation -> {
            ActionListener<SearchResponse> listener = invocation.getArgument(2);
            listener.onResponse(mockResponse);
            return null;
        }).when(client).execute(eq(GetQuerySetAction.INSTANCE), requestCaptor.capture(), any());

        // Execute
        restGetQuerySetAction.handleRequest(request, channel, client);

        // Verify
        OpenSearchDocRequest capturedRequest = requestCaptor.getValue();
        assertNotNull(capturedRequest.getSearchSourceBuilder());
        assertEquals(1000, capturedRequest.getSearchSourceBuilder().size());
    }
}
