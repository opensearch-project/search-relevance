/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */
package org.opensearch.searchrelevance.experiment;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Queue;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import org.opensearch.action.search.SearchResponse;
import org.opensearch.common.cache.Cache;
import org.opensearch.common.cache.CacheBuilder;
import org.opensearch.common.unit.TimeValue;
import org.opensearch.core.action.ActionListener;
import org.opensearch.searchrelevance.dao.JudgmentDao;
import org.opensearch.searchrelevance.executors.ExperimentTaskManager;
import org.opensearch.searchrelevance.model.AsyncStatus;
import org.opensearch.searchrelevance.model.ExperimentType;
import org.opensearch.searchrelevance.model.ExperimentVariant;
import org.opensearch.searchrelevance.model.SearchConfigurationDetails;
import org.opensearch.searchrelevance.scheduler.ExperimentCancellationToken;
import org.opensearch.searchrelevance.utils.TimeUtils;

import lombok.extern.log4j.Log4j2;

/**
 * Processor for handling POINTWISE_EVALUATION experiments with task scheduling
 */
@Log4j2
public class PointwiseExperimentProcessor {

    private final JudgmentDao judgmentDao;
    private final ExperimentTaskManager taskManager;

    // Use OpenSearch's built-in cache implementation with bounded size
    private final Cache<String, Map<String, String>> judgmentCache;

    // Configuration constants
    private static final long CACHE_SIZE = 100_000;
    private static final TimeValue CACHE_EXPIRE_TIME = TimeValue.timeValueHours(1);

    public PointwiseExperimentProcessor(JudgmentDao judgmentDao, ExperimentTaskManager taskManager) {
        this.judgmentDao = judgmentDao;
        this.taskManager = taskManager;

        // Initialize cache with size limit and TTL
        this.judgmentCache = CacheBuilder.<String, Map<String, String>>builder()
            .setMaximumWeight(CACHE_SIZE)
            .setExpireAfterAccess(CACHE_EXPIRE_TIME)
            .build();
    }

    /**
     * Process pointwise evaluation experiment with simple optimizations
     */
    public void processPointwiseExperiment(
        String experimentId,
        String queryText,
        Map<String, SearchConfigurationDetails> searchConfigurations,
        List<String> judgmentList,
        int size,
        AtomicBoolean hasFailure,
        String scheduledRunId,
        ExperimentCancellationToken cancellationToken,
        ActionListener<Map<String, Object>> listener
    ) {
        log.info(
            "Starting pointwise experiment {} with {} search configurations for query: {}",
            experimentId,
            searchConfigurations.size(),
            queryText
        );

        // Load judgments once and cache them
        loadJudgmentsAsync(experimentId, judgmentList, queryText).thenAccept(docIdToScores -> {
            log.info("Loaded {} document ratings for experiment {}", docIdToScores.size(), experimentId);
            processExperimentWithJudgments(
                experimentId,
                queryText,
                searchConfigurations,
                judgmentList,
                size,
                docIdToScores,
                hasFailure,
                scheduledRunId,
                cancellationToken,
                listener
            );
        }).exceptionally(e -> {
            if (hasFailure.compareAndSet(false, true)) {
                listener.onFailure(new Exception("Failed to load judgments", e));
            }
            return null;
        });
    }

    /**
     * Load and cache judgments for the experiment
     */
    private CompletableFuture<Map<String, String>> loadJudgmentsAsync(String experimentId, List<String> judgmentList, String queryText) {
        String cacheKey = experimentId + ":" + queryText;
        Map<String, String> cached = judgmentCache.get(cacheKey);
        if (Objects.nonNull(cached)) {
            return CompletableFuture.completedFuture(cached);
        }

        AtomicInteger failureCount = new AtomicInteger(0);
        int failureThreshold = Math.min(5, judgmentList.size());

        // Load judgments in parallel
        List<CompletableFuture<SearchResponse>> judgmentFutures = judgmentList.stream().map(judgmentId -> {
            CompletableFuture<SearchResponse> future = new CompletableFuture<>();
            judgmentDao.getJudgment(judgmentId, ActionListener.wrap(future::complete, future::completeExceptionally));
            return future;
        }).toList();

        return CompletableFuture.allOf(judgmentFutures.toArray(new CompletableFuture[0])).thenApply(v -> {
            Map<String, String> docIdToScores = new HashMap<>();

            for (CompletableFuture<SearchResponse> future : judgmentFutures) {
                try {
                    SearchResponse response = future.join();
                    extractJudgmentScores(queryText, response, docIdToScores);
                } catch (Exception e) {
                    log.error("Failed to process judgment response: {}", e.getMessage());
                    if (failureCount.incrementAndGet() >= failureThreshold) {
                        throw new RuntimeException(
                            String.format(
                                Locale.ROOT,
                                "Failed to load judgments: exceeded failure threshold %d/%d",
                                failureCount.get(),
                                failureThreshold
                            ),
                            e
                        );
                    }
                }
            }

            judgmentCache.put(cacheKey, docIdToScores);
            return docIdToScores;
        });
    }

    /**
     * Extract judgment scores from SearchResponse
     */
    private void extractJudgmentScores(String queryText, SearchResponse response, Map<String, String> docIdToScores) {
        if (Objects.isNull(response.getHits()) || response.getHits().getTotalHits().value() == 0) {
            return;
        }

        Map<String, Object> sourceAsMap = response.getHits().getHits()[0].getSourceAsMap();
        List<Map<String, Object>> judgmentRatings = (List<Map<String, Object>>) sourceAsMap.getOrDefault(
            "judgmentRatings",
            Collections.emptyList()
        );

        for (Map<String, Object> rating : judgmentRatings) {
            if (queryText.equals(rating.get("query"))) {
                List<Map<String, String>> docScoreRatings = (List<Map<String, String>>) rating.get("ratings");
                if (Objects.nonNull(docScoreRatings)) {
                    docScoreRatings.forEach(docScoreRating -> docIdToScores.put(docScoreRating.get("docId"), docScoreRating.get("rating")));
                }
                break;
            }
        }
    }

    /**
     * Process experiment with loaded judgments
     */
    private void processExperimentWithJudgments(
        String experimentId,
        String queryText,
        Map<String, SearchConfigurationDetails> searchConfigurations,
        List<String> judgmentList,
        int size,
        Map<String, String> docIdToScores,
        AtomicBoolean hasFailure,
        String scheduledRunId,
        ExperimentCancellationToken cancellationToken,
        ActionListener<Map<String, Object>> listener
    ) {
        // Create simple variants
        List<ExperimentVariant> experimentVariants = createPointwiseVariants(experimentId, searchConfigurations);

        // Process configurations in parallel
        Map<String, Object> configToExperimentVariants = new ConcurrentHashMap<>();
        // Use ConcurrentLinkedQueue for lock-free additions
        Queue<Map<String, Object>> allResults = new ConcurrentLinkedQueue<>();

        List<CompletableFuture<Void>> configFutures = searchConfigurations.entrySet().stream().map(entry -> {
            String searchConfigId = entry.getKey();
            SearchConfigurationDetails configDetails = entry.getValue();
            String index = configDetails.getIndex();
            String query = configDetails.getQuery();

            // Filter variants for this configuration
            List<ExperimentVariant> configVariants = experimentVariants.stream()
                .filter(variant -> searchConfigId.equals(variant.getParameters().get("searchConfigId")))
                .collect(Collectors.toList());

            // Use task manager to process variants
            CompletableFuture<Map<String, Object>> configFuture = taskManager.scheduleTasksAsync(
                ExperimentType.POINTWISE_EVALUATION,
                experimentId,
                searchConfigId,
                index,
                query,
                queryText,
                size,
                configVariants,
                judgmentList,
                docIdToScores,
                configToExperimentVariants,
                hasFailure,
                scheduledRunId,
                null,
                cancellationToken
            );

            // Transform results
            return configFuture.thenAccept(results -> {
                List<Map<String, Object>> configEvaluationResults = (List<Map<String, Object>>) results.get("evaluationResults");

                if (Objects.nonNull(configEvaluationResults) && !configEvaluationResults.isEmpty()) {
                    for (Map<String, Object> evalResult : configEvaluationResults) {
                        Map<String, Object> result = new HashMap<>();
                        result.put("evaluationId", evalResult.get("evaluationId"));
                        result.put("searchConfigurationId", searchConfigId);
                        result.put("queryText", queryText);
                        allResults.add(result);
                    }
                } else {
                    Map<String, Object> result = new HashMap<>();
                    result.put("queryText", queryText);
                    allResults.add(result);
                }
            }).exceptionally(ex -> {
                log.error("Failed to process config {}: {}", searchConfigId, ex.getMessage());
                return null;
            });
        }).collect(Collectors.toList());

        // Wait for all configurations to complete
        CompletableFuture.allOf(configFutures.toArray(new CompletableFuture[0])).thenAccept(v -> {
            Map<String, Object> queryResponse = new HashMap<>();
            queryResponse.put("results", new ArrayList<>(allResults));

            log.info("Completed pointwise experiment {} with {} results", experimentId, allResults.size());
            listener.onResponse(queryResponse);
        }).exceptionally(e -> {
            if (hasFailure.compareAndSet(false, true)) {
                listener.onFailure(new Exception("Failed to process search configurations", e));
            }
            return null;
        });
    }

    /**
     * Create simple experiment variants
     */
    private List<ExperimentVariant> createPointwiseVariants(
        String experimentId,
        Map<String, SearchConfigurationDetails> searchConfigurations
    ) {
        return searchConfigurations.entrySet().stream().map(entry -> {
            String searchConfigId = entry.getKey();
            SearchConfigurationDetails configDetails = entry.getValue();
            String searchPipeline = configDetails.getPipeline();

            Map<String, Object> parameters = new HashMap<>();
            parameters.put("searchConfigId", searchConfigId);
            parameters.put("searchPipeline", searchPipeline);

            return new ExperimentVariant(
                UUID.randomUUID().toString(),
                TimeUtils.getTimestamp(),
                ExperimentType.POINTWISE_EVALUATION,
                AsyncStatus.PROCESSING,
                experimentId,
                parameters,
                Map.of()
            );
        }).collect(Collectors.toList());
    }
}
