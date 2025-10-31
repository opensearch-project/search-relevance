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

import org.mockito.ArgumentCaptor;
import org.opensearch.action.DocWriteResponse;
import org.opensearch.action.index.IndexResponse;
import org.opensearch.common.xcontent.json.JsonXContent;
import org.opensearch.core.action.ActionListener;
import org.opensearch.core.rest.RestStatus;
import org.opensearch.rest.BytesRestResponse;
import org.opensearch.rest.RestRequest;
import org.opensearch.searchrelevance.plugin.SearchRelevanceRestTestCase;
import org.opensearch.searchrelevance.transport.experiment.PostExperimentAction;
import org.opensearch.searchrelevance.transport.experiment.PostExperimentRequest;

public class RestPostExperimentActionTests extends SearchRelevanceRestTestCase {

    private RestPostExperimentAction restPostExperimentAction;
    private static final String VALID_EXPERIMENT_CONTENT = "{"
        + "\"type\": \"POINTWISE_EVALUATION\","
        + "\"querySetId\": \"test_query_set_id\","
        + "\"searchConfigurationList\": [\"abcd-searchConfiguration-defg\"],"
        + "\"judgmentList\": [\"hjkl-judgements-id-asdf\"],"
        + "\"evaluationResultList\": ["
        + "  {\"searchText\": \"test query\", \"metrics\": {\"dcg@10\": 0.8, \"ndcg@10\": 0.75}, \"judgmentIds\": [\"j1\", \"j2\"], \"documentIds\": [\"d1\", \"d2\"]}"
        + "]"
        + "}";

    private static final String INVALID_TYPE_CONTENT = "{"
        + "\"type\": \"INVALID_TYPE\","
        + "\"querySetId\": \"test_query_set_id\","
        + "\"searchConfigurationList\": [\"abcd-searchConfiguration-defg\"],"
        + "\"judgmentList\": [\"hjkl-judgements-id-asdf\"],"
        + "\"evaluationResultList\": ["
        + "  {\"searchText\": \"test query\", \"metrics\": {\"dcg@10\": 0.8, \"ndcg@10\": 0.75}, \"judgmentIds\": [\"j1\", \"j2\"], \"documentIds\": [\"d1\", \"d2\"]}"
        + "]"
        + "}";

    @Override
    public void setUp() throws Exception {
        super.setUp();
        restPostExperimentAction = new RestPostExperimentAction(settingsAccessor);
        // Setup channel mock
        when(channel.newBuilder()).thenReturn(JsonXContent.contentBuilder());
        when(channel.newErrorBuilder()).thenReturn(JsonXContent.contentBuilder());
    }

    public void testPrepareRequest_WorkbenchDisabled() throws Exception {
        // Setup
        when(settingsAccessor.isWorkbenchEnabled()).thenReturn(false);
        RestRequest request = createPostRestRequestWithContent(VALID_EXPERIMENT_CONTENT, "experiments");
        when(channel.request()).thenReturn(request);

        // Execute
        restPostExperimentAction.handleRequest(request, channel, client);

        // Verify
        ArgumentCaptor<BytesRestResponse> responseCaptor = ArgumentCaptor.forClass(BytesRestResponse.class);
        verify(channel).sendResponse(responseCaptor.capture());
        assertEquals(RestStatus.FORBIDDEN, responseCaptor.getValue().status());
    }

    public void testPostExperiment_Success() throws Exception {
        // Setup
        when(settingsAccessor.isWorkbenchEnabled()).thenReturn(true);
        RestRequest request = createPostRestRequestWithContent(VALID_EXPERIMENT_CONTENT, "experiments");
        when(channel.request()).thenReturn(request);

        // Mock index response
        IndexResponse mockIndexResponse = mock(IndexResponse.class);
        when(mockIndexResponse.getId()).thenReturn("test_id");
        when(mockIndexResponse.getResult()).thenReturn(DocWriteResponse.Result.CREATED);

        doAnswer(invocation -> {
            ActionListener<IndexResponse> listener = invocation.getArgument(2);
            listener.onResponse(mockIndexResponse);
            return null;
        }).when(client).execute(eq(PostExperimentAction.INSTANCE), any(PostExperimentRequest.class), any());

        // Execute
        restPostExperimentAction.handleRequest(request, channel, client);

        // Verify
        ArgumentCaptor<BytesRestResponse> responseCaptor = ArgumentCaptor.forClass(BytesRestResponse.class);
        verify(channel).sendResponse(responseCaptor.capture());
        assertEquals(RestStatus.OK, responseCaptor.getValue().status());
    }

    public void testPostExperiment_InvalidType() throws Exception {
        // Setup
        when(settingsAccessor.isWorkbenchEnabled()).thenReturn(true);
        RestRequest request = createPostRestRequestWithContent(INVALID_TYPE_CONTENT, "experiments");
        when(channel.request()).thenReturn(request);

        // Execute and verify
        IllegalArgumentException exception = expectThrows(
            IllegalArgumentException.class,
            () -> restPostExperimentAction.handleRequest(request, channel, client)
        );
        assertTrue(exception.getMessage().contains("Invalid or missing experiment type"));
    }

    public void testPostExperiment_Failure() throws Exception {
        // Setup
        when(settingsAccessor.isWorkbenchEnabled()).thenReturn(true);
        RestRequest request = createPostRestRequestWithContent(VALID_EXPERIMENT_CONTENT, "experiments");
        when(channel.request()).thenReturn(request);

        doAnswer(invocation -> {
            ActionListener<IndexResponse> listener = invocation.getArgument(2);
            listener.onFailure(new IOException("Test exception"));
            return null;
        }).when(client).execute(eq(PostExperimentAction.INSTANCE), any(PostExperimentRequest.class), any());

        // Execute
        restPostExperimentAction.handleRequest(request, channel, client);

        // Verify
        ArgumentCaptor<BytesRestResponse> responseCaptor = ArgumentCaptor.forClass(BytesRestResponse.class);
        verify(channel).sendResponse(responseCaptor.capture());
        assertEquals(RestStatus.INTERNAL_SERVER_ERROR, responseCaptor.getValue().status());
    }
}
