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
import static org.mockito.Mockito.verify;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

import org.opensearch.core.action.ActionListener;
import org.opensearch.searchrelevance.dao.EvaluationResultDao;
import org.opensearch.searchrelevance.dao.ExperimentVariantDao;
import org.opensearch.searchrelevance.model.AsyncStatus;
import org.opensearch.searchrelevance.model.EvaluationResult;
import org.opensearch.searchrelevance.model.ExperimentType;
import org.opensearch.searchrelevance.model.ExperimentVariant;

/**
 * Tests for SearchResponseProcessor.processDocIds (remote engine mapped path)
 */
public class SearchResponseProcessorDocIdsTests extends org.apache.lucene.tests.util.LuceneTestCase {

    public void testProcessDocIdsPersistsEvaluationResult() throws Exception {
        // Mocks
        EvaluationResultDao evaluationResultDao = mock(EvaluationResultDao.class);
        ExperimentVariantDao experimentVariantDao = mock(ExperimentVariantDao.class);

        // Stub DAO calls to immediately succeed
        doAnswer(invocation -> {
            // Simulate success callback to allow downstream variant update
            ActionListener<?> listener = invocation.getArgument(1);
            listener.onResponse(null);
            return null;
        }).when(evaluationResultDao).putEvaluationResultEfficient(any(EvaluationResult.class), any(ActionListener.class));

        doAnswer(invocation -> {
            // Acknowledge variant write success
            ActionListener<?> listener = invocation.getArgument(1);
            listener.onResponse(null);
            return null;
        }).when(experimentVariantDao).putExperimentVariantEfficient(any(ExperimentVariant.class), any(ActionListener.class));

        // Under test
        SearchResponseProcessor processor = new SearchResponseProcessor(evaluationResultDao, experimentVariantDao);

        // Inputs
        String experimentId = "exp-1";
        String searchConfigId = "remote-config-1";
        String queryText = "test query";
        int size = 10;
        List<String> judgmentIds = List.of("j1", "j2");
        Map<String, String> docIdToScores = Map.of("A", "3", "B", "5");
        String evaluationId = "eval-1";

        ExperimentVariant variant = new ExperimentVariant(
            "variant-1",
            "2025-08-13T00:00:00Z",
            ExperimentType.REMOTE_SEARCH_EVALUATION,
            AsyncStatus.PROCESSING,
            experimentId,
            Map.of("remoteConfigId", searchConfigId),
            Map.of()
        );

        ExperimentTaskContext taskContext = new ExperimentTaskContext(
            experimentId,
            searchConfigId,
            queryText,
            1,
            new ConcurrentHashMap<>(),
            new CompletableFuture<>(),
            new AtomicBoolean(false),
            experimentVariantDao,
            ExperimentType.REMOTE_SEARCH_EVALUATION
        );

        // Act
        processor.processDocIds(
            List.of("A", "B"),
            variant,
            experimentId,
            searchConfigId,
            queryText,
            size,
            judgmentIds,
            docIdToScores,
            evaluationId,
            taskContext
        );

        // Assert - evaluation result persisted via efficient path
        verify(evaluationResultDao).putEvaluationResultEfficient(any(EvaluationResult.class), any(ActionListener.class));
    }
}
