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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.Before;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.opensearch.action.support.ActionFilters;
import org.opensearch.action.update.UpdateResponse;
import org.opensearch.core.action.ActionListener;
import org.opensearch.searchrelevance.dao.ExperimentDao;
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

    public void testPatchExperimentWithNameAndDescription() {
        String experimentId = "test-experiment-id";
        String newName = "Updated Name";
        String newDescription = "Updated Description";

        PatchExperimentRequest request = new PatchExperimentRequest(experimentId, newName, newDescription);

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
        verify(experimentDao).patchExperiment(eq(experimentId), eq(newName), eq(null), any(ActionListener.class));
    }

    public void testPatchExperimentWithDescriptionOnly() {
        String experimentId = "test-experiment-id";
        String newDescription = "Updated Description";

        PatchExperimentRequest request = new PatchExperimentRequest(experimentId, null, newDescription);

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
        verify(experimentDao).patchExperiment(eq(experimentId), eq(null), eq(newDescription), any(ActionListener.class));
    }

    public void testPatchExperimentFailure() {
        String experimentId = "test-experiment-id";
        PatchExperimentRequest request = new PatchExperimentRequest(experimentId, "New Name", null);

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
        // Error is propagated as-is, not wrapped
        assertSame(expectedException, exceptionCaptor.getValue());
    }

    public void testPatchExperimentNullRequest() {
        ActionListener<UpdateResponse> responseListener = mock(ActionListener.class);
        transportAction.doExecute(null, null, responseListener);

        ArgumentCaptor<Exception> exceptionCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(responseListener).onFailure(exceptionCaptor.capture());
        assertTrue(exceptionCaptor.getValue().getMessage().contains("Request cannot be null"));
    }

    public void testPatchExperimentNullListener() {
        PatchExperimentRequest request = new PatchExperimentRequest("test-id", "New Name", null);
        try {
            transportAction.doExecute(null, request, null);
            fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("Listener cannot be null"));
        }
    }
}
