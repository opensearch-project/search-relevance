/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */
package org.opensearch.searchrelevance.transport.judgment;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;

import org.apache.lucene.search.TotalHits;
import org.junit.Before;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.opensearch.action.index.IndexResponse;
import org.opensearch.action.search.SearchResponse;
import org.opensearch.action.support.ActionFilters;
import org.opensearch.core.action.ActionListener;
import org.opensearch.search.SearchHit;
import org.opensearch.search.SearchHits;
import org.opensearch.searchrelevance.dao.JudgmentDao;
import org.opensearch.searchrelevance.judgments.LlmJudgmentsProcessor;
import org.opensearch.searchrelevance.model.AsyncStatus;
import org.opensearch.searchrelevance.model.JudgmentType;
import org.opensearch.searchrelevance.utils.TimeUtils;
import org.opensearch.test.OpenSearchTestCase;
import org.opensearch.threadpool.ThreadPool;
import org.opensearch.transport.TransportService;

/**
 * Tests for RetryFailedJudgmentTransportAction.
 * Verifies validation logic: judgment not found, wrong type, still processing,
 * no failures, missing metadata, and request validation.
 */
public class RetryFailedJudgmentTransportActionTests extends OpenSearchTestCase {

    @Mock
    private TransportService transportService;
    @Mock
    private ActionFilters actionFilters;
    @Mock
    private JudgmentDao judgmentDao;
    @Mock
    private LlmJudgmentsProcessor llmJudgmentsProcessor;
    @Mock
    private ThreadPool threadPool;

    private RetryFailedJudgmentTransportAction action;

    @Before
    public void setup() {
        MockitoAnnotations.openMocks(this);
        // Make the GENERIC executor run tasks immediately on the calling thread
        ExecutorService directExecutor = mock(ExecutorService.class);
        when(threadPool.executor(any())).thenReturn(directExecutor);
        org.mockito.Mockito.doAnswer(invocation -> {
            ((Runnable) invocation.getArgument(0)).run();
            return null;
        }).when(directExecutor).execute(any(Runnable.class));
        action = new RetryFailedJudgmentTransportAction(transportService, actionFilters, judgmentDao, llmJudgmentsProcessor, threadPool);
    }

    public void testRetry_JudgmentNotFound() {
        SearchResponse mockResponse = mock(SearchResponse.class);
        SearchHits searchHits = new SearchHits(new SearchHit[0], new TotalHits(0, TotalHits.Relation.EQUAL_TO), 0.0f);
        when(mockResponse.getHits()).thenReturn(searchHits);
        when(judgmentDao.getJudgmentSync("not-found-id")).thenReturn(mockResponse);

        RetryFailedJudgmentRequest request = new RetryFailedJudgmentRequest("not-found-id");
        ActionListener<IndexResponse> listener = mock(ActionListener.class);
        action.doExecute(null, request, listener);

        ArgumentCaptor<Exception> exceptionCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(listener).onFailure(exceptionCaptor.capture());
        assertTrue(exceptionCaptor.getValue().getMessage().contains("Judgment not found"));
    }

    public void testRetry_NotLlmJudgmentType() {
        Map<String, Object> source = buildJudgmentSource(JudgmentType.UBI_JUDGMENT.name(), AsyncStatus.COMPLETED.name(), List.of());
        SearchResponse mockResponse = buildMockSearchResponse(source);
        when(judgmentDao.getJudgmentSync("ubi-judgment-id")).thenReturn(mockResponse);

        RetryFailedJudgmentRequest request = new RetryFailedJudgmentRequest("ubi-judgment-id");
        ActionListener<IndexResponse> listener = mock(ActionListener.class);
        action.doExecute(null, request, listener);

        ArgumentCaptor<Exception> exceptionCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(listener).onFailure(exceptionCaptor.capture());
        assertTrue(exceptionCaptor.getValue().getMessage().contains("Retry is only supported for LLM_JUDGMENT type"));
    }

    public void testRetry_InitialGenerationProcessing_Rejected() {
        // PROCESSING = initial generation still running. Never retryable, regardless of timestamp,
        // because a generation that hasn't COMPLETED may not have the full list of docs to rate.
        Map<String, Object> source = buildJudgmentSource(JudgmentType.LLM_JUDGMENT.name(), AsyncStatus.PROCESSING.name(), List.of());
        SearchResponse mockResponse = buildMockSearchResponse(source);
        when(judgmentDao.getJudgmentSync("processing-id")).thenReturn(mockResponse);

        RetryFailedJudgmentRequest request = new RetryFailedJudgmentRequest("processing-id");
        ActionListener<IndexResponse> listener = mock(ActionListener.class);
        action.doExecute(null, request, listener);

        ArgumentCaptor<Exception> exceptionCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(listener).onFailure(exceptionCaptor.capture());
        assertTrue(exceptionCaptor.getValue().getMessage().contains("initial generation is still PROCESSING"));
    }

    public void testRetry_RetryingFreshHeartbeat_Rejected() {
        // RETRYING with a fresh timestamp means another retry is actively running — reject.
        Map<String, Object> source = buildJudgmentSource(JudgmentType.LLM_JUDGMENT.name(), AsyncStatus.RETRYING.name(), List.of());
        source.put("timestamp", TimeUtils.getTimestamp()); // fresh heartbeat = now
        SearchResponse mockResponse = buildMockSearchResponse(source);
        when(judgmentDao.getJudgmentSync("retrying-id")).thenReturn(mockResponse);

        RetryFailedJudgmentRequest request = new RetryFailedJudgmentRequest("retrying-id");
        ActionListener<IndexResponse> listener = mock(ActionListener.class);
        action.doExecute(null, request, listener);

        ArgumentCaptor<Exception> exceptionCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(listener).onFailure(exceptionCaptor.capture());
        assertTrue(exceptionCaptor.getValue().getMessage().contains("A retry is already in progress"));
    }

    public void testRetry_StaleRetrying_AllowedToProceed() {
        // RETRYING but the heartbeat timestamp is old (previous retry died) — a new retry may take over.
        // The judgment has failures, so it reaches the RETRYING-status write (proving the stale guard
        // did NOT reject it with a 409).
        List<Map<String, Object>> judgmentRatings = new ArrayList<>();
        Map<String, Object> queryEntry = new HashMap<>();
        queryEntry.put("query", "superhero");
        queryEntry.put("ratings", new ArrayList<>());
        queryEntry.put("failures", List.of(Map.of("docId", "5")));
        judgmentRatings.add(queryEntry);

        Map<String, Object> source = buildJudgmentSource(JudgmentType.LLM_JUDGMENT.name(), AsyncStatus.RETRYING.name(), judgmentRatings);
        source.put("timestamp", "2000-01-01T00:00:00.000Z"); // far in the past = stale
        SearchResponse mockResponse = buildMockSearchResponse(source);
        when(judgmentDao.getJudgmentSync("stale-id")).thenReturn(mockResponse);

        // Let the RETRYING-status write succeed so we can observe that we got past the stale guard.
        // The status write uses optimistic concurrency, i.e. the 4-arg (judgment, seqNo, primaryTerm,
        // listener) overload; the listener is the last argument.
        doAnswer(invocation -> {
            ActionListener<IndexResponse> l = invocation.getArgument(3);
            l.onResponse(mock(IndexResponse.class));
            return null;
        }).when(judgmentDao).updateJudgment(any(), anyLong(), anyLong(), any());

        RetryFailedJudgmentRequest request = new RetryFailedJudgmentRequest("stale-id");
        ActionListener<IndexResponse> listener = mock(ActionListener.class);
        action.doExecute(null, request, listener);

        // Not rejected with CONFLICT — instead it proceeded and the caller got a success response.
        verify(listener).onResponse(any(IndexResponse.class));
    }

    public void testRetry_VersionConflict_ReturnsConflict() {
        // A concurrent retry won the race: the optimistic-concurrency status write fails with a
        // version conflict, which must surface to the caller as 409.
        List<Map<String, Object>> judgmentRatings = new ArrayList<>();
        Map<String, Object> queryEntry = new HashMap<>();
        queryEntry.put("query", "superhero");
        queryEntry.put("ratings", new ArrayList<>());
        queryEntry.put("failures", List.of(Map.of("docId", "5")));
        judgmentRatings.add(queryEntry);

        Map<String, Object> source = buildJudgmentSource(JudgmentType.LLM_JUDGMENT.name(), AsyncStatus.COMPLETED.name(), judgmentRatings);
        SearchResponse mockResponse = buildMockSearchResponse(source);
        when(judgmentDao.getJudgmentSync("conflict-id")).thenReturn(mockResponse);

        // The guarded status write loses the race → version conflict.
        doAnswer(invocation -> {
            ActionListener<IndexResponse> l = invocation.getArgument(3);
            l.onFailure(new org.opensearch.index.engine.VersionConflictEngineException(null, "conflict-id", "version conflict"));
            return null;
        }).when(judgmentDao).updateJudgment(any(), anyLong(), anyLong(), any());

        RetryFailedJudgmentRequest request = new RetryFailedJudgmentRequest("conflict-id");
        ActionListener<IndexResponse> listener = mock(ActionListener.class);
        action.doExecute(null, request, listener);

        ArgumentCaptor<Exception> exceptionCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(listener).onFailure(exceptionCaptor.capture());
        assertTrue(exceptionCaptor.getValue().getMessage().contains("concurrent retry is already in progress"));
    }

    public void testRetry_NoFailures() {
        List<Map<String, Object>> judgmentRatings = new ArrayList<>();
        Map<String, Object> queryEntry = new HashMap<>();
        queryEntry.put("query", "superhero");
        queryEntry.put("ratings", List.of(Map.of("docId", "1", "rating", "0.9")));
        // No failures field
        judgmentRatings.add(queryEntry);

        Map<String, Object> source = buildJudgmentSource(JudgmentType.LLM_JUDGMENT.name(), AsyncStatus.COMPLETED.name(), judgmentRatings);
        SearchResponse mockResponse = buildMockSearchResponse(source);
        when(judgmentDao.getJudgmentSync("no-failures-id")).thenReturn(mockResponse);

        RetryFailedJudgmentRequest request = new RetryFailedJudgmentRequest("no-failures-id");
        ActionListener<IndexResponse> listener = mock(ActionListener.class);
        action.doExecute(null, request, listener);

        ArgumentCaptor<Exception> exceptionCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(listener).onFailure(exceptionCaptor.capture());
        assertTrue(exceptionCaptor.getValue().getMessage().contains("No failed documents to retry"));
    }

    public void testRetry_MissingMetadata() {
        List<Map<String, Object>> judgmentRatings = new ArrayList<>();
        Map<String, Object> queryEntry = new HashMap<>();
        queryEntry.put("query", "superhero");
        queryEntry.put("ratings", List.of());
        queryEntry.put("failures", List.of(Map.of("docId", "5")));
        judgmentRatings.add(queryEntry);

        Map<String, Object> source = new HashMap<>();
        source.put("type", JudgmentType.LLM_JUDGMENT.name());
        source.put("status", AsyncStatus.COMPLETED.name());
        source.put("judgmentRatings", judgmentRatings);
        source.put("name", "test");
        // No metadata field

        SearchResponse mockResponse = buildMockSearchResponse(source);
        when(judgmentDao.getJudgmentSync("no-metadata-id")).thenReturn(mockResponse);

        RetryFailedJudgmentRequest request = new RetryFailedJudgmentRequest("no-metadata-id");
        ActionListener<IndexResponse> listener = mock(ActionListener.class);
        action.doExecute(null, request, listener);

        ArgumentCaptor<Exception> exceptionCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(listener).onFailure(exceptionCaptor.capture());
        assertTrue(exceptionCaptor.getValue().getMessage().contains("Judgment metadata is missing"));
    }

    public void testRequestValidation_NullId() {
        RetryFailedJudgmentRequest request = new RetryFailedJudgmentRequest((String) null);
        assertNotNull(request.validate());
    }

    public void testRequestValidation_EmptyId() {
        RetryFailedJudgmentRequest request = new RetryFailedJudgmentRequest("   ");
        assertNotNull(request.validate());
    }

    public void testRequestValidation_ValidId() {
        RetryFailedJudgmentRequest request = new RetryFailedJudgmentRequest("valid-id-123");
        assertNull(request.validate());
    }

    private Map<String, Object> buildJudgmentSource(String type, String status, List<Map<String, Object>> judgmentRatings) {
        Map<String, Object> source = new HashMap<>();
        source.put("type", type);
        source.put("status", status);
        source.put("judgmentRatings", judgmentRatings);
        source.put("name", "test judgment");

        Map<String, Object> metadata = new HashMap<>();
        metadata.put("modelId", "test-model");
        metadata.put("tokenLimit", 4000);
        metadata.put("size", 10);
        metadata.put("contextFields", List.of("title"));
        metadata.put("promptTemplate", "SearchText: {{searchText}}; Hits: {{hits}}");
        metadata.put("searchConfigurationList", List.of("config-1"));
        metadata.put("querySetId", "qs-1");
        source.put("metadata", metadata);

        return source;
    }

    private SearchResponse buildMockSearchResponse(Map<String, Object> source) {
        try {
            org.opensearch.core.xcontent.XContentBuilder builder = org.opensearch.common.xcontent.XContentFactory.jsonBuilder();
            builder.map(source);
            SearchHit hit = new SearchHit(1, "test-id", Map.of(), Map.of());
            hit.sourceRef(org.opensearch.core.common.bytes.BytesReference.bytes(builder));
            SearchHits searchHits = new SearchHits(new SearchHit[] { hit }, new TotalHits(1, TotalHits.Relation.EQUAL_TO), 1.0f);
            SearchResponse mockResponse = mock(SearchResponse.class);
            when(mockResponse.getHits()).thenReturn(searchHits);
            return mockResponse;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
