/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */
package org.opensearch.searchrelevance.executors;

import static org.opensearch.searchrelevance.executors.SearchRelevanceExecutor.SEARCH_RELEVANCE_EXEC_THREAD_POOL_NAME;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Future;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;

import org.opensearch.action.search.SearchRequest;
import org.opensearch.cluster.block.ClusterBlockException;
import org.opensearch.common.inject.Inject;
import org.opensearch.core.action.ActionListener;
import org.opensearch.core.common.breaker.CircuitBreakingException;
import org.opensearch.searchrelevance.dao.EvaluationResultDao;
import org.opensearch.searchrelevance.dao.ExperimentVariantDao;
import org.opensearch.searchrelevance.experiment.QuerySourceUtil;
import org.opensearch.searchrelevance.model.ExperimentType;
import org.opensearch.searchrelevance.model.ExperimentVariant;
import org.opensearch.searchrelevance.model.builder.SearchRequestBuilder;
import org.opensearch.searchrelevance.scheduler.ExperimentCancellationToken;
import org.opensearch.threadpool.ThreadPool;
import org.opensearch.transport.client.Client;

import lombok.extern.log4j.Log4j2;

/**
 * Task manager for scheduling experiment variant tasks using {@link BatchedAsyncExecutor}
 * for concurrency control. Supports HYBRID_OPTIMIZER and POINTWISE_EVALUATION experiment types.
 *
 * <p>Delegates to {@link BatchedAsyncExecutor#executeAsync} to process experiment variants
 * in sequential batches, preventing thread pool starvation when variant processing has
 * nested dependencies on the same pool (search callbacks, response processing).
 */
@Log4j2
public class ExperimentTaskManager {

    private final ConcurrentHashMap<String, ExperimentTaskContext> experimentTaskContexts = new ConcurrentHashMap<>();

    // Services
    private final Client client;
    private final ExperimentVariantDao experimentVariantDao;
    private final ThreadPool threadPool;
    private final SearchResponseProcessor searchResponseProcessor;
    private final BatchedAsyncExecutor<ExperimentVariant, Void> batchedExecutor;

    @Inject
    public ExperimentTaskManager(
        Client client,
        EvaluationResultDao evaluationResultDao,
        ExperimentVariantDao experimentVariantDao,
        ThreadPool threadPool
    ) {
        this.client = client;
        this.experimentVariantDao = experimentVariantDao;
        this.threadPool = threadPool;
        this.searchResponseProcessor = new SearchResponseProcessor(evaluationResultDao, experimentVariantDao);
        this.batchedExecutor = new BatchedAsyncExecutor<>(threadPool, SEARCH_RELEVANCE_EXEC_THREAD_POOL_NAME);

        log.info(
            "ExperimentTaskManager initialized with batch size {} (processors: {})",
            batchedExecutor.getBatchSize(),
            Runtime.getRuntime().availableProcessors()
        );
    }

    /**
     * Schedule experiment tasks using batched async execution.
     *
     * <p>Variants are processed in sequential batches via {@link BatchedAsyncExecutor}.
     * Each variant's search and response processing is natively async (uses
     * {@code client.search()} with {@code ActionListener}). Results are aggregated
     * through {@link ExperimentTaskContext}, which completes the returned future
     * when all variants finish.
     */
    public CompletableFuture<Map<String, Object>> scheduleTasksAsync(
        ExperimentType experimentType,
        String experimentId,
        String searchConfigId,
        String index,
        String query,
        String queryText,
        int size,
        List<ExperimentVariant> experimentVariants,
        List<String> judgmentIds,
        Map<String, String> docIdToScores,
        Map<String, Object> configToExperimentVariants,
        AtomicBoolean hasFailure,
        String scheduledRunId,
        Map<String, List<Future<?>>> runningFutures,
        ExperimentCancellationToken cancellationToken
    ) {
        CompletableFuture<Map<String, Object>> resultFuture = new CompletableFuture<>();

        ExperimentTaskContext taskContext = new ExperimentTaskContext(
            experimentId,
            searchConfigId,
            queryText,
            experimentVariants.size(),
            new ConcurrentHashMap<>(configToExperimentVariants),
            resultFuture,
            hasFailure,
            experimentVariantDao,
            experimentType
        );

        experimentTaskContexts.putIfAbsent(experimentId, taskContext);
        taskContext.getConfigToExperimentVariants().computeIfAbsent(searchConfigId, k -> new ConcurrentHashMap<String, Object>());

        log.info(
            "Scheduling {} {} experiment tasks for experiment {} with batched execution",
            experimentVariants.size(),
            experimentType,
            experimentId
        );

        // Cancellation supplier from token
        java.util.function.Supplier<Boolean> isCancelled = () -> cancellationToken != null && cancellationToken.isCancelled();

        // Use BatchedAsyncExecutor to process variants in controlled batches
        batchedExecutor.executeAsync(experimentVariants, variant -> {
            VariantTaskParameters params = createTaskParameters(
                experimentType,
                experimentId,
                searchConfigId,
                index,
                query,
                queryText,
                size,
                variant,
                judgmentIds,
                docIdToScores,
                taskContext,
                scheduledRunId,
                cancellationToken,
                runningFutures
            );
            return executeVariantAsync(params);
        }, isCancelled, ActionListener.wrap(results -> {
            experimentTaskContexts.remove(experimentId);
            // resultFuture is completed by ExperimentTaskContext.finishExperiment() when all
            // variant DAO callbacks fire completeVariantSuccess/Failure. Don't interfere here
            // for the normal flow — the async DAO writes may still be in-flight.
            // Only complete exceptionally if cancelled before any variants were processed.
            if (isCancelled.get() && !resultFuture.isDone()) {
                resultFuture.completeExceptionally(new TimeoutException("Experiment cancelled before variants could be processed"));
            }
            log.debug("Batched variant execution complete for experiment {}", experimentId);
        }, e -> {
            experimentTaskContexts.remove(experimentId);
            if (!resultFuture.isDone()) {
                resultFuture.completeExceptionally(e);
            }
            log.error("Batched variant execution failed for experiment {}", experimentId, e);
        }));

        return resultFuture;
    }

    /**
     * Execute a single variant asynchronously: build search request, execute search,
     * process response. Returns a CompletableFuture that completes when the variant
     * is fully processed (including response handling via ExperimentTaskContext).
     */
    private CompletableFuture<Void> executeVariantAsync(VariantTaskParameters params) {
        CompletableFuture<Void> future = new CompletableFuture<>();

        if (params.getTaskContext().getHasFailure().get()) {
            // Count the skipped variant so ExperimentTaskContext.remainingVariants reaches 0
            params.getTaskContext().completeVariantFailure();
            future.complete(null);
            return future;
        }
        if (params.getCancellationToken() != null && params.getCancellationToken().isCancelled()) {
            log.info("Cancelled variant task for experiment {}", params.getExperimentId());
            // Count the skipped variant so ExperimentTaskContext.remainingVariants reaches 0
            params.getTaskContext().completeVariantFailure();
            TimeoutException exception = new TimeoutException("Timed out at variant task");
            params.getTaskContext().getResultFuture().completeExceptionally(exception);
            future.completeExceptionally(exception);
            return future;
        }

        final String evaluationId = UUID.randomUUID().toString();
        SearchRequest searchRequest = buildSearchRequest(params);

        log.debug(
            "Experiment search request (experimentId={}, variantId={}, evaluationId={})",
            params.getExperimentId(),
            params.getExperimentVariant().getId(),
            evaluationId
        );

        client.search(searchRequest, new ActionListener<>() {
            @Override
            public void onResponse(org.opensearch.action.search.SearchResponse response) {
                try {
                    searchResponseProcessor.processSearchResponse(
                        response,
                        params.getExperimentVariant(),
                        params.getExperimentId(),
                        params.getSearchConfigId(),
                        params.getQueryText(),
                        params.getSize(),
                        params.getJudgmentIds(),
                        params.getDocIdToScores(),
                        evaluationId,
                        params.getTaskContext(),
                        params.getScheduledRunId()
                    );
                    future.complete(null);
                } catch (Exception e) {
                    // Ensure variant is counted even when response processing throws
                    params.getTaskContext().completeVariantFailure();
                    future.completeExceptionally(e);
                }
            }

            @Override
            public void onFailure(Exception e) {
                try {
                    handleSearchFailure(e, params.getExperimentVariant(), params.getExperimentId(), evaluationId, params.getTaskContext());
                    future.complete(null);
                } catch (Exception ex) {
                    // Ensure variant is counted even when failure handling throws
                    params.getTaskContext().completeVariantFailure();
                    future.completeExceptionally(ex);
                }
            }
        });

        return future;
    }

    /**
     * Create task parameters based on experiment type.
     */
    private VariantTaskParameters createTaskParameters(
        ExperimentType experimentType,
        String experimentId,
        String searchConfigId,
        String index,
        String query,
        String queryText,
        int size,
        ExperimentVariant variant,
        List<String> judgmentIds,
        Map<String, String> docIdToScores,
        ExperimentTaskContext taskContext,
        String scheduledRunId,
        ExperimentCancellationToken cancellationToken,
        Map<String, List<Future<?>>> runningFutures
    ) {
        if (experimentType == ExperimentType.POINTWISE_EVALUATION) {
            return PointwiseTaskParameters.builder()
                .experimentId(experimentId)
                .searchConfigId(searchConfigId)
                .index(index)
                .query(query)
                .queryText(queryText)
                .size(size)
                .experimentVariant(variant)
                .judgmentIds(judgmentIds)
                .docIdToScores(docIdToScores)
                .taskContext(taskContext)
                .searchPipeline(getSearchPipelineFromVariant(variant))
                .scheduledRunId(scheduledRunId)
                .cancellationToken(cancellationToken)
                .runningFutures(runningFutures)
                .build();
        } else {
            return VariantTaskParameters.builder()
                .experimentId(experimentId)
                .searchConfigId(searchConfigId)
                .index(index)
                .query(query)
                .queryText(queryText)
                .size(size)
                .experimentVariant(variant)
                .judgmentIds(judgmentIds)
                .docIdToScores(docIdToScores)
                .taskContext(taskContext)
                .scheduledRunId(scheduledRunId)
                .cancellationToken(cancellationToken)
                .runningFutures(runningFutures)
                .build();
        }
    }

    private String getSearchPipelineFromVariant(ExperimentVariant variant) {
        return (String) variant.getParameters().get("searchPipeline");
    }

    /**
     * Build search request based on experiment type.
     */
    private SearchRequest buildSearchRequest(VariantTaskParameters params) {
        if (params instanceof PointwiseTaskParameters pointwiseParams) {
            return SearchRequestBuilder.buildSearchRequest(
                pointwiseParams.getIndex(),
                pointwiseParams.getQuery(),
                pointwiseParams.getQueryText(),
                pointwiseParams.getSearchPipeline(),
                pointwiseParams.getSize()
            );
        } else {
            Map<String, Object> temporarySearchPipeline = QuerySourceUtil.createDefinitionOfTemporarySearchPipeline(
                params.getExperimentVariant()
            );
            return SearchRequestBuilder.buildRequestForHybridSearch(
                params.getIndex(),
                params.getQuery(),
                temporarySearchPipeline,
                params.getQueryText(),
                params.getSize()
            );
        }
    }

    private void handleSearchFailure(
        Exception e,
        ExperimentVariant experimentVariant,
        String experimentId,
        String evaluationId,
        ExperimentTaskContext taskContext
    ) {
        if (isCriticalSystemFailure(e)) {
            if (taskContext.getHasFailure().compareAndSet(false, true)) {
                log.error("Critical system failure for variant {}: {}", experimentVariant.getId(), e.getMessage());
                taskContext.getResultFuture().completeExceptionally(e);
            }
        } else {
            searchResponseProcessor.handleSearchFailure(e, experimentVariant, experimentId, evaluationId, taskContext);
        }
    }

    private boolean isCriticalSystemFailure(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            if (current instanceof OutOfMemoryError || current instanceof StackOverflowError) {
                return true;
            }
            if (current instanceof CircuitBreakingException || current instanceof ClusterBlockException) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }
}
