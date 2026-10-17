/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */
package org.opensearch.searchrelevance.transport.experiment;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.apache.lucene.search.TotalHits;
import org.junit.Before;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.opensearch.action.search.SearchResponse;
import org.opensearch.action.support.ActionFilters;
import org.opensearch.action.update.UpdateResponse;
import org.opensearch.core.action.ActionListener;
import org.opensearch.search.SearchHit;
import org.opensearch.search.SearchHits;
import org.opensearch.searchrelevance.dao.ExperimentDao;
import org.opensearch.searchrelevance.exception.SearchRelevanceException;
import org.opensearch.test.OpenSearchTestCase;
import org.opensearch.transport.TransportService;

public class PatchExperimentTransportActionTests extends OpenSearchTestCase {

    @Mock
    private TransportService transportService;
    @Mock
    private ActionFilters actionFilters;
    @Mock
    private ExperimentDao experimentDao;

    private PatchExperimentTransportAction transportAction;

    @Before
    public void setup() {
        MockitoAnnotations.openMocks(this);
        transportAction = new PatchExperimentTransportAction(transportService, actionFilters, experimentDao);
    }

    private SearchResponse createMockExperimentResponse(String status) {
        SearchResponse searchResponse = mock(SearchResponse.class);
        SearchHit searchHit = new SearchHit(1);
        searchHit.sourceRef(
            new org.opensearch.core.common.bytes.BytesArray("{\"status\":\"" + status + "\",\"type\":\"PAIRWISE_COMPARISON\"}")
        );
        SearchHits searchHits = new SearchHits(new SearchHit[] { searchHit }, new TotalHits(1, TotalHits.Relation.EQUAL_TO), 1.0f);
        when(searchResponse.getHits()).thenReturn(searchHits);
        return searchResponse;
    }

    private SearchResponse createEmptySearchResponse() {
        SearchResponse searchResponse = mock(SearchResponse.class);
        SearchHits searchHits = new SearchHits(new SearchHit[0], new TotalHits(0, TotalHits.Relation.EQUAL_TO), 0.0f);
        when(searchResponse.getHits()).thenReturn(searchHits);
        return searchResponse;
    }

    public void testPatchExperimentWithNameAndDescription() {
        String experimentId = "test-experiment-id";
        String newName = "Updated Name";
        String newDescription = "Updated Description";

        PatchExperimentRequest request = new PatchExperimentRequest(experimentId, newName, newDescription);

        // Mock getExperiment to return a COMPLETED experiment
        SearchResponse experimentResponse = createMockExperimentResponse("COMPLETED");
        doAnswer(invocation -> {
            ActionListener<SearchResponse> listener = invocation.getArgument(1);
            listener.onResponse(experimentResponse);
            return null;
        }).when(experimentDao).getExperiment(eq(experimentId), any(ActionListener.class));

        UpdateResponse mockUpdateResponse = mock(UpdateResponse.class);
        when(mockUpdateResponse.getId()).thenReturn(experimentId);
        doAnswer(invocation -> {
            ActionListener<UpdateResponse> listener = invocation.getArgument(3);
            listener.onResponse(mockUpdateResponse);
            return null;
        }).when(experimentDao).patchExperiment(eq(experimentId), eq(newName), eq(newDescription), any(ActionListener.class));

        ActionListener<UpdateResponse> responseListener = mock(ActionListener.class);
        transportAction.doExecute(null, request, responseListener);

        verify(responseListener).onResponse(mockUpdateResponse);
        verify(experimentDao).patchExperiment(eq(experimentId), eq(newName), eq(newDescription), any(ActionListener.class));
    }

    public void testPatchExperimentWithNameOnly() {
        String experimentId = "test-experiment-id";
        String newName = "Updated Name";

        PatchExperimentRequest request = new PatchExperimentRequest(experimentId, newName, null);

        SearchResponse experimentResponse = createMockExperimentResponse("COMPLETED");
        doAnswer(invocation -> {
            ActionListener<SearchResponse> listener = invocation.getArgument(1);
            listener.onResponse(experimentResponse);
            return null;
        }).when(experimentDao).getExperiment(eq(experimentId), any(ActionListener.class));

        UpdateResponse mockUpdateResponse = mock(UpdateResponse.class);
        when(mockUpdateResponse.getId()).thenReturn(experimentId);
        doAnswer(invocation -> {
            ActionListener<UpdateResponse> listener = invocation.getArgument(3);
            listener.onResponse(mockUpdateResponse);
            return null;
        }).when(experimentDao).patchExperiment(eq(experimentId), eq(newName), eq(null), any(ActionListener.class));

        ActionListener<UpdateResponse> responseListener = mock(ActionListener.class);
        transportAction.doExecute(null, request, responseListener);

        verify(responseListener).onResponse(mockUpdateResponse);
    }

    public void testPatchExperimentWithDescriptionOnly() {
        String experimentId = "test-experiment-id";
        String newDescription = "Updated Description";

        PatchExperimentRequest request = new PatchExperimentRequest(experimentId, null, newDescription);

        SearchResponse experimentResponse = createMockExperimentResponse("COMPLETED");
        doAnswer(invocation -> {
            ActionListener<SearchResponse> listener = invocation.getArgument(1);
            listener.onResponse(experimentResponse);
            return null;
        }).when(experimentDao).getExperiment(eq(experimentId), any(ActionListener.class));

        UpdateResponse mockUpdateResponse = mock(UpdateResponse.class);
        when(mockUpdateResponse.getId()).thenReturn(experimentId);
        doAnswer(invocation -> {
            ActionListener<UpdateResponse> listener = invocation.getArgument(3);
            listener.onResponse(mockUpdateResponse);
            return null;
        }).when(experimentDao).patchExperiment(eq(experimentId), eq(null), eq(newDescription), any(ActionListener.class));

        ActionListener<UpdateResponse> responseListener = mock(ActionListener.class);
        transportAction.doExecute(null, request, responseListener);

        verify(responseListener).onResponse(mockUpdateResponse);
    }

    public void testPatchExperimentRejectsProcessingStatus() {
        String experimentId = "test-experiment-id";
        PatchExperimentRequest request = new PatchExperimentRequest(experimentId, "New Name", null);

        // Mock getExperiment to return a PROCESSING experiment
        SearchResponse experimentResponse = createMockExperimentResponse("PROCESSING");
        doAnswer(invocation -> {
            ActionListener<SearchResponse> listener = invocation.getArgument(1);
            listener.onResponse(experimentResponse);
            return null;
        }).when(experimentDao).getExperiment(eq(experimentId), any(ActionListener.class));

        ActionListener<UpdateResponse> responseListener = mock(ActionListener.class);
        transportAction.doExecute(null, request, responseListener);

        // Should fail with CONFLICT status
        ArgumentCaptor<Exception> exceptionCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(responseListener).onFailure(exceptionCaptor.capture());
        assertTrue(exceptionCaptor.getValue() instanceof SearchRelevanceException);
        assertTrue(exceptionCaptor.getValue().getMessage().contains("PROCESSING"));

        // Should never attempt to patch
        verify(experimentDao, never()).patchExperiment(any(), any(), any(), any());
    }

    public void testPatchExperimentAllowsCompletedStatus() {
        String experimentId = "test-experiment-id";
        PatchExperimentRequest request = new PatchExperimentRequest(experimentId, "New Name", null);

        SearchResponse experimentResponse = createMockExperimentResponse("COMPLETED");
        doAnswer(invocation -> {
            ActionListener<SearchResponse> listener = invocation.getArgument(1);
            listener.onResponse(experimentResponse);
            return null;
        }).when(experimentDao).getExperiment(eq(experimentId), any(ActionListener.class));

        UpdateResponse mockUpdateResponse = mock(UpdateResponse.class);
        doAnswer(invocation -> {
            ActionListener<UpdateResponse> listener = invocation.getArgument(3);
            listener.onResponse(mockUpdateResponse);
            return null;
        }).when(experimentDao).patchExperiment(eq(experimentId), eq("New Name"), eq(null), any(ActionListener.class));

        ActionListener<UpdateResponse> responseListener = mock(ActionListener.class);
        transportAction.doExecute(null, request, responseListener);

        verify(responseListener).onResponse(mockUpdateResponse);
    }

    public void testPatchExperimentAllowsErrorStatus() {
        String experimentId = "test-experiment-id";
        PatchExperimentRequest request = new PatchExperimentRequest(experimentId, "New Name", null);

        SearchResponse experimentResponse = createMockExperimentResponse("ERROR");
        doAnswer(invocation -> {
            ActionListener<SearchResponse> listener = invocation.getArgument(1);
            listener.onResponse(experimentResponse);
            return null;
        }).when(experimentDao).getExperiment(eq(experimentId), any(ActionListener.class));

        UpdateResponse mockUpdateResponse = mock(UpdateResponse.class);
        doAnswer(invocation -> {
            ActionListener<UpdateResponse> listener = invocation.getArgument(3);
            listener.onResponse(mockUpdateResponse);
            return null;
        }).when(experimentDao).patchExperiment(eq(experimentId), eq("New Name"), eq(null), any(ActionListener.class));

        ActionListener<UpdateResponse> responseListener = mock(ActionListener.class);
        transportAction.doExecute(null, request, responseListener);

        verify(responseListener).onResponse(mockUpdateResponse);
    }

    public void testPatchExperimentNotFound() {
        String experimentId = "nonexistent-id";
        PatchExperimentRequest request = new PatchExperimentRequest(experimentId, "New Name", null);

        SearchResponse emptyResponse = createEmptySearchResponse();
        doAnswer(invocation -> {
            ActionListener<SearchResponse> listener = invocation.getArgument(1);
            listener.onResponse(emptyResponse);
            return null;
        }).when(experimentDao).getExperiment(eq(experimentId), any(ActionListener.class));

        ActionListener<UpdateResponse> responseListener = mock(ActionListener.class);
        transportAction.doExecute(null, request, responseListener);

        ArgumentCaptor<Exception> exceptionCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(responseListener).onFailure(exceptionCaptor.capture());
        assertTrue(exceptionCaptor.getValue().getMessage().contains("not found"));
    }

    public void testPatchExperimentFailure() {
        String experimentId = "test-experiment-id";
        PatchExperimentRequest request = new PatchExperimentRequest(experimentId, "New Name", null);

        SearchResponse experimentResponse = createMockExperimentResponse("COMPLETED");
        doAnswer(invocation -> {
            ActionListener<SearchResponse> listener = invocation.getArgument(1);
            listener.onResponse(experimentResponse);
            return null;
        }).when(experimentDao).getExperiment(eq(experimentId), any(ActionListener.class));

        Exception expectedException = new RuntimeException("Update failed");
        doAnswer(invocation -> {
            ActionListener<UpdateResponse> listener = invocation.getArgument(3);
            listener.onFailure(expectedException);
            return null;
        }).when(experimentDao).patchExperiment(eq(experimentId), eq("New Name"), eq(null), any(ActionListener.class));

        ActionListener<UpdateResponse> responseListener = mock(ActionListener.class);
        transportAction.doExecute(null, request, responseListener);

        ArgumentCaptor<Exception> exceptionCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(responseListener).onFailure(exceptionCaptor.capture());
        assertSame(expectedException, exceptionCaptor.getValue());
    }

    public void testPatchExperimentGetExperimentFailure() {
        String experimentId = "test-experiment-id";
        PatchExperimentRequest request = new PatchExperimentRequest(experimentId, "New Name", null);

        Exception fetchException = new RuntimeException("Fetch failed");
        doAnswer(invocation -> {
            ActionListener<SearchResponse> listener = invocation.getArgument(1);
            listener.onFailure(fetchException);
            return null;
        }).when(experimentDao).getExperiment(eq(experimentId), any(ActionListener.class));

        ActionListener<UpdateResponse> responseListener = mock(ActionListener.class);
        transportAction.doExecute(null, request, responseListener);

        ArgumentCaptor<Exception> exceptionCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(responseListener).onFailure(exceptionCaptor.capture());
        assertTrue(exceptionCaptor.getValue() instanceof SearchRelevanceException);
        assertTrue(exceptionCaptor.getValue().getMessage().contains("Failed to fetch experiment"));
    }
}
