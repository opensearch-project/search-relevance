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
import static org.opensearch.searchrelevance.common.PluginConstants.JUDGMENTS_URL;

import java.io.IOException;
import java.util.List;

import org.apache.lucene.search.TotalHits;
import org.hamcrest.Matchers;
import org.junit.Before;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.opensearch.action.search.SearchRequest;
import org.opensearch.action.search.SearchResponse;
import org.opensearch.action.search.SearchResponseSections;
import org.opensearch.action.search.ShardSearchFailure;
import org.opensearch.common.settings.Settings;
import org.opensearch.common.xcontent.XContentType;
import org.opensearch.common.xcontent.json.JsonXContent;
import org.opensearch.core.action.ActionListener;
import org.opensearch.core.common.Strings;
import org.opensearch.core.common.bytes.BytesArray;
import org.opensearch.core.rest.RestStatus;
import org.opensearch.core.xcontent.NamedXContentRegistry;
import org.opensearch.core.xcontent.XContentBuilder;
import org.opensearch.rest.BytesRestResponse;
import org.opensearch.rest.RestChannel;
import org.opensearch.rest.RestHandler;
import org.opensearch.rest.RestRequest;
import org.opensearch.search.SearchHit;
import org.opensearch.search.SearchHits;
import org.opensearch.search.SearchModule;
import org.opensearch.search.aggregations.InternalAggregations;
import org.opensearch.search.builder.SearchSourceBuilder;
import org.opensearch.searchrelevance.settings.SearchRelevanceSettingsAccessor;
import org.opensearch.searchrelevance.transport.judgment.SearchJudgmentAction;
import org.opensearch.test.OpenSearchTestCase;
import org.opensearch.test.rest.FakeRestRequest;
import org.opensearch.threadpool.TestThreadPool;
import org.opensearch.threadpool.ThreadPool;
import org.opensearch.transport.client.node.NodeClient;

public class RestSearchJudgmentActionTests extends OpenSearchTestCase {
    private RestSearchJudgmentAction restAction;
    private NodeClient client;
    private ThreadPool threadPool;

    @Mock
    private RestChannel channel;

    @Mock
    private SearchRelevanceSettingsAccessor settingsAccessor;

    private NamedXContentRegistry xContentRegistry;

    @Before
    public void setup() throws IOException {
        MockitoAnnotations.openMocks(this);
        restAction = new RestSearchJudgmentAction(settingsAccessor);
        threadPool = new TestThreadPool(this.getClass().getSimpleName() + "ThreadPool");
        client = spy(new NodeClient(Settings.EMPTY, threadPool));

        when(settingsAccessor.isWorkbenchEnabled()).thenReturn(true);

        xContentRegistry = new NamedXContentRegistry(new SearchModule(Settings.EMPTY, List.of()).getNamedXContents());

        XContentBuilder builder = JsonXContent.contentBuilder();
        when(channel.newBuilder()).thenReturn(builder);

        doAnswer(invocation -> {
            ActionListener<SearchResponse> listener = invocation.getArgument(2);
            SearchResponse response = buildSearchResponse();
            listener.onResponse(response);
            return null;
        }).when(client).execute(eq(SearchJudgmentAction.INSTANCE), any(), any());
    }

    @Override
    public void tearDown() throws Exception {
        super.tearDown();
        threadPool.shutdown();
        client.close();
    }

    public void testGetNameAndRoutes() {
        String actionName = restAction.getName();
        assertFalse(Strings.isNullOrEmpty(actionName));
        assertEquals("search_judgment_action", actionName);

        List<RestHandler.Route> routes = restAction.routes();
        assertNotNull(routes);
        assertEquals(2, routes.size());
        assertThat(routes.get(0).getPath(), Matchers.is(JUDGMENTS_URL + "/_search"));
        assertThat(routes.get(0).getMethod(), Matchers.anyOf(Matchers.is(RestRequest.Method.POST), Matchers.is(RestRequest.Method.GET)));
    }

    public void testHandleRequest_executesTransportAndReturnsResponse() throws Exception {
        RestRequest request = new FakeRestRequest.Builder(xContentRegistry).withMethod(RestRequest.Method.POST)
            .withPath(JUDGMENTS_URL + "/_search")
            .withContent(new BytesArray("{\"query\":{\"match_all\":{}}}"), XContentType.JSON)
            .build();

        restAction.handleRequest(request, channel, client);

        ArgumentCaptor<SearchRequest> requestCaptor = ArgumentCaptor.forClass(SearchRequest.class);
        verify(client, times(1)).execute(eq(SearchJudgmentAction.INSTANCE), requestCaptor.capture(), any());
        SearchRequest captured = requestCaptor.getValue();
        SearchSourceBuilder source = captured.source();
        assertNotNull(source);
        assertThat(source.toString(), Matchers.containsString("match_all"));

        ArgumentCaptor<BytesRestResponse> responseCaptor = ArgumentCaptor.forClass(BytesRestResponse.class);
        verify(channel, times(1)).sendResponse(responseCaptor.capture());
        assertEquals(RestStatus.OK, responseCaptor.getValue().status());
    }

    public void testHandleRequest_whenWorkbenchDisabled_returnsForbidden() throws Exception {
        when(settingsAccessor.isWorkbenchEnabled()).thenReturn(false);

        RestRequest request = new FakeRestRequest.Builder(NamedXContentRegistry.EMPTY).withMethod(RestRequest.Method.GET)
            .withPath(JUDGMENTS_URL + "/_search")
            .build();

        restAction.handleRequest(request, channel, client);

        verify(client, never()).execute(eq(SearchJudgmentAction.INSTANCE), any(), any());
        ArgumentCaptor<BytesRestResponse> responseCaptor = ArgumentCaptor.forClass(BytesRestResponse.class);
        verify(channel, times(1)).sendResponse(responseCaptor.capture());
        assertEquals(RestStatus.FORBIDDEN, responseCaptor.getValue().status());
    }

    public void testHandleRequest_whenNoContent_excludesJudgmentRatingsRatingsByDefault() throws Exception {
        RestRequest request = new FakeRestRequest.Builder(NamedXContentRegistry.EMPTY).withMethod(RestRequest.Method.GET)
            .withPath(JUDGMENTS_URL + "/_search")
            .build();

        restAction.handleRequest(request, channel, client);

        ArgumentCaptor<SearchRequest> requestCaptor = ArgumentCaptor.forClass(SearchRequest.class);
        verify(client, times(1)).execute(eq(SearchJudgmentAction.INSTANCE), requestCaptor.capture(), any());
        SearchRequest captured = requestCaptor.getValue();
        SearchSourceBuilder source = captured.source();
        assertNotNull(source);
        assertNotNull(source.fetchSource());
        assertNotNull(source.fetchSource().excludes());
        assertEquals(1, source.fetchSource().excludes().length);
        assertEquals("judgmentRatings.ratings", source.fetchSource().excludes()[0]);
    }

    public void testHandleRequest_whenQueryWithoutSourceFilter_excludesJudgmentRatingsRatingsByDefault() throws Exception {
        RestRequest request = new FakeRestRequest.Builder(xContentRegistry).withMethod(RestRequest.Method.POST)
            .withPath(JUDGMENTS_URL + "/_search")
            .withContent(new BytesArray("{\"query\":{\"term\":{\"status\":\"COMPLETED\"}}}"), XContentType.JSON)
            .build();

        restAction.handleRequest(request, channel, client);

        ArgumentCaptor<SearchRequest> requestCaptor = ArgumentCaptor.forClass(SearchRequest.class);
        verify(client, times(1)).execute(eq(SearchJudgmentAction.INSTANCE), requestCaptor.capture(), any());
        SearchRequest captured = requestCaptor.getValue();
        SearchSourceBuilder source = captured.source();
        assertNotNull(source);
        assertNotNull(source.fetchSource());
        assertNotNull(source.fetchSource().excludes());
        assertEquals(1, source.fetchSource().excludes().length);
        assertEquals("judgmentRatings.ratings", source.fetchSource().excludes()[0]);
    }

    public void testHandleRequest_whenQueryWithExplicitSourceFilter_respectsUserPreference() throws Exception {
        RestRequest request = new FakeRestRequest.Builder(xContentRegistry).withMethod(RestRequest.Method.POST)
            .withPath(JUDGMENTS_URL + "/_search")
            .withContent(new BytesArray("{\"query\":{\"match_all\":{}},\"_source\":{\"includes\":[\"*\"]}}"), XContentType.JSON)
            .build();

        restAction.handleRequest(request, channel, client);

        ArgumentCaptor<SearchRequest> requestCaptor = ArgumentCaptor.forClass(SearchRequest.class);
        verify(client, times(1)).execute(eq(SearchJudgmentAction.INSTANCE), requestCaptor.capture(), any());
        SearchRequest captured = requestCaptor.getValue();
        SearchSourceBuilder source = captured.source();
        assertNotNull(source);
        assertNotNull(source.fetchSource());
        // User specified includes, so our default exclusion should not be applied
        assertNotNull(source.fetchSource().includes());
        assertEquals(1, source.fetchSource().includes().length);
        assertEquals("*", source.fetchSource().includes()[0]);
    }

    public void testHandleRequest_whenQueryExplicitlyIncludesJudgmentRatings_respectsUserPreference() throws Exception {
        RestRequest request = new FakeRestRequest.Builder(xContentRegistry).withMethod(RestRequest.Method.POST)
            .withPath(JUDGMENTS_URL + "/_search")
            .withContent(
                new BytesArray("{\"query\":{\"match_all\":{}},\"_source\":[\"id\",\"name\",\"judgmentRatings\"]}"),
                XContentType.JSON
            )
            .build();

        restAction.handleRequest(request, channel, client);

        ArgumentCaptor<SearchRequest> requestCaptor = ArgumentCaptor.forClass(SearchRequest.class);
        verify(client, times(1)).execute(eq(SearchJudgmentAction.INSTANCE), requestCaptor.capture(), any());
        SearchRequest captured = requestCaptor.getValue();
        SearchSourceBuilder source = captured.source();
        assertNotNull(source);
        assertNotNull(source.fetchSource());
        // User specified specific fields including judgmentRatings
        assertNotNull(source.fetchSource().includes());
        assertTrue(source.fetchSource().includes().length > 0);
    }

    private SearchResponse buildSearchResponse() {
        SearchHit hit = new SearchHit(0);
        hit.sourceRef(new BytesArray("{\"id\":\"judgment-1\"}"));
        SearchHits hits = new SearchHits(new SearchHit[] { hit }, new TotalHits(1, TotalHits.Relation.EQUAL_TO), 1.0f);
        SearchResponseSections sections = new SearchResponseSections(hits, InternalAggregations.EMPTY, null, false, false, null, 1);
        return new SearchResponse(sections, null, 1, 1, 0, 10, ShardSearchFailure.EMPTY_ARRAY, SearchResponse.Clusters.EMPTY);
    }
}
