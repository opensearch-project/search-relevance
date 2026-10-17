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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;

import org.junit.Before;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.opensearch.Version;
import org.opensearch.common.settings.Settings;
import org.opensearch.core.action.ActionListener;
import org.opensearch.core.rest.RestStatus;
import org.opensearch.core.xcontent.NamedXContentRegistry;
import org.opensearch.rest.BytesRestResponse;
import org.opensearch.rest.RestChannel;
import org.opensearch.rest.RestRequest;
import org.opensearch.searchrelevance.plugin.SearchRelevanceRestTestCase;
import org.opensearch.searchrelevance.settings.SearchRelevanceSettingsAccessor;
import org.opensearch.searchrelevance.stats.SearchRelevanceStatsInput;
import org.opensearch.searchrelevance.stats.events.EventStatName;
import org.opensearch.searchrelevance.stats.info.InfoStatName;
import org.opensearch.searchrelevance.transport.stats.SearchRelevanceStatsAction;
import org.opensearch.searchrelevance.transport.stats.SearchRelevanceStatsRequest;
import org.opensearch.searchrelevance.transport.stats.SearchRelevanceStatsResponse;
import org.opensearch.searchrelevance.utils.ClusterUtil;
import org.opensearch.test.rest.FakeRestRequest;
import org.opensearch.threadpool.TestThreadPool;
import org.opensearch.threadpool.ThreadPool;
import org.opensearch.transport.client.node.NodeClient;

public class RestSearchRelevanceStatsActionTests extends SearchRelevanceRestTestCase {
    private NodeClient client;
    private ThreadPool threadPool;

    @Mock
    RestChannel channel;

    @Mock
    private SearchRelevanceSettingsAccessor settingsAccessor;

    @Mock
    private ClusterUtil clusterUtil;

    @Before
    public void setup() {
        MockitoAnnotations.openMocks(this);

        threadPool = new TestThreadPool(this.getClass().getSimpleName() + "ThreadPool");
        client = spy(new NodeClient(Settings.EMPTY, threadPool));

        when(clusterUtil.getClusterMinVersion()).thenReturn(Version.CURRENT);
        when(settingsAccessor.isStatsEnabled()).thenReturn(true);
        when(settingsAccessor.isWorkbenchEnabled()).thenReturn(true);

        doAnswer(invocation -> {
            ActionListener<SearchRelevanceStatsResponse> actionListener = invocation.getArgument(2);
            return null;
        }).when(client).execute(eq(SearchRelevanceStatsAction.INSTANCE), any(), any());
    }

    @Override
    public void tearDown() throws Exception {
        super.tearDown();
        threadPool.shutdown();
        client.close();
    }

    public void test_execute() throws Exception {
        RestSearchRelevanceStatsAction restSearchRelevanceStatsAction = new RestSearchRelevanceStatsAction(settingsAccessor, clusterUtil);

        RestRequest request = getRestRequest();
        restSearchRelevanceStatsAction.handleRequest(request, channel, client);

        ArgumentCaptor<SearchRelevanceStatsRequest> argumentCaptor = ArgumentCaptor.forClass(SearchRelevanceStatsRequest.class);
        verify(client, times(1)).execute(eq(SearchRelevanceStatsAction.INSTANCE), argumentCaptor.capture(), any());

        SearchRelevanceStatsInput capturedInput = argumentCaptor.getValue().getSearchRelevanceStatsInput();
        assertEquals(capturedInput.getEventStatNames(), EnumSet.allOf(EventStatName.class));
        assertEquals(capturedInput.getInfoStatNames(), EnumSet.allOf(InfoStatName.class));
        assertFalse(capturedInput.isFlatten());
        assertFalse(capturedInput.isIncludeMetadata());
        assertTrue(capturedInput.isIncludeIndividualNodes());
        assertTrue(capturedInput.isIncludeAllNodes());
        assertTrue(capturedInput.isIncludeInfo());
    }

    public void test_execute_customParams_includePartial() throws Exception {
        when(settingsAccessor.isStatsEnabled()).thenReturn(true);
        when(clusterUtil.getClusterMinVersion()).thenReturn(Version.CURRENT);

        RestSearchRelevanceStatsAction restSearchRelevanceStatsAction = new RestSearchRelevanceStatsAction(settingsAccessor, clusterUtil);

        Map<String, String> params = Map.of(
            RestSearchRelevanceStatsAction.FLATTEN_PARAM,
            "true",
            RestSearchRelevanceStatsAction.INCLUDE_METADATA_PARAM,
            "true",
            RestSearchRelevanceStatsAction.INCLUDE_INDIVIDUAL_NODES_PARAM,
            "false",
            RestSearchRelevanceStatsAction.INCLUDE_ALL_NODES_PARAM,
            "true",
            RestSearchRelevanceStatsAction.INCLUDE_INFO_PARAM,
            "true"
        );
        RestRequest request = new FakeRestRequest.Builder(NamedXContentRegistry.EMPTY).withParams(params).build();

        restSearchRelevanceStatsAction.handleRequest(request, channel, client);

        ArgumentCaptor<SearchRelevanceStatsRequest> argumentCaptor = ArgumentCaptor.forClass(SearchRelevanceStatsRequest.class);
        verify(client, times(1)).execute(eq(SearchRelevanceStatsAction.INSTANCE), argumentCaptor.capture(), any());

        SearchRelevanceStatsInput capturedInput = argumentCaptor.getValue().getSearchRelevanceStatsInput();

        assertEquals(capturedInput.getEventStatNames(), EnumSet.allOf(EventStatName.class));
        assertEquals(capturedInput.getInfoStatNames(), EnumSet.allOf(InfoStatName.class));
        assertTrue(capturedInput.isFlatten());
        assertTrue(capturedInput.isIncludeMetadata());
        assertFalse(capturedInput.isIncludeIndividualNodes());
        assertTrue(capturedInput.isIncludeAllNodes());
        assertTrue(capturedInput.isIncludeInfo());
    }

    public void test_execute_customParams_includeNone() throws Exception {
        when(settingsAccessor.isStatsEnabled()).thenReturn(true);
        when(clusterUtil.getClusterMinVersion()).thenReturn(Version.CURRENT);

        RestSearchRelevanceStatsAction restSearchRelevanceStatsAction = new RestSearchRelevanceStatsAction(settingsAccessor, clusterUtil);

        Map<String, String> params = new HashMap<>();
        params.put(RestSearchRelevanceStatsAction.FLATTEN_PARAM, "true");
        params.put(RestSearchRelevanceStatsAction.INCLUDE_METADATA_PARAM, "true");
        params.put(RestSearchRelevanceStatsAction.INCLUDE_INDIVIDUAL_NODES_PARAM, "false");
        params.put(RestSearchRelevanceStatsAction.INCLUDE_ALL_NODES_PARAM, "false");
        params.put(RestSearchRelevanceStatsAction.INCLUDE_INFO_PARAM, "false");
        RestRequest request = new FakeRestRequest.Builder(NamedXContentRegistry.EMPTY).withParams(params).build();

        restSearchRelevanceStatsAction.handleRequest(request, channel, client);

        ArgumentCaptor<SearchRelevanceStatsRequest> argumentCaptor = ArgumentCaptor.forClass(SearchRelevanceStatsRequest.class);
        verify(client, times(1)).execute(eq(SearchRelevanceStatsAction.INSTANCE), argumentCaptor.capture(), any());

        SearchRelevanceStatsInput capturedInput = argumentCaptor.getValue().getSearchRelevanceStatsInput();

        // Since we set individual nodes and all nodes to false, we shouldn't fetch any stats
        assertEquals(capturedInput.getEventStatNames(), EnumSet.noneOf(EventStatName.class));
        assertEquals(capturedInput.getInfoStatNames(), EnumSet.noneOf(InfoStatName.class));
        assertTrue(capturedInput.isFlatten());
        assertTrue(capturedInput.isIncludeMetadata());
        assertFalse(capturedInput.isIncludeIndividualNodes());
        assertFalse(capturedInput.isIncludeAllNodes());
        assertFalse(capturedInput.isIncludeInfo());

    }

    public void test_execute_olderVersion() throws Exception {
        when(clusterUtil.getClusterMinVersion()).thenReturn(Version.V_3_0_0);

        RestSearchRelevanceStatsAction restSearchRelevanceStatsAction = new RestSearchRelevanceStatsAction(settingsAccessor, clusterUtil);

        RestRequest request = getRestRequest();
        restSearchRelevanceStatsAction.handleRequest(request, channel, client);

        ArgumentCaptor<SearchRelevanceStatsRequest> argumentCaptor = ArgumentCaptor.forClass(SearchRelevanceStatsRequest.class);
        verify(client, times(1)).execute(eq(SearchRelevanceStatsAction.INSTANCE), argumentCaptor.capture(), any());

        SearchRelevanceStatsInput capturedInput = argumentCaptor.getValue().getSearchRelevanceStatsInput();
        assertEquals(capturedInput.getEventStatNames(), EnumSet.noneOf(EventStatName.class));
        assertEquals(capturedInput.getInfoStatNames(), EnumSet.noneOf(InfoStatName.class));
    }

    public void test_handleRequest_disabledForbidden() throws Exception {
        when(settingsAccessor.isStatsEnabled()).thenReturn(false);

        RestSearchRelevanceStatsAction restSearchRelevanceStatsAction = new RestSearchRelevanceStatsAction(settingsAccessor, clusterUtil);

        RestRequest request = getRestRequest();
        restSearchRelevanceStatsAction.handleRequest(request, channel, client);

        verify(client, never()).execute(eq(SearchRelevanceStatsAction.INSTANCE), any(), any());

        ArgumentCaptor<BytesRestResponse> responseCaptor = ArgumentCaptor.forClass(BytesRestResponse.class);
        verify(channel).sendResponse(responseCaptor.capture());

        BytesRestResponse response = responseCaptor.getValue();
        assertEquals(RestStatus.FORBIDDEN, response.status());
    }

    public void test_execute_statParameters() throws Exception {
        RestSearchRelevanceStatsAction restSearchRelevanceStatsAction = new RestSearchRelevanceStatsAction(settingsAccessor, clusterUtil);

        // Create request with stats not existing on 3.0.0
        Map<String, String> params = new HashMap<>();
        params.put(
            "stat",
            String.join(
                ",",
                EventStatName.LLM_JUDGMENT_RATING_GENERATIONS.getNameString(),
                EventStatName.UBI_JUDGMENT_RATING_GENERATIONS.getNameString()
            )
        );
        params.put("include_metadata", "true");
        params.put("flat_stat_paths", "true");
        RestRequest request = new FakeRestRequest.Builder(NamedXContentRegistry.EMPTY).withParams(params).build();

        restSearchRelevanceStatsAction.handleRequest(request, channel, client);

        ArgumentCaptor<SearchRelevanceStatsRequest> argumentCaptor = ArgumentCaptor.forClass(SearchRelevanceStatsRequest.class);
        verify(client, times(1)).execute(eq(SearchRelevanceStatsAction.INSTANCE), argumentCaptor.capture(), any());

        SearchRelevanceStatsInput capturedInput = argumentCaptor.getValue().getSearchRelevanceStatsInput();
        assertEquals(
            capturedInput.getEventStatNames(),
            EnumSet.of(EventStatName.LLM_JUDGMENT_RATING_GENERATIONS, EventStatName.UBI_JUDGMENT_RATING_GENERATIONS)
        );
        assertEquals(capturedInput.getInfoStatNames(), EnumSet.noneOf(InfoStatName.class));
        assertTrue(capturedInput.isFlatten());
        assertTrue(capturedInput.isIncludeMetadata());
    }

    public void test_execute_statParameters_olderVersion() throws Exception {
        when(clusterUtil.getClusterMinVersion()).thenReturn(Version.V_3_0_0);

        RestSearchRelevanceStatsAction restSearchRelevanceStatsAction = new RestSearchRelevanceStatsAction(settingsAccessor, clusterUtil);

        // Create request with stats not existing on 3.0.0
        Map<String, String> params = new HashMap<>();
        params.put(
            "stat",
            String.join(
                ",",
                EventStatName.LLM_JUDGMENT_RATING_GENERATIONS.getNameString(),
                EventStatName.UBI_JUDGMENT_RATING_GENERATIONS.getNameString(),
                EventStatName.IMPORT_JUDGMENT_RATING_GENERATIONS.getNameString()
            )
        );
        RestRequest request = new FakeRestRequest.Builder(NamedXContentRegistry.EMPTY).withParams(params).build();

        restSearchRelevanceStatsAction.handleRequest(request, channel, client);

        ArgumentCaptor<SearchRelevanceStatsRequest> argumentCaptor = ArgumentCaptor.forClass(SearchRelevanceStatsRequest.class);
        verify(client, times(1)).execute(eq(SearchRelevanceStatsAction.INSTANCE), argumentCaptor.capture(), any());

        SearchRelevanceStatsInput capturedInput = argumentCaptor.getValue().getSearchRelevanceStatsInput();
        assertEquals(capturedInput.getEventStatNames(), EnumSet.noneOf(EventStatName.class));
        assertEquals(capturedInput.getInfoStatNames(), EnumSet.noneOf(InfoStatName.class));
    }

    public void test_handleRequest_invalidStatParameter() {
        RestSearchRelevanceStatsAction restSearchRelevanceStatsAction = new RestSearchRelevanceStatsAction(settingsAccessor, clusterUtil);

        // Create request with invalid stat parameter
        Map<String, String> params = new HashMap<>();
        params.put("stat", "INVALID_STAT");
        RestRequest request = new FakeRestRequest.Builder(NamedXContentRegistry.EMPTY).withParams(params).build();

        assertThrows(IllegalArgumentException.class, () -> restSearchRelevanceStatsAction.handleRequest(request, channel, client));

        verify(client, never()).execute(eq(SearchRelevanceStatsAction.INSTANCE), any(), any());
    }

    private RestRequest getRestRequest() {
        Map<String, String> params = new HashMap<>();
        return new FakeRestRequest.Builder(NamedXContentRegistry.EMPTY).withParams(params).build();
    }
}
