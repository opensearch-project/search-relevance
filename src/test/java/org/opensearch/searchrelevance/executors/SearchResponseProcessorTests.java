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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.lucene.search.TotalHits;
import org.mockito.ArgumentCaptor;
import org.opensearch.action.search.SearchResponse;
import org.opensearch.common.unit.TimeValue;
import org.opensearch.core.action.ActionListener;
import org.opensearch.core.common.bytes.BytesArray;
import org.opensearch.search.SearchHit;
import org.opensearch.search.SearchHits;
import org.opensearch.searchrelevance.dao.EvaluationResultDao;
import org.opensearch.searchrelevance.dao.ExperimentVariantDao;
import org.opensearch.searchrelevance.model.AsyncStatus;
import org.opensearch.searchrelevance.model.EvaluationResult;
import org.opensearch.searchrelevance.model.ExperimentType;
import org.opensearch.searchrelevance.model.ExperimentVariant;
import org.opensearch.test.OpenSearchTestCase;

public class SearchResponseProcessorTests extends OpenSearchTestCase {

    private EvaluationResultDao evaluationResultDao;
    private ExperimentVariantDao experimentVariantDao;
    private SearchResponseProcessor processor;
    private ExperimentTaskContext taskContext;

    @Override
    public void setUp() throws Exception {
        super.setUp();
        evaluationResultDao = mock(EvaluationResultDao.class);
        experimentVariantDao = mock(ExperimentVariantDao.class);
        processor = new SearchResponseProcessor(evaluationResultDao, experimentVariantDao);
        taskContext = mock(ExperimentTaskContext.class);
        when(taskContext.getHasFailure()).thenReturn(new AtomicBoolean(false));
    }

    public void testProcessSearchResponsePersistsTookMs() {
        SearchResponse response = createSearchResponse(14L, "doc1", "doc2");
        ExperimentVariant variant = pointwiseVariant();
        stubSuccessfulEvaluationWrite();

        processor.processSearchResponse(
            response,
            variant,
            "experiment-1",
            "config-1",
            "red shoes",
            5,
            List.of("judgment-1"),
            Map.of("doc1", "2"),
            "eval-1",
            taskContext,
            null
        );

        ArgumentCaptor<EvaluationResult> captor = ArgumentCaptor.forClass(EvaluationResult.class);
        verify(evaluationResultDao).putEvaluationResultEfficient(captor.capture(), any(ActionListener.class));
        EvaluationResult stored = captor.getValue();
        assertEquals(Long.valueOf(14L), stored.tookMs());
        assertEquals("eval-1", stored.id());
        assertEquals("red shoes", stored.searchText());
        verify(taskContext).completeVariantSuccess();
        verify(experimentVariantDao, never()).putExperimentVariantEfficient(any(), any());
    }

    public void testProcessSearchResponseNoHitsPersistsTookMsOnVariant() {
        SearchResponse response = createSearchResponse(9L);
        ExperimentVariant variant = pointwiseVariant();
        stubSuccessfulVariantWrite();

        processor.processSearchResponse(
            response,
            variant,
            "experiment-1",
            "config-1",
            "red shoes",
            5,
            List.of("judgment-1"),
            Map.of(),
            "eval-1",
            taskContext,
            null
        );

        verify(evaluationResultDao, never()).putEvaluationResultEfficient(any(), any());
        ArgumentCaptor<ExperimentVariant> captor = ArgumentCaptor.forClass(ExperimentVariant.class);
        verify(experimentVariantDao).putExperimentVariantEfficient(captor.capture(), any(ActionListener.class));

        ExperimentVariant stored = captor.getValue();
        assertEquals(AsyncStatus.COMPLETED, stored.getStatus());
        assertEquals("no search hits found", stored.getResults().get("details"));
        assertEquals(9L, stored.getResults().get(EvaluationResult.TOOK_MS));
        assertEquals("eval-1", stored.getResults().get("evaluationResultId"));
        verify(taskContext).completeVariantFailure();
    }

    public void testHandleSearchFailureOmitsTookMs() {
        ExperimentVariant variant = pointwiseVariant();
        stubSuccessfulVariantWrite();

        processor.handleSearchFailure(new RuntimeException("search failed"), variant, "experiment-1", "eval-1", taskContext);

        ArgumentCaptor<ExperimentVariant> captor = ArgumentCaptor.forClass(ExperimentVariant.class);
        verify(experimentVariantDao).putExperimentVariantEfficient(captor.capture(), any(ActionListener.class));
        assertFalse(captor.getValue().getResults().containsKey(EvaluationResult.TOOK_MS));
        assertEquals(AsyncStatus.ERROR, captor.getValue().getStatus());
    }

    public void testProcessSearchResponseSkipsWhenTaskAlreadyFailed() {
        when(taskContext.getHasFailure()).thenReturn(new AtomicBoolean(true));
        SearchResponse response = createSearchResponse(14L, "doc1");

        processor.processSearchResponse(
            response,
            pointwiseVariant(),
            "experiment-1",
            "config-1",
            "red shoes",
            5,
            List.of(),
            Map.of(),
            "eval-1",
            taskContext,
            null
        );

        verify(evaluationResultDao, never()).putEvaluationResultEfficient(any(), any());
        verify(experimentVariantDao, never()).putExperimentVariantEfficient(any(), any());
    }

    private void stubSuccessfulEvaluationWrite() {
        doAnswer(invocation -> {
            ActionListener<?> listener = invocation.getArgument(1);
            listener.onResponse(null);
            return null;
        }).when(evaluationResultDao).putEvaluationResultEfficient(any(), any(ActionListener.class));
    }

    private void stubSuccessfulVariantWrite() {
        doAnswer(invocation -> {
            ActionListener<?> listener = invocation.getArgument(1);
            listener.onResponse(null);
            return null;
        }).when(experimentVariantDao).putExperimentVariantEfficient(any(), any(ActionListener.class));
    }

    private ExperimentVariant pointwiseVariant() {
        return ExperimentVariant.builder()
            .id("variant-1")
            .timestamp("2024-01-01T00:00:00.000Z")
            .type(ExperimentType.POINTWISE_EVALUATION)
            .status(AsyncStatus.PROCESSING)
            .experimentId("experiment-1")
            .parameters(Map.of())
            .results(Map.of())
            .build();
    }

    private SearchResponse createSearchResponse(long tookMs, String... docIds) {
        SearchResponse response = mock(SearchResponse.class);
        SearchHit[] searchHits = new SearchHit[docIds.length];
        for (int i = 0; i < docIds.length; i++) {
            searchHits[i] = new SearchHit(i + 1, docIds[i], Map.of(), Map.of());
            searchHits[i].sourceRef(new BytesArray("{}"));
        }
        SearchHits hits = new SearchHits(searchHits, new TotalHits(docIds.length, TotalHits.Relation.EQUAL_TO), 1.0f);
        when(response.getHits()).thenReturn(hits);
        when(response.getTook()).thenReturn(TimeValue.timeValueMillis(tookMs));
        return response;
    }
}
