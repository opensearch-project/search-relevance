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

import org.mockito.ArgumentCaptor;
import org.opensearch.action.search.SearchResponse;
import org.opensearch.common.xcontent.json.JsonXContent;
import org.opensearch.core.action.ActionListener;
import org.opensearch.core.rest.RestStatus;
import org.opensearch.rest.BytesRestResponse;
import org.opensearch.rest.RestRequest;
import org.opensearch.searchrelevance.plugin.SearchRelevanceRestTestCase;
import org.opensearch.searchrelevance.transport.OpenSearchDocRequest;
import org.opensearch.searchrelevance.transport.searchConfiguration.GetSearchConfigurationAction;

public class RestGetSearchConfigurationActionTests extends SearchRelevanceRestTestCase {

    private RestGetSearchConfigurationAction restGetSearchConfigurationAction;

    @Override
    public void setUp() throws Exception {
        super.setUp();
        restGetSearchConfigurationAction = new RestGetSearchConfigurationAction(settingsAccessor);
        // Setup channel mock
        when(channel.newBuilder()).thenReturn(JsonXContent.contentBuilder());
        when(channel.newErrorBuilder()).thenReturn(JsonXContent.contentBuilder());
    }

    public void testPrepareRequest_WorkbenchDisabled() throws Exception {
        // Setup
        when(settingsAccessor.isWorkbenchEnabled()).thenReturn(false);
        RestRequest request = createGetRestRequestWithParams("search_configurations", null, new HashMap<>());
        when(channel.request()).thenReturn(request);

        // Execute
        restGetSearchConfigurationAction.handleRequest(request, channel, client);

        // Verify
        ArgumentCaptor<BytesRestResponse> responseCaptor = ArgumentCaptor.forClass(BytesRestResponse.class);
        verify(channel).sendResponse(responseCaptor.capture());
        assertEquals(RestStatus.FORBIDDEN, responseCaptor.getValue().status());
    }

    public void testGetSpecificSearchConfiguration_Success() throws Exception {
        // Setup
        when(settingsAccessor.isWorkbenchEnabled()).thenReturn(true);
        RestRequest request = createGetRestRequestWithParams("search_configurations", "test_config_id", new HashMap<>());
        when(channel.request()).thenReturn(request);

        // Mock search response
        SearchResponse mockResponse = mock(SearchResponse.class);
        when(mockResponse.status()).thenReturn(RestStatus.OK);

        doAnswer(invocation -> {
            ActionListener<SearchResponse> listener = invocation.getArgument(2);
            listener.onResponse(mockResponse);
            return null;
        }).when(client).execute(eq(GetSearchConfigurationAction.INSTANCE), any(OpenSearchDocRequest.class), any());

        // Execute
        restGetSearchConfigurationAction.handleRequest(request, channel, client);

        // Verify
        verify(client).execute(eq(GetSearchConfigurationAction.INSTANCE), any(OpenSearchDocRequest.class), any());
        verify(channel).sendResponse(any(BytesRestResponse.class));
    }

    public void testListSearchConfigurations_Success() throws Exception {
        // Setup
        when(settingsAccessor.isWorkbenchEnabled()).thenReturn(true);
        RestRequest request = createGetRestRequestWithParams("search_configurations", null, new HashMap<>());
        when(channel.request()).thenReturn(request);

        // Mock search response
        SearchResponse mockResponse = mock(SearchResponse.class);
        when(mockResponse.status()).thenReturn(RestStatus.OK);

        doAnswer(invocation -> {
            ActionListener<SearchResponse> listener = invocation.getArgument(2);
            listener.onResponse(mockResponse);
            return null;
        }).when(client).execute(eq(GetSearchConfigurationAction.INSTANCE), any(OpenSearchDocRequest.class), any());

        // Execute
        restGetSearchConfigurationAction.handleRequest(request, channel, client);

        // Verify
        verify(client).execute(eq(GetSearchConfigurationAction.INSTANCE), any(OpenSearchDocRequest.class), any());
        verify(channel).sendResponse(any(BytesRestResponse.class));
    }

    public void testSearchConfiguration_WithSearchParams() throws Exception {
        // Setup
        when(settingsAccessor.isWorkbenchEnabled()).thenReturn(true);
        RestRequest request = createGetRestRequestWithParams("search_configurations", null, new HashMap<>());
        when(channel.request()).thenReturn(request);

        ArgumentCaptor<OpenSearchDocRequest> requestCaptor = ArgumentCaptor.forClass(OpenSearchDocRequest.class);

        // Mock search response
        SearchResponse mockResponse = mock(SearchResponse.class);
        when(mockResponse.status()).thenReturn(RestStatus.OK);

        doAnswer(invocation -> {
            ActionListener<SearchResponse> listener = invocation.getArgument(2);
            listener.onResponse(mockResponse);
            return null;
        }).when(client).execute(eq(GetSearchConfigurationAction.INSTANCE), requestCaptor.capture(), any());

        // Execute
        restGetSearchConfigurationAction.handleRequest(request, channel, client);

        // Verify search parameters
        OpenSearchDocRequest capturedRequest = requestCaptor.getValue();
        assertNotNull(capturedRequest.getSearchSourceBuilder());
        assertEquals(1000, capturedRequest.getSearchSourceBuilder().size());
    }

    public void testSearchConfiguration_Failure() throws Exception {
        // Setup
        when(settingsAccessor.isWorkbenchEnabled()).thenReturn(true);
        RestRequest request = createGetRestRequestWithParams("search_configurations", null, new HashMap<>());
        when(channel.request()).thenReturn(request);

        doAnswer(invocation -> {
            ActionListener<SearchResponse> listener = invocation.getArgument(2);
            listener.onFailure(new IOException("Test exception"));
            return null;
        }).when(client).execute(eq(GetSearchConfigurationAction.INSTANCE), any(OpenSearchDocRequest.class), any());

        // Execute
        restGetSearchConfigurationAction.handleRequest(request, channel, client);

        // Verify
        ArgumentCaptor<BytesRestResponse> responseCaptor = ArgumentCaptor.forClass(BytesRestResponse.class);
        verify(channel).sendResponse(responseCaptor.capture());
        assertEquals(RestStatus.INTERNAL_SERVER_ERROR, responseCaptor.getValue().status());
    }
}
