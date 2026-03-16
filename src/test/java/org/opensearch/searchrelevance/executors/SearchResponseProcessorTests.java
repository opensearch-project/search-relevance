/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */
package org.opensearch.searchrelevance.executors;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.lucene.search.TotalHits;
import org.junit.Before;
import org.mockito.ArgumentCaptor;
import org.opensearch.action.search.SearchResponse;
import org.opensearch.core.action.ActionListener;
import org.opensearch.search.SearchHit;
import org.opensearch.search.SearchHits;
import org.opensearch.searchrelevance.dao.EvaluationResultDao;
import org.opensearch.searchrelevance.dao.ExperimentVariantDao;
import org.opensearch.searchrelevance.metrics.EvaluationMetrics;
import org.opensearch.searchrelevance.model.EvaluationResult;
import org.opensearch.searchrelevance.model.ExperimentType;
import org.opensearch.searchrelevance.model.ExperimentVariant;
import org.opensearch.test.OpenSearchTestCase;

public class SearchResponseProcessorTests extends OpenSearchTestCase {

    private EvaluationResultDao evaluationResultDao;
    private ExperimentVariantDao experimentVariantDao;
    private SearchResponseProcessor searchResponseProcessor;
    private ExperimentTaskContext taskContext;

    @Before
    public void setUp() throws Exception {
        super.setUp();
        evaluationResultDao = mock(EvaluationResultDao.class);
        experimentVariantDao = mock(ExperimentVariantDao.class);
        searchResponseProcessor = new SearchResponseProcessor(evaluationResultDao, experimentVariantDao);
        taskContext = mock(ExperimentTaskContext.class);
        when(taskContext.getHasFailure()).thenReturn(new AtomicBoolean(false));
    }

    public void testProcessSearchResponseWithZeroHits() {
        SearchResponse response = mock(SearchResponse.class);
        SearchHits hits = new SearchHits(new SearchHit[0], new TotalHits(0, TotalHits.Relation.EQUAL_TO), 0.0f);
        when(response.getHits()).thenReturn(hits);

        ExperimentVariant variant = mock(ExperimentVariant.class);
        when(variant.getType()).thenReturn(ExperimentType.POINTWISE_EVALUATION);
        when(variant.getId()).thenReturn("variantId");

        String experimentId = "experimentId";
        String searchConfigId = "searchConfigId";
        String queryText = "queryText";
        int size = 10;
        List<String> judgmentIds = Collections.emptyList();
        Map<String, String> docIdToScores = Collections.emptyMap();
        String evaluationId = "evaluationId";
        String scheduledRunId = "scheduledRunId";

        searchResponseProcessor.processSearchResponse(
            response,
            variant,
            experimentId,
            searchConfigId,
            queryText,
            size,
            judgmentIds,
            docIdToScores,
            evaluationId,
            taskContext,
            scheduledRunId
        );

        ArgumentCaptor<EvaluationResult> captor = ArgumentCaptor.forClass(EvaluationResult.class);
        verify(evaluationResultDao).putEvaluationResultEfficient(captor.capture(), any(ActionListener.class));

        EvaluationResult result = captor.getValue();
        assertEquals(EvaluationMetrics.TOTAL_METRIC_COUNT, result.metrics().size());
        for (Map<String, Object> metric : result.metrics()) {
            assertEquals(0.0, ((Number) metric.get("value")).doubleValue(), 0.001);
        }

        assertEquals(evaluationId, result.id());
        assertEquals(experimentId, result.experimentId());
        assertEquals("variantId", result.experimentVariantId());
    }

    public void testProcessSearchResponseWithHits() {
        SearchResponse response = mock(SearchResponse.class);
        SearchHit hit = new SearchHit(1, "doc1", Collections.emptyMap(), null);
        SearchHits hits = new SearchHits(new SearchHit[] { hit }, new TotalHits(1, TotalHits.Relation.EQUAL_TO), 1.0f);
        when(response.getHits()).thenReturn(hits);

        ExperimentVariant variant = mock(ExperimentVariant.class);
        when(variant.getType()).thenReturn(ExperimentType.POINTWISE_EVALUATION);
        when(variant.getId()).thenReturn("variantId");

        String experimentId = "experimentId";
        String searchConfigId = "searchConfigId";
        String queryText = "queryText";
        int size = 10;
        List<String> judgmentIds = Collections.emptyList();
        Map<String, String> docIdToScores = Collections.emptyMap();
        String evaluationId = "evaluationId";
        String scheduledRunId = "scheduledRunId";

        searchResponseProcessor.processSearchResponse(
            response,
            variant,
            experimentId,
            searchConfigId,
            queryText,
            size,
            judgmentIds,
            docIdToScores,
            evaluationId,
            taskContext,
            scheduledRunId
        );

        ArgumentCaptor<EvaluationResult> captor = ArgumentCaptor.forClass(EvaluationResult.class);
        verify(evaluationResultDao).putEvaluationResultEfficient(captor.capture(), any(ActionListener.class));

        EvaluationResult result = captor.getValue();
        assertFalse(result.metrics().isEmpty());
    }

    public void testProcessSearchResponseWithException() {
        ExperimentVariant variant = mock(ExperimentVariant.class);
        when(variant.getType()).thenReturn(ExperimentType.POINTWISE_EVALUATION);
        when(variant.getId()).thenReturn("variantId");

        // Passing null response to trigger exception
        searchResponseProcessor.processSearchResponse(
            null,
            variant,
            "experimentId",
            "searchConfigId",
            "queryText",
            10,
            Collections.emptyList(),
            Collections.emptyMap(),
            "evaluationId",
            taskContext,
            "scheduledRunId"
        );

        verify(taskContext).completeVariantFailure();
    }
}
