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
import org.opensearch.action.index.IndexResponse;
import org.opensearch.common.unit.TimeValue;
import org.opensearch.common.xcontent.json.JsonXContent;
import org.opensearch.core.action.ActionListener;
import org.opensearch.core.rest.RestStatus;
import org.opensearch.rest.BytesRestResponse;
import org.opensearch.rest.RestRequest;
import org.opensearch.searchrelevance.plugin.SearchRelevanceRestTestCase;
import org.opensearch.searchrelevance.transport.scheduledJob.PostScheduledExperimentAction;
import org.opensearch.searchrelevance.transport.scheduledJob.PostScheduledExperimentRequest;
import org.opensearch.searchrelevance.utils.CronUtil;

public class RestPostScheduledExperimentActionTests extends SearchRelevanceRestTestCase {

    private RestPostScheduledExperimentAction restPostScheduledExperimentAction;
    private static final String TEST_CONTENT = "{"
        + "\"experimentId\": \"8b40830a-a05b-4bc6-968a-6be48aeadf3f\","
        + "\"cronExpression\": \"* * * * *\""
        + "}";
    private static final String LONG_INTERVAL_TEST_CONTENT = "{"
        + "\"experimentId\": \"8b40830a-a05b-4bc6-968a-6be48aeadf3f\","
        + "\"cronExpression\": \"* 7 * * *\""
        + "}";
    private static final String INVALID_CRON_TEST_CONTENT = "{"
        + "\"experimentId\": \"8b40830a-a05b-4bc6-968a-6be48aeadf3f\","
        + "\"cronExpression\": \"* 162 * * *\""
        + "}";

    @Override
    public void setUp() throws Exception {
        super.setUp();
        CronUtil cronUtil = new CronUtil(settingsAccessor);
        restPostScheduledExperimentAction = new RestPostScheduledExperimentAction(settingsAccessor, cronUtil);
        // Prepare some default settings
        when(settingsAccessor.getScheduledExperimentsMinimumInterval()).thenReturn(TimeValue.timeValueMinutes(1));
        // Setup channel mock
        when(channel.newBuilder()).thenReturn(JsonXContent.contentBuilder());
        when(channel.newErrorBuilder()).thenReturn(JsonXContent.contentBuilder());
    }

    public void testPrepareRequest_WorkbenchDisabled() throws Exception {
        // Setup
        when(settingsAccessor.isWorkbenchEnabled()).thenReturn(false);
        RestRequest request = createPostRestRequestWithContent(TEST_CONTENT, "experiment/schedule");
        when(channel.request()).thenReturn(request);

        // Execute
        restPostScheduledExperimentAction.handleRequest(request, channel, client);

        // Verify
        ArgumentCaptor<BytesRestResponse> responseCaptor = ArgumentCaptor.forClass(BytesRestResponse.class);
        verify(channel).sendResponse(responseCaptor.capture());
        assertEquals(RestStatus.FORBIDDEN, responseCaptor.getValue().status());
    }

    public void testPrepareRequest_JobSchedulerDisabled() throws Exception {
        // Setup
        when(settingsAccessor.isWorkbenchEnabled()).thenReturn(true);
        when(settingsAccessor.isScheduledExperimentsEnabled()).thenReturn(false);
        RestRequest request = createPostRestRequestWithContent(TEST_CONTENT, "experiment/schedule");
        when(channel.request()).thenReturn(request);

        // Execute
        restPostScheduledExperimentAction.handleRequest(request, channel, client);

        // Verify
        ArgumentCaptor<BytesRestResponse> responseCaptor = ArgumentCaptor.forClass(BytesRestResponse.class);
        verify(channel).sendResponse(responseCaptor.capture());
        assertEquals(RestStatus.FORBIDDEN, responseCaptor.getValue().status());
    }

    public void testPrepareRequest_WorkbenchEnabled_Success() throws Exception {
        // Setup
        when(settingsAccessor.isWorkbenchEnabled()).thenReturn(true);
        when(settingsAccessor.isScheduledExperimentsEnabled()).thenReturn(true);
        RestRequest request = createPostRestRequestWithContent(TEST_CONTENT, "experiment/schedule");
        when(channel.request()).thenReturn(request);

        doAnswer(invocation -> {
            ActionListener<IndexResponse> listener = invocation.getArgument(2);
            listener.onFailure(new IOException("Test exception"));
            return null;
        }).when(client).execute(eq(PostScheduledExperimentAction.INSTANCE), any(PostScheduledExperimentRequest.class), any());

        // Execute
        restPostScheduledExperimentAction.handleRequest(request, channel, client);

        // Verify
        verify(client).execute(eq(PostScheduledExperimentAction.INSTANCE), any(PostScheduledExperimentRequest.class), any());
        verify(channel).sendResponse(any(BytesRestResponse.class));
    }

    public void testPrepareRequest_WorkbenchEnabled_Failure() throws Exception {
        // Setup
        when(settingsAccessor.isWorkbenchEnabled()).thenReturn(true);
        when(settingsAccessor.isScheduledExperimentsEnabled()).thenReturn(true);
        RestRequest request = createPostRestRequestWithContent(TEST_CONTENT, "experiment/schedule");
        when(channel.request()).thenReturn(request);

        doAnswer(invocation -> {
            ActionListener<IndexResponse> listener = invocation.getArgument(2);
            listener.onFailure(new IOException("Test exception"));
            return null;
        }).when(client).execute(eq(PostScheduledExperimentAction.INSTANCE), any(PostScheduledExperimentRequest.class), any());

        // Execute
        restPostScheduledExperimentAction.handleRequest(request, channel, client);

        // Verify
        ArgumentCaptor<BytesRestResponse> responseCaptor = ArgumentCaptor.forClass(BytesRestResponse.class);
        verify(channel).sendResponse(responseCaptor.capture());
        assertEquals(RestStatus.INTERNAL_SERVER_ERROR, responseCaptor.getValue().status());
    }

    public void testExecuteRequest_MinimumInterval() throws Exception {
        // Setup
        when(settingsAccessor.isWorkbenchEnabled()).thenReturn(true);
        when(settingsAccessor.isScheduledExperimentsEnabled()).thenReturn(true);
        when(settingsAccessor.getScheduledExperimentsMinimumInterval()).thenReturn(TimeValue.timeValueHours(20));
        RestRequest request = createPostRestRequestWithContent(LONG_INTERVAL_TEST_CONTENT, "experiment/schedule");
        when(channel.request()).thenReturn(request);

        // Execute
        assertThrows(IllegalArgumentException.class, () -> restPostScheduledExperimentAction.handleRequest(request, channel, client));
    }

    public void testExecuteRequest_InvalidCron() throws Exception {
        // Setup
        when(settingsAccessor.isWorkbenchEnabled()).thenReturn(true);
        when(settingsAccessor.isScheduledExperimentsEnabled()).thenReturn(true);
        RestRequest request = createPostRestRequestWithContent(INVALID_CRON_TEST_CONTENT, "experiment/schedule");
        when(channel.request()).thenReturn(request);

        // Execute
        assertThrows(IllegalArgumentException.class, () -> restPostScheduledExperimentAction.handleRequest(request, channel, client));
    }
}
