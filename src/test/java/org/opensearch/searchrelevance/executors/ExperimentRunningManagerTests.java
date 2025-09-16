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
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
        PutExperimentRequest request = createExperimentRequest();

        ExperimentCancellationToken cancellationToken = new ExperimentCancellationToken("scheduled-experiment-result-id");
        cancellationToken.cancel();
        assertThrows(
            IllegalStateException.class,
            () -> experimentRunningManager.fetchSearchConfigurationsAsync(
                "experimentId",
                request,
                List.of("querySetReference"),
                cancellationToken,
                null
            )
        );

    }

    public void testExperimentRunningManagerExperimentEvaluationCancellation() {
        PutExperimentRequest request = createExperimentRequest();

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
            null
        );

        // Verify that the proper gate is reached for cancelling the token.
        verifyNoInteractions(metricsHelper, pointwiseExperimentProcessor, hybridOptimizerExperimentProcessor);
    }

    private PutExperimentRequest createExperimentRequest() {
        PutExperimentRequest request = new PutExperimentRequest(
            ExperimentType.PAIRWISE_COMPARISON,
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
