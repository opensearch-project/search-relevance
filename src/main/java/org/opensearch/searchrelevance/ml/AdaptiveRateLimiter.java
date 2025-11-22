/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */
package org.opensearch.searchrelevance.ml;

import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.opensearch.searchrelevance.ml.connector.ConnectorType;

/**
 * Adaptive rate limiter that learns optimal rates per model and handles circuit breaking
 */
public class AdaptiveRateLimiter {
    private static final Logger log = LogManager.getLogger(AdaptiveRateLimiter.class);

    private final ConcurrentMap<String, RateLimitState> rateLimitStates = new ConcurrentHashMap<>();
    private volatile ScheduledExecutorService cleanupScheduler;
    private final Object schedulerLock = new Object();

    public AdaptiveRateLimiter() {
        // Lazy initialization - don't create threads until actually needed
    }

    private ScheduledExecutorService getOrCreateScheduler() {
        if (cleanupScheduler == null) {
            synchronized (schedulerLock) {
                if (cleanupScheduler == null) {
                    cleanupScheduler = Executors.newSingleThreadScheduledExecutor(new ThreadFactory() {
                        @Override
                        public Thread newThread(Runnable r) {
                            Thread t = new Thread(r, "adaptive-rate-limiter-cleanup");
                            t.setDaemon(true);
                            return t;
                        }
                    });
                    // Schedule cleanup every hour
                    cleanupScheduler.scheduleAtFixedRate(this::cleanupOldEntries, 1, 1, TimeUnit.HOURS);
                }
            }
        }
        return cleanupScheduler;
    }

    public CompletableFuture<Void> applyRateLimit(String modelId, ConnectorType connectorType, long userRateLimit) {
        String key = getKey(modelId, connectorType);
        RateLimitState state = rateLimitStates.computeIfAbsent(key, k -> new RateLimitState(userRateLimit));

        // Circuit breaker: Stop trying if model seems dead
        if (state.shouldStopTrying()) {
            return CompletableFuture.failedFuture(new RuntimeException("Model appears to be unavailable: " + modelId));
        }

        long delayMs = state.calculateDelay();

        if (delayMs <= 0) {
            return CompletableFuture.completedFuture(null);
        }

        log.debug("Applying rate limit for {}: {}ms delay", key, delayMs);

        // Non-blocking delay using our managed executor
        CompletableFuture<Void> future = new CompletableFuture<>();
        getOrCreateScheduler().schedule(() -> future.complete(null), delayMs, TimeUnit.MILLISECONDS);
        return future;
    }

    public void recordResult(String modelId, ConnectorType connectorType, boolean success, Throwable error) {
        String key = getKey(modelId, connectorType);
        RateLimitState state = rateLimitStates.get(key);

        if (state != null) {
            if (success) {
                state.onSuccess();
            } else if (isRateLimitError(error)) {
                state.onRateLimit();
            } else if (isModelUnavailableError(error)) {
                state.onModelUnavailable();
            } else {
                state.onOtherError();
            }
        }
    }

    private String getKey(String modelId, ConnectorType connectorType) {
        return modelId + ":" + connectorType.getValue();
    }

    private boolean isRateLimitError(Throwable error) {
        if (error == null) return false;
        String message = error.getMessage().toLowerCase(Locale.ROOT);
        return message.contains("rate limit")
            || message.contains("throttling")
            || message.contains("too many requests")
            || message.contains("high request rate")
            || message.contains("acquire operation took longer")
            || message.contains("connection from the pool");
    }

    private boolean isModelUnavailableError(Throwable error) {
        if (error == null) return false;
        String message = error.getMessage().toLowerCase(Locale.ROOT);
        return message.contains("model not found") || message.contains("service unavailable") || message.contains("internal server error");
    }

    private void cleanupOldEntries() {
        long cutoff = System.currentTimeMillis() - 3600_000; // 1 hour
        java.util.concurrent.atomic.AtomicInteger removed = new java.util.concurrent.atomic.AtomicInteger(0);

        rateLimitStates.entrySet().removeIf(entry -> {
            if (entry.getValue().lastRequestTime < cutoff) {
                removed.incrementAndGet();
                return true;
            }
            return false;
        });

        if (removed.get() > 0) {
            log.debug("Cleaned up {} old rate limit entries", removed.get());
        }
    }

    public void scheduleTask(Runnable task, long delayMs) {
        getOrCreateScheduler().schedule(task, delayMs, TimeUnit.MILLISECONDS);
    }

    public void shutdown() {
        synchronized (schedulerLock) {
            if (cleanupScheduler != null) {
                cleanupScheduler.shutdown();
                try {
                    if (!cleanupScheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                        cleanupScheduler.shutdownNow();
                    }
                } catch (InterruptedException e) {
                    cleanupScheduler.shutdownNow();
                    Thread.currentThread().interrupt();
                }
                cleanupScheduler = null;
            }
        }
    }

    private static class RateLimitState {
        private volatile long currentDelayMs;
        private volatile long lastRequestTime;
        private volatile long lastSuccessTime;
        private volatile int consecutiveSuccesses;
        private volatile int consecutiveFailures;
        private final long initialDelayMs;

        // Conservative parameters
        private static final double BACKOFF_MULTIPLIER = 2.0;
        private static final double RECOVERY_FACTOR = 0.9;
        private static final int SUCCESSES_BEFORE_RECOVERY = 5;
        private static final long MAX_DELAY_MS = 300_000; // 5 minutes
        private static final int MAX_CONSECUTIVE_FAILURES = 10;
        private static final long MODEL_DEAD_THRESHOLD_MS = 1800_000; // 30 minutes
        private static final long CIRCUIT_OPEN_DURATION_MS = 300_000; // 5 minutes

        public RateLimitState(long initialDelayMs) {
            this.initialDelayMs = Math.max(0, initialDelayMs);
            this.currentDelayMs = this.initialDelayMs;
            this.lastSuccessTime = System.currentTimeMillis();
            this.lastRequestTime = System.currentTimeMillis();
        }

        public long calculateDelay() {
            lastRequestTime = System.currentTimeMillis();

            // Circuit breaker: Longer delay if too many failures
            if (isCircuitOpen()) {
                return CIRCUIT_OPEN_DURATION_MS;
            }

            return currentDelayMs;
        }

        public boolean isCircuitOpen() {
            return consecutiveFailures >= MAX_CONSECUTIVE_FAILURES;
        }

        public boolean shouldStopTrying() {
            long timeSinceLastSuccess = System.currentTimeMillis() - lastSuccessTime;
            return timeSinceLastSuccess > MODEL_DEAD_THRESHOLD_MS;
        }

        public void onSuccess() {
            consecutiveFailures = 0;
            consecutiveSuccesses++;
            lastSuccessTime = System.currentTimeMillis();

            // Conservative recovery - only after many successes
            if (consecutiveSuccesses >= SUCCESSES_BEFORE_RECOVERY) {
                currentDelayMs = Math.max(
                    initialDelayMs, // Never go below user's initial setting
                    (long) (currentDelayMs * RECOVERY_FACTOR)
                );
                consecutiveSuccesses = 0;
                log.debug("Rate limit recovered to {}ms", currentDelayMs);
            }
        }

        public void onRateLimit() {
            consecutiveSuccesses = 0;
            consecutiveFailures++;

            // Aggressive backoff on rate limit
            long oldDelay = currentDelayMs;
            currentDelayMs = Math.min(MAX_DELAY_MS, (long) (currentDelayMs * BACKOFF_MULTIPLIER));

            log.debug("Rate limit hit, increased delay from {}ms to {}ms", oldDelay, currentDelayMs);
        }

        public void onModelUnavailable() {
            consecutiveSuccesses = 0;
            consecutiveFailures++;
            log.debug("Model unavailable error, consecutive failures: {}", consecutiveFailures);
        }

        public void onOtherError() {
            // Don't change rate limiting for non-rate-limit errors
            consecutiveSuccesses = 0;
        }
    }
}
