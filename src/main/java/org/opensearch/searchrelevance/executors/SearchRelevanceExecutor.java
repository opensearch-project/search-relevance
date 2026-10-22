/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */
package org.opensearch.searchrelevance.executors;

import org.apache.lucene.search.TaskExecutor;
import org.opensearch.common.settings.Settings;
import org.opensearch.common.unit.TimeValue;
import org.opensearch.common.util.concurrent.OpenSearchExecutors;
import org.opensearch.threadpool.ExecutorBuilder;
import org.opensearch.threadpool.ScalingExecutorBuilder;
import org.opensearch.threadpool.ThreadPool;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/**
 * {@link SearchRelevanceExecutor} provides necessary implementation and instances to execute
 * search relevance tasks in parallel using a dedicated scaling thread pool. This ensures that one thread pool
 * is used for search relevance execution per node. The thread pool scales dynamically based on load,
 * with conservative core threads for baseline operations and higher max threads for experiment bursts.
 * This adaptive scaling optimizes resource utilization for the bursty nature of experiment workloads.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class SearchRelevanceExecutor {

    static final String SEARCH_RELEVANCE_EXEC_THREAD_POOL_NAME = "_plugin_search_relevance_executor";
    private static final Integer MIN_THREAD_SIZE = 2;
    private static final Integer PROCESSOR_COUNT_DIVISOR = 2;

    private static TaskExecutor taskExecutor;

    /**
     * Provide scaling executor builder to use for search relevance executors
     * @param settings Node level settings
     * @return the executor builder for search relevance's custom thread pool.
     */
    public static ExecutorBuilder<?> getExecutorBuilder(final Settings settings) {
        final int allocatedProcessors = OpenSearchExecutors.allocatedProcessors(settings);

        // Conservative core size - always-available threads for baseline load
        int coreThreads = Math.max(MIN_THREAD_SIZE, allocatedProcessors / 4);

        // Higher max size - handle experiment bursts
        int maxThreads = Math.max(allocatedProcessors / PROCESSOR_COUNT_DIVISOR, Math.min(coreThreads * 4, 32));

        // Keep threads alive for 5 minutes, typical experiment runs longer
        TimeValue keepAlive = TimeValue.timeValueMinutes(5);

        return new ScalingExecutorBuilder(
            SEARCH_RELEVANCE_EXEC_THREAD_POOL_NAME,
            coreThreads,    // Core threads (always alive)
            maxThreads,     // Max threads (scales for bursts)
            keepAlive       // Keep alive time
        );
    }

    /**
    * Initialize {@link TaskExecutor} to run tasks concurrently using {@link ThreadPool}
     * @param threadPool OpenSearch's thread pool instance
     */
    public static void initialize(ThreadPool threadPool) {
        if (threadPool == null) {
            throw new IllegalArgumentException(
                "Argument thread-pool to Search Relevance Executor cannot be null. This is required to build executor to run actions in parallel"
            );
        }
        taskExecutor = new TaskExecutor(threadPool.executor(SEARCH_RELEVANCE_EXEC_THREAD_POOL_NAME));
    }

    /**
     * Return TaskExecutor Wrapper that helps runs tasks concurrently
     * @return TaskExecutor instance to help run search tasks in parallel
     */
    public static TaskExecutor getExecutor() {
        return taskExecutor != null ? taskExecutor : new TaskExecutor(Runnable::run);
    }
}
