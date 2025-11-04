/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */
package org.opensearch.searchrelevance.executors;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import org.opensearch.core.action.ActionListener;
import org.opensearch.searchrelevance.dao.ExperimentVariantDao;
import org.opensearch.searchrelevance.model.ExperimentBatchStatus;
import org.opensearch.searchrelevance.model.ExperimentType;
import org.opensearch.searchrelevance.model.ExperimentVariant;

import lombok.Getter;
import lombok.extern.log4j.Log4j2;

/**
 * Context for tracking tasks for a specific experiment
 */
@Log4j2
@Getter
public class ExperimentTaskContext {
    private final String experimentId;
    private final String searchConfigId;
    private final String queryText;
    private final int totalVariants;
    private final ConcurrentHashMap<String, Object> configToExperimentVariants;
    private final CompletableFuture<Map<String, Object>> resultFuture;
    private final AtomicBoolean hasFailure;
    private final ExperimentVariantDao experimentVariantDao;
    private final ExperimentType experimentType;

    private final AtomicInteger remainingVariants;
    private final AtomicInteger successfulVariants;
    private final AtomicInteger failedVariants;

    public ExperimentTaskContext(
        String experimentId,
        String searchConfigId,
        String queryText,
        int totalVariants,
        ConcurrentHashMap<String, Object> configToExperimentVariants,
        CompletableFuture<Map<String, Object>> resultFuture,
        AtomicBoolean hasFailure,
        ExperimentVariantDao experimentVariantDao,
        ExperimentType experimentType
    ) {
        this.experimentId = experimentId;
        this.searchConfigId = searchConfigId;
        this.queryText = queryText;
        this.totalVariants = totalVariants;
        this.configToExperimentVariants = configToExperimentVariants;
        this.resultFuture = resultFuture;
        this.hasFailure = hasFailure;
        this.experimentVariantDao = experimentVariantDao;
        this.experimentType = experimentType;
        this.remainingVariants = new AtomicInteger(totalVariants);
        this.successfulVariants = new AtomicInteger(0);
        this.failedVariants = new AtomicInteger(0);

        log.info("TaskContext initialized for experiment {} with {} variants", experimentId, totalVariants);
    }

    /**
     * Non-blocking variant write
     */
    public void scheduleVariantWrite(ExperimentVariant variant, String evaluationId, boolean isSuccess) {
        // Store evaluationId immediately for successful variants before async write
        if (isSuccess) {
            ConcurrentHashMap<String, Object> map = (ConcurrentHashMap<String, Object>) configToExperimentVariants.get(searchConfigId);
            if (map != null) {
                map.put(variant.getId(), evaluationId);
            }
        }

        CompletableFuture.runAsync(() -> {
            experimentVariantDao.putExperimentVariantEfficient(variant, ActionListener.wrap(response -> {
                log.debug("write successful for variant: {}", variant.getId());
            }, error -> { log.error("write failed for variant {}: {}", variant.getId(), error.getMessage()); }));
        });
    }

    /**
     * Mark a variant as successfully completed
     */
    public void completeVariantSuccess() {
        successfulVariants.incrementAndGet();
        completeVariant();
    }

    /**
     * Mark a variant as failed
     */
    public void completeVariantFailure() {
        failedVariants.incrementAndGet();
        completeVariant();
    }

    /**
     * Mark a variant as complete and check if all variants are done
     */
    private void completeVariant() {
        if (remainingVariants.decrementAndGet() == 0) {
            finishExperiment();
        }
    }

    /**
     * Finish the experiment and send final response
     */
    private void finishExperiment() {
        Map<String, Object> transformedConfigToExperimentVariants = new HashMap<>();
        transformedConfigToExperimentVariants.put("searchConfigurationId", searchConfigId);

        List<Map<String, Object>> evaluationResults = formatEvaluationResults();
        transformedConfigToExperimentVariants.put("evaluationResults", evaluationResults);

        Map<String, Object> summary = new HashMap<>();
        summary.put("totalVariants", totalVariants);
        summary.put("successfulVariants", successfulVariants.get());
        summary.put("failedVariants", failedVariants.get());
        transformedConfigToExperimentVariants.put("summary", summary);

        ExperimentBatchStatus status;
        if (failedVariants.get() == totalVariants) {
            log.error(
                "All {} variants failed for search config {} in experiment {} with query '{}' - continuing experiment",
                totalVariants,
                searchConfigId,
                experimentId,
                queryText
            );
            status = ExperimentBatchStatus.ALL_FAILED;
        } else if (failedVariants.get() > 0) {
            log.warn(
                "Partial failure for search config {} in experiment {} with query '{}': {}/{} variants succeeded",
                searchConfigId,
                experimentId,
                queryText,
                successfulVariants.get(),
                totalVariants
            );
            status = ExperimentBatchStatus.PARTIAL_SUCCESS;
        } else {
            status = ExperimentBatchStatus.SUCCESS;
        }

        transformedConfigToExperimentVariants.put("status", status);

        // Complete the future with the results
        resultFuture.complete(transformedConfigToExperimentVariants);
    }

    /**
     * Format evaluation results for the final response based on experiment type
     */
    private List<Map<String, Object>> formatEvaluationResults() {
        List<Map<String, Object>> results = new ArrayList<>();
        ConcurrentHashMap<String, Object> configMap = (ConcurrentHashMap<String, Object>) configToExperimentVariants.get(searchConfigId);

        if (experimentType == ExperimentType.POINTWISE_EVALUATION) {
            // Pointwise format: individual entries with evaluationId, searchConfigurationId, queryText
            if (configMap != null && !configMap.isEmpty()) {
                configMap.forEach((variantId, evalId) -> {
                    Map<String, Object> result = new HashMap<>();
                    result.put("evaluationId", evalId);
                    result.put("searchConfigurationId", searchConfigId);
                    result.put("queryText", queryText);
                    results.add(result);
                });
            } else {
                // When no evaluations exist, add simple queryText entry
                Map<String, Object> result = new HashMap<>();
                result.put("queryText", queryText);
                results.add(result);
            }
        } else if (experimentType == ExperimentType.HYBRID_OPTIMIZER) {
            // Hybrid optimizer format: evaluationId and experimentVariantId
            if (configMap != null) {
                configMap.forEach((variantId, evalId) -> {
                    Map<String, Object> result = new HashMap<>();
                    result.put("evaluationId", evalId);
                    result.put("experimentVariantId", variantId);
                    results.add(result);
                });
            }
        } else {
            if (configMap != null && !configMap.isEmpty()) {
                configMap.forEach((variantId, evalId) -> {
                    Map<String, Object> result = new HashMap<>();
                    result.put("evaluationId", evalId);
                    result.put("experimentVariantId", variantId);
                    results.add(result);
                });
            }
        }
        return results;
    }
}
