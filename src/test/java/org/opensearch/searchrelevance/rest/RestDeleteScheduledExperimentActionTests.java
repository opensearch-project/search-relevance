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
import static org.opensearch.searchrelevance.common.PluginConstants.DOCUMENT_ID;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import org.mockito.ArgumentCaptor;
import org.opensearch.action.DocWriteResponse;
import org.opensearch.action.delete.DeleteResponse;
import org.opensearch.common.xcontent.json.JsonXContent;
import org.opensearch.core.action.ActionListener;
import org.opensearch.core.rest.RestStatus;
import org.opensearch.core.xcontent.NamedXContentRegistry;
import org.opensearch.rest.BytesRestResponse;
import org.opensearch.rest.RestRequest;
import org.opensearch.searchrelevance.exception.SearchRelevanceException;
import org.opensearch.searchrelevance.plugin.SearchRelevanceRestTestCase;
import org.opensearch.searchrelevance.transport.OpenSearchDocRequest;
import org.opensearch.searchrelevance.transport.scheduledJob.DeleteScheduledExperimentAction;
import org.opensearch.test.rest.FakeRestRequest;

public class RestDeleteScheduledExperimentActionTests extends SearchRelevanceRestTestCase {

    private RestDeleteScheduledExperimentAction restDeleteScheduledExperimentAction;

    @Override
    public void setUp() throws Exception {
        super.setUp();
        restDeleteScheduledExperimentAction = new RestDeleteScheduledExperimentAction(settingsAccessor);
        // Prepare some default settings
        when(settingsAccessor.isScheduledExperimentsEnabled()).thenReturn(true);
        // Setup channel mock
        when(channel.newBuilder()).thenReturn(JsonXContent.contentBuilder());
        when(channel.newErrorBuilder()).thenReturn(JsonXContent.contentBuilder());
    }

    protected RestRequest createScheduledDeleteRestRequestWithParams(String documentId) {
        Map<String, String> params = new HashMap<>();
        params.put(DOCUMENT_ID, documentId);
        if (documentId == null) {
            return new FakeRestRequest.Builder(NamedXContentRegistry.EMPTY).withMethod(RestRequest.Method.DELETE)
                .withPath("/_plugins/_search_relevance/experiment/schedule")
                .build();
        }
        return new FakeRestRequest.Builder(NamedXContentRegistry.EMPTY).withMethod(RestRequest.Method.DELETE)
            .withPath("/_plugins/_search_relevance/experiment/" + documentId + "/schedule")
            .withParams(params)
            .build();
    }

    public void testPrepareRequest_WorkbenchDisabled() throws Exception {
        // Setup
        when(settingsAccessor.isWorkbenchEnabled()).thenReturn(false);
        RestRequest request = createScheduledDeleteRestRequestWithParams(null);
        when(channel.request()).thenReturn(request);

        // Execute
        restDeleteScheduledExperimentAction.handleRequest(request, channel, client);

        // Verify
        ArgumentCaptor<BytesRestResponse> responseCaptor = ArgumentCaptor.forClass(BytesRestResponse.class);
        verify(channel).sendResponse(responseCaptor.capture());
        assertEquals(RestStatus.FORBIDDEN, responseCaptor.getValue().status());
    }

    public void testPrepareRequest_JobSchedulerDisabled() throws Exception {
        // Setup
        when(settingsAccessor.isWorkbenchEnabled()).thenReturn(true);
        when(settingsAccessor.isScheduledExperimentsEnabled()).thenReturn(false);
        RestRequest request = createScheduledDeleteRestRequestWithParams(null);
        when(channel.request()).thenReturn(request);

        // Execute
        restDeleteScheduledExperimentAction.handleRequest(request, channel, client);

        // Verify
        ArgumentCaptor<BytesRestResponse> responseCaptor = ArgumentCaptor.forClass(BytesRestResponse.class);
        verify(channel).sendResponse(responseCaptor.capture());
        assertEquals(RestStatus.FORBIDDEN, responseCaptor.getValue().status());
    }

    public void testDeleteScheduledExperiment_Success() throws Exception {
        // Setup
        when(settingsAccessor.isWorkbenchEnabled()).thenReturn(true);
        RestRequest request = createScheduledDeleteRestRequestWithParams("test_id");
        when(channel.request()).thenReturn(request);

        DeleteResponse response = mock(DeleteResponse.class);
        when(response.getResult()).thenReturn(DocWriteResponse.Result.DELETED);

        doAnswer(invocation -> {
            ActionListener<DeleteResponse> listener = invocation.getArgument(2);
            listener.onResponse(response);
            return null;
        }).when(client).execute(eq(DeleteScheduledExperimentAction.INSTANCE), any(OpenSearchDocRequest.class), any());

        // Execute
        restDeleteScheduledExperimentAction.handleRequest(request, channel, client);

        // Verify
        ArgumentCaptor<BytesRestResponse> responseCaptor = ArgumentCaptor.forClass(BytesRestResponse.class);
        verify(channel).sendResponse(responseCaptor.capture());
        assertEquals(RestStatus.OK, responseCaptor.getValue().status());
    }

    public void testDeleteScheduledExperiment_NotFound() throws Exception {
        // Setup
        when(settingsAccessor.isWorkbenchEnabled()).thenReturn(true);
        RestRequest request = createScheduledDeleteRestRequestWithParams("test_id");
        when(channel.request()).thenReturn(request);

        DeleteResponse response = mock(DeleteResponse.class);
        when(response.getResult()).thenReturn(DocWriteResponse.Result.NOT_FOUND);

        doAnswer(invocation -> {
            ActionListener<DeleteResponse> listener = invocation.getArgument(2);
            listener.onResponse(response);
            return null;
        }).when(client).execute(eq(DeleteScheduledExperimentAction.INSTANCE), any(OpenSearchDocRequest.class), any());

        // Execute
        restDeleteScheduledExperimentAction.handleRequest(request, channel, client);

        // Verify
        ArgumentCaptor<BytesRestResponse> responseCaptor = ArgumentCaptor.forClass(BytesRestResponse.class);
        verify(channel).sendResponse(responseCaptor.capture());
        assertEquals(RestStatus.NOT_FOUND, responseCaptor.getValue().status());
    }

    public void testDeleteScheduledExperiment_Failure() throws Exception {
        // Setup
        when(settingsAccessor.isWorkbenchEnabled()).thenReturn(true);
        RestRequest request = createScheduledDeleteRestRequestWithParams("test_id");
        when(channel.request()).thenReturn(request);

        doAnswer(invocation -> {
            ActionListener<DeleteResponse> listener = invocation.getArgument(2);
            listener.onFailure(new IOException("Test exception"));
            return null;
        }).when(client).execute(eq(DeleteScheduledExperimentAction.INSTANCE), any(OpenSearchDocRequest.class), any());

        // Execute
        restDeleteScheduledExperimentAction.handleRequest(request, channel, client);

        // Verify
        ArgumentCaptor<BytesRestResponse> responseCaptor = ArgumentCaptor.forClass(BytesRestResponse.class);
        verify(channel).sendResponse(responseCaptor.capture());
        assertEquals(RestStatus.INTERNAL_SERVER_ERROR, responseCaptor.getValue().status());
    }

    public void testDeleteScheduledExperiment_NullId() {
        // Setup
        when(settingsAccessor.isWorkbenchEnabled()).thenReturn(true);

        // We expect a SearchRelevanceException when id is null
        SearchRelevanceException exception = expectThrows(SearchRelevanceException.class, () -> {
            RestRequest request = createScheduledDeleteRestRequestWithParams(null);
            restDeleteScheduledExperimentAction.handleRequest(request, channel, client);
        });

        // Verify the exception details
        assertEquals("id cannot be null", exception.getMessage());
        assertEquals(RestStatus.BAD_REQUEST, exception.status());
    }
}
