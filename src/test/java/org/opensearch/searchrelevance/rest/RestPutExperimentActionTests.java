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
import java.util.Map;

import org.mockito.ArgumentCaptor;
import org.opensearch.action.DocWriteResponse;
import org.opensearch.action.index.IndexResponse;
import org.opensearch.common.xcontent.json.JsonXContent;
import org.opensearch.core.action.ActionListener;
import org.opensearch.core.rest.RestStatus;
import org.opensearch.rest.BytesRestResponse;
import org.opensearch.rest.RestRequest;
import org.opensearch.searchrelevance.plugin.SearchRelevanceRestTestCase;
import org.opensearch.searchrelevance.transport.experiment.PutExperimentAction;
import org.opensearch.searchrelevance.transport.experiment.PutExperimentRequest;

public class RestPutExperimentActionTests extends SearchRelevanceRestTestCase {

    private RestPutExperimentAction restPutExperimentAction;
    private static final String VALID_EXPERIMENT_CONTENT = "{"
        + "\"type\": \"POINTWISE_EVALUATION\","
        + "\"querySetId\": \"test_query_set_id\","
        + "\"searchConfigurationList\": [\"config1\", \"config2\"],"
        + "\"judgmentList\": [\"judgment1\", \"judgment2\"],"
        + "\"size\": 10"
        + "}";

    private static final String INVALID_TYPE_CONTENT = "{"
        + "\"type\": \"INVALID_TYPE\","
        + "\"querySetId\": \"test_query_set_id\","
        + "\"searchConfigurationList\": [\"config1\", \"config2\"],"
        + "\"judgmentList\": [\"judgment1\", \"judgment2\"],"
        + "\"size\": 10"
        + "}";

    private static final String VALID_IMPORT_EXPERIMENT_CONTENT = "{"
        + "\"type\": \"POINTWISE_EVALUATION_IMPORT\","
        + "\"querySetId\": \"test_query_set_id\","
        + "\"searchConfigurationList\": [\"config1\", \"config2\"],"
        + "\"judgmentList\": [\"judgment1\", \"judgment2\"],"
        + "\"evaluationResultList\": ["
        + "  {\"searchText\": \"test query 1\", \"dcg@10\": 0.8, \"ndcg@10\": 0.75},"
        + "  {\"searchText\": \"test query 2\", \"metrics\": {\"dcg@10\": 0.9, \"ndcg@10\": 0.85}}"
        + "]"
        + "}";

    private static final String VALID_IMPORT_WITH_NESTED_METRICS_CONTENT = "{"
        + "\"type\": \"POINTWISE_EVALUATION_IMPORT\","
        + "\"querySetId\": \"test_query_set_id\","
        + "\"searchConfigurationList\": [\"config1\", \"config2\"],"
        + "\"judgmentList\": [\"judgment1\", \"judgment2\"],"
        + "\"evaluationResultList\": ["
        + "  {\"searchText\": \"test query\", \"metrics\": {\"dcg@10\": 0.8, \"ndcg@10\": 0.75}, \"judgmentIds\": [\"j1\", \"j2\"], \"documentIds\": [\"d1\", \"d2\"]}"
        + "]"
        + "}";

    private static final String IMPORT_MISSING_EVALUATION_RESULTS_CONTENT = "{"
        + "\"type\": \"POINTWISE_EVALUATION_IMPORT\","
        + "\"querySetId\": \"test_query_set_id\","
        + "\"searchConfigurationList\": [\"config1\", \"config2\"],"
        + "\"judgmentList\": [\"judgment1\", \"judgment2\"]"
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

    public void testPutExperiment_InvalidType() throws Exception {
        // Setup
        when(settingsAccessor.isWorkbenchEnabled()).thenReturn(true);
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

    public void testPutImportExperiment_Success() throws Exception {
        // Setup
        when(settingsAccessor.isWorkbenchEnabled()).thenReturn(true);
        RestRequest request = createPutRestRequestWithContent(VALID_IMPORT_EXPERIMENT_CONTENT, "experiments");
        when(channel.request()).thenReturn(request);

        // Mock index response
        IndexResponse mockIndexResponse = mock(IndexResponse.class);
        when(mockIndexResponse.getId()).thenReturn("test_import_id");
        when(mockIndexResponse.getResult()).thenReturn(DocWriteResponse.Result.CREATED);

        ArgumentCaptor<PutExperimentRequest> requestCaptor = ArgumentCaptor.forClass(PutExperimentRequest.class);
        doAnswer(invocation -> {
            ActionListener<IndexResponse> listener = invocation.getArgument(2);
            listener.onResponse(mockIndexResponse);
            return null;
        }).when(client).execute(eq(PutExperimentAction.INSTANCE), requestCaptor.capture(), any());

        // Execute
        restPutExperimentAction.handleRequest(request, channel, client);

        // Verify
        ArgumentCaptor<BytesRestResponse> responseCaptor = ArgumentCaptor.forClass(BytesRestResponse.class);
        verify(channel).sendResponse(responseCaptor.capture());
        assertEquals(RestStatus.OK, responseCaptor.getValue().status());

        // Verify the request was created correctly for import
        PutExperimentRequest capturedRequest = requestCaptor.getValue();
        assertNotNull(capturedRequest.getEvaluationResultList());
        assertEquals(2, capturedRequest.getEvaluationResultList().size());
        assertEquals("test query 1", capturedRequest.getEvaluationResultList().get(0).get("searchText"));
        assertEquals("test query 2", capturedRequest.getEvaluationResultList().get(1).get("searchText"));
    }

    public void testPutImportExperiment_WithNestedMetrics_Success() throws Exception {
        // Setup
        when(settingsAccessor.isWorkbenchEnabled()).thenReturn(true);
        RestRequest request = createPutRestRequestWithContent(VALID_IMPORT_WITH_NESTED_METRICS_CONTENT, "experiments");
        when(channel.request()).thenReturn(request);

        // Mock index response
        IndexResponse mockIndexResponse = mock(IndexResponse.class);
        when(mockIndexResponse.getId()).thenReturn("test_nested_id");
        when(mockIndexResponse.getResult()).thenReturn(DocWriteResponse.Result.CREATED);

        ArgumentCaptor<PutExperimentRequest> requestCaptor = ArgumentCaptor.forClass(PutExperimentRequest.class);
        doAnswer(invocation -> {
            ActionListener<IndexResponse> listener = invocation.getArgument(2);
            listener.onResponse(mockIndexResponse);
            return null;
        }).when(client).execute(eq(PutExperimentAction.INSTANCE), requestCaptor.capture(), any());

        // Execute
        restPutExperimentAction.handleRequest(request, channel, client);

        // Verify
        ArgumentCaptor<BytesRestResponse> responseCaptor = ArgumentCaptor.forClass(BytesRestResponse.class);
        verify(channel).sendResponse(responseCaptor.capture());
        assertEquals(RestStatus.OK, responseCaptor.getValue().status());

        // Verify the request handles nested metrics and additional fields
        PutExperimentRequest capturedRequest = requestCaptor.getValue();
        assertNotNull(capturedRequest.getEvaluationResultList());
        assertEquals(1, capturedRequest.getEvaluationResultList().size());

        Map<String, Object> result = capturedRequest.getEvaluationResultList().get(0);
        assertEquals("test query", result.get("searchText"));
        assertTrue(result.containsKey("metrics"));
        assertTrue(result.containsKey("judgmentIds"));
        assertTrue(result.containsKey("documentIds"));
    }

    public void testPutImportExperiment_MissingEvaluationResults() throws Exception {
        // Setup
        when(settingsAccessor.isWorkbenchEnabled()).thenReturn(true);
        RestRequest request = createPutRestRequestWithContent(IMPORT_MISSING_EVALUATION_RESULTS_CONTENT, "experiments");
        when(channel.request()).thenReturn(request);

        // Mock index response for the case where missing evaluation results are handled gracefully
        IndexResponse mockIndexResponse = mock(IndexResponse.class);
        when(mockIndexResponse.getId()).thenReturn("test_missing_eval_id");
        when(mockIndexResponse.getResult()).thenReturn(DocWriteResponse.Result.CREATED);

        ArgumentCaptor<PutExperimentRequest> requestCaptor = ArgumentCaptor.forClass(PutExperimentRequest.class);
        doAnswer(invocation -> {
            ActionListener<IndexResponse> listener = invocation.getArgument(2);
            listener.onResponse(mockIndexResponse);
            return null;
        }).when(client).execute(eq(PutExperimentAction.INSTANCE), requestCaptor.capture(), any());

        // Execute
        restPutExperimentAction.handleRequest(request, channel, client);

        // Verify response
        ArgumentCaptor<BytesRestResponse> responseCaptor = ArgumentCaptor.forClass(BytesRestResponse.class);
        verify(channel).sendResponse(responseCaptor.capture());
        assertEquals(RestStatus.OK, responseCaptor.getValue().status());

        // Verify the request handles missing evaluation results (should be null or empty)
        PutExperimentRequest capturedRequest = requestCaptor.getValue();
        // Missing evaluation results should result in null or empty list
        if (capturedRequest.getEvaluationResultList() != null) {
            assertEquals(0, capturedRequest.getEvaluationResultList().size());
        }
    }
}
