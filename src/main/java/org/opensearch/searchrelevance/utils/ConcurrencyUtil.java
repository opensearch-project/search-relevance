/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */
package org.opensearch.searchrelevance.utils;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.opensearch.common.util.concurrent.FutureUtils;
import org.opensearch.searchrelevance.scheduler.ExperimentCancellationToken;
import org.opensearch.searchrelevance.scheduler.SearchRelevanceJobRunner;
import org.opensearch.threadpool.ThreadPool;

public class ConcurrencyUtil {
    private static final Logger log = LogManager.getLogger(SearchRelevanceJobRunner.class);

    /**
     * Wraps a future with a timeout value by scheduling a task set to cancel
     * the future after a timeout. Intended to be used for scheduled experiments.
     */
    public static <T> CompletableFuture<T> withTimeout(
        CompletableFuture<T> future,
        long timeoutSeconds,
        ExperimentCancellationToken cancellationToken,
        CountDownLatch actuallyFinished,
        ThreadPool threadPool
    ) {
        CompletableFuture<T> timeoutFuture = new CompletableFuture<>();

        // Here we are scheduling a task to cancel the scheduled experiments future after timeout.
        ScheduledFuture<?> timeout = threadPool.scheduler().schedule(() -> {
            // Signal that the future is timedout.
            cancellationToken.cancel();
            FutureUtils.cancel(future);
            timeoutFuture.completeExceptionally(new TimeoutException());
        }, timeoutSeconds, TimeUnit.SECONDS);

        // complete when original completes (This does not include the asynchronous operations that have started.)
        future.whenComplete((result, throwable) -> {
            /**
             * Wait for the operation to either be timed out or cancelled before moving onto cleanup.
             * In the case where the timeout happens, the async operations should receive the signal and
             * adjust the await.
             */
            try {
                actuallyFinished.await();
            } catch (Exception e) {
                log.error("Somehow the thread waiting for the experiment run and all the async tasks to complete was interrupted.");
            }
            FutureUtils.cancel(timeout); // Cancel timeout task
            if (throwable == null) {
                timeoutFuture.complete(result);
            } else {
                timeoutFuture.completeExceptionally(throwable);
            }
        });

        // timeoutFuture will end up storing the value of the original future when it is
        // either completed or completed exceptionally.
        return timeoutFuture;
    }
}
