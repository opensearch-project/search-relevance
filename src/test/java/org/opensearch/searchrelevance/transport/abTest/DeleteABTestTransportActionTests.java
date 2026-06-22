/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */
package org.opensearch.searchrelevance.transport.abTest;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.util.Collections;

import org.apache.lucene.search.TotalHits;
import org.junit.Before;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.opensearch.ResourceNotFoundException;
import org.opensearch.action.delete.DeleteResponse;
import org.opensearch.action.search.SearchResponse;
import org.opensearch.action.support.ActionFilters;
import org.opensearch.core.action.ActionListener;
import org.opensearch.core.rest.RestStatus;
import org.opensearch.index.reindex.BulkByScrollResponse;
import org.opensearch.search.SearchHit;
import org.opensearch.search.SearchHits;
import org.opensearch.searchrelevance.dao.ABTestDao;
import org.opensearch.searchrelevance.exception.SearchRelevanceException;
import org.opensearch.test.OpenSearchTestCase;
import org.opensearch.transport.TransportService;

public class DeleteABTestTransportActionTests extends OpenSearchTestCase {

    @Mock
    private TransportService transportService;
    @Mock
    private ActionFilters actionFilters;
    @Mock
    private ABTestDao abTestDao;

    private DeleteABTestTransportAction transportAction;

    @Before
    public void setup() {
        MockitoAnnotations.openMocks(this);
        transportAction = new DeleteABTestTransportAction(transportService, actionFilters, abTestDao);
    }

    private void mockGetABTestExists() {
        doAnswer(invocation -> {
            ActionListener<SearchResponse> listener = invocation.getArgument(1);
            SearchHit hit = new SearchHit(1, "my-test", Collections.emptyMap(), Collections.emptyMap());
            SearchHits hits = new SearchHits(new SearchHit[] { hit }, new TotalHits(1, TotalHits.Relation.EQUAL_TO), 1.0f);
            SearchResponse response = mock(SearchResponse.class);
            org.mockito.Mockito.when(response.getHits()).thenReturn(hits);
            listener.onResponse(response);
            return null;
        }).when(abTestDao).getABTest(any(String.class), any(ActionListener.class));
    }

    private void mockGetABTestNotFound() {
        doAnswer(invocation -> {
            ActionListener<SearchResponse> listener = invocation.getArgument(1);
            listener.onFailure(new ResourceNotFoundException("Document not found: my-test", RestStatus.NOT_FOUND));
            return null;
        }).when(abTestDao).getABTest(any(String.class), any(ActionListener.class));
    }

    private void mockDeleteSnapshots() {
        doAnswer(invocation -> {
            ActionListener<BulkByScrollResponse> listener = invocation.getArgument(1);
            BulkByScrollResponse mockResponse = mock(BulkByScrollResponse.class);
            listener.onResponse(mockResponse);
            return null;
        }).when(abTestDao).deleteSnapshotsByTestId(any(String.class), any(ActionListener.class));
    }

    private void mockDeleteABTest() {
        doAnswer(invocation -> {
            ActionListener<DeleteResponse> listener = invocation.getArgument(1);
            DeleteResponse mockResponse = mock(DeleteResponse.class);
            listener.onResponse(mockResponse);
            return null;
        }).when(abTestDao).deleteABTest(any(String.class), any(ActionListener.class));
    }

    /**
     * Happy path: test exists, deletes snapshots then live doc
     */
    @SuppressWarnings("unchecked")
    public void testHappyPath() {
        mockGetABTestExists();
        mockDeleteSnapshots();
        mockDeleteABTest();

        DeleteABTestRequest request = new DeleteABTestRequest("my-test");
        ActionListener<DeleteResponse> listener = mock(ActionListener.class);

        transportAction.doExecute(null, request, listener);

        verify(abTestDao).deleteSnapshotsByTestId(eq("my-test"), any(ActionListener.class));
        verify(abTestDao).deleteABTest(eq("my-test"), any(ActionListener.class));
        verify(listener).onResponse(any(DeleteResponse.class));
    }

    /**
     * Test not found returns 404
     */
    @SuppressWarnings("unchecked")
    public void testTestNotFound() {
        mockGetABTestNotFound();

        DeleteABTestRequest request = new DeleteABTestRequest("non-existent");
        ActionListener<DeleteResponse> listener = mock(ActionListener.class);

        transportAction.doExecute(null, request, listener);

        ArgumentCaptor<Exception> errorCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(listener).onFailure(errorCaptor.capture());
        assertTrue(errorCaptor.getValue().getMessage().contains("not found"));
        verify(abTestDao, never()).deleteSnapshotsByTestId(any(), any());
        verify(abTestDao, never()).deleteABTest(any(), any());
    }

    /**
     * Null request returns error
     */
    @SuppressWarnings("unchecked")
    public void testNullRequest() {
        ActionListener<DeleteResponse> listener = mock(ActionListener.class);

        transportAction.doExecute(null, null, listener);

        ArgumentCaptor<Exception> errorCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(listener).onFailure(errorCaptor.capture());
        assertTrue(errorCaptor.getValue().getMessage().contains("cannot be null"));
    }

    /**
     * Null testId returns error
     */
    @SuppressWarnings("unchecked")
    public void testNullTestId() {
        DeleteABTestRequest request = new DeleteABTestRequest((String) null);
        ActionListener<DeleteResponse> listener = mock(ActionListener.class);

        transportAction.doExecute(null, request, listener);

        ArgumentCaptor<Exception> errorCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(listener).onFailure(errorCaptor.capture());
        assertTrue(errorCaptor.getValue().getMessage().contains("cannot be null"));
    }

    /**
     * Snapshot deletion failure propagates error
     */
    @SuppressWarnings("unchecked")
    public void testSnapshotDeletionFailure() {
        mockGetABTestExists();

        doAnswer(invocation -> {
            ActionListener<BulkByScrollResponse> listener = invocation.getArgument(1);
            listener.onFailure(new SearchRelevanceException("Failed to delete snapshots", RestStatus.INTERNAL_SERVER_ERROR));
            return null;
        }).when(abTestDao).deleteSnapshotsByTestId(any(String.class), any(ActionListener.class));

        DeleteABTestRequest request = new DeleteABTestRequest("my-test");
        ActionListener<DeleteResponse> listener = mock(ActionListener.class);

        transportAction.doExecute(null, request, listener);

        verify(listener).onFailure(any(Exception.class));
        verify(abTestDao, never()).deleteABTest(any(), any());
    }
}
