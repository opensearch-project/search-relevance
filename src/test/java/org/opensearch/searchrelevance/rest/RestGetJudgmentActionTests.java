/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */
package org.opensearch.searchrelevance.rest;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.util.Map;

import org.mockito.ArgumentCaptor;
import org.opensearch.action.search.SearchResponse;
import org.opensearch.common.xcontent.json.JsonXContent;
import org.opensearch.core.action.ActionListener;
import org.opensearch.core.rest.RestStatus;
import org.opensearch.index.query.MatchAllQueryBuilder;
import org.opensearch.index.query.TermQueryBuilder;
import org.opensearch.rest.BytesRestResponse;
import org.opensearch.rest.RestRequest;
import org.opensearch.search.builder.SearchSourceBuilder;
import org.opensearch.searchrelevance.plugin.SearchRelevanceRestTestCase;
import org.opensearch.searchrelevance.transport.OpenSearchDocRequest;
import org.opensearch.searchrelevance.transport.judgment.GetJudgmentAction;

public class RestGetJudgmentActionTests extends SearchRelevanceRestTestCase {

    private RestGetJudgmentAction action;

    @Override
    public void setUp() throws Exception {
        super.setUp();
        action = new RestGetJudgmentAction(settingsAccessor);

        when(channel.newBuilder()).thenReturn(JsonXContent.contentBuilder());
        when(channel.newErrorBuilder()).thenReturn(JsonXContent.contentBuilder());
    }

    // Workbench disabled
    public void testWorkbenchDisabled() throws Exception {
        when(settingsAccessor.isWorkbenchEnabled()).thenReturn(false);

        RestRequest req = createGetRestRequestWithParams("judgments", "", Map.of());
        when(channel.request()).thenReturn(req);

        action.handleRequest(req, channel, client);

        ArgumentCaptor<BytesRestResponse> captor = ArgumentCaptor.forClass(BytesRestResponse.class);
        verify(channel).sendResponse(captor.capture());
        assertEquals(RestStatus.FORBIDDEN, captor.getValue().status());
    }

    // GET by judgment ID
    public void testGetById() throws Exception {
        when(settingsAccessor.isWorkbenchEnabled()).thenReturn(true);

        RestRequest req = createGetRestRequestWithParams("judgments", null, Map.of("id", "id123"));
        when(channel.request()).thenReturn(req);

        doAnswer(inv -> {
            OpenSearchDocRequest request = inv.getArgument(1);
            assertEquals("id123", request.getId());

            SearchResponse mockResp = mock(SearchResponse.class);
            when(mockResp.status()).thenReturn(RestStatus.OK);
            inv.<ActionListener<SearchResponse>>getArgument(2).onResponse(mockResp);
            return null;
        }).when(client).execute(eq(GetJudgmentAction.INSTANCE), any(), any());

        action.handleRequest(req, channel, client);
    }

    // Status permutations
    private void assertTermQueryForStatus(String input, String expectedUpper) throws Exception {
        RestRequest req = createGetRestRequestWithParams("judgments", "", Map.of("status", input));
        when(channel.request()).thenReturn(req);
        when(settingsAccessor.isWorkbenchEnabled()).thenReturn(true);

        doAnswer(inv -> {
            OpenSearchDocRequest request = inv.getArgument(1);
            SearchSourceBuilder ssb = request.getSearchSourceBuilder();

            assertTrue(ssb.query() instanceof TermQueryBuilder);
            TermQueryBuilder tq = (TermQueryBuilder) ssb.query();
            assertEquals("status.keyword", tq.fieldName());
            assertEquals(expectedUpper, tq.value());

            SearchResponse resp = mock(SearchResponse.class);
            when(resp.status()).thenReturn(RestStatus.OK);
            inv.<ActionListener<SearchResponse>>getArgument(2).onResponse(resp);
            return null;
        }).when(client).execute(eq(GetJudgmentAction.INSTANCE), any(), any());

        action.handleRequest(req, channel, client);
    }

    public void testStatusCompleted() throws Exception {
        assertTermQueryForStatus("COMPLETED", "COMPLETED");
    }

    public void testStatusProcessing() throws Exception {
        assertTermQueryForStatus("PROCESSING", "PROCESSING");
    }

    public void testStatusError() throws Exception {
        assertTermQueryForStatus("ERROR", "ERROR");
    }

    public void testStatusTimeout() throws Exception {
        assertTermQueryForStatus("TIMEOUT", "TIMEOUT");
    }

    public void testLowercaseStatusIsUppercased() throws Exception {
        assertTermQueryForStatus("completed", "COMPLETED");
    }

    // No status → match_all
    public void testMissingStatus_UsesMatchAllQuery() throws Exception {
        when(settingsAccessor.isWorkbenchEnabled()).thenReturn(true);

        RestRequest req = createGetRestRequestWithParams("judgments", "", Map.of());
        when(channel.request()).thenReturn(req);

        doAnswer(inv -> {
            OpenSearchDocRequest request = inv.getArgument(1);
            assertTrue(request.getSearchSourceBuilder().query() instanceof MatchAllQueryBuilder);

            SearchResponse resp = mock(SearchResponse.class);
            when(resp.status()).thenReturn(RestStatus.OK);
            inv.<ActionListener<SearchResponse>>getArgument(2).onResponse(resp);
            return null;
        }).when(client).execute(eq(GetJudgmentAction.INSTANCE), any(), any());

        action.handleRequest(req, channel, client);
    }

    // Empty status -> match_all (permissive)
    public void testEmptyStatus_UsesMatchAll() throws Exception {
        when(settingsAccessor.isWorkbenchEnabled()).thenReturn(true);

        RestRequest req = createGetRestRequestWithParams("judgments", "", Map.of("status", ""));
        when(channel.request()).thenReturn(req);

        doAnswer(inv -> {
            OpenSearchDocRequest request = inv.getArgument(1);
            assertTrue(request.getSearchSourceBuilder().query() instanceof MatchAllQueryBuilder);

            SearchResponse resp = mock(SearchResponse.class);
            when(resp.status()).thenReturn(RestStatus.OK);
            inv.<ActionListener<SearchResponse>>getArgument(2).onResponse(resp);
            return null;
        }).when(client).execute(eq(GetJudgmentAction.INSTANCE), any(), any());

        action.handleRequest(req, channel, client);
    }
}
