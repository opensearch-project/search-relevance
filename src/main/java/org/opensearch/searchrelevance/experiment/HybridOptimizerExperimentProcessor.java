/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */
package org.opensearch.searchrelevance.experiment;

import static org.opensearch.searchrelevance.common.MetricsConstants.POINTWISE_FIELD_NAME_SEARCH_CONFIGURATION_ID;
import static org.opensearch.searchrelevance.experiment.ExperimentOptionsForHybridSearch.EXPERIMENT_OPTION_COMBINATION_TECHNIQUE;
import static org.opensearch.searchrelevance.experiment.ExperimentOptionsForHybridSearch.EXPERIMENT_OPTION_NORMALIZATION_TECHNIQUE;
import static org.opensearch.searchrelevance.experiment.ExperimentOptionsForHybridSearch.EXPERIMENT_OPTION_WEIGHTS_FOR_COMBINATION;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Future;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;

import org.opensearch.action.search.SearchResponse;
import org.opensearch.core.action.ActionListener;
import org.opensearch.searchrelevance.dao.JudgmentDao;
import org.opensearch.searchrelevance.executors.ExperimentTaskManager;
import org.opensearch.searchrelevance.model.AsyncStatus;
import org.opensearch.searchrelevance.model.ExperimentType;
import org.opensearch.searchrelevance.model.ExperimentVariant;
import org.opensearch.searchrelevance.model.SearchConfigurationDetails;
import org.opensearch.searchrelevance.scheduler.ExperimentCancellationToken;
import org.opensearch.searchrelevance.utils.TimeUtils;

import com.google.common.annotations.VisibleForTesting;

import lombok.AllArgsConstructor;
import lombok.extern.log4j.Log4j2;

/**
 * Processor for handling HYBRID_OPTIMIZER experiments with non-blocking async operations
 */
@Log4j2
@AllArgsConstructor
public class HybridOptimizerExperimentProcessor {

    private final JudgmentDao judgmentDao;
    private final ExperimentTaskManager taskManager;

    /**
     * Process hybrid optimizer experiment using non-blocking async operations
     *
     * @param experimentId Experiment ID
     * @param queryText Query text to process
     * @param searchConfigurations Map of search configuration IDs to SearchConfigurationDetails
     * @param judgmentList List of judgment IDs
     * @param size Result size
     * @param hasFailure Failure flag
     * @param scheduledRunId id for the experiment to be scheduled
     * @param cancellationToken token to indicate whether the task has been cancelled
     * @param runningFutures futures set to be cancelled when the token is cancelled
     * @param listener Listener to notify when processing is complete
     */
    public void processHybridOptimizerExperiment(
        String experimentId,
        String queryText,
        Map<String, SearchConfigurationDetails> searchConfigurations,
        List<String> judgmentList,
        int size,
        AtomicBoolean hasFailure,
        String scheduledRunId,
        ExperimentCancellationToken cancellationToken,
        Map<String, List<Future<?>>> runningFutures,
        ActionListener<Map<String, Object>> listener
    ) {
        // Create parameter combinations for hybrid search
        Map<String, Object> defaultParametersForHybridSearch = ExperimentOptionsFactory.createDefaultExperimentParametersForHybridSearch();
        ExperimentOptionsForHybridSearch experimentOptionForHybridSearch = (ExperimentOptionsForHybridSearch) ExperimentOptionsFactory
            .createExperimentOptions(ExperimentOptionsFactory.HYBRID_SEARCH_EXPERIMENT_OPTIONS, defaultParametersForHybridSearch);

        List<ExperimentVariantHybridSearchDTO> experimentVariantDTOs = experimentOptionForHybridSearch.getParameterCombinations(true);
        List<ExperimentVariant> experimentVariants = new ArrayList<>();

        log.info(
            "Starting hybrid optimizer experiment {} with {} parameter combinations for query: {}",
            experimentId,
            experimentVariantDTOs.size(),
            queryText
        );

        for (ExperimentVariantHybridSearchDTO experimentVariantDTO : experimentVariantDTOs) {
            Map<String, Object> parameters = new HashMap<>(
                Map.of(
                    EXPERIMENT_OPTION_NORMALIZATION_TECHNIQUE,
                    experimentVariantDTO.getNormalizationTechnique(),
                    EXPERIMENT_OPTION_COMBINATION_TECHNIQUE,
                    experimentVariantDTO.getCombinationTechnique(),
                    EXPERIMENT_OPTION_WEIGHTS_FOR_COMBINATION,
                    experimentVariantDTO.getQueryWeightsForCombination()
                )
            );
            String experimentVariantId = UUID.randomUUID().toString();

            // Create lightweight ExperimentVariant without storing it to index
            ExperimentVariant experimentVariant = new ExperimentVariant(
                experimentVariantId,
                TimeUtils.getTimestamp(),
                ExperimentType.HYBRID_OPTIMIZER,
                AsyncStatus.PROCESSING,
                experimentId,
                parameters,
                Map.of()
            );
            experimentVariants.add(experimentVariant);
        }

        log.info(
            "Experiment {}: Created {} experiment variants, proceeding to judgment processing",
            experimentId,
            experimentVariants.size()
        );

        // Process judgments asynchronously
        processJudgmentsAsync(queryText, judgmentList).thenAccept(docIdToScores -> {
            log.info("Processing search configurations for query '{}' with {} document ratings", queryText, docIdToScores.size());

            // Process search configurations with optimized task manager
            processSearchConfigurationsAsync(
                experimentId,
                queryText,
                searchConfigurations,
                judgmentList,
                size,
                experimentVariants,
                docIdToScores,
                hasFailure,
                scheduledRunId,
                cancellationToken,
                runningFutures,
                listener
            );
        }).exceptionally(e -> {
            if (hasFailure.compareAndSet(false, true)) {
                listener.onFailure(new Exception("Failed to process judgments", e));
            }
            return null;
        });
    }

    /**
     * Process judgments asynchronously using CompletableFuture
     */
    private CompletableFuture<Map<String, String>> processJudgmentsAsync(String queryText, List<String> judgmentList) {
        log.info("Processing {} judgments for query: {}", judgmentList.size(), queryText);

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
                }
            }

            if (docIdToScores.isEmpty()) {
                log.warn("No ratings found for query: {} in any judgment responses", queryText);
            } else {
                log.info("Found {} document ratings for query: {}", docIdToScores.size(), queryText);
            }

            return docIdToScores;
        });
    }

    /**
     * Extract judgment scores from SearchResponse
     */
    private void extractJudgmentScores(String queryText, SearchResponse response, Map<String, String> docIdToScores) {
        if (response.getHits().getTotalHits().value() == 0) {
            log.warn("No judgment found in response");
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
                if (docScoreRatings != null) {
                    docScoreRatings.forEach(docScoreRating -> docIdToScores.put(docScoreRating.get("docId"), docScoreRating.get("rating")));
                }
                break;
            }
        }
    }

    private boolean checkIfCancelled(ExperimentCancellationToken cancellationToken) {
        if (cancellationToken != null && cancellationToken.isCancelled()) {
            return true;
        }
        return false;
    }

    /**
     * Process search configurations using optimized task manager
     */
    @VisibleForTesting
    void processSearchConfigurationsAsync(
        String experimentId,
        String queryText,
        Map<String, SearchConfigurationDetails> searchConfigurations,
        List<String> judgmentList,
        int size,
        List<ExperimentVariant> experimentVariants,
        Map<String, String> docIdToScores,
        AtomicBoolean hasFailure,
        String scheduledRunId,
        ExperimentCancellationToken cancellationToken,
        Map<String, List<Future<?>>> runningFutures,
        ActionListener<Map<String, Object>> finalListener
    ) {
        Map<String, Object> hydratedResults = new ConcurrentHashMap<>();
        List<Map<String, Object>> queryResults = Collections.synchronizedList(new ArrayList<>());

        // Create futures for each search configuration
        List<CompletableFuture<Map<String, Object>>> configFutures = new ArrayList<>();

        for (Map.Entry<String, SearchConfigurationDetails> entry : searchConfigurations.entrySet()) {
            // If the task is cancelled, we will stop scheduling more configurations, add a failed future,
            // and signal that there has been a failure to the task scheduler.
            if (checkIfCancelled(cancellationToken)) {
                CompletableFuture<Map<String, Object>> futureToFail = new CompletableFuture<>();
                configFutures.add(futureToFail);
                futureToFail.completeExceptionally(new TimeoutException("Timed out when processing search configurations"));
                if (hasFailure.compareAndSet(false, true)) {
                    finalListener.onFailure(new TimeoutException("Timed out when processing search configurations"));
                }
                log.info("When processing search configurations in HybridOptimizerExperimentProcessor, a timeout cancellation occured");
                break;
            }
            String searchConfigId = entry.getKey();
            SearchConfigurationDetails configDetails = entry.getValue();
            String index = configDetails.getIndex();
            String query = configDetails.getQuery();

            // Use optimized task manager to process variants
            CompletableFuture<Map<String, Object>> configFuture = taskManager.scheduleTasksAsync(
                ExperimentType.HYBRID_OPTIMIZER,
                experimentId,
                searchConfigId,
                index,
                query,
                queryText,
                size,
                experimentVariants,
                judgmentList,
                docIdToScores,
                hydratedResults,
                hasFailure,
                scheduledRunId,
                runningFutures,
                cancellationToken
            );

            // Transform the result for this search configuration
            CompletableFuture<Map<String, Object>> transformedFuture = configFuture.thenApply(results -> {
                List<Map<String, Object>> evaluationResults = (List<Map<String, Object>>) results.get("evaluationResults");

                if (evaluationResults != null && !evaluationResults.isEmpty()) {
                    Map<String, Object> searchConfigResult = new HashMap<>();
                    searchConfigResult.put(POINTWISE_FIELD_NAME_SEARCH_CONFIGURATION_ID, searchConfigId);
                    searchConfigResult.put("evaluationResults", new ArrayList<>(evaluationResults));
                    queryResults.add(searchConfigResult);
                }

                return results;
            });

            configFutures.add(transformedFuture);
        }

        // Wait for all configurations to complete
        CompletableFuture.allOf(configFutures.toArray(new CompletableFuture[0])).thenAccept(v -> {
            Map<String, Object> queryResponse = new HashMap<>();
            queryResponse.put("searchConfigurationResults", new ArrayList<>(queryResults));
            finalListener.onResponse(queryResponse);
        }).exceptionally(e -> {
            if (hasFailure.compareAndSet(false, true)) {
                finalListener.onFailure(new Exception("Failed to process search configurations", e));
            }
            return null;
        });
    }
}
