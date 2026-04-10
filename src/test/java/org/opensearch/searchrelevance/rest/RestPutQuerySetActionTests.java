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
import org.opensearch.action.index.IndexResponse;
import org.opensearch.common.xcontent.json.JsonXContent;
import org.opensearch.core.action.ActionListener;
import org.opensearch.core.rest.RestStatus;
import org.opensearch.rest.BytesRestResponse;
import org.opensearch.rest.RestRequest;
import org.opensearch.searchrelevance.plugin.SearchRelevanceRestTestCase;
import org.opensearch.searchrelevance.transport.queryset.PutQuerySetAction;
import org.opensearch.searchrelevance.transport.queryset.PutQuerySetRequest;

public class RestPutQuerySetActionTests extends SearchRelevanceRestTestCase {

    private RestPutQuerySetAction restPutQuerySetAction;
    private static final String TEST_CONTENT = "{"
        + "\"name\": \"test_name\","
        + "\"description\": \"test_description\","
        + "\"sampling\": \"manual\","
        + "\"querySetQueries\": ["
        + "  {\"queryText\": \"test\"}"
        + "]"
        + "}";

    private static final String TEST_CONTENT_WITH_REFERENCE = "{"
        + "\"name\": \"test_name\","
        + "\"description\": \"test_description\","
        + "\"sampling\": \"manual\","
        + "\"querySetQueries\": ["
        + "  {\"queryText\": \"test\", \"referenceAnswer\": \"reference answer\"}"
        + "]"
        + "}";

    private static final String TEST_CONTENT_INVALID_QUERY_TEXT = "{"
        + "\"name\": \"test_name\","
        + "\"description\": \"test_description\","
        + "\"sampling\": \"manual\","
        + "\"querySetQueries\": ["
        + "  {\"queryText\": \"test\\\"\", \"referenceAnswer\": \"reference answer\"}"
        + "]"
        + "}";

    private static final String TEST_CONTENT_INVALID_REFERENCE_ANSWER = "{"
        + "\"name\": \"test_name\","
        + "\"description\": \"test_description\","
        + "\"sampling\": \"manual\","
        + "\"querySetQueries\": ["
        + "  {\"queryText\": \"test\", \"referenceAnswer\": \"reference answer\\\"\"}"
        + "]"
        + "}";

    private static final String INVALID_TYPE_CONTENT = "{"
        + "\"name\": \"test_name\","
        + "\"type\": \"INVALID_TYPE\","
        + "\"sampling\": \"manual\","
        + "\"querySetQueries\": ["
        + "  {\"queryText\": \"test\"}"
        + "]"
        + "}";

    private static final String INVALID_SAMPLING_CONTENT = "{"
        + "\"name\": \"test_name\","
        + "\"sampling\": \"invalid_sampling\","
        + "\"querySetQueries\": ["
        + "  {\"queryText\": \"test\"}"
        + "]"
        + "}";

    private static final String EMPTY_QUERIES_CONTENT = "{"
        + "\"name\": \"test_name\","
        + "\"sampling\": \"manual\","
        + "\"querySetQueries\": []"
        + "}";

    private static final String MISSING_QUERIES_CONTENT = "{" + "\"name\": \"test_name\"," + "\"sampling\": \"manual\"" + "}";

    private static final String MISSING_NAME_CONTENT = "{"
        + "\"sampling\": \"manual\","
        + "\"querySetQueries\": ["
        + "  {\"queryText\": \"test\"}"
        + "]"
        + "}";

    @Override
    public void setUp() throws Exception {
        super.setUp();
        restPutQuerySetAction = new RestPutQuerySetAction(settingsAccessor);
        // Setup channel mock
        when(channel.newBuilder()).thenReturn(JsonXContent.contentBuilder());
        when(channel.newErrorBuilder()).thenReturn(JsonXContent.contentBuilder());
    }

    public void testPrepareRequest_WorkbenchDisabled() throws Exception {
        // Setup
        when(settingsAccessor.isWorkbenchEnabled()).thenReturn(false);
        RestRequest request = createPutRestRequestWithContent(TEST_CONTENT, "query_sets");
        when(channel.request()).thenReturn(request);
        // Execute
        restPutQuerySetAction.handleRequest(request, channel, client);

        // Verify
        ArgumentCaptor<BytesRestResponse> responseCaptor = ArgumentCaptor.forClass(BytesRestResponse.class);
        verify(channel).sendResponse(responseCaptor.capture());
        assertEquals(RestStatus.FORBIDDEN, responseCaptor.getValue().status());
    }

    public void testPrepareRequest_WorkbenchEnabled() throws Exception {
        // Setup
        when(settingsAccessor.isWorkbenchEnabled()).thenReturn(true);
        when(settingsAccessor.getMaxQuerySetAllowed()).thenReturn(1000);
        RestRequest request = createPutRestRequestWithContent(TEST_CONTENT, "query_sets");
        when(channel.request()).thenReturn(request);
        // Mock index response
        IndexResponse mockIndexResponse = mock(IndexResponse.class);
        when(mockIndexResponse.getId()).thenReturn("test_id");

        doAnswer(invocation -> {
            ActionListener<IndexResponse> listener = invocation.getArgument(2);
            listener.onResponse(mockIndexResponse);
            return null;
        }).when(client).execute(eq(PutQuerySetAction.INSTANCE), any(PutQuerySetRequest.class), any());

        // Execute
        restPutQuerySetAction.handleRequest(request, channel, client);

        // Verify
        verify(client).execute(eq(PutQuerySetAction.INSTANCE), any(PutQuerySetRequest.class), any());
        verify(channel).sendResponse(any(BytesRestResponse.class));
    }

    public void testPrepareRequest_FailureCase() throws Exception {
        // Setup
        when(settingsAccessor.isWorkbenchEnabled()).thenReturn(true);
        when(settingsAccessor.getMaxQuerySetAllowed()).thenReturn(1000);
        RestRequest request = createPutRestRequestWithContent(TEST_CONTENT, "query_sets");
        when(channel.request()).thenReturn(request);

        doAnswer(invocation -> {
            ActionListener<IndexResponse> listener = invocation.getArgument(2);
            listener.onFailure(new IOException("Test exception"));
            return null;
        }).when(client).execute(eq(PutQuerySetAction.INSTANCE), any(PutQuerySetRequest.class), any());

        // Execute
        restPutQuerySetAction.handleRequest(request, channel, client);

        // Verify
        ArgumentCaptor<BytesRestResponse> responseCaptor = ArgumentCaptor.forClass(BytesRestResponse.class);
        verify(channel).sendResponse(responseCaptor.capture());
        assertEquals(RestStatus.INTERNAL_SERVER_ERROR, responseCaptor.getValue().status());
    }

    public void testPrepareRequest_WithReferenceAnswer() throws Exception {
        // Setup
        when(settingsAccessor.isWorkbenchEnabled()).thenReturn(true);
        when(settingsAccessor.getMaxQuerySetAllowed()).thenReturn(1000);
        RestRequest request = createPutRestRequestWithContent(TEST_CONTENT_WITH_REFERENCE, "query_sets");
        when(channel.request()).thenReturn(request);
        // Mock index response
        IndexResponse mockIndexResponse = mock(IndexResponse.class);
        when(mockIndexResponse.getId()).thenReturn("test_id");

        doAnswer(invocation -> {
            ActionListener<IndexResponse> listener = invocation.getArgument(2);
            listener.onResponse(mockIndexResponse);
            return null;
        }).when(client).execute(eq(PutQuerySetAction.INSTANCE), any(PutQuerySetRequest.class), any());

        // Execute
        restPutQuerySetAction.handleRequest(request, channel, client);

        // Verify
        verify(client).execute(eq(PutQuerySetAction.INSTANCE), any(PutQuerySetRequest.class), any());
        verify(channel).sendResponse(any(BytesRestResponse.class));
    }

    public void testPrepareRequest_InvalidQueryText() throws Exception {
        // Setup
        when(settingsAccessor.isWorkbenchEnabled()).thenReturn(true);
        when(settingsAccessor.getMaxQuerySetAllowed()).thenReturn(1000);
        RestRequest request = createPutRestRequestWithContent(TEST_CONTENT_INVALID_QUERY_TEXT, "query_sets");
        when(channel.request()).thenReturn(request);

        // Execute
        restPutQuerySetAction.handleRequest(request, channel, client);

        // Verify
        ArgumentCaptor<BytesRestResponse> responseCaptor = ArgumentCaptor.forClass(BytesRestResponse.class);
        verify(channel).sendResponse(responseCaptor.capture());
        assertEquals(RestStatus.BAD_REQUEST, responseCaptor.getValue().status());
        String response = responseCaptor.getValue().content().utf8ToString();
        assertTrue("Response should contain 'Invalid queryText': " + response, response.contains("Invalid queryText"));
    }

    public void testPrepareRequest_InvalidReferenceAnswer() throws Exception {
        // Setup
        when(settingsAccessor.isWorkbenchEnabled()).thenReturn(true);
        when(settingsAccessor.getMaxQuerySetAllowed()).thenReturn(1000);
        RestRequest request = createPutRestRequestWithContent(TEST_CONTENT_INVALID_REFERENCE_ANSWER, "query_sets");
        when(channel.request()).thenReturn(request);

        // Execute
        restPutQuerySetAction.handleRequest(request, channel, client);

        // Verify
        ArgumentCaptor<BytesRestResponse> responseCaptor = ArgumentCaptor.forClass(BytesRestResponse.class);
        verify(channel).sendResponse(responseCaptor.capture());
        assertEquals(RestStatus.BAD_REQUEST, responseCaptor.getValue().status());
        String response = responseCaptor.getValue().content().utf8ToString();
        assertTrue(
            "Response should contain error about invalid referenceAnswer value: " + response,
            response.contains("referenceAnswer") && response.contains("invalid characters")
        );
    }

    public void testPrepareRequest_WithNumericExpectedScore() throws Exception {
        // Test that numeric values like expectedScore are properly converted to strings
        String content = "{"
            + "\"name\": \"test_name\","
            + "\"description\": \"test_description\","
            + "\"sampling\": \"manual\","
            + "\"querySetQueries\": ["
            + "  {\"queryText\": \"test\", \"expectedScore\": 1.0}"
            + "]"
            + "}";

        // Setup
        when(settingsAccessor.isWorkbenchEnabled()).thenReturn(true);
        when(settingsAccessor.getMaxQuerySetAllowed()).thenReturn(1000);
        RestRequest request = createPutRestRequestWithContent(content, "query_sets");
        when(channel.request()).thenReturn(request);

        // Mock index response
        IndexResponse mockIndexResponse = mock(IndexResponse.class);
        when(mockIndexResponse.getId()).thenReturn("test_id");

        ArgumentCaptor<PutQuerySetRequest> requestCaptor = ArgumentCaptor.forClass(PutQuerySetRequest.class);

        doAnswer(invocation -> {
            ActionListener<IndexResponse> listener = invocation.getArgument(2);
            listener.onResponse(mockIndexResponse);
            return null;
        }).when(client).execute(eq(PutQuerySetAction.INSTANCE), requestCaptor.capture(), any());

        // Execute
        restPutQuerySetAction.handleRequest(request, channel, client);

        // Verify the expectedScore was converted to string "1.0"
        PutQuerySetRequest capturedRequest = requestCaptor.getValue();
        assertEquals("1.0", capturedRequest.getQuerySetQueries().get(0).getCustomizedKeyValueMap().get("expectedScore"));
    }

    public void testPrepareRequest_WithBooleanValue() throws Exception {
        // Test that boolean values are properly converted to strings
        String content = "{"
            + "\"name\": \"test_name\","
            + "\"description\": \"test_description\","
            + "\"sampling\": \"manual\","
            + "\"querySetQueries\": ["
            + "  {\"queryText\": \"test\", \"isRelevant\": true}"
            + "]"
            + "}";

        // Setup
        when(settingsAccessor.isWorkbenchEnabled()).thenReturn(true);
        when(settingsAccessor.getMaxQuerySetAllowed()).thenReturn(1000);
        RestRequest request = createPutRestRequestWithContent(content, "query_sets");
        when(channel.request()).thenReturn(request);

        // Mock index response
        IndexResponse mockIndexResponse = mock(IndexResponse.class);
        when(mockIndexResponse.getId()).thenReturn("test_id");

        ArgumentCaptor<PutQuerySetRequest> requestCaptor = ArgumentCaptor.forClass(PutQuerySetRequest.class);

        doAnswer(invocation -> {
            ActionListener<IndexResponse> listener = invocation.getArgument(2);
            listener.onResponse(mockIndexResponse);
            return null;
        }).when(client).execute(eq(PutQuerySetAction.INSTANCE), requestCaptor.capture(), any());

        // Execute
        restPutQuerySetAction.handleRequest(request, channel, client);

        // Verify the boolean was converted to string "true"
        PutQuerySetRequest capturedRequest = requestCaptor.getValue();
        assertEquals("true", capturedRequest.getQuerySetQueries().get(0).getCustomizedKeyValueMap().get("isRelevant"));
    }

    public void testPrepareRequest_WithIntegerValue() throws Exception {
        // Test that integer values are properly converted to strings
        String content = "{"
            + "\"name\": \"test_name\","
            + "\"description\": \"test_description\","
            + "\"sampling\": \"manual\","
            + "\"querySetQueries\": ["
            + "  {\"queryText\": \"test\", \"rank\": 5}"
            + "]"
            + "}";

        // Setup
        when(settingsAccessor.isWorkbenchEnabled()).thenReturn(true);
        when(settingsAccessor.getMaxQuerySetAllowed()).thenReturn(1000);
        RestRequest request = createPutRestRequestWithContent(content, "query_sets");
        when(channel.request()).thenReturn(request);

        // Mock index response
        IndexResponse mockIndexResponse = mock(IndexResponse.class);
        when(mockIndexResponse.getId()).thenReturn("test_id");

        ArgumentCaptor<PutQuerySetRequest> requestCaptor = ArgumentCaptor.forClass(PutQuerySetRequest.class);

        doAnswer(invocation -> {
            ActionListener<IndexResponse> listener = invocation.getArgument(2);
            listener.onResponse(mockIndexResponse);
            return null;
        }).when(client).execute(eq(PutQuerySetAction.INSTANCE), requestCaptor.capture(), any());

        // Execute
        restPutQuerySetAction.handleRequest(request, channel, client);

        // Verify the integer was converted to string "5"
        PutQuerySetRequest capturedRequest = requestCaptor.getValue();
        assertEquals("5", capturedRequest.getQuerySetQueries().get(0).getCustomizedKeyValueMap().get("rank"));
    }

    public void testPrepareRequest_WithMixedTypes() throws Exception {
        // Test that multiple different types are all properly converted to strings
        String content = "{"
            + "\"name\": \"test_name\","
            + "\"description\": \"test_description\","
            + "\"sampling\": \"manual\","
            + "\"querySetQueries\": ["
            + "  {\"queryText\": \"test\", \"expectedScore\": 1.5, \"rank\": 3, \"isRelevant\": true, \"category\": \"product\"}"
            + "]"
            + "}";

        // Setup
        when(settingsAccessor.isWorkbenchEnabled()).thenReturn(true);
        when(settingsAccessor.getMaxQuerySetAllowed()).thenReturn(1000);
        RestRequest request = createPutRestRequestWithContent(content, "query_sets");
        when(channel.request()).thenReturn(request);

        // Mock index response
        IndexResponse mockIndexResponse = mock(IndexResponse.class);
        when(mockIndexResponse.getId()).thenReturn("test_id");

        ArgumentCaptor<PutQuerySetRequest> requestCaptor = ArgumentCaptor.forClass(PutQuerySetRequest.class);

        doAnswer(invocation -> {
            ActionListener<IndexResponse> listener = invocation.getArgument(2);
            listener.onResponse(mockIndexResponse);
            return null;
        }).when(client).execute(eq(PutQuerySetAction.INSTANCE), requestCaptor.capture(), any());

        // Execute
        restPutQuerySetAction.handleRequest(request, channel, client);

        // Verify all types were converted to strings
        PutQuerySetRequest capturedRequest = requestCaptor.getValue();
        Map<String, String> customMap = capturedRequest.getQuerySetQueries().get(0).getCustomizedKeyValueMap();
        assertEquals("1.5", customMap.get("expectedScore"));
        assertEquals("3", customMap.get("rank"));
        assertEquals("true", customMap.get("isRelevant"));
        assertEquals("product", customMap.get("category"));
    }

    public void testPrepareRequest_InvalidType() throws Exception {
        when(settingsAccessor.isWorkbenchEnabled()).thenReturn(true);
        when(settingsAccessor.getMaxQuerySetAllowed()).thenReturn(1000);
        RestRequest request = createPutRestRequestWithContent(INVALID_TYPE_CONTENT, "query_sets");
        when(channel.request()).thenReturn(request);

        restPutQuerySetAction.handleRequest(request, channel, client);

        ArgumentCaptor<BytesRestResponse> responseCaptor = ArgumentCaptor.forClass(BytesRestResponse.class);
        verify(channel).sendResponse(responseCaptor.capture());
        assertEquals(RestStatus.BAD_REQUEST, responseCaptor.getValue().status());
        assertTrue(responseCaptor.getValue().content().utf8ToString().contains("Invalid type"));
    }

    public void testPrepareRequest_InvalidSampling() throws Exception {
        when(settingsAccessor.isWorkbenchEnabled()).thenReturn(true);
        when(settingsAccessor.getMaxQuerySetAllowed()).thenReturn(1000);
        RestRequest request = createPutRestRequestWithContent(INVALID_SAMPLING_CONTENT, "query_sets");
        when(channel.request()).thenReturn(request);

        restPutQuerySetAction.handleRequest(request, channel, client);

        ArgumentCaptor<BytesRestResponse> responseCaptor = ArgumentCaptor.forClass(BytesRestResponse.class);
        verify(channel).sendResponse(responseCaptor.capture());
        assertEquals(RestStatus.BAD_REQUEST, responseCaptor.getValue().status());
        assertTrue(responseCaptor.getValue().content().utf8ToString().contains("Invalid sampling"));
    }

    public void testPrepareRequest_EmptyQueries() throws Exception {
        when(settingsAccessor.isWorkbenchEnabled()).thenReturn(true);
        when(settingsAccessor.getMaxQuerySetAllowed()).thenReturn(1000);
        RestRequest request = createPutRestRequestWithContent(EMPTY_QUERIES_CONTENT, "query_sets");
        when(channel.request()).thenReturn(request);

        restPutQuerySetAction.handleRequest(request, channel, client);

        ArgumentCaptor<BytesRestResponse> responseCaptor = ArgumentCaptor.forClass(BytesRestResponse.class);
        verify(channel).sendResponse(responseCaptor.capture());
        assertEquals(RestStatus.BAD_REQUEST, responseCaptor.getValue().status());
        assertTrue(responseCaptor.getValue().content().utf8ToString().contains("cannot be empty"));
    }

    public void testPrepareRequest_MissingQueries() throws Exception {
        when(settingsAccessor.isWorkbenchEnabled()).thenReturn(true);
        when(settingsAccessor.getMaxQuerySetAllowed()).thenReturn(1000);
        RestRequest request = createPutRestRequestWithContent(MISSING_QUERIES_CONTENT, "query_sets");
        when(channel.request()).thenReturn(request);

        restPutQuerySetAction.handleRequest(request, channel, client);

        ArgumentCaptor<BytesRestResponse> responseCaptor = ArgumentCaptor.forClass(BytesRestResponse.class);
        verify(channel).sendResponse(responseCaptor.capture());
        assertEquals(RestStatus.BAD_REQUEST, responseCaptor.getValue().status());
        assertTrue(responseCaptor.getValue().content().utf8ToString().contains("must be a list"));
    }

    public void testPrepareRequest_MissingName() throws Exception {
        when(settingsAccessor.isWorkbenchEnabled()).thenReturn(true);
        when(settingsAccessor.getMaxQuerySetAllowed()).thenReturn(1000);
        RestRequest request = createPutRestRequestWithContent(MISSING_NAME_CONTENT, "query_sets");
        when(channel.request()).thenReturn(request);

        restPutQuerySetAction.handleRequest(request, channel, client);

        ArgumentCaptor<BytesRestResponse> responseCaptor = ArgumentCaptor.forClass(BytesRestResponse.class);
        verify(channel).sendResponse(responseCaptor.capture());
        assertEquals(RestStatus.BAD_REQUEST, responseCaptor.getValue().status());
        assertTrue(responseCaptor.getValue().content().utf8ToString().contains("Invalid name"));
    }

    public void testPrepareRequest_QuerySetLimitExceeded() throws Exception {
        when(settingsAccessor.isWorkbenchEnabled()).thenReturn(true);
        when(settingsAccessor.getMaxQuerySetAllowed()).thenReturn(0);
        RestRequest request = createPutRestRequestWithContent(TEST_CONTENT, "query_sets");
        when(channel.request()).thenReturn(request);

        restPutQuerySetAction.handleRequest(request, channel, client);

        ArgumentCaptor<BytesRestResponse> responseCaptor = ArgumentCaptor.forClass(BytesRestResponse.class);
        verify(channel).sendResponse(responseCaptor.capture());
        assertEquals(RestStatus.FORBIDDEN, responseCaptor.getValue().status());
        assertTrue(responseCaptor.getValue().content().utf8ToString().contains("Query Set Limit Exceeded"));
    }

    public void testPrepareRequest_NullTypeDefaultsToManual() throws Exception {
        // Content without type field should default to manual
        when(settingsAccessor.isWorkbenchEnabled()).thenReturn(true);
        when(settingsAccessor.getMaxQuerySetAllowed()).thenReturn(1000);
        RestRequest request = createPutRestRequestWithContent(TEST_CONTENT, "query_sets");
        when(channel.request()).thenReturn(request);

        IndexResponse mockIndexResponse = mock(IndexResponse.class);
        when(mockIndexResponse.getId()).thenReturn("test_id");

        ArgumentCaptor<PutQuerySetRequest> requestCaptor = ArgumentCaptor.forClass(PutQuerySetRequest.class);
        doAnswer(invocation -> {
            ActionListener<IndexResponse> listener = invocation.getArgument(2);
            listener.onResponse(mockIndexResponse);
            return null;
        }).when(client).execute(eq(PutQuerySetAction.INSTANCE), requestCaptor.capture(), any());

        restPutQuerySetAction.handleRequest(request, channel, client);

        PutQuerySetRequest capturedRequest = requestCaptor.getValue();
        assertEquals("manual", capturedRequest.getSampling());
        assertEquals("test_name", capturedRequest.getName());
    }
}
