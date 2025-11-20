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
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import org.mockito.ArgumentCaptor;
import org.opensearch.action.search.SearchResponse;
import org.opensearch.common.xcontent.json.JsonXContent;
import org.opensearch.core.action.ActionListener;
import org.opensearch.core.rest.RestStatus;
import org.opensearch.core.xcontent.NamedXContentRegistry;
import org.opensearch.rest.BytesRestResponse;
import org.opensearch.rest.RestRequest;
import org.opensearch.searchrelevance.plugin.SearchRelevanceRestTestCase;
import org.opensearch.searchrelevance.transport.OpenSearchDocRequest;
import org.opensearch.searchrelevance.transport.scheduledJob.GetScheduledExperimentAction;
import org.opensearch.test.rest.FakeRestRequest;

public class RestGetScheduledExperimentActionTests extends SearchRelevanceRestTestCase {

    private RestGetScheduledExperimentAction restGetScheduledExperimentAction;

    @Override
    public void setUp() throws Exception {
        super.setUp();
        restGetScheduledExperimentAction = new RestGetScheduledExperimentAction(settingsAccessor);
        // Prepare some default settings
        when(settingsAccessor.isScheduledExperimentsEnabled()).thenReturn(true);
        // Setup channel mock
        when(channel.newBuilder()).thenReturn(JsonXContent.contentBuilder());
        when(channel.newErrorBuilder()).thenReturn(JsonXContent.contentBuilder());
    }

    protected RestRequest createScheduledGetRestRequestWithParams(String documentId, Map<String, String> additionalParams) {
        Map<String, String> params = new HashMap<>(additionalParams);
        if (documentId == null) {
            return new FakeRestRequest.Builder(NamedXContentRegistry.EMPTY).withParams(params)
                .withMethod(RestRequest.Method.GET)
                .withPath("/_plugins/_search_relevance/experiment/schedule")
                .build();
        }
        return new FakeRestRequest.Builder(NamedXContentRegistry.EMPTY).withParams(params)
            .withMethod(RestRequest.Method.GET)
            .withPath("/_plugins/_search_relevance/experiment/" + documentId + "/schedule")
            .build();
    }

    public void testPrepareRequest_WorkbenchDisabled() throws Exception {
        // Setup
        when(settingsAccessor.isWorkbenchEnabled()).thenReturn(false);
        RestRequest request = createScheduledGetRestRequestWithParams(null, new HashMap<>());
        when(channel.request()).thenReturn(request);

        // Execute
        restGetScheduledExperimentAction.handleRequest(request, channel, client);

        // Verify
        ArgumentCaptor<BytesRestResponse> responseCaptor = ArgumentCaptor.forClass(BytesRestResponse.class);
        verify(channel).sendResponse(responseCaptor.capture());
        assertEquals(RestStatus.FORBIDDEN, responseCaptor.getValue().status());
    }

    public void testPrepareRequest_JobSchedulerDisabled() throws Exception {
        // Setup
        when(settingsAccessor.isWorkbenchEnabled()).thenReturn(true);
        when(settingsAccessor.isScheduledExperimentsEnabled()).thenReturn(false);
        RestRequest request = createScheduledGetRestRequestWithParams(null, new HashMap<>());
        when(channel.request()).thenReturn(request);

        // Execute
        restGetScheduledExperimentAction.handleRequest(request, channel, client);

        // Verify
        ArgumentCaptor<BytesRestResponse> responseCaptor = ArgumentCaptor.forClass(BytesRestResponse.class);
        verify(channel).sendResponse(responseCaptor.capture());
        assertEquals(RestStatus.FORBIDDEN, responseCaptor.getValue().status());
    }

    public void testGetSpecificScheduledExperiment_Success() throws Exception {
        // Setup
        when(settingsAccessor.isWorkbenchEnabled()).thenReturn(true);
        RestRequest request = createScheduledGetRestRequestWithParams("test_scheduledExperimentId", new HashMap<>());
        when(channel.request()).thenReturn(request);

        // Mock search response
        SearchResponse mockResponse = mock(SearchResponse.class);
        when(mockResponse.status()).thenReturn(RestStatus.OK);

        doAnswer(invocation -> {
            ActionListener<SearchResponse> listener = invocation.getArgument(2);
            listener.onResponse(mockResponse);
            return null;
        }).when(client).execute(eq(GetScheduledExperimentAction.INSTANCE), any(OpenSearchDocRequest.class), any());

        // Execute
        restGetScheduledExperimentAction.handleRequest(request, channel, client);

        // Verify
        verify(client).execute(eq(GetScheduledExperimentAction.INSTANCE), any(OpenSearchDocRequest.class), any());
        verify(channel).sendResponse(any(BytesRestResponse.class));
    }

    public void testListScheduledExperiments_Success() throws Exception {
        // Setup
        when(settingsAccessor.isWorkbenchEnabled()).thenReturn(true);
        RestRequest request = createScheduledGetRestRequestWithParams(null, new HashMap<>());
        when(channel.request()).thenReturn(request);

        // Mock search response
        SearchResponse mockResponse = mock(SearchResponse.class);
        when(mockResponse.status()).thenReturn(RestStatus.OK);

        doAnswer(invocation -> {
            ActionListener<SearchResponse> listener = invocation.getArgument(2);
            listener.onResponse(mockResponse);
            return null;
        }).when(client).execute(eq(GetScheduledExperimentAction.INSTANCE), any(OpenSearchDocRequest.class), any());

        // Execute
        restGetScheduledExperimentAction.handleRequest(request, channel, client);

        // Verify
        verify(client).execute(eq(GetScheduledExperimentAction.INSTANCE), any(OpenSearchDocRequest.class), any());
        verify(channel).sendResponse(any(BytesRestResponse.class));
    }

    public void testScheduledExperiment_Failure() throws Exception {
        // Setup
        when(settingsAccessor.isWorkbenchEnabled()).thenReturn(true);
        RestRequest request = createScheduledGetRestRequestWithParams(null, new HashMap<>());
        when(channel.request()).thenReturn(request);

        doAnswer(invocation -> {
            ActionListener<SearchResponse> listener = invocation.getArgument(2);
            listener.onFailure(new IOException("Test exception"));
            return null;
        }).when(client).execute(eq(GetScheduledExperimentAction.INSTANCE), any(OpenSearchDocRequest.class), any());

        // Execute
        restGetScheduledExperimentAction.handleRequest(request, channel, client);

        // Verify
        ArgumentCaptor<BytesRestResponse> responseCaptor = ArgumentCaptor.forClass(BytesRestResponse.class);
        verify(channel).sendResponse(responseCaptor.capture());
        assertEquals(RestStatus.INTERNAL_SERVER_ERROR, responseCaptor.getValue().status());
    }

}
