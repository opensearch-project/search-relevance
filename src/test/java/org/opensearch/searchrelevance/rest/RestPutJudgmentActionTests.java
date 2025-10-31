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
import org.opensearch.searchrelevance.exception.SearchRelevanceException;
import org.opensearch.searchrelevance.plugin.SearchRelevanceRestTestCase;
import org.opensearch.searchrelevance.transport.judgment.PutImportJudgmentRequest;
import org.opensearch.searchrelevance.transport.judgment.PutJudgmentAction;
import org.opensearch.searchrelevance.transport.judgment.PutLlmJudgmentRequest;
import org.opensearch.searchrelevance.transport.judgment.PutUbiJudgmentRequest;

public class RestPutJudgmentActionTests extends SearchRelevanceRestTestCase {

    private RestPutJudgmentAction restPutJudgmentAction;
    private static final String LLM_JUDGMENT_CONTENT = "{"
        + "\"name\": \"test_name\","
        + "\"description\": \"test_description\","
        + "\"type\": \"LLM_JUDGMENT\","
        + "\"modelId\": \"test_model_id\","
        + "\"querySetId\": \"test_query_set_id\","
        + "\"searchConfigurationList\": [\"config1\", \"config2\"],"
        + "\"size\": 10,"
        + "\"tokenLimit\": 1000,"
        + "\"contextFields\": [\"field1\", \"field2\"],"
        + "\"ignoreFailure\": false"
        + "}";

    private static final String UBI_JUDGMENT_CONTENT = "{"
        + "\"name\": \"test_name\","
        + "\"description\": \"test_description\","
        + "\"type\": \"UBI_JUDGMENT\","
        + "\"clickModel\": \"test_click_model\","
        + "\"maxRank\": 10"
        + "}";

    private static final String IMPORT_JUDGMENT_CONTENT = """
        {
          "name": "test_name",
          "description": "test_description",
          "type": "IMPORT_JUDGMENT",
          "judgmentRatings": [
            {
              "query": "red shoes",
              "ratings": [
                {
                  "docId": "B077ZJXCTS",
                  "rating": "0.000"
                }
              ]
            },
            {
              "query": "blue jeans",
              "ratings": [
                {
                  "docId": "B071S6LTJJ",
                  "rating": "0.000"
                }
              ]
            }
          ]
        }""";

    private static final String INVALID_TYPE_CONTENT = "{"
        + "\"name\": \"test_name\","
        + "\"description\": \"test_description\","
        + "\"type\": \"INVALID_TYPE\""
        + "}";

    @Override
    public void setUp() throws Exception {
        super.setUp();
        restPutJudgmentAction = new RestPutJudgmentAction(settingsAccessor);
        // Setup channel mock
        when(channel.newBuilder()).thenReturn(JsonXContent.contentBuilder());
        when(channel.newErrorBuilder()).thenReturn(JsonXContent.contentBuilder());
    }

    public void testPrepareRequest_WorkbenchDisabled() throws Exception {
        // Setup
        when(settingsAccessor.isWorkbenchEnabled()).thenReturn(false);
        RestRequest request = createPutRestRequestWithContent(LLM_JUDGMENT_CONTENT, "judgment");
        when(channel.request()).thenReturn(request);

        // Execute
        restPutJudgmentAction.handleRequest(request, channel, client);

        // Verify
        ArgumentCaptor<BytesRestResponse> responseCaptor = ArgumentCaptor.forClass(BytesRestResponse.class);
        verify(channel).sendResponse(responseCaptor.capture());
        assertEquals(RestStatus.FORBIDDEN, responseCaptor.getValue().status());
    }

    public void testPutLlmJudgment_Success() throws Exception {
        // Setup
        when(settingsAccessor.isWorkbenchEnabled()).thenReturn(true);
        RestRequest request = createPutRestRequestWithContent(LLM_JUDGMENT_CONTENT, "judgment");
        when(channel.request()).thenReturn(request);

        // Mock index response
        IndexResponse mockIndexResponse = mock(IndexResponse.class);
        when(mockIndexResponse.getId()).thenReturn("test_id");

        doAnswer(invocation -> {
            ActionListener<IndexResponse> listener = invocation.getArgument(2);
            listener.onResponse(mockIndexResponse);
            return null;
        }).when(client).execute(eq(PutJudgmentAction.INSTANCE), any(PutLlmJudgmentRequest.class), any());

        // Execute
        restPutJudgmentAction.handleRequest(request, channel, client);

        // Verify
        ArgumentCaptor<BytesRestResponse> responseCaptor = ArgumentCaptor.forClass(BytesRestResponse.class);
        verify(channel).sendResponse(responseCaptor.capture());
        assertEquals(RestStatus.OK, responseCaptor.getValue().status());
    }

    public void testPutUbiJudgment_Success() throws Exception {
        // Setup
        when(settingsAccessor.isWorkbenchEnabled()).thenReturn(true);
        RestRequest request = createPutRestRequestWithContent(UBI_JUDGMENT_CONTENT, "judgment");
        when(channel.request()).thenReturn(request);

        // Mock index response
        IndexResponse mockIndexResponse = mock(IndexResponse.class);
        when(mockIndexResponse.getId()).thenReturn("test_id");

        doAnswer(invocation -> {
            ActionListener<IndexResponse> listener = invocation.getArgument(2);
            listener.onResponse(mockIndexResponse);
            return null;
        }).when(client).execute(eq(PutJudgmentAction.INSTANCE), any(PutUbiJudgmentRequest.class), any());

        // Execute
        restPutJudgmentAction.handleRequest(request, channel, client);

        // Verify
        ArgumentCaptor<BytesRestResponse> responseCaptor = ArgumentCaptor.forClass(BytesRestResponse.class);
        verify(channel).sendResponse(responseCaptor.capture());
        assertEquals(RestStatus.OK, responseCaptor.getValue().status());
    }

    public void testPutImportJudgment_Success() throws Exception {
        // Setup
        when(settingsAccessor.isWorkbenchEnabled()).thenReturn(true);
        RestRequest request = createPutRestRequestWithContent(IMPORT_JUDGMENT_CONTENT, "judgment");
        when(channel.request()).thenReturn(request);

        // Mock index response
        IndexResponse mockIndexResponse = mock(IndexResponse.class);
        when(mockIndexResponse.getId()).thenReturn("test_id");

        doAnswer(invocation -> {
            ActionListener<IndexResponse> listener = invocation.getArgument(2);
            listener.onResponse(mockIndexResponse);
            return null;
        }).when(client).execute(eq(PutJudgmentAction.INSTANCE), any(PutImportJudgmentRequest.class), any());

        // Execute
        restPutJudgmentAction.handleRequest(request, channel, client);

        // Verify
        ArgumentCaptor<BytesRestResponse> responseCaptor = ArgumentCaptor.forClass(BytesRestResponse.class);
        verify(channel).sendResponse(responseCaptor.capture());
        assertEquals(RestStatus.OK, responseCaptor.getValue().status());
    }

    public void testPutJudgment_InvalidType() {
        // Setup
        when(settingsAccessor.isWorkbenchEnabled()).thenReturn(true);
        RestRequest request = createPutRestRequestWithContent(INVALID_TYPE_CONTENT, "judgments");
        when(channel.request()).thenReturn(request);

        // Execute and verify
        IllegalArgumentException exception = expectThrows(
            IllegalArgumentException.class,
            () -> restPutJudgmentAction.prepareRequest(request, client)
        );
        assertTrue(exception.getMessage().contains("Invalid or missing judgment type"));
    }

    public void testPutLlmJudgment_MissingModelId() {
        // Setup
        when(settingsAccessor.isWorkbenchEnabled()).thenReturn(true);
        String content = "{" + "\"name\": \"test_name\"," + "\"description\": \"test_description\"," + "\"type\": \"LLM_JUDGMENT\"" + "}";
        RestRequest request = createPutRestRequestWithContent(content, "judgment");
        when(channel.request()).thenReturn(request);

        // Execute and verify
        SearchRelevanceException exception = expectThrows(
            SearchRelevanceException.class,
            () -> restPutJudgmentAction.handleRequest(request, channel, client)
        );
        assertEquals("modelId is required for LLM_JUDGMENT", exception.getMessage());
        assertEquals(RestStatus.BAD_REQUEST, exception.status());
    }

    public void testPutJudgment_Failure() throws Exception {
        // Setup
        when(settingsAccessor.isWorkbenchEnabled()).thenReturn(true);
        RestRequest request = createPutRestRequestWithContent(LLM_JUDGMENT_CONTENT, "judgment");
        when(channel.request()).thenReturn(request);

        doAnswer(invocation -> {
            ActionListener<IndexResponse> listener = invocation.getArgument(2);
            listener.onFailure(new IOException("Test exception"));
            return null;
        }).when(client).execute(eq(PutJudgmentAction.INSTANCE), any(), any());

        // Execute
        restPutJudgmentAction.handleRequest(request, channel, client);

        // Verify
        ArgumentCaptor<BytesRestResponse> responseCaptor = ArgumentCaptor.forClass(BytesRestResponse.class);
        verify(channel).sendResponse(responseCaptor.capture());
        assertEquals(RestStatus.INTERNAL_SERVER_ERROR, responseCaptor.getValue().status());
    }
}
