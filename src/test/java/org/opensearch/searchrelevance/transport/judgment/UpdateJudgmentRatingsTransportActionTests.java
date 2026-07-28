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
import static org.mockito.Mockito.never;
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
import org.opensearch.searchrelevance.model.Judgment;
import org.opensearch.searchrelevance.model.JudgmentType;
import org.opensearch.test.OpenSearchTestCase;
import org.opensearch.threadpool.ThreadPool;
import org.opensearch.transport.TransportService;

/**
 * Tests for UpdateJudgmentRatingsTransportAction (manual rating edits).
 * Verifies: judgment not found (404), wrong type (400), in-flight status (409),
 * query not found (404), optimistic-concurrency version conflict (409), the single- and
 * multi-adjustment happy paths, and request validation.
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

        UpdateJudgmentRatingsRequest request = new UpdateJudgmentRatingsRequest(
            "missing-id",
            List.of(new RatingAdjustment("superhero", "1", "0.9"))
        );
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

        UpdateJudgmentRatingsRequest request = new UpdateJudgmentRatingsRequest(
            "ubi-id",
            List.of(new RatingAdjustment("superhero", "1", "0.9"))
        );
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

        UpdateJudgmentRatingsRequest request = new UpdateJudgmentRatingsRequest(
            "processing-id",
            List.of(new RatingAdjustment("superhero", "1", "0.9"))
        );
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

        UpdateJudgmentRatingsRequest request = new UpdateJudgmentRatingsRequest(
            "retrying-id",
            List.of(new RatingAdjustment("superhero", "1", "0.9"))
        );
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
        UpdateJudgmentRatingsRequest request = new UpdateJudgmentRatingsRequest(
            "ok-id",
            List.of(new RatingAdjustment("comedy", "1", "0.9"))
        );
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

        UpdateJudgmentRatingsRequest request = new UpdateJudgmentRatingsRequest(
            "conflict-id",
            List.of(new RatingAdjustment("superhero", "1", "0.9"))
        );
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

        UpdateJudgmentRatingsRequest request = new UpdateJudgmentRatingsRequest(
            "ok-id",
            List.of(new RatingAdjustment("superhero", "1", "0.5"))
        );
        ActionListener<IndexResponse> listener = mock(ActionListener.class);
        action.doExecute(null, request, listener);

        verify(listener).onResponse(any(IndexResponse.class));
    }

    public void testUpdate_MultipleAdjustments_AppliedInOneWrite() {
        // The judgment has one query "superhero" with docId "1" rated and docId "5" in failures.
        // Adjust both in a single request: overwrite the rated doc and rescue the failed doc.
        Map<String, Object> source = buildJudgmentSource(JudgmentType.LLM_JUDGMENT.name(), AsyncStatus.COMPLETED.name());
        SearchResponse loaded = buildMockSearchResponse(source);
        when(judgmentDao.getJudgmentSync("ok-id")).thenReturn(loaded);

        doAnswer(invocation -> {
            ActionListener<IndexResponse> l = invocation.getArgument(3);
            l.onResponse(mock(IndexResponse.class));
            return null;
        }).when(judgmentDao).updateJudgment(any(), anyLong(), anyLong(), any());

        UpdateJudgmentRatingsRequest request = new UpdateJudgmentRatingsRequest(
            "ok-id",
            List.of(new RatingAdjustment("superhero", "1", "0.3"), new RatingAdjustment("superhero", "5", "0.7"))
        );
        ActionListener<IndexResponse> listener = mock(ActionListener.class);
        action.doExecute(null, request, listener);

        // A single guarded write carries all adjustments; the request succeeds.
        verify(judgmentDao).updateJudgment(any(), anyLong(), anyLong(), any());
        verify(listener).onResponse(any(IndexResponse.class));
    }

    public void testUpdate_MultipleQueries_FailedAndSuccessfulDocs() {
        // Two queries, each with one rated doc and one failed doc. In a single request:
        // "superhero": overwrite rated doc "1", rescue failed doc "5"
        // "comedy": overwrite rated doc "2", rescue failed doc "7"
        // Assert the persisted judgment has all four docs rated and both failures lists emptied.
        Map<String, Object> source = buildTwoQueryJudgmentSource();
        SearchResponse loaded = buildMockSearchResponse(source);
        when(judgmentDao.getJudgmentSync("multi-id")).thenReturn(loaded);

        doAnswer(invocation -> {
            ActionListener<IndexResponse> l = invocation.getArgument(3);
            l.onResponse(mock(IndexResponse.class));
            return null;
        }).when(judgmentDao).updateJudgment(any(), anyLong(), anyLong(), any());

        UpdateJudgmentRatingsRequest request = new UpdateJudgmentRatingsRequest(
            "multi-id",
            List.of(
                new RatingAdjustment("superhero", "1", "0.3"),
                new RatingAdjustment("superhero", "5", "0.7"),
                new RatingAdjustment("comedy", "2", "0.4"),
                new RatingAdjustment("comedy", "7", "0.6")
            )
        );
        ActionListener<IndexResponse> listener = mock(ActionListener.class);
        action.doExecute(null, request, listener);

        verify(listener).onResponse(any(IndexResponse.class));

        // Capture what was actually persisted by the single guarded write.
        ArgumentCaptor<Judgment> judgmentCaptor = ArgumentCaptor.forClass(Judgment.class);
        verify(judgmentDao).updateJudgment(judgmentCaptor.capture(), anyLong(), anyLong(), any());
        List<Map<String, Object>> written = judgmentCaptor.getValue().getJudgmentRatings();
        assertEquals(2, written.size());

        // "superhero": doc 1 overwritten to 0.3, doc 5 rescued at 0.7, failures now empty.
        Map<String, Object> superhero = findQueryEntry(written, "superhero");
        assertEquals("0.3", ratingOf(superhero, "1"));
        assertEquals("0.7", ratingOf(superhero, "5"));
        assertTrue(((List<?>) superhero.get("failures")).isEmpty());

        // "comedy": doc 2 overwritten to 0.4, doc 7 rescued at 0.6, failures now empty.
        Map<String, Object> comedy = findQueryEntry(written, "comedy");
        assertEquals("0.4", ratingOf(comedy, "2"));
        assertEquals("0.6", ratingOf(comedy, "7"));
        assertTrue(((List<?>) comedy.get("failures")).isEmpty());
    }

    public void testUpdate_RescuingAllFailures_ClearsStaleFailureReason() {
        // The judgment carries a failure reason from the run that produced its failures. Rating the
        // last failed doc must clear it, so a healthy judgment does not keep reporting a failure.
        Map<String, Object> source = buildJudgmentSource(JudgmentType.LLM_JUDGMENT.name(), AsyncStatus.COMPLETED.name());
        @SuppressWarnings("unchecked")
        Map<String, Object> metadata = (Map<String, Object>) source.get("metadata");
        metadata.put("failedQueries", 1);
        metadata.put("lastFailureReason", "ThrottlingException: Rate exceeded");
        SearchResponse loaded = buildMockSearchResponse(source);
        when(judgmentDao.getJudgmentSync("rescue-id")).thenReturn(loaded);

        doAnswer(invocation -> {
            ActionListener<IndexResponse> l = invocation.getArgument(3);
            l.onResponse(mock(IndexResponse.class));
            return null;
        }).when(judgmentDao).updateJudgment(any(), anyLong(), anyLong(), any());

        // Doc "5" is the judgment's only failure; rating it leaves nothing failing.
        UpdateJudgmentRatingsRequest request = new UpdateJudgmentRatingsRequest(
            "rescue-id",
            List.of(new RatingAdjustment("superhero", "5", "0.8"))
        );
        ActionListener<IndexResponse> listener = mock(ActionListener.class);
        action.doExecute(null, request, listener);

        verify(listener).onResponse(any(IndexResponse.class));

        ArgumentCaptor<Judgment> judgmentCaptor = ArgumentCaptor.forClass(Judgment.class);
        verify(judgmentDao).updateJudgment(judgmentCaptor.capture(), anyLong(), anyLong(), any());
        Map<String, Object> writtenMetadata = judgmentCaptor.getValue().getMetadata();
        assertEquals(0, writtenMetadata.get("failedQueries"));
        assertFalse("stale lastFailureReason must be cleared", writtenMetadata.containsKey("lastFailureReason"));
        assertEquals("unrelated metadata must be preserved", "test-model", writtenMetadata.get("modelId"));
    }

    public void testUpdate_FailuresRemaining_KeepsFailureReason() {
        // Only one of the two failed docs is rated, so the judgment is still failing and the recorded
        // reason must survive.
        Map<String, Object> source = buildTwoQueryJudgmentSource();
        @SuppressWarnings("unchecked")
        Map<String, Object> metadata = (Map<String, Object>) source.get("metadata");
        metadata.put("lastFailureReason", "ThrottlingException: Rate exceeded");
        SearchResponse loaded = buildMockSearchResponse(source);
        when(judgmentDao.getJudgmentSync("partial-id")).thenReturn(loaded);

        doAnswer(invocation -> {
            ActionListener<IndexResponse> l = invocation.getArgument(3);
            l.onResponse(mock(IndexResponse.class));
            return null;
        }).when(judgmentDao).updateJudgment(any(), anyLong(), anyLong(), any());

        // "superhero" doc 5 is rescued, but "comedy" doc 7 is left failing.
        UpdateJudgmentRatingsRequest request = new UpdateJudgmentRatingsRequest(
            "partial-id",
            List.of(new RatingAdjustment("superhero", "5", "0.8"))
        );
        ActionListener<IndexResponse> listener = mock(ActionListener.class);
        action.doExecute(null, request, listener);

        ArgumentCaptor<Judgment> judgmentCaptor = ArgumentCaptor.forClass(Judgment.class);
        verify(judgmentDao).updateJudgment(judgmentCaptor.capture(), anyLong(), anyLong(), any());
        Map<String, Object> writtenMetadata = judgmentCaptor.getValue().getMetadata();
        assertEquals(1, writtenMetadata.get("failedQueries"));
        assertEquals("ThrottlingException: Rate exceeded", writtenMetadata.get("lastFailureReason"));
    }

    public void testUpdate_MultipleAdjustments_UnknownQueryFailsWholeRequest() {
        // The first adjustment is valid, the second names a query not in the judgment: the whole
        // request must fail with 404 and nothing is written.
        Map<String, Object> source = buildJudgmentSource(JudgmentType.LLM_JUDGMENT.name(), AsyncStatus.COMPLETED.name());
        SearchResponse loaded = buildMockSearchResponse(source);
        when(judgmentDao.getJudgmentSync("ok-id")).thenReturn(loaded);

        UpdateJudgmentRatingsRequest request = new UpdateJudgmentRatingsRequest(
            "ok-id",
            List.of(new RatingAdjustment("superhero", "1", "0.3"), new RatingAdjustment("comedy", "9", "0.7"))
        );
        ActionListener<IndexResponse> listener = mock(ActionListener.class);
        action.doExecute(null, request, listener);

        ArgumentCaptor<Exception> exceptionCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(listener).onFailure(exceptionCaptor.capture());
        assertTrue(exceptionCaptor.getValue().getMessage().contains("Query not found in judgment"));
        verify(judgmentDao, never()).updateJudgment(any(), anyLong(), anyLong(), any());
    }

    public void testRequestValidation_NullId() {
        UpdateJudgmentRatingsRequest request = new UpdateJudgmentRatingsRequest(
            null,
            List.of(new RatingAdjustment("superhero", "1", "0.9"))
        );
        assertNotNull(request.validate());
    }

    public void testRequestValidation_EmptyAdjustments() {
        UpdateJudgmentRatingsRequest request = new UpdateJudgmentRatingsRequest("id-1", List.of());
        assertNotNull(request.validate());
    }

    public void testRequestValidation_MissingFields() {
        UpdateJudgmentRatingsRequest request = new UpdateJudgmentRatingsRequest(
            "id-1",
            List.of(new RatingAdjustment("superhero", "1", ""))
        );
        assertNotNull(request.validate());
    }

    public void testRequestValidation_Valid() {
        UpdateJudgmentRatingsRequest request = new UpdateJudgmentRatingsRequest(
            "id-1",
            List.of(new RatingAdjustment("superhero", "1", "0.9"))
        );
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

    /**
     * A judgment with two queries, each holding one rated doc and one failed doc:
     * "superhero" -> rated 1, failed 5; "comedy" -> rated 2, failed 7.
     */
    private Map<String, Object> buildTwoQueryJudgmentSource() {
        Map<String, Object> source = new HashMap<>();
        source.put("type", JudgmentType.LLM_JUDGMENT.name());
        source.put("status", AsyncStatus.COMPLETED.name());
        source.put("name", "two query judgment");

        List<Map<String, Object>> judgmentRatings = new ArrayList<>();
        judgmentRatings.add(buildQueryEntry("superhero", "1", "0.9", "5"));
        judgmentRatings.add(buildQueryEntry("comedy", "2", "0.8", "7"));
        source.put("judgmentRatings", judgmentRatings);

        Map<String, Object> metadata = new HashMap<>();
        metadata.put("modelId", "test-model");
        metadata.put("querySetId", "qs-1");
        source.put("metadata", metadata);

        return source;
    }

    /** One query entry with a single rated doc and a single failed doc. */
    private Map<String, Object> buildQueryEntry(String query, String ratedDocId, String rating, String failedDocId) {
        Map<String, Object> queryEntry = new HashMap<>();
        queryEntry.put("query", query);

        List<Map<String, Object>> ratings = new ArrayList<>();
        Map<String, Object> rated = new HashMap<>();
        rated.put("docId", ratedDocId);
        rated.put("rating", rating);
        ratings.add(rated);
        queryEntry.put("ratings", ratings);

        List<Map<String, Object>> failures = new ArrayList<>();
        Map<String, Object> failed = new HashMap<>();
        failed.put("docId", failedDocId);
        failures.add(failed);
        queryEntry.put("failures", failures);

        return queryEntry;
    }

    /** Locate the entry for a query in a judgmentRatings list. */
    private Map<String, Object> findQueryEntry(List<Map<String, Object>> judgmentRatings, String query) {
        for (Map<String, Object> entry : judgmentRatings) {
            if (query.equals(entry.get("query"))) {
                return entry;
            }
        }
        throw new AssertionError("query not present in written judgment: " + query);
    }

    /** Read the rating value stored for a docId under a query entry. */
    @SuppressWarnings("unchecked")
    private String ratingOf(Map<String, Object> queryEntry, String docId) {
        for (Map<String, Object> rating : (List<Map<String, Object>>) queryEntry.get("ratings")) {
            if (docId.equals(rating.get("docId"))) {
                return String.valueOf(rating.get("rating"));
            }
        }
        throw new AssertionError("docId not rated: " + docId);
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
