/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */
package org.opensearch.searchrelevance.scheduler;

public class ExperimentCancellationToken extends AbstractCancellationToken {
    private final String experimentId;

    public ExperimentCancellationToken(String experimentId) {
        this.experimentId = experimentId;
    }

    public String getExperimentResultId() {
        return experimentId;
    }
}
