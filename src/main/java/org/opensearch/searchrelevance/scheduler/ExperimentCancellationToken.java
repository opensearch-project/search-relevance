/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */
package org.opensearch.searchrelevance.scheduler;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Token for signaling whether a scheduled experiment has been cancelled
 */
public class ExperimentCancellationToken {
    private final AtomicBoolean cancelled = new AtomicBoolean(false);
    // cancellationCallbacks should be short running and completed syncronously quickly.
    private final List<Runnable> cancellationCallbacks = new CopyOnWriteArrayList<>();
    private final String scheduledExperimentResultId;

    public ExperimentCancellationToken(String scheduledExperimentResultId) {
        this.scheduledExperimentResultId = scheduledExperimentResultId;
    }

    public String getScheduledExperimentResultId() {
        return scheduledExperimentResultId;
    }

    public boolean isCancelled() {
        return cancelled.get();
    }

    public void cancel() {
        if (cancelled.compareAndSet(false, true)) {
            cancellationCallbacks.forEach(Runnable::run);
        }
    }

    public void onCancel(Runnable callback) {
        cancellationCallbacks.add(callback);
        if (isCancelled()) {
            callback.run();
        }
    }
}
