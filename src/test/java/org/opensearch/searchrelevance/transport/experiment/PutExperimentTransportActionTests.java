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

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.List;

import org.apache.lucene.search.TotalHits;
import org.junit.Before;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.opensearch.action.index.IndexResponse;
import org.opensearch.action.search.SearchResponse;
import org.opensearch.action.support.ActionFilters;
import org.opensearch.core.action.ActionListener;
import org.opensearch.core.common.bytes.BytesReference;
import org.opensearch.search.SearchHit;
import org.opensearch.search.SearchHits;
import org.opensearch.searchrelevance.dao.ExperimentDao;
import org.opensearch.searchrelevance.dao.JudgmentDao;
import org.opensearch.searchrelevance.dao.QuerySetDao;
import org.opensearch.searchrelevance.dao.SearchConfigurationDao;
import org.opensearch.searchrelevance.executors.ExperimentRunningManager;
import org.opensearch.searchrelevance.executors.ExperimentTaskManager;
import org.opensearch.searchrelevance.metrics.MetricsHelper;
import org.opensearch.searchrelevance.model.AsyncStatus;
import org.opensearch.searchrelevance.model.Experiment;
import org.opensearch.searchrelevance.model.ExperimentType;
import org.opensearch.searchrelevance.settings.SearchRelevanceSettingsAccessor;
import org.opensearch.test.OpenSearchTestCase;
import org.opensearch.threadpool.ThreadPool;
import org.opensearch.transport.TransportService;

public class PutExperimentTransportActionTests extends OpenSearchTestCase {

    @Mock
    private TransportService transportService;
    @Mock
    private ActionFilters actionFilters;
    @Mock
    private ExperimentDao experimentDao;
    @Mock
    private QuerySetDao querySetDao;
    @Mock
    private SearchConfigurationDao searchConfigurationDao;
    @Mock
    private MetricsHelper metricsHelper;
    @Mock
    private JudgmentDao judgmentDao;
    @Mock
    private ExperimentTaskManager experimentTaskManager;
    private ExperimentRunningManager experimentRunningManager;
    @Mock
    private ThreadPool threadPool;
    @Mock
    private SearchRelevanceSettingsAccessor settingsAccessor;

    private PutExperimentTransportAction transportAction;

    @Before
    public void setup() {
        MockitoAnnotations.openMocks(this);
        experimentRunningManager = new ExperimentRunningManager(
            experimentDao,
            querySetDao,
            searchConfigurationDao,
            null,
            metricsHelper,
            null,
            null,
            threadPool,
            settingsAccessor
        );
        transportAction = new PutExperimentTransportAction(
            transportService,
            actionFilters,
            experimentDao,
            querySetDao,
            searchConfigurationDao,
            metricsHelper,
            judgmentDao,
            experimentTaskManager,
            experimentRunningManager,
            threadPool,
            settingsAccessor
        );
    }

    public void testEmptyQueryTextsCompletesExperimentImmediately() {
        PutExperimentRequest request = new PutExperimentRequest(
            ExperimentType.PAIRWISE_COMPARISON,
            null,
            "test-queryset-id",
            List.of("config1"),
            List.of("judgment1"),
            10
        );

        IndexResponse mockIndexResponse = mock(IndexResponse.class);
        doAnswer(invocation -> {
            ActionListener<IndexResponse> listener = invocation.getArgument(1);
            listener.onResponse(mockIndexResponse);
            return null;
        }).when(experimentDao).putExperiment(any(Experiment.class), any(ActionListener.class));

        SearchResponse mockQuerySetResponse = mock(SearchResponse.class);

        SearchHit searchHit = new SearchHit(0, "test-id", null, null);
        String jsonSource =
            "{\"id\":\"test-queryset-id\",\"name\":\"test-queryset\",\"description\":\"test description\",\"timestamp\":\"2023-01-01T00:00:00Z\",\"sampling\":\"random\",\"querySetQueries\":[]}";
        searchHit.sourceRef(BytesReference.fromByteBuffer(ByteBuffer.wrap(jsonSource.getBytes(StandardCharsets.UTF_8))));

        SearchHits searchHits = new SearchHits(new SearchHit[] { searchHit }, new TotalHits(1, TotalHits.Relation.EQUAL_TO), 1.0f);
        when(mockQuerySetResponse.getHits()).thenReturn(searchHits);

        doAnswer(invocation -> {
            ActionListener<SearchResponse> listener = invocation.getArgument(1);
            listener.onResponse(mockQuerySetResponse);
            return null;
        }).when(querySetDao).getQuerySet(eq("test-queryset-id"), any(ActionListener.class));

        ActionListener<IndexResponse> responseListener = mock(ActionListener.class);
        transportAction.doExecute(null, request, responseListener);

        verify(responseListener).onResponse(mockIndexResponse);

        ArgumentCaptor<Experiment> experimentCaptor = ArgumentCaptor.forClass(Experiment.class);
        verify(experimentDao).updateExperiment(experimentCaptor.capture(), any(ActionListener.class));

        Experiment finalExperiment = experimentCaptor.getValue();
        assertEquals(AsyncStatus.COMPLETED, finalExperiment.status());
        assertTrue(finalExperiment.results().isEmpty());
        assertEquals(request.getJudgmentList(), finalExperiment.judgmentList());
    }

    public void testNullRequestReturnsError() {
        ActionListener<IndexResponse> responseListener = mock(ActionListener.class);
        transportAction.doExecute(null, null, responseListener);

        ArgumentCaptor<Exception> exceptionCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(responseListener).onFailure(exceptionCaptor.capture());

        Exception exception = exceptionCaptor.getValue();
        assertTrue(exception.getMessage().contains("Request cannot be null"));
    }

    public void testQuerySetNotFoundHandlesError() {
        PutExperimentRequest request = new PutExperimentRequest(
            ExperimentType.PAIRWISE_COMPARISON,
            null,
            "nonexistent-queryset",
            List.of("config1"),
            List.of("judgment1"),
            10
        );

        IndexResponse mockIndexResponse = mock(IndexResponse.class);
        doAnswer(invocation -> {
            ActionListener<IndexResponse> listener = invocation.getArgument(1);
            listener.onResponse(mockIndexResponse);
            return null;
        }).when(experimentDao).putExperiment(any(Experiment.class), any(ActionListener.class));

        doAnswer(invocation -> {
            ActionListener<SearchResponse> listener = invocation.getArgument(1);
            listener.onFailure(new RuntimeException("QuerySet not found"));
            return null;
        }).when(querySetDao).getQuerySet(eq("nonexistent-queryset"), any(ActionListener.class));

        ActionListener<IndexResponse> responseListener = mock(ActionListener.class);
        transportAction.doExecute(null, request, responseListener);

        verify(responseListener).onResponse(mockIndexResponse);

        ArgumentCaptor<Experiment> experimentCaptor = ArgumentCaptor.forClass(Experiment.class);
        verify(experimentDao).updateExperiment(experimentCaptor.capture(), any(ActionListener.class));

        Experiment errorExperiment = experimentCaptor.getValue();
        assertEquals(AsyncStatus.ERROR, errorExperiment.status());
    }

    public void testExperimentCreationFailure() {
        PutExperimentRequest request = new PutExperimentRequest(
            ExperimentType.PAIRWISE_COMPARISON,
            null,
            "test-queryset-id",
            List.of("config1"),
            List.of("judgment1"),
            10
        );

        doAnswer(invocation -> {
            ActionListener<IndexResponse> listener = invocation.getArgument(1);
            listener.onFailure(new RuntimeException("Database error"));
            return null;
        }).when(experimentDao).putExperiment(any(Experiment.class), any(ActionListener.class));

        ActionListener<IndexResponse> responseListener = mock(ActionListener.class);
        transportAction.doExecute(null, request, responseListener);

        ArgumentCaptor<Exception> exceptionCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(responseListener).onFailure(exceptionCaptor.capture());

        Exception exception = exceptionCaptor.getValue();
        assertTrue(exception.getMessage().contains("Failed to create initial experiment"));
    }
}
