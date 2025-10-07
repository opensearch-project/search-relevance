/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */
package org.opensearch.searchrelevance.executors;

import static org.opensearch.searchrelevance.metrics.EvaluationMetrics.calculateEvaluationMetrics;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.opensearch.action.search.SearchResponse;
import org.opensearch.core.action.ActionListener;
import org.opensearch.search.SearchHit;
import org.opensearch.searchrelevance.dao.EvaluationResultDao;
import org.opensearch.searchrelevance.dao.ExperimentVariantDao;
import org.opensearch.searchrelevance.model.AsyncStatus;
import org.opensearch.searchrelevance.model.EvaluationResult;
import org.opensearch.searchrelevance.model.ExperimentType;
import org.opensearch.searchrelevance.model.ExperimentVariant;
import org.opensearch.searchrelevance.utils.TimeUtils;

import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;

/**
 * Handles processing of search responses for experiment variants
 */
@Log4j2
@RequiredArgsConstructor
public class SearchResponseProcessor {
    private final EvaluationResultDao evaluationResultDao;
    private final ExperimentVariantDao experimentVariantDao;

    /**
     * Process search response and create evaluation results
     */
    public void processSearchResponse(
        SearchResponse response,
        ExperimentVariant experimentVariant,
        String experimentId,
        String searchConfigId,
        String queryText,
        int size,
        List<String> judgmentIds,
        Map<String, String> docIdToScores,
        String evaluationId,
        ExperimentTaskContext taskContext,
        String scheduledRunId
    ) {
        if (taskContext.getHasFailure().get()) return;

        try {
            if (response.getHits().getTotalHits().value() == 0) {
                handleNoHits(experimentVariant, experimentId, searchConfigId, evaluationId, taskContext);
                return;
            }

            SearchHit[] hits = response.getHits().getHits();
            List<String> docIds = Arrays.stream(hits).map(SearchHit::getId).collect(Collectors.toList());

            List<Map<String, Object>> metrics = calculateEvaluationMetrics(docIds, docIdToScores, size);

            // Pass null for experiment variant parameters if not a hybrid experiment
            String experimentVariantParameters = experimentVariant.getType() == ExperimentType.HYBRID_OPTIMIZER
                ? experimentVariant.getTextualParameters()
                : null;

            EvaluationResult evaluationResult = new EvaluationResult(
                evaluationId,
                TimeUtils.getTimestamp(),
                searchConfigId,
                queryText,
                judgmentIds,
                docIds,
                metrics,
                experimentId,
                experimentVariant.getId(),
                experimentVariantParameters,
                scheduledRunId
            );

            evaluationResultDao.putEvaluationResultEfficient(
                evaluationResult,
                ActionListener.wrap(
                    success -> updateExperimentVariant(experimentVariant, experimentId, searchConfigId, evaluationId, taskContext),
                    error -> handleTaskFailure(experimentVariant, error, taskContext)
                )
            );
        } catch (Exception e) {
            handleTaskFailure(experimentVariant, e, taskContext);
        }
    }

    private void handleNoHits(
        ExperimentVariant experimentVariant,
        String experimentId,
        String searchConfigId,
        String evaluationId,
        ExperimentTaskContext taskContext
    ) {
        log.warn("No hits found for search config: {} and variant: {}", searchConfigId, experimentVariant.getId());

        ExperimentVariant noHitsVariant = new ExperimentVariant(
            experimentVariant.getId(),
            TimeUtils.getTimestamp(),
            experimentVariant.getType(),
            AsyncStatus.COMPLETED,
            experimentId,
            experimentVariant.getParameters(),
            Map.of("evaluationResultId", evaluationId, "details", "no search hits found")
        );

        experimentVariantDao.putExperimentVariantEfficient(noHitsVariant, ActionListener.wrap(success -> {
            log.debug("Persisted no-hits variant: {}", experimentVariant.getId());
            taskContext.completeVariantFailure();
        }, error -> handleTaskFailure(experimentVariant, error, taskContext)));
    }

    private void updateExperimentVariant(
        ExperimentVariant experimentVariant,
        String experimentId,
        String searchConfigId,
        String evaluationId,
        ExperimentTaskContext taskContext
    ) {
        // Create variant directly with COMPLETED status
        ExperimentVariant completedVariant = new ExperimentVariant(
            experimentVariant.getId(),
            TimeUtils.getTimestamp(),
            experimentVariant.getType(),
            AsyncStatus.COMPLETED,
            experimentId,
            experimentVariant.getParameters(),
            Map.of("evaluationResultId", evaluationId)
        );

        taskContext.scheduleVariantWrite(completedVariant, evaluationId, true);

        log.debug("Scheduled write for completed experiment variant: {}", experimentVariant.getId());
        taskContext.completeVariantSuccess();
    }

    private void handleTaskFailure(ExperimentVariant experimentVariant, Exception e, ExperimentTaskContext taskContext) {
        log.error("Variant failure for {}: {}", experimentVariant.getId(), e.getMessage());
        taskContext.completeVariantFailure();
    }

    /**
     * Handle search failure
     */
    public void handleSearchFailure(
        Exception e,
        ExperimentVariant experimentVariant,
        String experimentId,
        String evaluationId,
        ExperimentTaskContext taskContext
    ) {
        ExperimentVariant experimentVariantResult = new ExperimentVariant(
            experimentVariant.getId(),
            TimeUtils.getTimestamp(),
            experimentVariant.getType(),
            AsyncStatus.ERROR,
            experimentId,
            experimentVariant.getParameters(),
            Map.of("evaluationResultId", evaluationId, "error", e.getMessage())
        );

        experimentVariantDao.putExperimentVariantEfficient(experimentVariantResult, ActionListener.wrap(success -> {
            log.error("Error executing variant {}: {}", experimentVariant.getId(), e.getMessage());
            taskContext.completeVariantFailure();
        }, error -> {
            log.error("Failed to persist error status for variant {}: {}", experimentVariant.getId(), error.getMessage());
            taskContext.completeVariantFailure();
        }));
    }
}
