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

import java.util.Collections;
import java.util.List;

import org.junit.Before;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.opensearch.action.delete.DeleteResponse;
import org.opensearch.action.support.ActionFilters;
import org.opensearch.cluster.service.ClusterService;
import org.opensearch.core.action.ActionListener;
import org.opensearch.core.rest.RestStatus;
import org.opensearch.index.reindex.BulkByScrollResponse;
import org.opensearch.index.reindex.ScrollableHitSource;
import org.opensearch.searchrelevance.dao.EvaluationResultDao;
import org.opensearch.searchrelevance.dao.ExperimentDao;
import org.opensearch.searchrelevance.dao.ExperimentVariantDao;
import org.opensearch.searchrelevance.dao.ScheduledExperimentHistoryDao;
import org.opensearch.searchrelevance.dao.ScheduledJobsDao;
import org.opensearch.searchrelevance.exception.SearchRelevanceException;
import org.opensearch.searchrelevance.transport.OpenSearchDocRequest;
import org.opensearch.test.OpenSearchTestCase;
import org.opensearch.transport.TransportService;

public class DeleteExperimentTransportActionTests extends OpenSearchTestCase {

    @Mock
    private ClusterService clusterService;
    @Mock
    private TransportService transportService;
    @Mock
    private ActionFilters actionFilters;
    @Mock
    private ExperimentDao experimentDao;
    @Mock
    private ExperimentVariantDao experimentVariantDao;
    @Mock
    private EvaluationResultDao evaluationResultDao;
    @Mock
    private ScheduledJobsDao scheduledJobsDao;
    @Mock
    private ScheduledExperimentHistoryDao scheduledExperimentHistoryDao;

    private DeleteExperimentTransportAction transportAction;

    @Before
    public void setup() {
        MockitoAnnotations.openMocks(this);
        transportAction = new DeleteExperimentTransportAction(
            clusterService,
            transportService,
            actionFilters,
            experimentDao,
            evaluationResultDao,
            experimentVariantDao,
            scheduledJobsDao,
            scheduledExperimentHistoryDao
        );
    }

    public void testSuccessfulDeletion() {
        String experimentId = "test-experiment-id";
        OpenSearchDocRequest request = new OpenSearchDocRequest(experimentId);

        DeleteResponse mockDeleteResponse = mock(DeleteResponse.class);
        when(mockDeleteResponse.status()).thenReturn(RestStatus.OK);

        // Mock successful experiment deletion
        doAnswer(invocation -> {
            ActionListener<DeleteResponse> listener = invocation.getArgument(1);
            listener.onResponse(mockDeleteResponse);
            return null;
        }).when(experimentDao).deleteExperiment(eq(experimentId), any(ActionListener.class));

        // Mock successful evaluation results deletion
        BulkByScrollResponse mockBulkResponse = createSuccessfulBulkResponse();
        doAnswer(invocation -> {
            ActionListener<BulkByScrollResponse> listener = invocation.getArgument(1);
            listener.onResponse(mockBulkResponse);
            return null;
        }).when(evaluationResultDao).deleteEvaluationResultByExperimentId(eq(experimentId), any(ActionListener.class));

        // Mock successful experiment variants deletion
        doAnswer(invocation -> {
            ActionListener<BulkByScrollResponse> listener = invocation.getArgument(1);
            listener.onResponse(mockBulkResponse);
            return null;
        }).when(experimentVariantDao).deleteExperimentVariantByExperimentId(eq(experimentId), any(ActionListener.class));

        // Mock successful scheduled job deletion
        doAnswer(invocation -> {
            ActionListener<DeleteResponse> listener = invocation.getArgument(1);
            listener.onResponse(mockDeleteResponse);
            return null;
        }).when(scheduledJobsDao).deleteScheduledJob(eq(experimentId), any(ActionListener.class));

        // Mock successful scheduled experiment history deletion
        doAnswer(invocation -> {
            ActionListener<BulkByScrollResponse> listener = invocation.getArgument(1);
            listener.onResponse(mockBulkResponse);
            return null;
        }).when(scheduledExperimentHistoryDao).deleteScheduledExperimentHistoryByExperimentId(eq(experimentId), any(ActionListener.class));

        ActionListener<DeleteResponse> responseListener = mock(ActionListener.class);
        transportAction.doExecute(null, request, responseListener);

        verify(experimentDao).deleteExperiment(eq(experimentId), any(ActionListener.class));
        verify(evaluationResultDao).deleteEvaluationResultByExperimentId(eq(experimentId), any(ActionListener.class));
        verify(experimentVariantDao).deleteExperimentVariantByExperimentId(eq(experimentId), any(ActionListener.class));
        verify(scheduledJobsDao).deleteScheduledJob(eq(experimentId), any(ActionListener.class));
        verify(scheduledExperimentHistoryDao).deleteScheduledExperimentHistoryByExperimentId(eq(experimentId), any(ActionListener.class));
    }

    public void testNullExperimentId() {
        OpenSearchDocRequest request = new OpenSearchDocRequest((String) null);

        ActionListener<DeleteResponse> responseListener = mock(ActionListener.class);
        transportAction.doExecute(null, request, responseListener);

        ArgumentCaptor<Exception> exceptionCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(responseListener).onFailure(exceptionCaptor.capture());

        Exception exception = exceptionCaptor.getValue();
        assertTrue(exception instanceof SearchRelevanceException);
        assertTrue(exception.getMessage().contains("Experiment ID cannot be null or empty"));
        assertEquals(RestStatus.BAD_REQUEST, ((SearchRelevanceException) exception).status());

        verify(experimentDao, never()).deleteExperiment(any(), any(ActionListener.class));
    }

    public void testEvaluationResultDeletionWithBulkFailures() {
        String experimentId = "test-experiment-id";
        OpenSearchDocRequest request = new OpenSearchDocRequest(experimentId);

        DeleteResponse mockDeleteResponse = mock(DeleteResponse.class);
        doAnswer(invocation -> {
            ActionListener<DeleteResponse> listener = invocation.getArgument(1);
            listener.onResponse(mockDeleteResponse);
            return null;
        }).when(experimentDao).deleteExperiment(eq(experimentId), any(ActionListener.class));

        // Mock bulk response with failures
        BulkByScrollResponse mockBulkResponse = createBulkResponseWithBulkFailures();
        doAnswer(invocation -> {
            ActionListener<BulkByScrollResponse> listener = invocation.getArgument(1);
            listener.onResponse(mockBulkResponse);
            return null;
        }).when(evaluationResultDao).deleteEvaluationResultByExperimentId(eq(experimentId), any(ActionListener.class));

        ActionListener<DeleteResponse> responseListener = mock(ActionListener.class);
        transportAction.doExecute(null, request, responseListener);

        ArgumentCaptor<Exception> exceptionCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(responseListener).onFailure(exceptionCaptor.capture());

        Exception exception = exceptionCaptor.getValue();
        assertTrue(exception instanceof SearchRelevanceException);
        assertTrue(exception.getMessage().contains("Failed to delete the evaluation results"));
        assertTrue(exception.getMessage().contains("due to failures in deleting evaluation results"));
    }

    public void testEvaluationResultDeletionWithSearchFailures() {
        String experimentId = "test-experiment-id";
        OpenSearchDocRequest request = new OpenSearchDocRequest(experimentId);

        DeleteResponse mockDeleteResponse = mock(DeleteResponse.class);
        doAnswer(invocation -> {
            ActionListener<DeleteResponse> listener = invocation.getArgument(1);
            listener.onResponse(mockDeleteResponse);
            return null;
        }).when(experimentDao).deleteExperiment(eq(experimentId), any(ActionListener.class));

        // Mock bulk response with search failures
        BulkByScrollResponse mockBulkResponse = createBulkResponseWithSearchFailures();
        doAnswer(invocation -> {
            ActionListener<BulkByScrollResponse> listener = invocation.getArgument(1);
            listener.onResponse(mockBulkResponse);
            return null;
        }).when(evaluationResultDao).deleteEvaluationResultByExperimentId(eq(experimentId), any(ActionListener.class));

        ActionListener<DeleteResponse> responseListener = mock(ActionListener.class);
        transportAction.doExecute(null, request, responseListener);

        ArgumentCaptor<Exception> exceptionCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(responseListener).onFailure(exceptionCaptor.capture());

        Exception exception = exceptionCaptor.getValue();
        assertTrue(exception instanceof SearchRelevanceException);
        assertTrue(exception.getMessage().contains("Failed to delete the evaluation results"));
        assertTrue(exception.getMessage().contains("due to failures in deleting evaluation results"));
    }

    public void testExperimentVariantDeletionWithBulkFailures() {
        String experimentId = "test-experiment-id";
        OpenSearchDocRequest request = new OpenSearchDocRequest(experimentId);

        DeleteResponse mockDeleteResponse = mock(DeleteResponse.class);
        doAnswer(invocation -> {
            ActionListener<DeleteResponse> listener = invocation.getArgument(1);
            listener.onResponse(mockDeleteResponse);
            return null;
        }).when(experimentDao).deleteExperiment(eq(experimentId), any(ActionListener.class));

        BulkByScrollResponse successBulkResponse = createSuccessfulBulkResponse();
        doAnswer(invocation -> {
            ActionListener<BulkByScrollResponse> listener = invocation.getArgument(1);
            listener.onResponse(successBulkResponse);
            return null;
        }).when(evaluationResultDao).deleteEvaluationResultByExperimentId(eq(experimentId), any(ActionListener.class));

        // Mock bulk response with failures for variants
        BulkByScrollResponse mockBulkResponse = createBulkResponseWithBulkFailures();
        doAnswer(invocation -> {
            ActionListener<BulkByScrollResponse> listener = invocation.getArgument(1);
            listener.onResponse(mockBulkResponse);
            return null;
        }).when(experimentVariantDao).deleteExperimentVariantByExperimentId(eq(experimentId), any(ActionListener.class));

        ActionListener<DeleteResponse> responseListener = mock(ActionListener.class);
        transportAction.doExecute(null, request, responseListener);

        ArgumentCaptor<Exception> exceptionCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(responseListener).onFailure(exceptionCaptor.capture());

        Exception exception = exceptionCaptor.getValue();
        assertTrue(exception instanceof SearchRelevanceException);
        assertTrue(exception.getMessage().contains("Failed to delete experiment"));
        assertTrue(exception.getMessage().contains("due to failures in deleting experiment variants"));
    }

    public void testExperimentVariantDeletionWithSearchFailures() {
        String experimentId = "test-experiment-id";
        OpenSearchDocRequest request = new OpenSearchDocRequest(experimentId);

        DeleteResponse mockDeleteResponse = mock(DeleteResponse.class);
        doAnswer(invocation -> {
            ActionListener<DeleteResponse> listener = invocation.getArgument(1);
            listener.onResponse(mockDeleteResponse);
            return null;
        }).when(experimentDao).deleteExperiment(eq(experimentId), any(ActionListener.class));

        BulkByScrollResponse successBulkResponse = createSuccessfulBulkResponse();
        doAnswer(invocation -> {
            ActionListener<BulkByScrollResponse> listener = invocation.getArgument(1);
            listener.onResponse(successBulkResponse);
            return null;
        }).when(evaluationResultDao).deleteEvaluationResultByExperimentId(eq(experimentId), any(ActionListener.class));

        // Mock bulk response with search failures for variants
        BulkByScrollResponse mockBulkResponse = createBulkResponseWithSearchFailures();
        doAnswer(invocation -> {
            ActionListener<BulkByScrollResponse> listener = invocation.getArgument(1);
            listener.onResponse(mockBulkResponse);
            return null;
        }).when(experimentVariantDao).deleteExperimentVariantByExperimentId(eq(experimentId), any(ActionListener.class));

        ActionListener<DeleteResponse> responseListener = mock(ActionListener.class);
        transportAction.doExecute(null, request, responseListener);

        ArgumentCaptor<Exception> exceptionCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(responseListener).onFailure(exceptionCaptor.capture());

        Exception exception = exceptionCaptor.getValue();
        assertTrue(exception instanceof SearchRelevanceException);
        assertTrue(exception.getMessage().contains("Failed to delete experiment"));
        assertTrue(exception.getMessage().contains("due to failures in deleting experiment variants"));
    }

    public void testEvaluationResultDeletionFailureDoesNotStopOtherDeletions() {
        String experimentId = "test-experiment-id";
        OpenSearchDocRequest request = new OpenSearchDocRequest(experimentId);

        DeleteResponse mockDeleteResponse = mock(DeleteResponse.class);
        doAnswer(invocation -> {
            ActionListener<DeleteResponse> listener = invocation.getArgument(1);
            listener.onResponse(mockDeleteResponse);
            return null;
        }).when(experimentDao).deleteExperiment(eq(experimentId), any(ActionListener.class));

        // Mock evaluation results deletion failure
        doAnswer(invocation -> {
            ActionListener<BulkByScrollResponse> listener = invocation.getArgument(1);
            listener.onFailure(new RuntimeException("Index not found"));
            return null;
        }).when(evaluationResultDao).deleteEvaluationResultByExperimentId(eq(experimentId), any(ActionListener.class));

        BulkByScrollResponse successBulkResponse = createSuccessfulBulkResponse();
        doAnswer(invocation -> {
            ActionListener<BulkByScrollResponse> listener = invocation.getArgument(1);
            listener.onResponse(successBulkResponse);
            return null;
        }).when(experimentVariantDao).deleteExperimentVariantByExperimentId(eq(experimentId), any(ActionListener.class));

        doAnswer(invocation -> {
            ActionListener<DeleteResponse> listener = invocation.getArgument(1);
            listener.onResponse(mockDeleteResponse);
            return null;
        }).when(scheduledJobsDao).deleteScheduledJob(eq(experimentId), any(ActionListener.class));

        doAnswer(invocation -> {
            ActionListener<BulkByScrollResponse> listener = invocation.getArgument(1);
            listener.onResponse(successBulkResponse);
            return null;
        }).when(scheduledExperimentHistoryDao).deleteScheduledExperimentHistoryByExperimentId(eq(experimentId), any(ActionListener.class));

        ActionListener<DeleteResponse> responseListener = mock(ActionListener.class);
        transportAction.doExecute(null, request, responseListener);

        // Verify all deletions are still attempted
        verify(experimentDao).deleteExperiment(eq(experimentId), any(ActionListener.class));
        verify(evaluationResultDao).deleteEvaluationResultByExperimentId(eq(experimentId), any(ActionListener.class));
        verify(experimentVariantDao).deleteExperimentVariantByExperimentId(eq(experimentId), any(ActionListener.class));
        verify(scheduledJobsDao).deleteScheduledJob(eq(experimentId), any(ActionListener.class));
        verify(scheduledExperimentHistoryDao).deleteScheduledExperimentHistoryByExperimentId(eq(experimentId), any(ActionListener.class));
    }

    public void testScheduledJobDeletionFailureIsLogged() {
        String experimentId = "test-experiment-id";
        OpenSearchDocRequest request = new OpenSearchDocRequest(experimentId);

        DeleteResponse mockDeleteResponse = mock(DeleteResponse.class);
        doAnswer(invocation -> {
            ActionListener<DeleteResponse> listener = invocation.getArgument(1);
            listener.onResponse(mockDeleteResponse);
            return null;
        }).when(experimentDao).deleteExperiment(eq(experimentId), any(ActionListener.class));

        BulkByScrollResponse successBulkResponse = createSuccessfulBulkResponse();
        doAnswer(invocation -> {
            ActionListener<BulkByScrollResponse> listener = invocation.getArgument(1);
            listener.onResponse(successBulkResponse);
            return null;
        }).when(evaluationResultDao).deleteEvaluationResultByExperimentId(eq(experimentId), any(ActionListener.class));

        doAnswer(invocation -> {
            ActionListener<BulkByScrollResponse> listener = invocation.getArgument(1);
            listener.onResponse(successBulkResponse);
            return null;
        }).when(experimentVariantDao).deleteExperimentVariantByExperimentId(eq(experimentId), any(ActionListener.class));

        // Mock scheduled job deletion failure
        doAnswer(invocation -> {
            ActionListener<DeleteResponse> listener = invocation.getArgument(1);
            listener.onFailure(new RuntimeException("Scheduled job not found"));
            return null;
        }).when(scheduledJobsDao).deleteScheduledJob(eq(experimentId), any(ActionListener.class));

        // Mock successful scheduled experiment history deletion
        doAnswer(invocation -> {
            ActionListener<BulkByScrollResponse> listener = invocation.getArgument(1);
            listener.onResponse(successBulkResponse);
            return null;
        }).when(scheduledExperimentHistoryDao).deleteScheduledExperimentHistoryByExperimentId(eq(experimentId), any(ActionListener.class));

        ActionListener<DeleteResponse> responseListener = mock(ActionListener.class);
        transportAction.doExecute(null, request, responseListener);

        // Verify all deletions are attempted
        verify(experimentDao).deleteExperiment(eq(experimentId), any(ActionListener.class));
        verify(evaluationResultDao).deleteEvaluationResultByExperimentId(eq(experimentId), any(ActionListener.class));
        verify(experimentVariantDao).deleteExperimentVariantByExperimentId(eq(experimentId), any(ActionListener.class));
        verify(scheduledJobsDao).deleteScheduledJob(eq(experimentId), any(ActionListener.class));
        verify(scheduledExperimentHistoryDao).deleteScheduledExperimentHistoryByExperimentId(eq(experimentId), any(ActionListener.class));
    }

    public void testScheduledExperimentHistoryDeletionFailureIsLogged() {
        String experimentId = "test-experiment-id";
        OpenSearchDocRequest request = new OpenSearchDocRequest(experimentId);

        DeleteResponse mockDeleteResponse = mock(DeleteResponse.class);
        doAnswer(invocation -> {
            ActionListener<DeleteResponse> listener = invocation.getArgument(1);
            listener.onResponse(mockDeleteResponse);
            return null;
        }).when(experimentDao).deleteExperiment(eq(experimentId), any(ActionListener.class));

        BulkByScrollResponse successBulkResponse = createSuccessfulBulkResponse();
        doAnswer(invocation -> {
            ActionListener<BulkByScrollResponse> listener = invocation.getArgument(1);
            listener.onResponse(successBulkResponse);
            return null;
        }).when(evaluationResultDao).deleteEvaluationResultByExperimentId(eq(experimentId), any(ActionListener.class));

        doAnswer(invocation -> {
            ActionListener<BulkByScrollResponse> listener = invocation.getArgument(1);
            listener.onResponse(successBulkResponse);
            return null;
        }).when(experimentVariantDao).deleteExperimentVariantByExperimentId(eq(experimentId), any(ActionListener.class));

        // Mock successful scheduled job deletion
        doAnswer(invocation -> {
            ActionListener<DeleteResponse> listener = invocation.getArgument(1);
            listener.onResponse(mockDeleteResponse);
            return null;
        }).when(scheduledJobsDao).deleteScheduledJob(eq(experimentId), any(ActionListener.class));

        // Mock scheduled experiment history deletion failure
        doAnswer(invocation -> {
            ActionListener<BulkByScrollResponse> listener = invocation.getArgument(1);
            listener.onFailure(new RuntimeException("Scheduled experiment history not found"));
            return null;
        }).when(scheduledExperimentHistoryDao).deleteScheduledExperimentHistoryByExperimentId(eq(experimentId), any(ActionListener.class));

        ActionListener<DeleteResponse> responseListener = mock(ActionListener.class);
        transportAction.doExecute(null, request, responseListener);

        // Verify all deletions are attempted
        verify(experimentDao).deleteExperiment(eq(experimentId), any(ActionListener.class));
        verify(evaluationResultDao).deleteEvaluationResultByExperimentId(eq(experimentId), any(ActionListener.class));
        verify(experimentVariantDao).deleteExperimentVariantByExperimentId(eq(experimentId), any(ActionListener.class));
        verify(scheduledJobsDao).deleteScheduledJob(eq(experimentId), any(ActionListener.class));
        verify(scheduledExperimentHistoryDao).deleteScheduledExperimentHistoryByExperimentId(eq(experimentId), any(ActionListener.class));
    }

    // Helper methods to create mock BulkByScrollResponse objects
    private BulkByScrollResponse createSuccessfulBulkResponse() {
        BulkByScrollResponse mockResponse = mock(BulkByScrollResponse.class);
        when(mockResponse.getBulkFailures()).thenReturn(Collections.emptyList());
        when(mockResponse.getSearchFailures()).thenReturn(Collections.emptyList());
        return mockResponse;
    }

    @SuppressWarnings("unchecked")
    private BulkByScrollResponse createBulkResponseWithBulkFailures() {
        BulkByScrollResponse mockResponse = mock(BulkByScrollResponse.class);

        // Create a non-empty list to simulate bulk failures
        List mockFailures = Collections.singletonList(new Object());
        when(mockResponse.getBulkFailures()).thenReturn(mockFailures);
        when(mockResponse.getSearchFailures()).thenReturn(Collections.emptyList());

        return mockResponse;
    }

    @SuppressWarnings("unchecked")
    private BulkByScrollResponse createBulkResponseWithSearchFailures() {
        BulkByScrollResponse mockResponse = mock(BulkByScrollResponse.class);

        // Create a non-empty list to simulate search failures
        List mockFailures = Collections.singletonList(mock(ScrollableHitSource.SearchFailure.class));
        when(mockResponse.getBulkFailures()).thenReturn(Collections.emptyList());
        when(mockResponse.getSearchFailures()).thenReturn(mockFailures);

        return mockResponse;
    }
}
