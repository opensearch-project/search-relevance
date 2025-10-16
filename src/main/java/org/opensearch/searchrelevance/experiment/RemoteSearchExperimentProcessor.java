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
import org.opensearch.searchrelevance.utils.TimeUtils;

import lombok.extern.log4j.Log4j2;

/**
 * Processor for handling REMOTE_SEARCH_EVALUATION experiments with task scheduling
 * Uses ExperimentTaskManager to execute remote queries via RemoteSearchExecutor.
 */
@Log4j2
public class RemoteSearchExperimentProcessor {

    private final JudgmentDao judgmentDao;
    private final ExperimentTaskManager taskManager;

    // Use OpenSearch's built-in cache implementation with bounded size
    private final Cache<String, Map<String, String>> judgmentCache;

    // Configuration constants
    private static final long CACHE_SIZE = 100_000;
    private static final TimeValue CACHE_EXPIRE_TIME = TimeValue.timeValueHours(1);

    public RemoteSearchExperimentProcessor(JudgmentDao judgmentDao, ExperimentTaskManager taskManager) {
        this.judgmentDao = judgmentDao;
        this.taskManager = taskManager;

        // Initialize cache with size limit and TTL
        this.judgmentCache = CacheBuilder.<String, Map<String, String>>builder()
            .setMaximumWeight(CACHE_SIZE)
            .setExpireAfterAccess(CACHE_EXPIRE_TIME)
            .build();
    }

    /**
     * Process remote search evaluation experiment (per queryText).
     * remoteConfigIds holds the list of remote configuration IDs to execute against.
     */
    public void processRemoteExperiment(
        String experimentId,
        String queryText,
        List<String> remoteConfigIds,
        List<String> judgmentList,
        int size,
        AtomicBoolean hasFailure,
        ActionListener<Map<String, Object>> listener
    ) {
        log.info(
            "Starting remote search experiment {} with {} remote configs for query: {}",
            experimentId,
            remoteConfigIds.size(),
            queryText
        );

        // Load judgments once and cache them
        loadJudgmentsAsync(experimentId, judgmentList, queryText).thenAccept(docIdToScores -> {
            log.info("Loaded {} document ratings for experiment {}", docIdToScores.size(), experimentId);
            processWithJudgments(experimentId, queryText, remoteConfigIds, judgmentList, size, docIdToScores, hasFailure, listener);
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
    @SuppressWarnings("unchecked")
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
    private void processWithJudgments(
        String experimentId,
        String queryText,
        List<String> remoteConfigIds,
        List<String> judgmentList,
        int size,
        Map<String, String> docIdToScores,
        AtomicBoolean hasFailure,
        ActionListener<Map<String, Object>> listener
    ) {
        // Create one variant per remote configuration
        List<ExperimentVariant> variants = createRemoteVariants(experimentId, remoteConfigIds);

        // Process configurations in parallel
        Map<String, Object> configToExperimentVariants = new ConcurrentHashMap<>();
        Queue<Map<String, Object>> allResults = new ConcurrentLinkedQueue<>();

        List<CompletableFuture<Void>> configFutures = remoteConfigIds.stream().map(remoteConfigId -> {
            // Filter variants for this remote configuration
            List<ExperimentVariant> configVariants = variants.stream()
                .filter(v -> remoteConfigId.equals(v.getParameters().get("remoteConfigId")))
                .collect(Collectors.toList());

            // Use task manager to process variants
            CompletableFuture<Map<String, Object>> configFuture = taskManager.scheduleTasksAsync(
                ExperimentType.REMOTE_SEARCH_EVALUATION,
                experimentId,
                remoteConfigId,     // reuse field to track by remote config id
                "",                 // index not used for remote
                "{}",               // query body not needed - templates can use queryText/size
                queryText,
                size,
                configVariants,
                judgmentList,
                docIdToScores,
                configToExperimentVariants,
                hasFailure
            );

            // Transform results to a compact representation (similar to Pointwise)
            return configFuture.thenAccept(results -> {
                List<Map<String, Object>> evaluationResults = (List<Map<String, Object>>) results.get("evaluationResults");
                if (evaluationResults != null && !evaluationResults.isEmpty()) {
                    for (Map<String, Object> evalResult : evaluationResults) {
                        Map<String, Object> result = new HashMap<>();
                        result.put("evaluationId", evalResult.get("evaluationId"));
                        result.put("searchConfigurationId", remoteConfigId); // keep field name for compatibility
                        result.put("queryText", queryText);
                        allResults.add(result);
                    }
                } else {
                    Map<String, Object> result = new HashMap<>();
                    result.put("queryText", queryText);
                    result.put("searchConfigurationId", remoteConfigId);
                    allResults.add(result);
                }
            }).exceptionally(ex -> {
                log.error("Failed to process remote config {}: {}", remoteConfigId, ex.getMessage());
                return null;
            });
        }).collect(Collectors.toList());

        // Wait for all configurations to complete
        CompletableFuture.allOf(configFutures.toArray(new CompletableFuture[0])).thenAccept(v -> {
            Map<String, Object> queryResponse = new HashMap<>();
            queryResponse.put("results", new ArrayList<>(allResults));

            log.info("Completed remote experiment {} with {} results", experimentId, allResults.size());
            listener.onResponse(queryResponse);
        }).exceptionally(e -> {
            if (hasFailure.compareAndSet(false, true)) {
                listener.onFailure(new Exception("Failed to process remote configurations", e));
            }
            return null;
        });
    }

    /**
     * Create one experiment variant per remote configuration
     */
    private List<ExperimentVariant> createRemoteVariants(String experimentId, List<String> remoteConfigIds) {
        return remoteConfigIds.stream().map(remoteConfigId -> {
            Map<String, Object> parameters = new HashMap<>();
            parameters.put("remoteConfigId", remoteConfigId);

            return new ExperimentVariant(
                UUID.randomUUID().toString(),
                TimeUtils.getTimestamp(),
                ExperimentType.REMOTE_SEARCH_EVALUATION,
                AsyncStatus.PROCESSING,
                experimentId,
                parameters,
                Map.of()
            );
        }).collect(Collectors.toList());
    }
}
