/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */
package org.opensearch.searchrelevance.indices;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.opensearch.searchrelevance.indices.SearchRelevanceIndices.QUERY_SET;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.lucene.search.TotalHits;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.opensearch.ResourceNotFoundException;
import org.opensearch.action.DocWriteRequest;
import org.opensearch.action.DocWriteResponse;
import org.opensearch.action.StepListener;
import org.opensearch.action.admin.indices.create.CreateIndexRequest;
import org.opensearch.action.admin.indices.create.CreateIndexResponse;
import org.opensearch.action.delete.DeleteRequestBuilder;
import org.opensearch.action.delete.DeleteResponse;
import org.opensearch.action.index.IndexRequestBuilder;
import org.opensearch.action.search.SearchRequest;
import org.opensearch.action.search.SearchResponse;
import org.opensearch.action.support.WriteRequest;
import org.opensearch.cluster.ClusterState;
import org.opensearch.cluster.metadata.Metadata;
import org.opensearch.cluster.service.ClusterService;
import org.opensearch.common.settings.Settings;
import org.opensearch.common.util.concurrent.ThreadContext;
import org.opensearch.common.xcontent.XContentFactory;
import org.opensearch.core.action.ActionListener;
import org.opensearch.core.common.bytes.BytesReference;
import org.opensearch.core.index.shard.ShardId;
import org.opensearch.core.rest.RestStatus;
import org.opensearch.core.xcontent.ToXContent;
import org.opensearch.core.xcontent.XContentBuilder;
import org.opensearch.index.query.QueryBuilders;
import org.opensearch.search.SearchHit;
import org.opensearch.search.SearchHits;
import org.opensearch.search.builder.SearchSourceBuilder;
import org.opensearch.search.sort.SortOrder;
import org.opensearch.searchrelevance.exception.SearchRelevanceException;
import org.opensearch.searchrelevance.model.QuerySet;
import org.opensearch.test.OpenSearchTestCase;
import org.opensearch.threadpool.ThreadPool;
import org.opensearch.transport.client.AdminClient;
import org.opensearch.transport.client.Client;
import org.opensearch.transport.client.IndicesAdminClient;

public class SearchRelevanceIndicesManagerTests extends OpenSearchTestCase {
    @Mock
    private Client client;
    @Mock
    private ClusterService clusterService;

    @Mock
    private ClusterState clusterState;
    @Mock
    private Metadata metadata;
    @Mock
    private AdminClient adminClient;
    @Mock
    private ThreadPool threadPool;
    @Mock
    private IndicesAdminClient indicesAdminClient;

    private AutoCloseable openMocks;
    private SearchRelevanceIndicesManager indicesManager;
    private ThreadContext threadContext;

    @Override
    public void setUp() throws Exception {
        super.setUp();
        openMocks = MockitoAnnotations.openMocks(this);

        // Setup ThreadContext
        Settings settings = Settings.builder().build();
        threadContext = new ThreadContext(settings);

        // Setup mock chain
        when(client.threadPool()).thenReturn(threadPool);
        when(threadPool.getThreadContext()).thenReturn(threadContext);
        when(clusterService.state()).thenReturn(clusterState);
        when(clusterState.metadata()).thenReturn(metadata);
        when(client.admin()).thenReturn(adminClient);
        when(adminClient.indices()).thenReturn(indicesAdminClient);

        indicesManager = new SearchRelevanceIndicesManager(clusterService, client);
    }

    @Override
    public void tearDown() throws Exception {
        super.tearDown();
        openMocks.close();
    }

    public void testCreateIndexIfAbsentWhenIndexDoesNotExist() {
        when(metadata.hasIndex(QUERY_SET.getIndexName())).thenReturn(false);
        StepListener<Void> stepListener = new StepListener<>();

        indicesManager.createIndexIfAbsent(QUERY_SET, stepListener);

        ArgumentCaptor<CreateIndexRequest> requestCaptor = ArgumentCaptor.forClass(CreateIndexRequest.class);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<ActionListener<CreateIndexResponse>> listenerCaptor = ArgumentCaptor.forClass(ActionListener.class);
        verify(indicesAdminClient).create(requestCaptor.capture(), listenerCaptor.capture());

        CreateIndexRequest capturedRequest = requestCaptor.getValue();
        assertEquals(QUERY_SET.getIndexName(), capturedRequest.index());
        assertEquals(QUERY_SET.getMapping(), capturedRequest.mappings());

        CreateIndexResponse response = new CreateIndexResponse(true, true, QUERY_SET.getIndexName());
        listenerCaptor.getValue().onResponse(response);
    }

    public void testCreateIndexIfAbsentWhenIndexExists() {
        when(metadata.hasIndex(QUERY_SET.getIndexName())).thenReturn(true);
        StepListener<Void> stepListener = new StepListener<>();

        indicesManager.createIndexIfAbsent(QUERY_SET, stepListener);

        // create index should be not be called
        verify(indicesAdminClient, never()).create(any(CreateIndexRequest.class), any());
    }

    public void testCreateIndexIfAbsentWhenCreationFails() {
        when(metadata.hasIndex(QUERY_SET.getIndexName())).thenReturn(false);
        StepListener<Void> stepListener = new StepListener<>();

        indicesManager.createIndexIfAbsent(QUERY_SET, stepListener);

        ArgumentCaptor<CreateIndexRequest> requestCaptor = ArgumentCaptor.forClass(CreateIndexRequest.class);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<ActionListener<CreateIndexResponse>> listenerCaptor = ArgumentCaptor.forClass(ActionListener.class);
        verify(indicesAdminClient).create(requestCaptor.capture(), listenerCaptor.capture());

        CreateIndexRequest capturedRequest = requestCaptor.getValue();
        assertEquals(QUERY_SET.getIndexName(), capturedRequest.index());
        assertEquals(QUERY_SET.getMapping(), capturedRequest.mappings());

        Exception exception = new RuntimeException("Creation failed");
        listenerCaptor.getValue().onFailure(exception);
    }

    public void testPutDocWhenSucceeded() throws IOException {
        QuerySet querySet = new QuerySet("test_id", "test_name", "test_description", "test_timestamp", "test_sampling", List.of());
        XContentBuilder xContentBuilder = querySet.toXContent(XContentFactory.jsonBuilder(), ToXContent.EMPTY_PARAMS);

        IndexRequestBuilder indexRequestBuilder = mock(IndexRequestBuilder.class);
        when(client.prepareIndex(QUERY_SET.getIndexName())).thenReturn(indexRequestBuilder);
        when(indexRequestBuilder.setId("test_id")).thenReturn(indexRequestBuilder);
        when(indexRequestBuilder.setOpType(DocWriteRequest.OpType.CREATE)).thenReturn(indexRequestBuilder);
        when(indexRequestBuilder.setRefreshPolicy(WriteRequest.RefreshPolicy.IMMEDIATE)).thenReturn(indexRequestBuilder);
        when(indexRequestBuilder.setSource(xContentBuilder)).thenReturn(indexRequestBuilder);

        @SuppressWarnings("unchecked")
        ActionListener<SearchResponse> listener = mock(ActionListener.class);
        indicesManager.putDoc("test_id", xContentBuilder, QUERY_SET, listener);

        verify(indexRequestBuilder).execute(any(ActionListener.class));
        verify(indexRequestBuilder).setId("test_id");
        verify(indexRequestBuilder).setOpType(DocWriteRequest.OpType.CREATE);
        verify(indexRequestBuilder).setRefreshPolicy(WriteRequest.RefreshPolicy.IMMEDIATE);
        verify(indexRequestBuilder).setSource(xContentBuilder);
    }

    public void testPutDocWhenFailed() throws IOException {
        QuerySet querySet = new QuerySet("test_id", "test_name", "test_description", "test_timestamp", "test_sampling", List.of());
        XContentBuilder xContentBuilder = querySet.toXContent(XContentFactory.jsonBuilder(), ToXContent.EMPTY_PARAMS);

        when(client.prepareIndex(QUERY_SET.getIndexName())).thenThrow(
            new SearchRelevanceException("No such index", RestStatus.INTERNAL_SERVER_ERROR)
        );

        @SuppressWarnings("unchecked")
        ActionListener<SearchResponse> listener = mock(ActionListener.class);
        SearchRelevanceException exception = assertThrows(
            SearchRelevanceException.class,
            () -> indicesManager.putDoc("test_id", xContentBuilder, QUERY_SET, listener)
        );

        assertEquals(RestStatus.INTERNAL_SERVER_ERROR, exception.status());
        assertTrue(exception.getMessage().contains("Failed to store doc"));
    }

    public void testGetDocByDocIdWhenSucceeded() throws IOException {
        String docId = "test_id";
        QuerySet querySet = new QuerySet(docId, "test_name", "test_description", "test_timestamp", "test_sampling", List.of());
        XContentBuilder xContentBuilder = querySet.toXContent(XContentFactory.jsonBuilder(), ToXContent.EMPTY_PARAMS);

        Map<String, Object> sourceMap = new HashMap<>();
        sourceMap.put("id", docId);
        sourceMap.put("name", "test_name");
        sourceMap.put("description", "test_description");
        SearchHit[] hits = new SearchHit[] { new SearchHit(1, docId, Map.of(), Map.of()).sourceRef(BytesReference.bytes(xContentBuilder)) };
        SearchHits searchHits = new SearchHits(hits, new TotalHits(1, TotalHits.Relation.EQUAL_TO), 1.0f);

        SearchResponse searchResponse = mock(SearchResponse.class);
        when(searchResponse.getHits()).thenReturn(searchHits);

        doAnswer(invocation -> {
            ActionListener<SearchResponse> listener = invocation.getArgument(1);
            listener.onResponse(searchResponse);
            return null;
        }).when(client).search(any(SearchRequest.class), any(ActionListener.class));

        @SuppressWarnings("unchecked")
        ActionListener<SearchResponse> listener = mock(ActionListener.class);
        indicesManager.getDocByDocId(docId, QUERY_SET, listener);

        ArgumentCaptor<SearchRequest> requestCaptor = ArgumentCaptor.forClass(SearchRequest.class);
        verify(client).search(requestCaptor.capture(), any(ActionListener.class));

        SearchRequest capturedRequest = requestCaptor.getValue();
        assertEquals(QUERY_SET.getIndexName(), capturedRequest.indices()[0]);
        assertEquals(QueryBuilders.termQuery("_id", docId).toString(), capturedRequest.source().query().toString());

        ArgumentCaptor<SearchResponse> responseCaptor = ArgumentCaptor.forClass(SearchResponse.class);
        verify(listener).onResponse(responseCaptor.capture());

        SearchResponse capturedResponse = responseCaptor.getValue();
        assertEquals(searchResponse, capturedResponse);
    }

    public void testGetDocByDocIdWhenFailed() {
        String docId = "non_existent_id";

        SearchHit[] emptyHits = new SearchHit[0];
        SearchHits searchHits = new SearchHits(emptyHits, new TotalHits(0, TotalHits.Relation.EQUAL_TO), 0.0f);

        SearchResponse emptyResponse = mock(SearchResponse.class);
        when(emptyResponse.getHits()).thenReturn(searchHits);

        doAnswer(invocation -> {
            ActionListener<SearchResponse> listener = invocation.getArgument(1);
            listener.onResponse(emptyResponse);
            return null;
        }).when(client).search(any(SearchRequest.class), any(ActionListener.class));

        @SuppressWarnings("unchecked")
        ActionListener<SearchResponse> listener = mock(ActionListener.class);
        indicesManager.getDocByDocId(docId, QUERY_SET, listener);

        ArgumentCaptor<Exception> exceptionCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(listener).onFailure(exceptionCaptor.capture());

        Exception capturedException = exceptionCaptor.getValue();
        assertTrue(capturedException instanceof ResourceNotFoundException);
        assertEquals("Document not found: " + docId, capturedException.getMessage());
    }

    public void testListDocsWhenSucceeded() throws IOException {
        QuerySet querySet1 = new QuerySet("id1", "name1", "desc1", "timestamp1", "sampling1", List.of());
        QuerySet querySet2 = new QuerySet("id2", "name2", "desc2", "timestamp2", "sampling2", List.of());

        SearchHit[] hits = new SearchHit[] {
            new SearchHit(1, "id1", Map.of(), Map.of()).sourceRef(
                BytesReference.bytes(querySet1.toXContent(XContentFactory.jsonBuilder(), ToXContent.EMPTY_PARAMS))
            ),
            new SearchHit(2, "id2", Map.of(), Map.of()).sourceRef(
                BytesReference.bytes(querySet2.toXContent(XContentFactory.jsonBuilder(), ToXContent.EMPTY_PARAMS))
            ) };
        SearchHits searchHits = new SearchHits(hits, new TotalHits(2, TotalHits.Relation.EQUAL_TO), 1.0f);

        SearchResponse searchResponse = mock(SearchResponse.class);
        when(searchResponse.getHits()).thenReturn(searchHits);

        SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder().query(QueryBuilders.matchAllQuery())
            .from(0)
            .size(10)
            .sort("timestamp", SortOrder.DESC);

        doAnswer(invocation -> {
            ActionListener<SearchResponse> listener = invocation.getArgument(1);
            listener.onResponse(searchResponse);
            return null;
        }).when(client).search(any(SearchRequest.class), any(ActionListener.class));

        @SuppressWarnings("unchecked")
        ActionListener<SearchResponse> listener = mock(ActionListener.class);
        indicesManager.listDocsBySearchRequest(searchSourceBuilder, QUERY_SET, listener);

        ArgumentCaptor<SearchRequest> requestCaptor = ArgumentCaptor.forClass(SearchRequest.class);
        verify(client).search(requestCaptor.capture(), any(ActionListener.class));

        SearchRequest capturedRequest = requestCaptor.getValue();
        assertEquals(QUERY_SET.getIndexName(), capturedRequest.indices()[0]);
        assertEquals(searchSourceBuilder, capturedRequest.source());
        assertEquals(QueryBuilders.matchAllQuery().toString(), capturedRequest.source().query().toString());

        ArgumentCaptor<SearchResponse> responseCaptor = ArgumentCaptor.forClass(SearchResponse.class);
        verify(listener).onResponse(responseCaptor.capture());

        SearchResponse capturedResponse = responseCaptor.getValue();
        assertEquals(2, capturedResponse.getHits().getTotalHits().value());
    }

    public void testListDocsWhenFailed() {
        SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder().query(QueryBuilders.matchAllQuery()).from(0).size(10);

        doAnswer(invocation -> {
            ActionListener<SearchResponse> listener = invocation.getArgument(1);
            listener.onFailure(new RuntimeException("Search operation failed"));
            return null;
        }).when(client).search(any(SearchRequest.class), any(ActionListener.class));

        @SuppressWarnings("unchecked")
        ActionListener<SearchResponse> listener = mock(ActionListener.class);
        indicesManager.listDocsBySearchRequest(searchSourceBuilder, QUERY_SET, listener);

        ArgumentCaptor<Exception> exceptionCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(listener).onFailure(exceptionCaptor.capture());

        Exception capturedException = exceptionCaptor.getValue();
        assertTrue(capturedException instanceof SearchRelevanceException);
        assertEquals("Failed to list documents", capturedException.getMessage());
        assertEquals(RestStatus.INTERNAL_SERVER_ERROR, ((SearchRelevanceException) capturedException).status());
    }

    public void testDeleteDocByDocIdWhenSucceeded() {
        String docId = "test_id";

        DeleteRequestBuilder deleteRequestBuilder = mock(DeleteRequestBuilder.class);
        when(client.prepareDelete(QUERY_SET.getIndexName(), docId)).thenReturn(deleteRequestBuilder);
        when(deleteRequestBuilder.setRefreshPolicy(WriteRequest.RefreshPolicy.IMMEDIATE)).thenReturn(deleteRequestBuilder);

        DeleteResponse deleteResponse = new DeleteResponse(new ShardId(QUERY_SET.getIndexName(), "_na_", 0), docId, 1L, 1L, 1L, true);

        doAnswer(invocation -> {
            ActionListener<DeleteResponse> listener = invocation.getArgument(0);
            listener.onResponse(deleteResponse);
            return null;
        }).when(deleteRequestBuilder).execute(any(ActionListener.class));

        @SuppressWarnings("unchecked")
        ActionListener<DeleteResponse> listener = mock(ActionListener.class);
        indicesManager.deleteDocByDocId(docId, QUERY_SET, listener);

        verify(deleteRequestBuilder).setRefreshPolicy(WriteRequest.RefreshPolicy.IMMEDIATE);
        verify(deleteRequestBuilder).execute(any(ActionListener.class));

        ArgumentCaptor<DeleteResponse> responseCaptor = ArgumentCaptor.forClass(DeleteResponse.class);
        verify(listener).onResponse(responseCaptor.capture());

        DeleteResponse capturedResponse = responseCaptor.getValue();
        assertEquals(docId, capturedResponse.getId());
        assertTrue(capturedResponse.getResult() == DocWriteResponse.Result.DELETED);
    }

    public void testDeleteDocByDocIdWhenDocIdNotFound() {
        String docId = "non_existent_id";

        DeleteRequestBuilder deleteRequestBuilder = mock(DeleteRequestBuilder.class);
        when(client.prepareDelete(QUERY_SET.getIndexName(), docId)).thenReturn(deleteRequestBuilder);
        when(deleteRequestBuilder.setRefreshPolicy(WriteRequest.RefreshPolicy.IMMEDIATE)).thenReturn(deleteRequestBuilder);

        DeleteResponse deleteResponse = new DeleteResponse(new ShardId(QUERY_SET.getIndexName(), "_na_", 0), docId, -1L, 1L, 1L, false);

        doAnswer(invocation -> {
            ActionListener<DeleteResponse> listener = invocation.getArgument(0);
            listener.onResponse(deleteResponse);
            return null;
        }).when(deleteRequestBuilder).execute(any(ActionListener.class));

        @SuppressWarnings("unchecked")
        ActionListener<DeleteResponse> listener = mock(ActionListener.class);
        indicesManager.deleteDocByDocId(docId, QUERY_SET, listener);

        ArgumentCaptor<DeleteResponse> responseCaptor = ArgumentCaptor.forClass(DeleteResponse.class);
        verify(listener).onResponse(responseCaptor.capture());

        DeleteResponse capturedResponse = responseCaptor.getValue();
        assertEquals(docId, capturedResponse.getId());
        assertTrue(capturedResponse.getResult() == DocWriteResponse.Result.NOT_FOUND);
    }

    public void testDeleteDocByDocIdWhenFailed() {
        String docId = "test_id";

        DeleteRequestBuilder deleteRequestBuilder = mock(DeleteRequestBuilder.class);
        when(client.prepareDelete(QUERY_SET.getIndexName(), docId)).thenReturn(deleteRequestBuilder);
        when(deleteRequestBuilder.setRefreshPolicy(WriteRequest.RefreshPolicy.IMMEDIATE)).thenReturn(deleteRequestBuilder);

        doAnswer(invocation -> {
            ActionListener<DeleteResponse> listener = invocation.getArgument(0);
            listener.onFailure(new RuntimeException("Delete operation failed"));
            return null;
        }).when(deleteRequestBuilder).execute(any(ActionListener.class));

        @SuppressWarnings("unchecked")
        ActionListener<DeleteResponse> listener = mock(ActionListener.class);
        indicesManager.deleteDocByDocId(docId, QUERY_SET, listener);

        ArgumentCaptor<Exception> exceptionCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(listener).onFailure(exceptionCaptor.capture());

        Exception capturedException = exceptionCaptor.getValue();
        assertTrue(capturedException instanceof SearchRelevanceException);
        assertEquals("Failed to delete doc", capturedException.getMessage());
        assertEquals(RestStatus.INTERNAL_SERVER_ERROR, ((SearchRelevanceException) capturedException).status());
    }

}
