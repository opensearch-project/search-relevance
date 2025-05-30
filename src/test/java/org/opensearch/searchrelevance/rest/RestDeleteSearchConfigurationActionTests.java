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

import org.mockito.ArgumentCaptor;
import org.opensearch.action.DocWriteResponse;
import org.opensearch.action.delete.DeleteResponse;
import org.opensearch.common.xcontent.json.JsonXContent;
import org.opensearch.core.action.ActionListener;
import org.opensearch.core.rest.RestStatus;
import org.opensearch.rest.BytesRestResponse;
import org.opensearch.rest.RestRequest;
import org.opensearch.searchrelevance.exception.SearchRelevanceException;
import org.opensearch.searchrelevance.plugin.SearchRelevanceRestTestCase;
import org.opensearch.searchrelevance.transport.OpenSearchDocRequest;
import org.opensearch.searchrelevance.transport.searchConfiguration.DeleteSearchConfigurationAction;

public class RestDeleteSearchConfigurationActionTests extends SearchRelevanceRestTestCase {

    private RestDeleteSearchConfigurationAction restDeleteSearchConfigurationAction;

    @Override
    public void setUp() throws Exception {
        super.setUp();
        restDeleteSearchConfigurationAction = new RestDeleteSearchConfigurationAction(settingsAccessor);
        // Setup channel mock
        when(channel.newBuilder()).thenReturn(JsonXContent.contentBuilder());
        when(channel.newErrorBuilder()).thenReturn(JsonXContent.contentBuilder());
    }

    public void testPrepareRequest_WorkbenchDisabled() throws Exception {
        // Setup
        when(settingsAccessor.isWorkbenchEnabled()).thenReturn(false);
        RestRequest request = createDeleteRestRequestWithPath("search_configurations", "test_id");
        when(channel.request()).thenReturn(request);

        // Execute
        restDeleteSearchConfigurationAction.handleRequest(request, channel, client);

        // Verify
        ArgumentCaptor<BytesRestResponse> responseCaptor = ArgumentCaptor.forClass(BytesRestResponse.class);
        verify(channel).sendResponse(responseCaptor.capture());
        assertEquals(RestStatus.FORBIDDEN, responseCaptor.getValue().status());
    }

    public void testDeleteSearchConfiguration_Success() throws Exception {
        // Setup
        when(settingsAccessor.isWorkbenchEnabled()).thenReturn(true);
        RestRequest request = createDeleteRestRequestWithParams("search_configurations", "test_id");
        when(channel.request()).thenReturn(request);

        DeleteResponse response = mock(DeleteResponse.class);
        when(response.getResult()).thenReturn(DocWriteResponse.Result.DELETED);

        doAnswer(invocation -> {
            ActionListener<DeleteResponse> listener = invocation.getArgument(2);
            listener.onResponse(response);
            return null;
        }).when(client).execute(eq(DeleteSearchConfigurationAction.INSTANCE), any(OpenSearchDocRequest.class), any());

        // Execute
        restDeleteSearchConfigurationAction.handleRequest(request, channel, client);

        // Verify
        ArgumentCaptor<BytesRestResponse> responseCaptor = ArgumentCaptor.forClass(BytesRestResponse.class);
        verify(channel).sendResponse(responseCaptor.capture());
        assertEquals(RestStatus.OK, responseCaptor.getValue().status());
    }

    public void testDeleteSearchConfiguration_NotFound() throws Exception {
        // Setup
        when(settingsAccessor.isWorkbenchEnabled()).thenReturn(true);
        RestRequest request = createDeleteRestRequestWithParams("search_configurations", "test_id");
        when(channel.request()).thenReturn(request);

        DeleteResponse response = mock(DeleteResponse.class);
        when(response.getResult()).thenReturn(DocWriteResponse.Result.NOT_FOUND);

        doAnswer(invocation -> {
            ActionListener<DeleteResponse> listener = invocation.getArgument(2);
            listener.onResponse(response);
            return null;
        }).when(client).execute(eq(DeleteSearchConfigurationAction.INSTANCE), any(OpenSearchDocRequest.class), any());

        // Execute
        restDeleteSearchConfigurationAction.handleRequest(request, channel, client);

        // Verify
        ArgumentCaptor<BytesRestResponse> responseCaptor = ArgumentCaptor.forClass(BytesRestResponse.class);
        verify(channel).sendResponse(responseCaptor.capture());
        assertEquals(RestStatus.NOT_FOUND, responseCaptor.getValue().status());
    }

    public void testDeleteSearchConfiguration_Failure() throws Exception {
        // Setup
        when(settingsAccessor.isWorkbenchEnabled()).thenReturn(true);
        RestRequest request = createDeleteRestRequestWithParams("search_configurations", "test_id");
        when(channel.request()).thenReturn(request);

        doAnswer(invocation -> {
            ActionListener<DeleteResponse> listener = invocation.getArgument(2);
            listener.onFailure(new IOException("Test exception"));
            return null;
        }).when(client).execute(eq(DeleteSearchConfigurationAction.INSTANCE), any(OpenSearchDocRequest.class), any());

        // Execute
        restDeleteSearchConfigurationAction.handleRequest(request, channel, client);

        // Verify
        ArgumentCaptor<BytesRestResponse> responseCaptor = ArgumentCaptor.forClass(BytesRestResponse.class);
        verify(channel).sendResponse(responseCaptor.capture());
        assertEquals(RestStatus.INTERNAL_SERVER_ERROR, responseCaptor.getValue().status());
    }

    public void testDeleteSearchConfiguration_NullId() {
        // Setup
        when(settingsAccessor.isWorkbenchEnabled()).thenReturn(true);

        // We expect a SearchRelevanceException when id is null
        SearchRelevanceException exception = expectThrows(SearchRelevanceException.class, () -> {
            RestRequest request = createDeleteRestRequestWithParams("search_configurations", null);
            restDeleteSearchConfigurationAction.handleRequest(request, channel, client);
        });

        // Verify the exception details
        assertEquals("id cannot be null", exception.getMessage());
        assertEquals(RestStatus.INTERNAL_SERVER_ERROR, exception.status());
    }
}
