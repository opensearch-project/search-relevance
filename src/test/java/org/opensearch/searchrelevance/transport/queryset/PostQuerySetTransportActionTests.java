/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */
package org.opensearch.searchrelevance.transport.queryset;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.Map;

import org.apache.lucene.search.TotalHits;
import org.junit.Before;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.opensearch.Version;
import org.opensearch.action.index.IndexResponse;
import org.opensearch.action.search.SearchRequest;
import org.opensearch.action.search.SearchResponse;
import org.opensearch.action.support.ActionFilters;
import org.opensearch.cluster.ClusterName;
import org.opensearch.cluster.ClusterState;
import org.opensearch.cluster.metadata.AliasMetadata;
import org.opensearch.cluster.metadata.IndexMetadata;
import org.opensearch.cluster.metadata.IndexNameExpressionResolver;
import org.opensearch.cluster.metadata.MappingMetadata;
import org.opensearch.cluster.metadata.Metadata;
import org.opensearch.cluster.service.ClusterService;
import org.opensearch.common.settings.Settings;
import org.opensearch.common.util.concurrent.ThreadContext;
import org.opensearch.core.action.ActionListener;
import org.opensearch.core.rest.RestStatus;
import org.opensearch.index.mapper.MapperService;
import org.opensearch.search.SearchHit;
import org.opensearch.search.SearchHits;
import org.opensearch.searchrelevance.dao.QuerySetDao;
import org.opensearch.searchrelevance.exception.SearchRelevanceException;
import org.opensearch.test.OpenSearchTestCase;
import org.opensearch.transport.TransportService;
import org.opensearch.transport.client.Client;

public class PostQuerySetTransportActionTests extends OpenSearchTestCase {

    private static final Map<String, Object> VALID_QUERIES_MAPPING = Map.of("properties", Map.of("user_query", Map.of("type", "keyword")));
    private static final Map<String, Object> UNRELATED_MAPPING = Map.of("properties", Map.of("something_else", Map.of("type", "keyword")));

    @Mock
    private ClusterService clusterService;
    @Mock
    private TransportService transportService;
    @Mock
    private ActionFilters actionFilters;
    @Mock
    private Client client;
    @Mock
    private QuerySetDao querySetDao;

    private PostQuerySetTransportAction action;

    @Before
    public void setup() {
        MockitoAnnotations.openMocks(this);
        action = new PostQuerySetTransportAction(
            clusterService,
            new IndexNameExpressionResolver(new ThreadContext(Settings.EMPTY)),
            transportService,
            actionFilters,
            client,
            querySetDao
        );
    }

    public void testValidation_QueriesIndexNotFound() {
        when(clusterService.state()).thenReturn(clusterState());

        ActionListener<IndexResponse> responseListener = mock(ActionListener.class);
        action.doExecute(null, querySetRequest(), responseListener);

        ArgumentCaptor<Exception> exceptionCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(responseListener).onFailure(exceptionCaptor.capture());
        verifyNoInteractions(querySetDao);
        verifyNoInteractions(client);

        Exception exception = exceptionCaptor.getValue();
        assertTrue(exception instanceof SearchRelevanceException);
        assertEquals(RestStatus.BAD_REQUEST, ((SearchRelevanceException) exception).status());
        assertTrue(exception.getMessage(), exception.getMessage().contains("UBI queries index [ubi_queries] does not exist"));
        assertTrue(exception.getMessage(), exception.getMessage().contains("ubiQueriesIndex"));
    }

    public void testValidation_QueriesIndexMissingRequiredField() {
        when(clusterService.state()).thenReturn(clusterState(index("ubi_queries", UNRELATED_MAPPING)));

        ActionListener<IndexResponse> responseListener = mock(ActionListener.class);
        action.doExecute(null, querySetRequest(), responseListener);

        ArgumentCaptor<Exception> exceptionCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(responseListener).onFailure(exceptionCaptor.capture());
        verifyNoInteractions(querySetDao);

        Exception exception = exceptionCaptor.getValue();
        assertEquals(RestStatus.BAD_REQUEST, ((SearchRelevanceException) exception).status());
        assertTrue(exception.getMessage(), exception.getMessage().contains("user_query"));
    }

    public void testValidation_QueriesIndexResolvedFromAlias() {
        when(clusterService.state()).thenReturn(
            clusterState(index("opensearch_dashboards_sample_ubi_queries", VALID_QUERIES_MAPPING, "ubi_queries"))
        );
        stubEmptySearchResponse();

        ActionListener<IndexResponse> responseListener = mock(ActionListener.class);
        action.doExecute(null, querySetRequest(), responseListener);

        verify(querySetDao).putQuerySet(any(), any());
    }

    public void testEmptyNameIsRejectedBeforeAnySampling() {
        when(clusterService.state()).thenReturn(clusterState(index("ubi_queries", VALID_QUERIES_MAPPING)));

        ActionListener<IndexResponse> responseListener = mock(ActionListener.class);
        action.doExecute(null, new PostUbiQuerySetRequest("   ", "test description", "random", 10, null), responseListener);

        ArgumentCaptor<Exception> exceptionCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(responseListener).onFailure(exceptionCaptor.capture());
        verifyNoInteractions(client);
        verifyNoInteractions(querySetDao);

        assertEquals(RestStatus.BAD_REQUEST, ((SearchRelevanceException) exceptionCaptor.getValue()).status());
        assertTrue(exceptionCaptor.getValue().getMessage().contains("Name cannot be null or empty"));
    }

    public void testSamplingFailureCompletesListenerExactlyOnce() {
        when(clusterService.state()).thenReturn(clusterState(index("ubi_queries", VALID_QUERIES_MAPPING)));
        doAnswer(invocation -> { throw new IllegalStateException("sampling blew up"); }).when(client)
            .search(any(SearchRequest.class), any(ActionListener.class));

        ActionListener<IndexResponse> responseListener = mock(ActionListener.class);
        action.doExecute(null, querySetRequest(), responseListener);

        ArgumentCaptor<Exception> exceptionCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(responseListener, times(1)).onFailure(exceptionCaptor.capture());
        verify(responseListener, never()).onResponse(any());
        verifyNoInteractions(querySetDao);

        assertEquals(RestStatus.INTERNAL_SERVER_ERROR, ((SearchRelevanceException) exceptionCaptor.getValue()).status());
    }

    public void testDoExecuteDoesNotBlockOnPendingSampling() throws Exception {
        when(clusterService.state()).thenReturn(clusterState(index("ubi_queries", VALID_QUERIES_MAPPING)));
        doAnswer(invocation -> null).when(client).search(any(SearchRequest.class), any(ActionListener.class));

        ActionListener<IndexResponse> responseListener = mock(ActionListener.class);
        Thread caller = new Thread(() -> action.doExecute(null, querySetRequest(), responseListener));
        caller.start();
        caller.join(10_000);

        assertFalse("doExecute blocked on the incomplete sampling future", caller.isAlive());
        verifyNoInteractions(querySetDao);
    }

    private void stubEmptySearchResponse() {
        SearchResponse searchResponse = mock(SearchResponse.class);
        when(searchResponse.getHits()).thenReturn(new SearchHits(new SearchHit[0], new TotalHits(0, TotalHits.Relation.EQUAL_TO), 0.0f));
        doAnswer(invocation -> {
            ActionListener<SearchResponse> listener = invocation.getArgument(1);
            listener.onResponse(searchResponse);
            return null;
        }).when(client).search(any(SearchRequest.class), any(ActionListener.class));
    }

    private static PostUbiQuerySetRequest querySetRequest() {
        return new PostUbiQuerySetRequest("test-query-set", "test description", "random", 10, null);
    }

    private static ClusterState clusterState(IndexMetadata.Builder... indices) {
        Metadata.Builder metadata = Metadata.builder();
        for (IndexMetadata.Builder index : indices) {
            metadata.put(index.build(), false);
        }
        return ClusterState.builder(new ClusterName("test")).metadata(metadata.build()).build();
    }

    private static IndexMetadata.Builder index(String indexName, Map<String, Object> mappingSource, String... aliases) {
        IndexMetadata.Builder builder = IndexMetadata.builder(indexName)
            .settings(
                Settings.builder()
                    .put(IndexMetadata.SETTING_VERSION_CREATED, Version.CURRENT)
                    .put(IndexMetadata.SETTING_NUMBER_OF_SHARDS, 1)
                    .put(IndexMetadata.SETTING_NUMBER_OF_REPLICAS, 0)
            )
            .putMapping(new MappingMetadata(MapperService.SINGLE_MAPPING_NAME, mappingSource));
        for (String alias : aliases) {
            builder.putAlias(AliasMetadata.builder(alias));
        }
        return builder;
    }
}
