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
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.LongAdder;

import org.opensearch.action.search.SearchRequest;
import org.opensearch.cluster.block.ClusterBlockException;
import org.opensearch.common.inject.Inject;
import org.opensearch.common.settings.Settings;
import org.opensearch.common.util.concurrent.AbstractRunnable;
import org.opensearch.common.util.concurrent.OpenSearchExecutors;
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

import com.google.common.annotations.VisibleForTesting;

import lombok.extern.log4j.Log4j2;

/**
 * Generic task manager for scheduling experiment tasks with concurrency control and backpressure handling.
 * Supports HYBRID_OPTIMIZER and POINTWISE_EVALUATION experiment types.
 */
@Log4j2
public class ExperimentTaskManager {
    public static final int TASK_RETRY_DELAY_MILLISECONDS = 1000;
    public static final int ALLOCATED_PROCESSORS = OpenSearchExecutors.allocatedProcessors(Settings.EMPTY);

    private static final int DEFAULT_MIN_CONCURRENT_THREADS = 24;
    private static final int PROCESSOR_NUMBER_DIVISOR = 2;
    protected static final String THREAD_POOL_EXECUTOR_NAME = ThreadPool.Names.GENERIC;

    private final int maxConcurrentTasks;
    private final ConcurrentHashMap<String, ExperimentTaskContext> experimentTaskContexts = new ConcurrentHashMap<>();
    private final Semaphore concurrencyControl;

    // Use LongAdder for better concurrent counting performance
    private final LongAdder activeTasks = new LongAdder();

    // Services
    private final Client client;
    private final EvaluationResultDao evaluationResultDao;
    private final ExperimentVariantDao experimentVariantDao;
    private final ThreadPool threadPool;
    private final SearchResponseProcessor searchResponseProcessor;

    @Inject
    public ExperimentTaskManager(
        Client client,
        EvaluationResultDao evaluationResultDao,
        ExperimentVariantDao experimentVariantDao,
        ThreadPool threadPool
    ) {
        this.client = client;
        this.evaluationResultDao = evaluationResultDao;
        this.experimentVariantDao = experimentVariantDao;
        this.threadPool = threadPool;
        this.searchResponseProcessor = new SearchResponseProcessor(evaluationResultDao, experimentVariantDao);

        this.maxConcurrentTasks = Math.max(2, Math.min(DEFAULT_MIN_CONCURRENT_THREADS, ALLOCATED_PROCESSORS / PROCESSOR_NUMBER_DIVISOR));
        this.concurrencyControl = new Semaphore(maxConcurrentTasks, true);

        log.info(
            "ExperimentTaskManager initialized with max {} concurrent tasks (processors: {})",
            maxConcurrentTasks,
            ALLOCATED_PROCESSORS
        );
    }

    /**
     * Schedule experiment tasks using non-blocking mechanisms
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
        // Create a CompletableFuture to track the overall completion
        CompletableFuture<Map<String, Object>> resultFuture = new CompletableFuture<>();

        // Create optimized task context
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

        // Use putIfAbsent for atomic operation
        experimentTaskContexts.putIfAbsent(experimentId, taskContext);

        // Initialize config map using computeIfAbsent (non-blocking)
        taskContext.getConfigToExperimentVariants().computeIfAbsent(searchConfigId, k -> new ConcurrentHashMap<String, Object>());

        log.info(
            "Scheduling {} {} experiment tasks for experiment {} with non-blocking concurrency",
            experimentVariants.size(),
            experimentType,
            experimentId
        );

        // Schedule tasks asynchronously
        List<CompletableFuture<Void>> variantFutures = experimentVariants.stream().map(variant -> {
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

            return scheduleVariantTaskAsync(params);
        }).toList();

        // When all variants complete, clean up
        CompletableFuture.allOf(variantFutures.toArray(new CompletableFuture[0])).whenComplete((v, ex) -> {
            experimentTaskContexts.remove(experimentId);
            activeTasks.decrement();
        });

        return resultFuture;
    }

    /**
     * Create task parameters based on experiment type
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
            // Default to hybrid optimizer parameters
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

    /**
     * Extract search pipeline from variant parameters for pointwise experiments
     */
    private String getSearchPipelineFromVariant(ExperimentVariant variant) {
        return (String) variant.getParameters().get("searchPipeline");
    }

    private boolean checkIfCancelled(ExperimentCancellationToken cancellationToken) {
        if (cancellationToken != null && cancellationToken.isCancelled()) {
            return true;
        }
        return false;
    }

    /**
     * Schedule a single variant task asynchronously
     */
    private CompletableFuture<Void> scheduleVariantTaskAsync(VariantTaskParameters params) {
        CompletableFuture<Void> future = new CompletableFuture<>();

        if (params.getTaskContext().getHasFailure().get()) {
            future.complete(null);
            return future;
        }
        if (checkIfCancelled(params.getCancellationToken())) {
            log.info("Cancelled when scheduling variant task for experiment id {}", params.getExperimentId());
            TimeoutException exception = new TimeoutException("Timed out at variant task async");
            params.getTaskContext().getResultFuture().completeExceptionally(exception);
            future.completeExceptionally(exception);
            return future;
        }

        // Try to acquire permit non-blocking
        if (concurrencyControl.tryAcquire()) {
            activeTasks.increment();
            submitTaskToThreadPool(params, future);
        } else {
            // Schedule with backpressure using CompletableFuture
            CompletableFuture.delayedExecutor(
                TASK_RETRY_DELAY_MILLISECONDS,
                TimeUnit.MILLISECONDS,
                threadPool.executor(THREAD_POOL_EXECUTOR_NAME)
            ).execute(() -> {
                scheduleVariantTaskAsync(params).whenComplete((v, ex) -> {
                    if (ex != null) {
                        future.completeExceptionally(ex);
                    } else {
                        future.complete(v);
                    }
                });
            });
        }

        return future;
    }

    private void submitTaskToThreadPool(VariantTaskParameters params, CompletableFuture<Void> future) {
        try {
            if (checkIfCancelled(params.getCancellationToken())) {
                throw new RejectedExecutionException("Task timed out");
            }
            Future<?> variantTaskFuture = threadPool.executor(SEARCH_RELEVANCE_EXEC_THREAD_POOL_NAME)
                .submit(new OptimizedVariantTaskRunnable(params, future));
            // This should only be used if the experiment is one that is scheduled to run.
            if (params.getScheduledRunId() != null
                && params.getRunningFutures() != null
                && checkIfCancelled(params.getCancellationToken()) == false) {
                try {
                    params.getRunningFutures().get(params.getScheduledRunId()).add(variantTaskFuture);
                } catch (Exception e) {
                    log.info(
                        "Submitting variant for scheduled experiment with underlying experiment {} cannot be completed",
                        params.getExperimentId()
                    );
                }
            }
        } catch (RejectedExecutionException e) {
            concurrencyControl.release();
            activeTasks.decrement();
            log.warn("Thread pool queue full, retrying for variant: {}", params.getExperimentVariant().getId());

            // Retry with backpressure
            CompletableFuture.delayedExecutor(
                TASK_RETRY_DELAY_MILLISECONDS,
                TimeUnit.MILLISECONDS,
                threadPool.executor(THREAD_POOL_EXECUTOR_NAME)
            ).execute(() -> scheduleVariantTaskAsync(params));
        }
    }

    /**
     * Execute variant task using CompletableFuture for better async handling
     */
    private void executeVariantTaskAsync(VariantTaskParameters params, CompletableFuture<Void> future) {
        if (params.getTaskContext().getHasFailure().get()) {
            concurrencyControl.release();
            activeTasks.decrement();
            future.complete(null);
            return;
        }
        if (checkIfCancelled(params.getCancellationToken())) {
            log.info(
                "Cancelled scheduled experiment with underlying experiment id {} when executing variant task async",
                params.getExperimentId()
            );
            concurrencyControl.release();
            activeTasks.decrement();
            TimeoutException exception = new TimeoutException("Timed out at variant task async");
            params.getTaskContext().getResultFuture().completeExceptionally(exception);
            future.completeExceptionally(exception);
            return;
        }

        final String evaluationId = UUID.randomUUID().toString();
        SearchRequest searchRequest = buildSearchRequest(params, evaluationId);

        // Convert ActionListener to CompletableFuture
        CompletableFuture<Void> searchFuture = new CompletableFuture<>();

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
                    searchFuture.complete(null);
                } catch (Exception e) {
                    searchFuture.completeExceptionally(e);
                } finally {
                    concurrencyControl.release();
                    activeTasks.decrement();
                }
            }

            @Override
            public void onFailure(Exception e) {
                try {
                    handleSearchFailure(e, params.getExperimentVariant(), params.getExperimentId(), evaluationId, params.getTaskContext());
                    searchFuture.complete(null);
                } catch (Exception ex) {
                    searchFuture.completeExceptionally(ex);
                } finally {
                    concurrencyControl.release();
                    activeTasks.decrement();
                }
            }
        });

        // Chain the futures
        searchFuture.whenComplete((v, ex) -> {
            if (ex != null) {
                future.completeExceptionally(ex);
            } else {
                future.complete(null);
            }
        });
    }

    /**
     * Build search request based on experiment type
     */
    private SearchRequest buildSearchRequest(VariantTaskParameters params, String evaluationId) {
        if (params instanceof PointwiseTaskParameters) {
            PointwiseTaskParameters pointwiseParams = (PointwiseTaskParameters) params;
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

    /**
     * Get current concurrency metrics
     */
    @VisibleForTesting
    protected Map<String, Object> getConcurrencyMetrics() {
        return Map.of(
            "active_experiments",
            experimentTaskContexts.size(),
            "active_tasks",
            activeTasks.sum(),
            "max_concurrent_tasks",
            maxConcurrentTasks,
            "available_permits",
            concurrencyControl.availablePermits(),
            "queued_threads",
            concurrencyControl.getQueueLength(),
            "thread_pool",
            SEARCH_RELEVANCE_EXEC_THREAD_POOL_NAME
        );
    }

    /**
     * Optimized runnable using CompletableFuture
     */
    private class OptimizedVariantTaskRunnable extends AbstractRunnable {
        private final VariantTaskParameters params;
        private final CompletableFuture<Void> future;

        OptimizedVariantTaskRunnable(VariantTaskParameters params, CompletableFuture<Void> future) {
            this.params = params;
            this.future = future;
        }

        @Override
        public void onFailure(Exception e) {
            concurrencyControl.release();
            activeTasks.decrement();

            if (e.getCause() instanceof RejectedExecutionException) {
                log.warn("Thread pool queue full, retrying task for variant: {}", params.getExperimentVariant().getId());
                scheduleVariantTaskAsync(params).whenComplete((v, ex) -> {
                    if (ex != null) {
                        future.completeExceptionally(ex);
                    } else {
                        future.complete(v);
                    }
                });
            } else {
                handleTaskFailure(params.getExperimentVariant(), e, params.getTaskContext());
                future.completeExceptionally(e);
            }
        }

        @Override
        protected void doRun() {
            executeVariantTaskAsync(params, future);
        }
    }

    private void handleTaskFailure(ExperimentVariant experimentVariant, Exception e, ExperimentTaskContext taskContext) {
        if (isCriticalSystemFailure(e)) {
            if (taskContext.getHasFailure().compareAndSet(false, true)) {
                log.error("Critical system failure for variant {}: {}", experimentVariant.getId(), e.getMessage());
                taskContext.getResultFuture().completeExceptionally(e);
            }
        } else {
            log.error("Variant failure for {}: {}", experimentVariant.getId(), e.getMessage());
            taskContext.completeVariantFailure();
        }
    }
}
