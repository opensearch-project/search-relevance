/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */
package org.opensearch.searchrelevance.executors;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.lucene.search.TotalHits;
import org.mockito.Mock;
import org.opensearch.action.search.SearchResponse;
import org.opensearch.common.xcontent.XContentFactory;
import org.opensearch.core.action.ActionListener;
import org.opensearch.core.common.bytes.BytesReference;
import org.opensearch.search.SearchHit;
import org.opensearch.search.SearchHits;
import org.opensearch.searchrelevance.dao.ExperimentDao;
import org.opensearch.searchrelevance.dao.QuerySetDao;
import org.opensearch.searchrelevance.dao.ScheduledExperimentHistoryDao;
import org.opensearch.searchrelevance.dao.SearchConfigurationDao;
import org.opensearch.searchrelevance.experiment.HybridOptimizerExperimentProcessor;
import org.opensearch.searchrelevance.experiment.PointwiseExperimentProcessor;
import org.opensearch.searchrelevance.metrics.MetricsHelper;
import org.opensearch.searchrelevance.model.ExperimentType;
import org.opensearch.searchrelevance.model.ScheduledExperimentResult;
import org.opensearch.searchrelevance.scheduler.ExperimentCancellationToken;
import org.opensearch.searchrelevance.settings.SearchRelevanceSettingsAccessor;
import org.opensearch.searchrelevance.transport.experiment.PutExperimentRequest;
import org.opensearch.test.OpenSearchTestCase;
import org.opensearch.threadpool.ThreadPool;

public class ExperimentRunningManagerTests extends OpenSearchTestCase {
    @Mock
    private ExperimentDao experimentDao;
    private QuerySetDao querySetDao;
    private SearchConfigurationDao searchConfigurationDao;
    private ScheduledExperimentHistoryDao scheduledExperimentHistoryDao;
    private MetricsHelper metricsHelper;
    private HybridOptimizerExperimentProcessor hybridOptimizerExperimentProcessor;
    private PointwiseExperimentProcessor pointwiseExperimentProcessor;
    @Mock
    private ThreadPool threadPool;
    @Mock
    private SearchRelevanceSettingsAccessor settingsAccessor;
    private ExperimentRunningManager experimentRunningManager;

    @Override
    public void setUp() throws Exception {
        super.setUp();
        querySetDao = mock(QuerySetDao.class);
        searchConfigurationDao = mock(SearchConfigurationDao.class);
        scheduledExperimentHistoryDao = mock(ScheduledExperimentHistoryDao.class);
        metricsHelper = mock(MetricsHelper.class);
        hybridOptimizerExperimentProcessor = mock(HybridOptimizerExperimentProcessor.class);
        pointwiseExperimentProcessor = mock(PointwiseExperimentProcessor.class);
        experimentRunningManager = new ExperimentRunningManager(
            experimentDao,
            querySetDao,
            searchConfigurationDao,
            scheduledExperimentHistoryDao,
            metricsHelper,
            hybridOptimizerExperimentProcessor,
            pointwiseExperimentProcessor,
            threadPool,
            settingsAccessor
        );
        doAnswer(invocation -> {
            ActionListener<SearchResponse> listener = invocation.getArgument(1);
            listener.onResponse(createMockQuerySetResponse());
            return null;
        }).when(querySetDao).getQuerySet(any(String.class), any(ActionListener.class));
        doAnswer(invocation -> {
            ActionListener<SearchResponse> listener = invocation.getArgument(1);
            listener.onResponse(createMockSearchConfigurationResponse());
            return null;
        }).when(searchConfigurationDao).getSearchConfiguration(any(String.class), any(ActionListener.class));
        doAnswer(invocation -> {
            ActionListener<SearchResponse> listener = invocation.getArgument(1);
            listener.onResponse(null);
            return null;
        }).when(scheduledExperimentHistoryDao)
            .updateScheduledExperimentResult(any(ScheduledExperimentResult.class), any(ActionListener.class));
    }

    public void testExperimentRunningManagerSearchConfigurationCancellation() {
        CountDownLatch actuallyFinished = new CountDownLatch(1);
        PutExperimentRequest request = createExperimentRequest();

        ExperimentCancellationToken cancellationToken = new ExperimentCancellationToken("scheduled-experiment-result-id");
        cancellationToken.cancel();
        experimentRunningManager.fetchSearchConfigurationsAsync(
            "experimentId",
            request,
            List.of("querySetReference"),
            cancellationToken,
            actuallyFinished
        );

        assertEquals(0, actuallyFinished.getCount());
    }

    public void testExperimentRunningManagerSearchConfigurationSingleCancelled() {
        CountDownLatch actuallyFinished = new CountDownLatch(1);
        PutExperimentRequest request = createExperimentRequest();

        ExperimentCancellationToken cancellationToken = new ExperimentCancellationToken("scheduled-experiment-result-id");
        SearchResponse searchConfigResponse = mock(SearchResponse.class);
        doAnswer(invocation -> {
            ActionListener<SearchResponse> listener = invocation.getArgument(1);
            cancellationToken.cancel();
            listener.onResponse(searchConfigResponse);
            return null;
        }).when(searchConfigurationDao).getSearchConfiguration(any(String.class), any(ActionListener.class));
        experimentRunningManager.fetchSearchConfigurationsAsync(
            "experimentId",
            request,
            List.of("querySetReference"),
            cancellationToken,
            actuallyFinished
        );

        assertEquals(0, actuallyFinished.getCount());

    }

    public void testExperimentRunningManagerExperimentEvaluationCancellation() {
        PutExperimentRequest request = createExperimentRequest();
        CountDownLatch actuallyFinished = new CountDownLatch(1);

        ExperimentCancellationToken cancellationToken = new ExperimentCancellationToken("scheduled-experiment-result-id");
        cancellationToken.cancel();
        experimentRunningManager.executeExperimentEvaluation(
            "experimentId",
            request,
            null,
            List.of("queryText"),
            null,
            null,
            new AtomicBoolean(false),
            null,
            cancellationToken,
            actuallyFinished
        );

        // Verify that the proper gate is reached for cancelling the token.
        verifyNoInteractions(metricsHelper, pointwiseExperimentProcessor, hybridOptimizerExperimentProcessor);

        assertEquals(0, actuallyFinished.getCount());
    }

    public void testConcurrentFutureMapUpdates() {
        PutExperimentRequest request = createExperimentRequest();
        Map<String, List<Future<?>>> runningFutures = new ConcurrentHashMap<>();
        ExperimentCancellationToken cancellationToken = new ExperimentCancellationToken("scheduled-experiment-result-id");
        runningFutures.compute(request.getScheduledExperimentResultId(), (key, existingList) -> {
            List<Future<?>> list = existingList != null ? existingList : new CopyOnWriteArrayList<>();
            return list;
        });

        List<Future<?>> list1 = runningFutures.get(request.getScheduledExperimentResultId());
        List<Future<?>> list2 = runningFutures.get(request.getScheduledExperimentResultId());
        list1.add(new CompletableFuture<>());
        list2.add(new CompletableFuture<>());

        assertEquals(2, runningFutures.get(request.getScheduledExperimentResultId()).size());

    }

    public void testStartExperimentRunReject() {
        // Make sure that only at most one experiment run for a given scheduled experiment run id
        // can be scheduled at a given time
        // Also makes sure the entry in the mapping futures is removed when the cancellation token is cancelled.
        PutExperimentRequest request = createExperimentRequest();
        CountDownLatch actuallyFinished1 = new CountDownLatch(1);

        doAnswer(invocation -> { return null; }).when(querySetDao).getQuerySet(any(String.class), any(ActionListener.class));

        ExperimentCancellationToken cancellationToken1 = new ExperimentCancellationToken("scheduled-experiment-result-id");
        experimentRunningManager.startExperimentRun("experimentId", request, cancellationToken1, actuallyFinished1);

        assertEquals(1, actuallyFinished1.getCount());

        verifyNoInteractions(scheduledExperimentHistoryDao);

        // Try running a second experiment and make sure that it does not run successfully
        CountDownLatch actuallyFinished2 = new CountDownLatch(1);
        ExperimentCancellationToken cancellationToken2 = new ExperimentCancellationToken("scheduled-experiment-result-id");

        experimentRunningManager.startExperimentRun("experimentId", request, cancellationToken2, actuallyFinished2);

        // This indicates that the async failure method was hit
        verify(scheduledExperimentHistoryDao, times(1)).updateScheduledExperimentResult(
            any(ScheduledExperimentResult.class),
            any(ActionListener.class)
        );

        // Finish running the first run
        cancellationToken1.cancel();

        // After the first run finishes, we can start a new experiment
        experimentRunningManager.startExperimentRun("experimentId", request, cancellationToken2, actuallyFinished2);
        verify(scheduledExperimentHistoryDao, times(1)).updateScheduledExperimentResult(
            any(ScheduledExperimentResult.class),
            any(ActionListener.class)
        );

    }

    private PutExperimentRequest createExperimentRequest() {
        PutExperimentRequest request = new PutExperimentRequest(
            ExperimentType.POINTWISE_EVALUATION,
            "scheduled-experiment-result-id",
            "test-queryset-id",
            List.of("config1"),
            List.of("judgment1"),
            10
        );
        return request;
    }

    private SearchResponse createMockQuerySetResponse() {
        SearchResponse response = mock(SearchResponse.class);

        Map<String, Object> sourceMap = new HashMap<>();
        List<Map<String, Object>> querySetQueries = Arrays.asList(Map.of("queryText", "queryText1"), Map.of("queryText", "queryText2"));
        sourceMap.put("querySetQueries", querySetQueries);

        SearchHit hit = new SearchHit(1, "queyset1", Map.of(), Map.of());
        try {
            BytesReference sourceBytes = BytesReference.bytes(XContentFactory.jsonBuilder().map(sourceMap));
            hit.sourceRef(sourceBytes);
        } catch (Exception e) {
            throw new RuntimeException("Failed to create queryset response", e);
        }

        SearchHits hits = new SearchHits(new SearchHit[] { hit }, new TotalHits(1, TotalHits.Relation.EQUAL_TO), 1.0f);

        when(response.getHits()).thenReturn(hits);
        return response;
    }

    private SearchResponse createMockSearchConfigurationResponse() {
        SearchResponse response = mock(SearchResponse.class);

        Map<String, Object> sourceMap = new HashMap<>();

        SearchHit hit = new SearchHit(1, "searchconfig1", Map.of(), Map.of());
        try {
            BytesReference sourceBytes = BytesReference.bytes(XContentFactory.jsonBuilder().map(sourceMap));
            hit.sourceRef(sourceBytes);
        } catch (Exception e) {
            throw new RuntimeException("Failed to create search configuration response", e);
        }

        SearchHits hits = new SearchHits(new SearchHit[] { hit }, new TotalHits(1, TotalHits.Relation.EQUAL_TO), 1.0f);

        when(response.getHits()).thenReturn(hits);
        return response;
    }
}
