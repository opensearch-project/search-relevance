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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;

import org.mockito.ArgumentCaptor;
import org.opensearch.action.DocWriteResponse;
import org.opensearch.action.update.UpdateResponse;
import org.opensearch.common.xcontent.json.JsonXContent;
import org.opensearch.core.action.ActionListener;
import org.opensearch.core.index.Index;
import org.opensearch.core.index.shard.ShardId;
import org.opensearch.core.rest.RestStatus;
import org.opensearch.rest.BaseRestHandler;
import org.opensearch.rest.BytesRestResponse;
import org.opensearch.rest.RestRequest;
import org.opensearch.searchrelevance.plugin.SearchRelevanceRestTestCase;
import org.opensearch.searchrelevance.transport.experiment.PatchExperimentAction;
import org.opensearch.searchrelevance.transport.experiment.PatchExperimentRequest;

public class RestPatchExperimentActionTests extends SearchRelevanceRestTestCase {

    private RestPatchExperimentAction restPatchExperimentAction;

    private static final String VALID_PATCH_CONTENT_BOTH = "{\"name\": \"Updated Name\", \"description\": \"Updated Description\"}";
    private static final String VALID_PATCH_CONTENT_NAME_ONLY = "{\"name\": \"Updated Name\"}";
    private static final String VALID_PATCH_CONTENT_DESCRIPTION_ONLY = "{\"description\": \"Updated Description\"}";
    private static final String EMPTY_PATCH_CONTENT = "{}";
    private static final String INVALID_NAME_TOO_LONG = "{\"name\": \"" + "a".repeat(51) + "\"}";
    private static final String INVALID_NAME_WITH_QUOTES = "{\"name\": \"Name with \\\"quotes\\\"\"}";
    private static final String INVALID_DESCRIPTION_TOO_LONG = "{\"description\": \"" + "a".repeat(251) + "\"}";

    @Override
    public void setUp() throws Exception {
        super.setUp();
        restPatchExperimentAction = new RestPatchExperimentAction(settingsAccessor);
        when(channel.newBuilder()).thenReturn(JsonXContent.contentBuilder());
        when(channel.newErrorBuilder()).thenReturn(JsonXContent.contentBuilder());
    }

    public void testGetName() {
        assertEquals("patch_experiment_action", restPatchExperimentAction.getName());
    }

    public void testRoutes() {
        assertEquals(1, restPatchExperimentAction.routes().size());
        BaseRestHandler.Route route = restPatchExperimentAction.routes().get(0);
        assertEquals(RestRequest.Method.PATCH, route.getMethod());
        assertEquals("/_plugins/_search_relevance/experiments/{id}", route.getPath());
    }

    public void testPrepareRequest_WorkbenchDisabled() throws Exception {
        when(settingsAccessor.isWorkbenchEnabled()).thenReturn(false);
        RestRequest request = createPatchRestRequestWithContent(VALID_PATCH_CONTENT_BOTH, "experiments", "test-experiment-id");
        when(channel.request()).thenReturn(request);

        restPatchExperimentAction.handleRequest(request, channel, client);

        ArgumentCaptor<BytesRestResponse> responseCaptor = ArgumentCaptor.forClass(BytesRestResponse.class);
        verify(channel).sendResponse(responseCaptor.capture());
        assertEquals(RestStatus.FORBIDDEN, responseCaptor.getValue().status());
    }

    public void testPatchExperiment_MissingExperimentId() throws Exception {
        when(settingsAccessor.isWorkbenchEnabled()).thenReturn(true);
        RestRequest request = createPatchRestRequestWithContent(VALID_PATCH_CONTENT_BOTH, "experiments", "");
        when(channel.request()).thenReturn(request);

        restPatchExperimentAction.handleRequest(request, channel, client);

        ArgumentCaptor<BytesRestResponse> responseCaptor = ArgumentCaptor.forClass(BytesRestResponse.class);
        verify(channel).sendResponse(responseCaptor.capture());
        assertEquals(RestStatus.BAD_REQUEST, responseCaptor.getValue().status());
    }

    public void testPatchExperiment_EmptyRequest() throws Exception {
        when(settingsAccessor.isWorkbenchEnabled()).thenReturn(true);
        RestRequest request = createPatchRestRequestWithContent(EMPTY_PATCH_CONTENT, "experiments", "test-experiment-id");
        when(channel.request()).thenReturn(request);

        restPatchExperimentAction.handleRequest(request, channel, client);

        ArgumentCaptor<BytesRestResponse> responseCaptor = ArgumentCaptor.forClass(BytesRestResponse.class);
        verify(channel).sendResponse(responseCaptor.capture());
        assertEquals(RestStatus.BAD_REQUEST, responseCaptor.getValue().status());
        assertTrue(responseCaptor.getValue().content().utf8ToString().contains("At least one of"));
    }

    public void testPatchExperiment_SuccessWithBothFields() throws Exception {
        when(settingsAccessor.isWorkbenchEnabled()).thenReturn(true);
        RestRequest request = createPatchRestRequestWithContent(VALID_PATCH_CONTENT_BOTH, "experiments", "test-experiment-id");
        when(channel.request()).thenReturn(request);

        UpdateResponse mockUpdateResponse = createMockUpdateResponse("test-experiment-id", DocWriteResponse.Result.UPDATED);

        doAnswer(invocation -> {
            ActionListener<UpdateResponse> listener = invocation.getArgument(2);
            listener.onResponse(mockUpdateResponse);
            return null;
        }).when(client).execute(eq(PatchExperimentAction.INSTANCE), any(PatchExperimentRequest.class), any());

        restPatchExperimentAction.handleRequest(request, channel, client);

        ArgumentCaptor<BytesRestResponse> responseCaptor = ArgumentCaptor.forClass(BytesRestResponse.class);
        verify(channel).sendResponse(responseCaptor.capture());
        assertEquals(RestStatus.OK, responseCaptor.getValue().status());
    }

    public void testPatchExperiment_SuccessWithNameOnly() throws Exception {
        when(settingsAccessor.isWorkbenchEnabled()).thenReturn(true);
        RestRequest request = createPatchRestRequestWithContent(VALID_PATCH_CONTENT_NAME_ONLY, "experiments", "test-experiment-id");
        when(channel.request()).thenReturn(request);

        UpdateResponse mockUpdateResponse = createMockUpdateResponse("test-experiment-id", DocWriteResponse.Result.UPDATED);

        doAnswer(invocation -> {
            ActionListener<UpdateResponse> listener = invocation.getArgument(2);
            listener.onResponse(mockUpdateResponse);
            return null;
        }).when(client).execute(eq(PatchExperimentAction.INSTANCE), any(PatchExperimentRequest.class), any());

        restPatchExperimentAction.handleRequest(request, channel, client);

        ArgumentCaptor<BytesRestResponse> responseCaptor = ArgumentCaptor.forClass(BytesRestResponse.class);
        verify(channel).sendResponse(responseCaptor.capture());
        assertEquals(RestStatus.OK, responseCaptor.getValue().status());
    }

    public void testPatchExperiment_SuccessWithDescriptionOnly() throws Exception {
        when(settingsAccessor.isWorkbenchEnabled()).thenReturn(true);
        RestRequest request = createPatchRestRequestWithContent(VALID_PATCH_CONTENT_DESCRIPTION_ONLY, "experiments", "test-experiment-id");
        when(channel.request()).thenReturn(request);

        UpdateResponse mockUpdateResponse = createMockUpdateResponse("test-experiment-id", DocWriteResponse.Result.UPDATED);

        doAnswer(invocation -> {
            ActionListener<UpdateResponse> listener = invocation.getArgument(2);
            listener.onResponse(mockUpdateResponse);
            return null;
        }).when(client).execute(eq(PatchExperimentAction.INSTANCE), any(PatchExperimentRequest.class), any());

        restPatchExperimentAction.handleRequest(request, channel, client);

        ArgumentCaptor<BytesRestResponse> responseCaptor = ArgumentCaptor.forClass(BytesRestResponse.class);
        verify(channel).sendResponse(responseCaptor.capture());
        assertEquals(RestStatus.OK, responseCaptor.getValue().status());
    }

    public void testPatchExperiment_InvalidNameTooLong() throws Exception {
        when(settingsAccessor.isWorkbenchEnabled()).thenReturn(true);
        RestRequest request = createPatchRestRequestWithContent(INVALID_NAME_TOO_LONG, "experiments", "test-experiment-id");
        when(channel.request()).thenReturn(request);

        restPatchExperimentAction.handleRequest(request, channel, client);

        ArgumentCaptor<BytesRestResponse> responseCaptor = ArgumentCaptor.forClass(BytesRestResponse.class);
        verify(channel).sendResponse(responseCaptor.capture());
        assertEquals(RestStatus.BAD_REQUEST, responseCaptor.getValue().status());
        assertTrue(responseCaptor.getValue().content().utf8ToString().contains("Invalid name"));
    }

    public void testPatchExperiment_InvalidNameWithQuotes() throws Exception {
        when(settingsAccessor.isWorkbenchEnabled()).thenReturn(true);
        RestRequest request = createPatchRestRequestWithContent(INVALID_NAME_WITH_QUOTES, "experiments", "test-experiment-id");
        when(channel.request()).thenReturn(request);

        restPatchExperimentAction.handleRequest(request, channel, client);

        ArgumentCaptor<BytesRestResponse> responseCaptor = ArgumentCaptor.forClass(BytesRestResponse.class);
        verify(channel).sendResponse(responseCaptor.capture());
        assertEquals(RestStatus.BAD_REQUEST, responseCaptor.getValue().status());
        assertTrue(responseCaptor.getValue().content().utf8ToString().contains("Invalid name"));
    }

    public void testPatchExperiment_InvalidDescriptionTooLong() throws Exception {
        when(settingsAccessor.isWorkbenchEnabled()).thenReturn(true);
        RestRequest request = createPatchRestRequestWithContent(INVALID_DESCRIPTION_TOO_LONG, "experiments", "test-experiment-id");
        when(channel.request()).thenReturn(request);

        restPatchExperimentAction.handleRequest(request, channel, client);

        ArgumentCaptor<BytesRestResponse> responseCaptor = ArgumentCaptor.forClass(BytesRestResponse.class);
        verify(channel).sendResponse(responseCaptor.capture());
        assertEquals(RestStatus.BAD_REQUEST, responseCaptor.getValue().status());
        assertTrue(responseCaptor.getValue().content().utf8ToString().contains("Invalid description"));
    }

    public void testPatchExperiment_Failure() throws Exception {
        when(settingsAccessor.isWorkbenchEnabled()).thenReturn(true);
        RestRequest request = createPatchRestRequestWithContent(VALID_PATCH_CONTENT_BOTH, "experiments", "test-experiment-id");
        when(channel.request()).thenReturn(request);

        doAnswer(invocation -> {
            ActionListener<UpdateResponse> listener = invocation.getArgument(2);
            listener.onFailure(new IOException("Test exception"));
            return null;
        }).when(client).execute(eq(PatchExperimentAction.INSTANCE), any(PatchExperimentRequest.class), any());

        restPatchExperimentAction.handleRequest(request, channel, client);

        ArgumentCaptor<BytesRestResponse> responseCaptor = ArgumentCaptor.forClass(BytesRestResponse.class);
        verify(channel).sendResponse(responseCaptor.capture());
        assertEquals(RestStatus.INTERNAL_SERVER_ERROR, responseCaptor.getValue().status());
    }

    private UpdateResponse createMockUpdateResponse(String id, DocWriteResponse.Result result) {
        ShardId shardId = new ShardId(new Index("test-index", "test-uuid"), 0);
        return new UpdateResponse(shardId, id, 1, 1, 1, result);
    }
}
