/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */
package org.opensearch.searchrelevance.dao;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.util.Map;

import org.junit.Before;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.opensearch.action.update.UpdateResponse;
import org.opensearch.core.action.ActionListener;
import org.opensearch.searchrelevance.indices.SearchRelevanceIndices;
import org.opensearch.searchrelevance.indices.SearchRelevanceIndicesManager;
import org.opensearch.test.OpenSearchTestCase;

public class ExperimentDaoTests extends OpenSearchTestCase {

    @Mock
    private SearchRelevanceIndicesManager searchRelevanceIndicesManager;

    private ExperimentDao experimentDao;

    @Before
    public void setup() {
        MockitoAnnotations.openMocks(this);
        experimentDao = new ExperimentDao(searchRelevanceIndicesManager);
    }

    public void testPatchExperiment_WithNameAndDescription() {
        String experimentId = "test-experiment-id";
        String name = "Test Name";
        String description = "Test Description";

        UpdateResponse mockResponse = mock(UpdateResponse.class);
        doAnswer(invocation -> {
            ActionListener<UpdateResponse> listener = invocation.getArgument(3);
            listener.onResponse(mockResponse);
            return null;
        }).when(searchRelevanceIndicesManager)
            .patchDoc(eq(experimentId), any(Map.class), eq(SearchRelevanceIndices.EXPERIMENT), any(ActionListener.class));

        @SuppressWarnings("unchecked")
        ActionListener<UpdateResponse> listener = mock(ActionListener.class);
        experimentDao.patchExperiment(experimentId, name, description, listener);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> updatesCaptor = ArgumentCaptor.forClass(Map.class);
        verify(searchRelevanceIndicesManager).patchDoc(
            eq(experimentId),
            updatesCaptor.capture(),
            eq(SearchRelevanceIndices.EXPERIMENT),
            any(ActionListener.class)
        );

        Map<String, Object> capturedUpdates = updatesCaptor.getValue();
        assertEquals(2, capturedUpdates.size());
        assertEquals(name, capturedUpdates.get("name"));
        assertEquals(description, capturedUpdates.get("description"));

        verify(listener).onResponse(mockResponse);
    }

    public void testPatchExperiment_WithNameOnly() {
        String experimentId = "test-experiment-id";
        String name = "Test Name";

        UpdateResponse mockResponse = mock(UpdateResponse.class);
        doAnswer(invocation -> {
            ActionListener<UpdateResponse> listener = invocation.getArgument(3);
            listener.onResponse(mockResponse);
            return null;
        }).when(searchRelevanceIndicesManager)
            .patchDoc(eq(experimentId), any(Map.class), eq(SearchRelevanceIndices.EXPERIMENT), any(ActionListener.class));

        @SuppressWarnings("unchecked")
        ActionListener<UpdateResponse> listener = mock(ActionListener.class);
        experimentDao.patchExperiment(experimentId, name, null, listener);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> updatesCaptor = ArgumentCaptor.forClass(Map.class);
        verify(searchRelevanceIndicesManager).patchDoc(
            eq(experimentId),
            updatesCaptor.capture(),
            eq(SearchRelevanceIndices.EXPERIMENT),
            any(ActionListener.class)
        );

        Map<String, Object> capturedUpdates = updatesCaptor.getValue();
        assertEquals(1, capturedUpdates.size());
        assertEquals(name, capturedUpdates.get("name"));
        assertNull(capturedUpdates.get("description"));
    }

    public void testPatchExperiment_WithDescriptionOnly() {
        String experimentId = "test-experiment-id";
        String description = "Test Description";

        UpdateResponse mockResponse = mock(UpdateResponse.class);
        doAnswer(invocation -> {
            ActionListener<UpdateResponse> listener = invocation.getArgument(3);
            listener.onResponse(mockResponse);
            return null;
        }).when(searchRelevanceIndicesManager)
            .patchDoc(eq(experimentId), any(Map.class), eq(SearchRelevanceIndices.EXPERIMENT), any(ActionListener.class));

        @SuppressWarnings("unchecked")
        ActionListener<UpdateResponse> listener = mock(ActionListener.class);
        experimentDao.patchExperiment(experimentId, null, description, listener);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> updatesCaptor = ArgumentCaptor.forClass(Map.class);
        verify(searchRelevanceIndicesManager).patchDoc(
            eq(experimentId),
            updatesCaptor.capture(),
            eq(SearchRelevanceIndices.EXPERIMENT),
            any(ActionListener.class)
        );

        Map<String, Object> capturedUpdates = updatesCaptor.getValue();
        assertEquals(1, capturedUpdates.size());
        assertNull(capturedUpdates.get("name"));
        assertEquals(description, capturedUpdates.get("description"));
    }

    public void testPatchExperiment_Failure() {
        String experimentId = "test-experiment-id";
        String name = "Test Name";

        Exception expectedException = new RuntimeException("Update failed");
        doAnswer(invocation -> {
            ActionListener<UpdateResponse> listener = invocation.getArgument(3);
            listener.onFailure(expectedException);
            return null;
        }).when(searchRelevanceIndicesManager)
            .patchDoc(eq(experimentId), any(Map.class), eq(SearchRelevanceIndices.EXPERIMENT), any(ActionListener.class));

        @SuppressWarnings("unchecked")
        ActionListener<UpdateResponse> listener = mock(ActionListener.class);
        experimentDao.patchExperiment(experimentId, name, null, listener);

        ArgumentCaptor<Exception> exceptionCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(listener).onFailure(exceptionCaptor.capture());
        assertSame(expectedException, exceptionCaptor.getValue());
    }
}
