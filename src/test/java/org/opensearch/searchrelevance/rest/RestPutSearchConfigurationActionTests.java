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
import org.opensearch.searchrelevance.transport.searchConfiguration.PutSearchConfigurationAction;
import org.opensearch.searchrelevance.transport.searchConfiguration.PutSearchConfigurationRequest;

public class RestPutSearchConfigurationActionTests extends SearchRelevanceRestTestCase {

    private RestPutSearchConfigurationAction restPutSearchConfigurationAction;
    private static final String FULL_TEST_CONTENT = "{"
        + "\"name\": \"test_name\","
        + "\"index\": \"test_index\","
        + "\"query\": \"{\\\"match_all\\\": {}}\","
        + "\"searchPipeline\": \"test_pipeline\""
        + "}";

    private static final String MINIMAL_TEST_CONTENT = "{"
        + "\"name\": \"test_name\","
        + "\"index\": \"test_index\","
        + "\"query\": \"{\\\"match_all\\\": {}}\""
        + "}";

    private static final String INVALID_NAME_TEST_CONTENT = "{"
        + "\"name\": \"test_name_that_is_way_too_long_and_exceeds_the_fifty_character_limit\","
        + "\"index\": \"test_index\","
        + "\"query\": \"{\\\"match_all\\\": {}}\""
        + "}";

    @Override
    public void setUp() throws Exception {
        super.setUp();
        restPutSearchConfigurationAction = new RestPutSearchConfigurationAction(settingsAccessor);
        // Setup channel mock
        when(channel.newBuilder()).thenReturn(JsonXContent.contentBuilder());
        when(channel.newErrorBuilder()).thenReturn(JsonXContent.contentBuilder());
    }

    public void testPrepareRequest_WorkbenchDisabled() throws Exception {
        // Setup
        when(settingsAccessor.isWorkbenchEnabled()).thenReturn(false);
        RestRequest request = createPutRestRequestWithContent(FULL_TEST_CONTENT, "search_configurations");
        when(channel.request()).thenReturn(request);

        // Execute
        restPutSearchConfigurationAction.handleRequest(request, channel, client);

        // Verify
        ArgumentCaptor<BytesRestResponse> responseCaptor = ArgumentCaptor.forClass(BytesRestResponse.class);
        verify(channel).sendResponse(responseCaptor.capture());
        assertEquals(RestStatus.FORBIDDEN, responseCaptor.getValue().status());
    }

    public void testPutSearchConfiguration_Success() throws Exception {
        // Setup
        when(settingsAccessor.isWorkbenchEnabled()).thenReturn(true);
        RestRequest request = createPutRestRequestWithContent(FULL_TEST_CONTENT, "search_configurations");
        when(channel.request()).thenReturn(request);

        // Mock index response
        IndexResponse mockIndexResponse = mock(IndexResponse.class);
        when(mockIndexResponse.getId()).thenReturn("test_id");

        doAnswer(invocation -> {
            ActionListener<IndexResponse> listener = invocation.getArgument(2);
            listener.onResponse(mockIndexResponse);
            return null;
        }).when(client).execute(eq(PutSearchConfigurationAction.INSTANCE), any(PutSearchConfigurationRequest.class), any());

        // Execute
        restPutSearchConfigurationAction.handleRequest(request, channel, client);

        // Verify
        verify(client).execute(eq(PutSearchConfigurationAction.INSTANCE), any(PutSearchConfigurationRequest.class), any());
        verify(channel).sendResponse(any(BytesRestResponse.class));
    }

    public void testPutSearchConfiguration_WithDefaultSearchPipeline() throws Exception {
        // Setup
        when(settingsAccessor.isWorkbenchEnabled()).thenReturn(true);
        RestRequest request = createPutRestRequestWithContent(MINIMAL_TEST_CONTENT, "search_configurations");
        when(channel.request()).thenReturn(request);

        ArgumentCaptor<PutSearchConfigurationRequest> requestCaptor = ArgumentCaptor.forClass(PutSearchConfigurationRequest.class);

        doAnswer(invocation -> {
            ActionListener<IndexResponse> listener = invocation.getArgument(2);
            IndexResponse response = mock(IndexResponse.class);
            listener.onResponse(response);
            return null;
        }).when(client).execute(eq(PutSearchConfigurationAction.INSTANCE), requestCaptor.capture(), any());

        // Execute
        restPutSearchConfigurationAction.handleRequest(request, channel, client);

        // Verify
        PutSearchConfigurationRequest capturedRequest = requestCaptor.getValue();
        assertEquals("", capturedRequest.getSearchPipeline());
    }

    public void testPutSearchConfiguration_Failure() throws Exception {
        // Setup
        when(settingsAccessor.isWorkbenchEnabled()).thenReturn(true);
        RestRequest request = createPutRestRequestWithContent(FULL_TEST_CONTENT, "search_configurations");
        when(channel.request()).thenReturn(request);

        doAnswer(invocation -> {
            ActionListener<IndexResponse> listener = invocation.getArgument(2);
            listener.onFailure(new IOException("Test exception"));
            return null;
        }).when(client).execute(eq(PutSearchConfigurationAction.INSTANCE), any(PutSearchConfigurationRequest.class), any());

        // Execute
        restPutSearchConfigurationAction.handleRequest(request, channel, client);

        // Verify
        ArgumentCaptor<BytesRestResponse> responseCaptor = ArgumentCaptor.forClass(BytesRestResponse.class);
        verify(channel).sendResponse(responseCaptor.capture());
        assertEquals(RestStatus.INTERNAL_SERVER_ERROR, responseCaptor.getValue().status());
    }

    public void testPutSearchConfiguration_InvalidName() throws Exception {
        // Setup
        when(settingsAccessor.isWorkbenchEnabled()).thenReturn(true);
        RestRequest request = createPutRestRequestWithContent(INVALID_NAME_TEST_CONTENT, "search_configurations");
        when(channel.request()).thenReturn(request);

        // Execute
        restPutSearchConfigurationAction.handleRequest(request, channel, client);

        // Verify
        ArgumentCaptor<BytesRestResponse> responseCaptor = ArgumentCaptor.forClass(BytesRestResponse.class);
        verify(channel).sendResponse(responseCaptor.capture());
        assertEquals(RestStatus.BAD_REQUEST, responseCaptor.getValue().status());
        String response = responseCaptor.getValue().content().utf8ToString();
        assertTrue("Response should contain 'Invalid name': " + response, response.contains("Invalid name"));
        assertTrue("Response should contain 'exceeds maximum length': " + response, response.contains("exceeds maximum length"));
    }
}
