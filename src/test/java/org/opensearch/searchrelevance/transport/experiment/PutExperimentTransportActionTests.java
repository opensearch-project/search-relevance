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
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.lucene.search.TotalHits;
import org.junit.Before;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.opensearch.action.index.IndexResponse;
import org.opensearch.action.search.SearchResponse;
import org.opensearch.action.support.ActionFilters;
import org.opensearch.common.xcontent.XContentFactory;
import org.opensearch.core.action.ActionListener;
import org.opensearch.core.common.bytes.BytesReference;
import org.opensearch.search.SearchHit;
import org.opensearch.search.SearchHits;
import org.opensearch.searchrelevance.dao.ExperimentDao;
import org.opensearch.searchrelevance.dao.JudgmentDao;
import org.opensearch.searchrelevance.dao.QuerySetDao;
import org.opensearch.searchrelevance.dao.ScheduledExperimentHistoryDao;
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
    private ScheduledExperimentHistoryDao scheduledExperimentHistoryDao;
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
            judgmentDao,
            scheduledExperimentHistoryDao,
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

        doAnswer(invocation -> {
            ActionListener<SearchResponse> listener = invocation.getArgument(1);
            listener.onResponse(createSearchConfigurationHit());
            return null;
        }).when(searchConfigurationDao).getSearchConfiguration(any(String.class), any(ActionListener.class));

        doAnswer(invocation -> {
            ActionListener<SearchResponse> listener = invocation.getArgument(1);
            listener.onResponse(createJudgmentHit());
            return null;
        }).when(judgmentDao).getJudgment(any(String.class), any(ActionListener.class));
    }

    public void testEmptyQueryTextsCompletesExperimentImmediately() {
        PutExperimentRequest request = new PutExperimentRequest(
            ExperimentType.PAIRWISE_COMPARISON,
            null,
            null,
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

        // Mock validation passing - QuerySet exists
        SearchResponse mockValidationResponse = mock(SearchResponse.class);
        SearchHits validationHits = new SearchHits(new SearchHit[0], new TotalHits(1, TotalHits.Relation.EQUAL_TO), 0.0f);
        when(mockValidationResponse.getHits()).thenReturn(validationHits);

        doAnswer(invocation -> {
            ActionListener<SearchResponse> listener = invocation.getArgument(1);
            listener.onResponse(mockValidationResponse);
            return null;
        }).when(querySetDao).checkQuerySetExists(eq("test-queryset-id"), any(ActionListener.class));

        // Mock actual QuerySet retrieval for processing
        doAnswer(invocation -> {
            ActionListener<SearchResponse> listener = invocation.getArgument(1);
            listener.onResponse(mockQuerySetResponse);
            return null;
        }).when(querySetDao).getQuerySet(eq("test-queryset-id"), any(ActionListener.class));

        // Mock SearchConfiguration validation passing
        SearchResponse mockConfigValidationResponse = mock(SearchResponse.class);
        SearchHits configValidationHits = new SearchHits(new SearchHit[0], new TotalHits(1, TotalHits.Relation.EQUAL_TO), 0.0f);
        when(mockConfigValidationResponse.getHits()).thenReturn(configValidationHits);
        doAnswer(invocation -> {
            ActionListener<SearchResponse> listener = invocation.getArgument(1);
            listener.onResponse(mockConfigValidationResponse);
            return null;
        }).when(searchConfigurationDao).checkSearchConfigurationExists(eq("config1"), any(ActionListener.class));

        // Mock Judgment validation passing
        SearchResponse mockJudgmentValidationResponse = mock(SearchResponse.class);
        SearchHits judgmentValidationHits = new SearchHits(new SearchHit[0], new TotalHits(1, TotalHits.Relation.EQUAL_TO), 0.0f);
        when(mockJudgmentValidationResponse.getHits()).thenReturn(judgmentValidationHits);
        doAnswer(invocation -> {
            ActionListener<SearchResponse> listener = invocation.getArgument(1);
            listener.onResponse(mockJudgmentValidationResponse);
            return null;
        }).when(judgmentDao).checkJudgmentExists(eq("judgment1"), any(ActionListener.class));

        ActionListener<IndexResponse> responseListener = mock(ActionListener.class);
        transportAction.doExecute(null, request, responseListener);

        verify(responseListener).onResponse(mockIndexResponse);

        ArgumentCaptor<Experiment> experimentCaptor = ArgumentCaptor.forClass(Experiment.class);
        verify(experimentDao).updateExperiment(experimentCaptor.capture(), any(ActionListener.class));

        Experiment finalExperiment = experimentCaptor.getValue();
        assertEquals(AsyncStatus.COMPLETED, finalExperiment.status());
        assertTrue(finalExperiment.results().isEmpty());
        assertEquals(request.getJudgmentList(), finalExperiment.judgmentList());
        assertNotNull(finalExperiment.inputSignature());
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
            null,
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

        // Mock validation passing - QuerySet exists
        SearchResponse mockValidationResponse = mock(SearchResponse.class);
        SearchHits validationHits = new SearchHits(new SearchHit[0], new TotalHits(1, TotalHits.Relation.EQUAL_TO), 0.0f);
        when(mockValidationResponse.getHits()).thenReturn(validationHits);

        doAnswer(invocation -> {
            ActionListener<SearchResponse> listener = invocation.getArgument(1);
            listener.onResponse(mockValidationResponse);
            return null;
        }).when(querySetDao).checkQuerySetExists(eq("nonexistent-queryset"), any(ActionListener.class));

        // Mock actual QuerySet retrieval failing during processing
        doAnswer(invocation -> {
            ActionListener<SearchResponse> listener = invocation.getArgument(1);
            listener.onFailure(new RuntimeException("QuerySet not found"));
            return null;
        }).when(querySetDao).getQuerySet(eq("nonexistent-queryset"), any(ActionListener.class));

        // Mock SearchConfiguration validation passing
        SearchResponse mockConfigValidationResponse = mock(SearchResponse.class);
        SearchHits configValidationHits = new SearchHits(new SearchHit[0], new TotalHits(1, TotalHits.Relation.EQUAL_TO), 0.0f);
        when(mockConfigValidationResponse.getHits()).thenReturn(configValidationHits);
        doAnswer(invocation -> {
            ActionListener<SearchResponse> listener = invocation.getArgument(1);
            listener.onResponse(mockConfigValidationResponse);
            return null;
        }).when(searchConfigurationDao).checkSearchConfigurationExists(eq("config1"), any(ActionListener.class));

        // Mock Judgment validation passing
        SearchResponse mockJudgmentValidationResponse = mock(SearchResponse.class);
        SearchHits judgmentValidationHits = new SearchHits(new SearchHit[0], new TotalHits(1, TotalHits.Relation.EQUAL_TO), 0.0f);
        when(mockJudgmentValidationResponse.getHits()).thenReturn(judgmentValidationHits);
        doAnswer(invocation -> {
            ActionListener<SearchResponse> listener = invocation.getArgument(1);
            listener.onResponse(mockJudgmentValidationResponse);
            return null;
        }).when(judgmentDao).checkJudgmentExists(eq("judgment1"), any(ActionListener.class));

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
            null,
            null,
            "test-queryset-id",
            List.of("config1"),
            List.of("judgment1"),
            10
        );

        // Mock validation passing - QuerySet exists
        SearchResponse mockValidationResponse = mock(SearchResponse.class);
        SearchHits validationHits = new SearchHits(new SearchHit[0], new TotalHits(1, TotalHits.Relation.EQUAL_TO), 0.0f);
        when(mockValidationResponse.getHits()).thenReturn(validationHits);

        doAnswer(invocation -> {
            ActionListener<SearchResponse> listener = invocation.getArgument(1);
            listener.onResponse(mockValidationResponse);
            return null;
        }).when(querySetDao).checkQuerySetExists(eq("test-queryset-id"), any(ActionListener.class));

        // Mock experiment creation failing
        doAnswer(invocation -> {
            ActionListener<IndexResponse> listener = invocation.getArgument(1);
            listener.onFailure(new RuntimeException("Database error"));
            return null;
        }).when(experimentDao).putExperiment(any(Experiment.class), any(ActionListener.class));

        // Mock SearchConfiguration validation passing
        SearchResponse mockConfigValidationResponse = mock(SearchResponse.class);
        SearchHits configValidationHits = new SearchHits(new SearchHit[0], new TotalHits(1, TotalHits.Relation.EQUAL_TO), 0.0f);
        when(mockConfigValidationResponse.getHits()).thenReturn(configValidationHits);
        doAnswer(invocation -> {
            ActionListener<SearchResponse> listener = invocation.getArgument(1);
            listener.onResponse(mockConfigValidationResponse);
            return null;
        }).when(searchConfigurationDao).checkSearchConfigurationExists(eq("config1"), any(ActionListener.class));

        // Mock Judgment validation passing
        SearchResponse mockJudgmentValidationResponse = mock(SearchResponse.class);
        SearchHits judgmentValidationHits = new SearchHits(new SearchHit[0], new TotalHits(1, TotalHits.Relation.EQUAL_TO), 0.0f);
        when(mockJudgmentValidationResponse.getHits()).thenReturn(judgmentValidationHits);
        doAnswer(invocation -> {
            ActionListener<SearchResponse> listener = invocation.getArgument(1);
            listener.onResponse(mockJudgmentValidationResponse);
            return null;
        }).when(judgmentDao).checkJudgmentExists(eq("judgment1"), any(ActionListener.class));

        ActionListener<IndexResponse> responseListener = mock(ActionListener.class);
        transportAction.doExecute(null, request, responseListener);

        ArgumentCaptor<Exception> exceptionCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(responseListener).onFailure(exceptionCaptor.capture());

        Exception exception = exceptionCaptor.getValue();
        assertTrue(exception.getMessage().contains("Failed to create initial experiment"));
    }

    public void testValidation_QuerySetNotFound() {
        PutExperimentRequest request = new PutExperimentRequest(
            ExperimentType.POINTWISE_EVALUATION,
            null,
            null,
            null,
            "missing-queryset-id",
            List.of(),
            List.of(),
            10
        );

        // Mock QuerySet DAO to return 0 hits (entity not found)
        SearchResponse mockResponse = mock(SearchResponse.class);
        SearchHits searchHits = new SearchHits(new SearchHit[0], new TotalHits(0, TotalHits.Relation.EQUAL_TO), 0.0f);
        when(mockResponse.getHits()).thenReturn(searchHits);

        doAnswer(invocation -> {
            ActionListener<SearchResponse> listener = invocation.getArgument(1);
            listener.onResponse(mockResponse);
            return null;
        }).when(querySetDao).checkQuerySetExists(eq("missing-queryset-id"), any(ActionListener.class));

        ActionListener<IndexResponse> responseListener = mock(ActionListener.class);
        transportAction.doExecute(null, request, responseListener);

        ArgumentCaptor<Exception> exceptionCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(responseListener).onFailure(exceptionCaptor.capture());

        Exception exception = exceptionCaptor.getValue();
        assertTrue(exception.getMessage().contains("QuerySet [missing-queryset-id] does not exist"));
    }

    public void testValidation_SearchConfigurationNotFound() {
        PutExperimentRequest request = new PutExperimentRequest(
            ExperimentType.POINTWISE_EVALUATION,
            null,
            null,
            null,
            "valid-queryset-id",
            List.of("missing-config-id"),
            List.of(),
            10
        );

        // Mock QuerySet exists
        SearchResponse mockQuerySetResponse = mock(SearchResponse.class);
        SearchHits querySetHits = new SearchHits(new SearchHit[0], new TotalHits(1, TotalHits.Relation.EQUAL_TO), 0.0f);
        when(mockQuerySetResponse.getHits()).thenReturn(querySetHits);

        doAnswer(invocation -> {
            ActionListener<SearchResponse> listener = invocation.getArgument(1);
            listener.onResponse(mockQuerySetResponse);
            return null;
        }).when(querySetDao).checkQuerySetExists(eq("valid-queryset-id"), any(ActionListener.class));

        // Mock SearchConfiguration DAO to return 0 hits
        SearchResponse mockResponse = mock(SearchResponse.class);
        SearchHits searchHits = new SearchHits(new SearchHit[0], new TotalHits(0, TotalHits.Relation.EQUAL_TO), 0.0f);
        when(mockResponse.getHits()).thenReturn(searchHits);

        doAnswer(invocation -> {
            ActionListener<SearchResponse> listener = invocation.getArgument(1);
            listener.onResponse(mockResponse);
            return null;
        }).when(searchConfigurationDao).checkSearchConfigurationExists(eq("missing-config-id"), any(ActionListener.class));

        ActionListener<IndexResponse> responseListener = mock(ActionListener.class);
        transportAction.doExecute(null, request, responseListener);

        ArgumentCaptor<Exception> exceptionCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(responseListener).onFailure(exceptionCaptor.capture());

        Exception exception = exceptionCaptor.getValue();
        assertTrue(exception.getMessage().contains("SearchConfiguration [missing-config-id] does not exist"));
    }

    public void testValidation_JudgmentNotFound() {
        PutExperimentRequest request = new PutExperimentRequest(
            ExperimentType.POINTWISE_EVALUATION,
            null,
            null,
            null,
            "valid-queryset-id",
            List.of(),
            List.of("missing-judgment-id"),
            10
        );

        // Mock QuerySet exists
        SearchResponse mockQuerySetResponse = mock(SearchResponse.class);
        SearchHits querySetHits = new SearchHits(new SearchHit[0], new TotalHits(1, TotalHits.Relation.EQUAL_TO), 0.0f);
        when(mockQuerySetResponse.getHits()).thenReturn(querySetHits);

        doAnswer(invocation -> {
            ActionListener<SearchResponse> listener = invocation.getArgument(1);
            listener.onResponse(mockQuerySetResponse);
            return null;
        }).when(querySetDao).checkQuerySetExists(eq("valid-queryset-id"), any(ActionListener.class));

        // Mock Judgment DAO to return 0 hits
        SearchResponse mockResponse = mock(SearchResponse.class);
        SearchHits searchHits = new SearchHits(new SearchHit[0], new TotalHits(0, TotalHits.Relation.EQUAL_TO), 0.0f);
        when(mockResponse.getHits()).thenReturn(searchHits);

        doAnswer(invocation -> {
            ActionListener<SearchResponse> listener = invocation.getArgument(1);
            listener.onResponse(mockResponse);
            return null;
        }).when(judgmentDao).checkJudgmentExists(eq("missing-judgment-id"), any(ActionListener.class));

        ActionListener<IndexResponse> responseListener = mock(ActionListener.class);
        transportAction.doExecute(null, request, responseListener);

        ArgumentCaptor<Exception> exceptionCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(responseListener).onFailure(exceptionCaptor.capture());

        Exception exception = exceptionCaptor.getValue();
        assertTrue(exception.getMessage().contains("Judgment [missing-judgment-id] does not exist"));
    }

    // ============================================
    // generateDefaultExperimentName Tests
    // ============================================

    public void testGenerateDefaultExperimentName_ValidInputs() {
        String id = "a1b2c3d4-e5f6-7890-abcd-ef1234567890";
        String result = PutExperimentTransportAction.generateDefaultExperimentName(ExperimentType.PAIRWISE_COMPARISON, id);
        assertEquals("PAIRWISE_COMPARISON-a1b2c3d4", result);
    }

    public void testGenerateDefaultExperimentName_ShortId() {
        String shortId = "abc";
        String result = PutExperimentTransportAction.generateDefaultExperimentName(ExperimentType.POINTWISE_EVALUATION, shortId);
        assertEquals("POINTWISE_EVALUATION-abc", result);
    }

    public void testGenerateDefaultExperimentName_ExactlyEightChars() {
        String exactId = "12345678";
        String result = PutExperimentTransportAction.generateDefaultExperimentName(ExperimentType.HYBRID_OPTIMIZER, exactId);
        assertEquals("HYBRID_OPTIMIZER-12345678", result);
    }

    public void testGenerateDefaultExperimentName_NullType() {
        try {
            PutExperimentTransportAction.generateDefaultExperimentName(null, "test-id");
            fail("Expected NullPointerException");
        } catch (NullPointerException e) {
            assertTrue(e.getMessage().contains("Experiment type must not be null"));
        }
    }

    public void testGenerateDefaultExperimentName_NullId() {
        try {
            PutExperimentTransportAction.generateDefaultExperimentName(ExperimentType.PAIRWISE_COMPARISON, null);
            fail("Expected NullPointerException");
        } catch (NullPointerException e) {
            assertTrue(e.getMessage().contains("Experiment ID must not be null"));
        }
    }

    public void testGenerateDefaultExperimentName_AllTypes() {
        String id = "test-uuid-1234";

        assertEquals(
            "PAIRWISE_COMPARISON-test-uui",
            PutExperimentTransportAction.generateDefaultExperimentName(ExperimentType.PAIRWISE_COMPARISON, id)
        );
        assertEquals(
            "POINTWISE_EVALUATION-test-uui",
            PutExperimentTransportAction.generateDefaultExperimentName(ExperimentType.POINTWISE_EVALUATION, id)
        );
        assertEquals(
            "HYBRID_OPTIMIZER-test-uui",
            PutExperimentTransportAction.generateDefaultExperimentName(ExperimentType.HYBRID_OPTIMIZER, id)
        );
    }

    private SearchResponse createSearchConfigurationHit() {
        try {
            SearchResponse response = mock(SearchResponse.class);
            Map<String, Object> sourceMap = new HashMap<>();
            sourceMap.put("id", "config1");
            sourceMap.put("name", "cfg");
            sourceMap.put("timestamp", "2024-01-01T00:00:00Z");
            sourceMap.put("index", "idx");
            sourceMap.put("query", "{}");
            sourceMap.put("searchPipeline", "");
            sourceMap.put("description", "");
            SearchHit hit = new SearchHit(1, "config1", Map.of(), Map.of());
            hit.sourceRef(BytesReference.bytes(XContentFactory.jsonBuilder().map(sourceMap)));
            SearchHits hits = new SearchHits(new SearchHit[] { hit }, new TotalHits(1, TotalHits.Relation.EQUAL_TO), 1.0f);
            when(response.getHits()).thenReturn(hits);
            return response;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private SearchResponse createJudgmentHit() {
        try {
            SearchResponse response = mock(SearchResponse.class);
            Map<String, Object> sourceMap = new HashMap<>();
            sourceMap.put("id", "judgment1");
            sourceMap.put("timestamp", "2024-01-01T00:00:00Z");
            sourceMap.put("name", "j");
            sourceMap.put("status", "COMPLETED");
            sourceMap.put("type", "IMPORT_JUDGMENT");
            sourceMap.put("metadata", Map.of());
            sourceMap.put("judgmentRatings", List.of());
            SearchHit hit = new SearchHit(1, "judgment1", Map.of(), Map.of());
            hit.sourceRef(BytesReference.bytes(XContentFactory.jsonBuilder().map(sourceMap)));
            SearchHits hits = new SearchHits(new SearchHit[] { hit }, new TotalHits(1, TotalHits.Relation.EQUAL_TO), 1.0f);
            when(response.getHits()).thenReturn(hits);
            return response;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
