/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */
package org.opensearch.searchrelevance.transport.experiment;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.Before;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.opensearch.action.index.IndexResponse;
import org.opensearch.action.support.ActionFilters;
import org.opensearch.cluster.service.ClusterService;
import org.opensearch.core.action.ActionListener;
import org.opensearch.core.index.Index;
import org.opensearch.core.index.shard.ShardId;
import org.opensearch.searchrelevance.dao.EvaluationResultDao;
import org.opensearch.searchrelevance.dao.ExperimentDao;
import org.opensearch.searchrelevance.dao.ExperimentVariantDao;
import org.opensearch.searchrelevance.dao.QuerySetDao;
import org.opensearch.searchrelevance.dao.SearchConfigurationDao;
import org.opensearch.searchrelevance.metrics.MetricsHelper;
import org.opensearch.searchrelevance.model.AsyncStatus;
import org.opensearch.searchrelevance.model.EvaluationResult;
import org.opensearch.searchrelevance.model.Experiment;
import org.opensearch.searchrelevance.model.ExperimentType;
import org.opensearch.searchrelevance.model.SearchConfiguration;
import org.opensearch.searchrelevance.settings.SearchRelevanceSettingsAccessor;
import org.opensearch.test.OpenSearchTestCase;
import org.opensearch.transport.TransportService;

public class PostExperimentTransportActionTests extends OpenSearchTestCase {

    @Mock
    private ClusterService clusterService;

    @Mock
    private TransportService transportService;

    @Mock
    private ActionFilters actionFilters;

    @Mock
    private ExperimentDao experimentDao;

    @Mock
    private EvaluationResultDao evaluationResultDao;

    @Mock
    private ExperimentVariantDao experimentVariantDao;

    @Mock
    private QuerySetDao querySetDao;

    @Mock
    private SearchConfigurationDao searchConfigurationDao;

    @Mock
    private MetricsHelper metricsHelper;

    @Mock
    private SearchRelevanceSettingsAccessor settingsAccessor;

    private PostExperimentTransportAction transportAction;

    @Before
    public void setup() {
        MockitoAnnotations.openMocks(this);
        transportAction = new PostExperimentTransportAction(
            clusterService,
            transportService,
            actionFilters,
            experimentDao,
            experimentVariantDao,
            querySetDao,
            searchConfigurationDao,
            evaluationResultDao,
            metricsHelper,
            settingsAccessor
        );
    }

    public void testAllEvaluationResultsSucceed() throws InterruptedException {
        // Setup test data
        List<Map<String, Object>> evaluationResults = createTestEvaluationResults(3);
        PostExperimentRequest request = createTestRequest(evaluationResults);

        // Mock dependencies
        setupMockDependencies();

        // Mock all evaluation results to succeed
        mockEvaluationResultDaoSuccess();

        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<Experiment> finalExperiment = new AtomicReference<>();

        // Mock experiment update to capture the final experiment
        doAnswer(invocation -> {
            Experiment experiment = invocation.getArgument(0);
            finalExperiment.set(experiment);
            ActionListener<Object> listener = invocation.getArgument(1);
            listener.onResponse(mock(IndexResponse.class));
            latch.countDown();
            return null;
        }).when(experimentDao).updateExperiment(any(Experiment.class), any(ActionListener.class));

        // Execute
        ActionListener<IndexResponse> listener = createMockListener();
        transportAction.doExecute(null, request, listener);

        // Wait for async processing
        assertTrue("Test timed out", latch.await(5, TimeUnit.SECONDS));

        // Verify final experiment has COMPLETED status
        assertNotNull(finalExperiment.get());
        assertEquals(AsyncStatus.COMPLETED, finalExperiment.get().status());
        assertEquals(3, finalExperiment.get().results().size());

        // Verify all evaluation results were attempted to be stored
        verify(evaluationResultDao, times(3)).putEvaluationResult(any(EvaluationResult.class), any(ActionListener.class));
    }

    public void testSomeEvaluationResultsFail() throws InterruptedException {
        // Setup test data
        List<Map<String, Object>> evaluationResults = createTestEvaluationResults(3);
        PostExperimentRequest request = createTestRequest(evaluationResults);

        // Mock dependencies
        setupMockDependencies();

        // Mock first two evaluation results to succeed, third to fail
        mockEvaluationResultDaoPartialFailure();

        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<Experiment> finalExperiment = new AtomicReference<>();

        // Mock experiment update to capture the final experiment
        doAnswer(invocation -> {
            Experiment experiment = invocation.getArgument(0);
            finalExperiment.set(experiment);
            ActionListener<Object> listener = invocation.getArgument(1);
            listener.onResponse(mock(IndexResponse.class));
            latch.countDown();
            return null;
        }).when(experimentDao).updateExperiment(any(Experiment.class), any(ActionListener.class));

        // Execute
        ActionListener<IndexResponse> listener = createMockListener();
        transportAction.doExecute(null, request, listener);

        // Wait for async processing
        assertTrue("Test timed out", latch.await(5, TimeUnit.SECONDS));

        // Verify final experiment has ERROR status but contains successful results
        assertNotNull(finalExperiment.get());
        assertEquals(AsyncStatus.ERROR, finalExperiment.get().status());
        assertEquals(2, finalExperiment.get().results().size()); // Only 2 successful results

        // Verify all evaluation results were attempted to be stored
        verify(evaluationResultDao, times(3)).putEvaluationResult(any(EvaluationResult.class), any(ActionListener.class));
    }

    public void testAllEvaluationResultsFail() throws InterruptedException {
        // Setup test data
        List<Map<String, Object>> evaluationResults = createTestEvaluationResults(2);
        PostExperimentRequest request = createTestRequest(evaluationResults);

        // Mock dependencies
        setupMockDependencies();

        // Mock all evaluation results to fail
        mockEvaluationResultDaoAllFailure();

        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<Experiment> finalExperiment = new AtomicReference<>();

        // Mock experiment update to capture the final experiment
        doAnswer(invocation -> {
            Experiment experiment = invocation.getArgument(0);
            finalExperiment.set(experiment);
            ActionListener<Object> listener = invocation.getArgument(1);
            listener.onResponse(mock(IndexResponse.class));
            latch.countDown();
            return null;
        }).when(experimentDao).updateExperiment(any(Experiment.class), any(ActionListener.class));

        // Execute
        ActionListener<IndexResponse> listener = createMockListener();
        transportAction.doExecute(null, request, listener);

        // Wait for async processing
        assertTrue("Test timed out", latch.await(5, TimeUnit.SECONDS));

        // Verify final experiment has ERROR status and no successful results
        assertNotNull(finalExperiment.get());
        assertEquals(AsyncStatus.ERROR, finalExperiment.get().status());
        assertEquals(0, finalExperiment.get().results().size()); // No successful results

        // Verify all evaluation results were attempted to be stored
        verify(evaluationResultDao, times(2)).putEvaluationResult(any(EvaluationResult.class), any(ActionListener.class));
    }

    public void testMaxLimitEnforcement() throws InterruptedException {
        // Setup test data with more results than the limit
        List<Map<String, Object>> evaluationResults = createTestEvaluationResults(5);
        PostExperimentRequest request = createTestRequest(evaluationResults);

        // Mock dependencies with a limit of 3
        setupMockDependencies();
        when(settingsAccessor.getMaxQuerySetAllowed()).thenReturn(3);

        // Mock all evaluation results to succeed
        mockEvaluationResultDaoSuccess();

        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<Experiment> finalExperiment = new AtomicReference<>();

        // Mock experiment update to capture the final experiment
        doAnswer(invocation -> {
            Experiment experiment = invocation.getArgument(0);
            finalExperiment.set(experiment);
            ActionListener<Object> listener = invocation.getArgument(1);
            listener.onResponse(mock(IndexResponse.class));
            latch.countDown();
            return null;
        }).when(experimentDao).updateExperiment(any(Experiment.class), any(ActionListener.class));

        // Execute
        ActionListener<IndexResponse> listener = createMockListener();
        transportAction.doExecute(null, request, listener);

        // Wait for async processing
        assertTrue("Test timed out", latch.await(5, TimeUnit.SECONDS));

        // Verify only 3 evaluation results were processed (due to limit)
        verify(evaluationResultDao, times(3)).putEvaluationResult(any(EvaluationResult.class), any(ActionListener.class));

        // Verify final experiment has COMPLETED status with 3 results
        assertNotNull(finalExperiment.get());
        assertEquals(AsyncStatus.COMPLETED, finalExperiment.get().status());
        assertEquals(3, finalExperiment.get().results().size());
    }

    public void testNullRequest() {
        ActionListener<IndexResponse> listener = createMockListener();
        transportAction.doExecute(null, null, listener);

        // Verify that onFailure was called
        ArgumentCaptor<Exception> exceptionCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(listener).onFailure(exceptionCaptor.capture());
        assertTrue(exceptionCaptor.getValue().getMessage().contains("Request cannot be null"));
    }

    private List<Map<String, Object>> createTestEvaluationResults(int count) {
        List<Map<String, Object>> results = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            results.add(
                Map.of(
                    "searchText",
                    "test query " + i,
                    "metrics",
                    List.of(Map.of("metric", "dcg@10", "value", 0.8)),
                    "documentIds",
                    List.of("doc" + i + "_1", "doc" + i + "_2")
                )
            );
        }
        return results;
    }

    private PostExperimentRequest createTestRequest(List<Map<String, Object>> evaluationResults) {
        return new PostExperimentRequest(
            ExperimentType.POINTWISE_EVALUATION,
            "test-queryset-id",
            List.of("test-search-config-id"),
            List.of("test-judgment-id"),
            10,
            evaluationResults,
            List.of()
        );
    }

    private void setupMockDependencies() {
        // Mock search configuration
        SearchConfiguration searchConfig = mock(SearchConfiguration.class);
        when(searchConfig.id()).thenReturn("test-search-config-id");
        when(searchConfigurationDao.getSearchConfigurationSync("test-search-config-id")).thenReturn(searchConfig);

        // Mock settings
        when(settingsAccessor.getMaxQuerySetAllowed()).thenReturn(100);

        // Mock initial experiment creation
        doAnswer(invocation -> {
            ActionListener<Object> listener = invocation.getArgument(1);
            IndexResponse response = new IndexResponse(new ShardId(new Index("test", "uuid"), 0), "test-id", 1L, 1L, 1L, true);
            listener.onResponse(response);
            return null;
        }).when(experimentDao).putExperiment(any(Experiment.class), any(ActionListener.class));
    }

    private void mockEvaluationResultDaoSuccess() {
        doAnswer(invocation -> {
            ActionListener<Object> listener = invocation.getArgument(1);
            listener.onResponse(mock(IndexResponse.class));
            return null;
        }).when(evaluationResultDao).putEvaluationResult(any(EvaluationResult.class), any(ActionListener.class));
    }

    private void mockEvaluationResultDaoPartialFailure() {
        doAnswer(invocation -> {
            EvaluationResult result = invocation.getArgument(0);
            ActionListener<Object> listener = invocation.getArgument(1);

            // Fail if the query text contains "test query 2" (third evaluation result)
            if (result.searchText().contains("test query 2")) {
                listener.onFailure(new RuntimeException("Simulated failure"));
            } else {
                listener.onResponse(mock(IndexResponse.class));
            }
            return null;
        }).when(evaluationResultDao).putEvaluationResult(any(EvaluationResult.class), any(ActionListener.class));
    }

    private void mockEvaluationResultDaoAllFailure() {
        doAnswer(invocation -> {
            ActionListener<Object> listener = invocation.getArgument(1);
            listener.onFailure(new RuntimeException("Simulated failure"));
            return null;
        }).when(evaluationResultDao).putEvaluationResult(any(EvaluationResult.class), any(ActionListener.class));
    }

    @SuppressWarnings("unchecked")
    private ActionListener<IndexResponse> createMockListener() {
        ActionListener<IndexResponse> listener = mock(ActionListener.class);
        doAnswer(invocation -> {
            // Just acknowledge the response
            return null;
        }).when(listener).onResponse(any(IndexResponse.class));

        doAnswer(invocation -> {
            // Just acknowledge the failure
            return null;
        }).when(listener).onFailure(any(Exception.class));

        return listener;
    }
}
