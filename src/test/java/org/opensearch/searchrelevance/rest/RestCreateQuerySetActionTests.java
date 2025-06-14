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
import org.opensearch.action.index.IndexResponse;
import org.opensearch.common.xcontent.json.JsonXContent;
import org.opensearch.core.action.ActionListener;
import org.opensearch.core.rest.RestStatus;
import org.opensearch.rest.BytesRestResponse;
import org.opensearch.rest.RestRequest;
import org.opensearch.searchrelevance.plugin.SearchRelevanceRestTestCase;
import org.opensearch.searchrelevance.transport.queryset.PostQuerySetAction;
import org.opensearch.searchrelevance.transport.queryset.PostQuerySetRequest;

public class RestCreateQuerySetActionTests extends SearchRelevanceRestTestCase {

    private RestCreateQuerySetAction restCreateQuerySetAction;
    private static final String FULL_TEST_CONTENT = "{"
        + "\"name\": \"test_name\","
        + "\"description\": \"test_description\","
        + "\"sampling\": \"custom_sampling\","
        + "\"querySetSize\": 20"
        + "}";

    private static final String MINIMAL_TEST_CONTENT = "{" + "\"name\": \"test_name\"," + "\"description\": \"test_description\"" + "}";

    @Override
    public void setUp() throws Exception {
        super.setUp();
        restCreateQuerySetAction = new RestCreateQuerySetAction(settingsAccessor);
        // Setup channel mock
        when(channel.newBuilder()).thenReturn(JsonXContent.contentBuilder());
        when(channel.newErrorBuilder()).thenReturn(JsonXContent.contentBuilder());
    }

    public void testPrepareRequest_WorkbenchDisabled() throws Exception {
        // Setup
        when(settingsAccessor.isWorkbenchEnabled()).thenReturn(false);
        RestRequest request = createPutRestRequestWithContent(FULL_TEST_CONTENT, "query_sets");
        when(channel.request()).thenReturn(request);

        // Execute
        restCreateQuerySetAction.handleRequest(request, channel, client);

        // Verify
        ArgumentCaptor<BytesRestResponse> responseCaptor = ArgumentCaptor.forClass(BytesRestResponse.class);
        verify(channel).sendResponse(responseCaptor.capture());
        assertEquals(RestStatus.FORBIDDEN, responseCaptor.getValue().status());
    }

    public void testPrepareRequest_WorkbenchEnabled_Success() throws Exception {
        // Setup
        when(settingsAccessor.isWorkbenchEnabled()).thenReturn(true);
        when(settingsAccessor.getMaxQuerySetAllowed()).thenReturn(1000);
        RestRequest request = createPutRestRequestWithContent(FULL_TEST_CONTENT, "query_sets");
        when(channel.request()).thenReturn(request);

        // Mock index response
        IndexResponse mockIndexResponse = mock(IndexResponse.class);
        when(mockIndexResponse.getId()).thenReturn("test_id");

        doAnswer(invocation -> {
            ActionListener<IndexResponse> listener = invocation.getArgument(2);
            listener.onResponse(mockIndexResponse);
            return null;
        }).when(client).execute(eq(PostQuerySetAction.INSTANCE), any(PostQuerySetRequest.class), any());

        // Execute
        restCreateQuerySetAction.handleRequest(request, channel, client);

        // Verify
        verify(client).execute(eq(PostQuerySetAction.INSTANCE), any(PostQuerySetRequest.class), any());
        verify(channel).sendResponse(any(BytesRestResponse.class));
    }

    public void testPrepareRequest_WorkbenchEnabled_Failure() throws Exception {
        // Setup
        when(settingsAccessor.isWorkbenchEnabled()).thenReturn(true);
        when(settingsAccessor.getMaxQuerySetAllowed()).thenReturn(1000);
        RestRequest request = createPutRestRequestWithContent(FULL_TEST_CONTENT, "query_sets");
        when(channel.request()).thenReturn(request);

        doAnswer(invocation -> {
            ActionListener<IndexResponse> listener = invocation.getArgument(2);
            listener.onFailure(new IOException("Test exception"));
            return null;
        }).when(client).execute(eq(PostQuerySetAction.INSTANCE), any(PostQuerySetRequest.class), any());

        // Execute
        restCreateQuerySetAction.handleRequest(request, channel, client);

        // Verify
        ArgumentCaptor<BytesRestResponse> responseCaptor = ArgumentCaptor.forClass(BytesRestResponse.class);
        verify(channel).sendResponse(responseCaptor.capture());
        assertEquals(RestStatus.INTERNAL_SERVER_ERROR, responseCaptor.getValue().status());
    }

    public void testPrepareRequest_DefaultValues() throws Exception {
        // Setup
        when(settingsAccessor.isWorkbenchEnabled()).thenReturn(true);
        when(settingsAccessor.getMaxQuerySetAllowed()).thenReturn(1000);
        RestRequest request = createPutRestRequestWithContent(MINIMAL_TEST_CONTENT, "query_sets");
        when(channel.request()).thenReturn(request);

        ArgumentCaptor<PostQuerySetRequest> requestCaptor = ArgumentCaptor.forClass(PostQuerySetRequest.class);

        doAnswer(invocation -> {
            ActionListener<IndexResponse> listener = invocation.getArgument(2);
            IndexResponse response = mock(IndexResponse.class);
            listener.onResponse(response);
            return null;
        }).when(client).execute(eq(PostQuerySetAction.INSTANCE), requestCaptor.capture(), any());

        // Execute
        restCreateQuerySetAction.handleRequest(request, channel, client);

        // Verify default values
        PostQuerySetRequest capturedRequest = requestCaptor.getValue();
        assertEquals("pptss", capturedRequest.getSampling());
        assertEquals(10, capturedRequest.getQuerySetSize());
    }
}
