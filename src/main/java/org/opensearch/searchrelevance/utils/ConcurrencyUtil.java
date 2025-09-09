/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */
package org.opensearch.searchrelevance.utils;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import org.opensearch.threadpool.ThreadPool;

public class ConcurrencyUtil {
    // Wraps a future with a timeout value by scheduling a task set to cancel the future after a timeout.
    public static <T> CompletableFuture<T> withTimeout(CompletableFuture<T> future, long timeoutSeconds, ThreadPool threadPool) {
        CompletableFuture<T> timeoutFuture = new CompletableFuture<>();

        ScheduledFuture<?> timeout = threadPool.scheduler().schedule(() -> {
            if (timeoutFuture.cancel(false)) {
                future.cancel(true);
            }
        }, timeoutSeconds, TimeUnit.SECONDS);

        // complete when original completes
        future.whenComplete((result, throwable) -> {
            timeout.cancel(false); // Cancel timeout
            if (throwable == null) {
                timeoutFuture.complete(result);
            } else {
                timeoutFuture.completeExceptionally(throwable);
            }
        });

        return timeoutFuture;
    }
}
