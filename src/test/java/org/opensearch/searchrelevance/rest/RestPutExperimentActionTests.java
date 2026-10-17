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
import org.opensearch.action.index.IndexResponse;
import org.opensearch.common.xcontent.json.JsonXContent;
import org.opensearch.core.action.ActionListener;
import org.opensearch.core.rest.RestStatus;
import org.opensearch.rest.BytesRestResponse;
import org.opensearch.rest.RestRequest;
import org.opensearch.searchrelevance.exception.SearchRelevanceException;
import org.opensearch.searchrelevance.plugin.SearchRelevanceRestTestCase;
import org.opensearch.searchrelevance.transport.experiment.PutExperimentAction;
import org.opensearch.searchrelevance.transport.experiment.PutExperimentRequest;

public class RestPutExperimentActionTests extends SearchRelevanceRestTestCase {

    private RestPutExperimentAction restPutExperimentAction;
    private static final String VALID_EXPERIMENT_CONTENT = "{"
        + "\"type\": \"POINTWISE_EVALUATION\","
        + "\"querySetId\": \"test_query_set_id\","
        + "\"searchConfigurationList\": [\"config1\"],"
        + "\"judgmentList\": [\"judgment1\", \"judgment2\"],"
        + "\"size\": 10"
        + "}";

    private static final String INVALID_TYPE_CONTENT = "{"
        + "\"type\": \"INVALID_TYPE\","
        + "\"querySetId\": \"test_query_set_id\","
        + "\"searchConfigurationList\": [\"config1\"],"
        + "\"judgmentList\": [\"judgment1\", \"judgment2\"],"
        + "\"size\": 10"
        + "}";

    private static final String INVALID_SEARCH_CONFIGURATION_CONTENT = "{"
        + "\"type\": \"POINTWISE_EVALUATION\","
        + "\"querySetId\": \"test_query_set_id\","
        + "\"searchConfigurationList\": [\"config1\", \"config2\"],"
        + "\"judgmentList\": [\"judgment1\", \"judgment2\"],"
        + "\"size\": 10"
        + "}";

    @Override
    public void setUp() throws Exception {
        super.setUp();
        restPutExperimentAction = new RestPutExperimentAction(settingsAccessor);
        // Setup channel mock
        when(channel.newBuilder()).thenReturn(JsonXContent.contentBuilder());
        when(channel.newErrorBuilder()).thenReturn(JsonXContent.contentBuilder());
    }

    public void testPrepareRequest_WorkbenchDisabled() throws Exception {
        // Setup
        when(settingsAccessor.isWorkbenchEnabled()).thenReturn(false);
        RestRequest request = createPutRestRequestWithContent(VALID_EXPERIMENT_CONTENT, "experiments");
        when(channel.request()).thenReturn(request);

        // Execute
        restPutExperimentAction.handleRequest(request, channel, client);

        // Verify
        ArgumentCaptor<BytesRestResponse> responseCaptor = ArgumentCaptor.forClass(BytesRestResponse.class);
        verify(channel).sendResponse(responseCaptor.capture());
        assertEquals(RestStatus.FORBIDDEN, responseCaptor.getValue().status());
    }

    public void testPutExperiment_Success() throws Exception {
        // Setup
        when(settingsAccessor.isWorkbenchEnabled()).thenReturn(true);
        when(settingsAccessor.getMaxQuerySetAllowed()).thenReturn(1000);
        RestRequest request = createPutRestRequestWithContent(VALID_EXPERIMENT_CONTENT, "experiments");
        when(channel.request()).thenReturn(request);

        // Mock index response
        IndexResponse mockIndexResponse = mock(IndexResponse.class);
        when(mockIndexResponse.getId()).thenReturn("test_id");
        when(mockIndexResponse.getResult()).thenReturn(DocWriteResponse.Result.CREATED);

        doAnswer(invocation -> {
            ActionListener<IndexResponse> listener = invocation.getArgument(2);
            listener.onResponse(mockIndexResponse);
            return null;
        }).when(client).execute(eq(PutExperimentAction.INSTANCE), any(PutExperimentRequest.class), any());

        // Execute
        restPutExperimentAction.handleRequest(request, channel, client);

        // Verify
        ArgumentCaptor<BytesRestResponse> responseCaptor = ArgumentCaptor.forClass(BytesRestResponse.class);
        verify(channel).sendResponse(responseCaptor.capture());
        assertEquals(RestStatus.OK, responseCaptor.getValue().status());
    }

    public void testPutExperiment_InvalidType() {
        // Setup
        when(settingsAccessor.isWorkbenchEnabled()).thenReturn(true);
        when(settingsAccessor.getMaxQuerySetAllowed()).thenReturn(1000);
        RestRequest request = createPutRestRequestWithContent(INVALID_TYPE_CONTENT, "experiments");
        when(channel.request()).thenReturn(request);

        // Execute and verify
        IllegalArgumentException exception = expectThrows(
            IllegalArgumentException.class,
            () -> restPutExperimentAction.handleRequest(request, channel, client)
        );
        assertTrue(exception.getMessage().contains("Invalid or missing experiment type"));
    }

    public void testPutExperiment_Failure() throws Exception {
        // Setup
        when(settingsAccessor.isWorkbenchEnabled()).thenReturn(true);
        when(settingsAccessor.getMaxQuerySetAllowed()).thenReturn(1000);
        RestRequest request = createPutRestRequestWithContent(VALID_EXPERIMENT_CONTENT, "experiments");
        when(channel.request()).thenReturn(request);

        doAnswer(invocation -> {
            ActionListener<IndexResponse> listener = invocation.getArgument(2);
            listener.onFailure(new IOException("Test exception"));
            return null;
        }).when(client).execute(eq(PutExperimentAction.INSTANCE), any(PutExperimentRequest.class), any());

        // Execute
        restPutExperimentAction.handleRequest(request, channel, client);

        // Verify
        ArgumentCaptor<BytesRestResponse> responseCaptor = ArgumentCaptor.forClass(BytesRestResponse.class);
        verify(channel).sendResponse(responseCaptor.capture());
        assertEquals(RestStatus.INTERNAL_SERVER_ERROR, responseCaptor.getValue().status());
    }

    public void testPutExperiment_InputValidationFailure() {
        // Setup
        when(settingsAccessor.isWorkbenchEnabled()).thenReturn(true);
        RestRequest request = createPutRestRequestWithContent(INVALID_SEARCH_CONFIGURATION_CONTENT, "experiments");
        when(channel.request()).thenReturn(request);

        SearchRelevanceException exception = expectThrows(
            SearchRelevanceException.class,
            () -> restPutExperimentAction.handleRequest(request, channel, client)
        );
        assertTrue(exception.getMessage().contains("POINTWISE_EVALUATION"));
    }
}
