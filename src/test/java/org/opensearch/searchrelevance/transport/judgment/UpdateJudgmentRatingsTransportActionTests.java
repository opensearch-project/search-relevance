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
import org.opensearch.searchrelevance.model.AsyncStatus;
import org.opensearch.searchrelevance.model.JudgmentType;
import org.opensearch.test.OpenSearchTestCase;
import org.opensearch.threadpool.ThreadPool;
import org.opensearch.transport.TransportService;

/**
 * Tests for UpdateJudgmentRatingsTransportAction (single-rating manual edit).
 * Verifies: judgment not found (404), wrong type (400), in-flight status (409),
 * query not found (404), optimistic-concurrency version conflict (409), the happy path,
 * and request validation.
 */
public class UpdateJudgmentRatingsTransportActionTests extends OpenSearchTestCase {

    @Mock
    private TransportService transportService;
    @Mock
    private ActionFilters actionFilters;
    @Mock
    private JudgmentDao judgmentDao;
    @Mock
    private ThreadPool threadPool;

    private UpdateJudgmentRatingsTransportAction action;

    @Before
    public void setup() {
        MockitoAnnotations.openMocks(this);
        // Make the GENERIC executor run tasks immediately on the calling thread.
        ExecutorService directExecutor = mock(ExecutorService.class);
        when(threadPool.executor(any())).thenReturn(directExecutor);
        doAnswer(invocation -> {
            ((Runnable) invocation.getArgument(0)).run();
            return null;
        }).when(directExecutor).execute(any(Runnable.class));
        action = new UpdateJudgmentRatingsTransportAction(transportService, actionFilters, judgmentDao, threadPool);
    }

    public void testUpdate_JudgmentNotFound_Returns404() {
        SearchResponse mockResponse = mock(SearchResponse.class);
        SearchHits searchHits = new SearchHits(new SearchHit[0], new TotalHits(0, TotalHits.Relation.EQUAL_TO), 0.0f);
        when(mockResponse.getHits()).thenReturn(searchHits);
        when(judgmentDao.getJudgmentSync("missing-id")).thenReturn(mockResponse);

        UpdateJudgmentRatingsRequest request = new UpdateJudgmentRatingsRequest("missing-id", "superhero", "1", "0.9");
        ActionListener<IndexResponse> listener = mock(ActionListener.class);
        action.doExecute(null, request, listener);

        ArgumentCaptor<Exception> exceptionCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(listener).onFailure(exceptionCaptor.capture());
        assertTrue(exceptionCaptor.getValue().getMessage().contains("Judgment not found"));
    }

    public void testUpdate_NotLlmJudgmentType_Returns400() {
        Map<String, Object> source = buildJudgmentSource(JudgmentType.UBI_JUDGMENT.name(), AsyncStatus.COMPLETED.name());
        SearchResponse loaded = buildMockSearchResponse(source);
        when(judgmentDao.getJudgmentSync("ubi-id")).thenReturn(loaded);

        UpdateJudgmentRatingsRequest request = new UpdateJudgmentRatingsRequest("ubi-id", "superhero", "1", "0.9");
        ActionListener<IndexResponse> listener = mock(ActionListener.class);
        action.doExecute(null, request, listener);

        ArgumentCaptor<Exception> exceptionCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(listener).onFailure(exceptionCaptor.capture());
        assertTrue(exceptionCaptor.getValue().getMessage().contains("only supported for LLM_JUDGMENT type"));
    }

    public void testUpdate_JudgmentProcessing_Returns409() {
        Map<String, Object> source = buildJudgmentSource(JudgmentType.LLM_JUDGMENT.name(), AsyncStatus.PROCESSING.name());
        SearchResponse loaded = buildMockSearchResponse(source);
        when(judgmentDao.getJudgmentSync("processing-id")).thenReturn(loaded);

        UpdateJudgmentRatingsRequest request = new UpdateJudgmentRatingsRequest("processing-id", "superhero", "1", "0.9");
        ActionListener<IndexResponse> listener = mock(ActionListener.class);
        action.doExecute(null, request, listener);

        ArgumentCaptor<Exception> exceptionCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(listener).onFailure(exceptionCaptor.capture());
        assertTrue(exceptionCaptor.getValue().getMessage().contains("cannot edit ratings until it completes"));
    }

    public void testUpdate_JudgmentRetrying_Returns409() {
        Map<String, Object> source = buildJudgmentSource(JudgmentType.LLM_JUDGMENT.name(), AsyncStatus.RETRYING.name());
        SearchResponse loaded = buildMockSearchResponse(source);
        when(judgmentDao.getJudgmentSync("retrying-id")).thenReturn(loaded);

        UpdateJudgmentRatingsRequest request = new UpdateJudgmentRatingsRequest("retrying-id", "superhero", "1", "0.9");
        ActionListener<IndexResponse> listener = mock(ActionListener.class);
        action.doExecute(null, request, listener);

        ArgumentCaptor<Exception> exceptionCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(listener).onFailure(exceptionCaptor.capture());
        assertTrue(exceptionCaptor.getValue().getMessage().contains("cannot edit ratings until it completes"));
    }

    public void testUpdate_QueryNotFound_Returns404() {
        Map<String, Object> source = buildJudgmentSource(JudgmentType.LLM_JUDGMENT.name(), AsyncStatus.COMPLETED.name());
        SearchResponse loaded = buildMockSearchResponse(source);
        when(judgmentDao.getJudgmentSync("ok-id")).thenReturn(loaded);

        // "comedy" is not a query in the judgment (only "superhero" is).
        UpdateJudgmentRatingsRequest request = new UpdateJudgmentRatingsRequest("ok-id", "comedy", "1", "0.9");
        ActionListener<IndexResponse> listener = mock(ActionListener.class);
        action.doExecute(null, request, listener);

        ArgumentCaptor<Exception> exceptionCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(listener).onFailure(exceptionCaptor.capture());
        assertTrue(exceptionCaptor.getValue().getMessage().contains("Query not found in judgment"));
    }

    public void testUpdate_VersionConflict_Returns409() {
        // A concurrent edit or retry changed the doc since we read it: the optimistic-concurrency
        // write fails with a version conflict, which must surface to the caller.
        Map<String, Object> source = buildJudgmentSource(JudgmentType.LLM_JUDGMENT.name(), AsyncStatus.COMPLETED.name());
        SearchResponse loaded = buildMockSearchResponse(source);
        when(judgmentDao.getJudgmentSync("conflict-id")).thenReturn(loaded);

        doAnswer(invocation -> {
            ActionListener<IndexResponse> l = invocation.getArgument(3);
            l.onFailure(new org.opensearch.index.engine.VersionConflictEngineException(null, "conflict-id", "version conflict"));
            return null;
        }).when(judgmentDao).updateJudgment(any(), anyLong(), anyLong(), any());

        UpdateJudgmentRatingsRequest request = new UpdateJudgmentRatingsRequest("conflict-id", "superhero", "1", "0.9");
        ActionListener<IndexResponse> listener = mock(ActionListener.class);
        action.doExecute(null, request, listener);

        ArgumentCaptor<Exception> exceptionCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(listener).onFailure(exceptionCaptor.capture());
        assertTrue(exceptionCaptor.getValue().getMessage().contains("version conflict"));
    }

    public void testUpdate_HappyPath_AdjustsRatingAndSucceeds() {
        Map<String, Object> source = buildJudgmentSource(JudgmentType.LLM_JUDGMENT.name(), AsyncStatus.COMPLETED.name());
        SearchResponse loaded = buildMockSearchResponse(source);
        when(judgmentDao.getJudgmentSync("ok-id")).thenReturn(loaded);

        // The guarded write (4-arg overload) succeeds.
        doAnswer(invocation -> {
            ActionListener<IndexResponse> l = invocation.getArgument(3);
            l.onResponse(mock(IndexResponse.class));
            return null;
        }).when(judgmentDao).updateJudgment(any(), anyLong(), anyLong(), any());

        UpdateJudgmentRatingsRequest request = new UpdateJudgmentRatingsRequest("ok-id", "superhero", "1", "0.5");
        ActionListener<IndexResponse> listener = mock(ActionListener.class);
        action.doExecute(null, request, listener);

        verify(listener).onResponse(any(IndexResponse.class));
    }

    public void testRequestValidation_NullId() {
        UpdateJudgmentRatingsRequest request = new UpdateJudgmentRatingsRequest(null, "superhero", "1", "0.9");
        assertNotNull(request.validate());
    }

    public void testRequestValidation_MissingFields() {
        UpdateJudgmentRatingsRequest request = new UpdateJudgmentRatingsRequest("id-1", "superhero", "1", "");
        assertNotNull(request.validate());
    }

    public void testRequestValidation_Valid() {
        UpdateJudgmentRatingsRequest request = new UpdateJudgmentRatingsRequest("id-1", "superhero", "1", "0.9");
        assertNull(request.validate());
    }

    private Map<String, Object> buildJudgmentSource(String type, String status) {
        Map<String, Object> source = new HashMap<>();
        source.put("type", type);
        source.put("status", status);
        source.put("name", "test judgment");

        // One query "superhero" with docId "1" rated and docId "5" in failures.
        Map<String, Object> queryEntry = new HashMap<>();
        queryEntry.put("query", "superhero");
        List<Map<String, Object>> ratings = new ArrayList<>();
        Map<String, Object> r = new HashMap<>();
        r.put("docId", "1");
        r.put("rating", "0.9");
        ratings.add(r);
        queryEntry.put("ratings", ratings);
        List<Map<String, Object>> failures = new ArrayList<>();
        Map<String, Object> f = new HashMap<>();
        f.put("docId", "5");
        failures.add(f);
        queryEntry.put("failures", failures);
        List<Map<String, Object>> judgmentRatings = new ArrayList<>();
        judgmentRatings.add(queryEntry);
        source.put("judgmentRatings", judgmentRatings);

        Map<String, Object> metadata = new HashMap<>();
        metadata.put("modelId", "test-model");
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
