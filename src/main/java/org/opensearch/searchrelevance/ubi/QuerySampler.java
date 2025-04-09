/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */
package org.opensearch.searchrelevance.ubi;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.opensearch.transport.client.Client;

import reactor.util.annotation.NonNull;

public abstract class QuerySampler {
    private static final Logger LOGGER = LogManager.getLogger(QuerySampler.class);
    private final Client client;
    private final int size;

    protected QuerySampler(int size, @NonNull Client client) {
        this.client = client;
        this.size = size;
    }

    protected Client getClient() {
        return client;
    }

    protected int getSize() {
        return size;
    }

    public abstract CompletableFuture<Map<String, Integer>> sample();

    public static QuerySampler create(String name, int size, Client client) {
        return switch (name) {
            case ProbabilityProportionalToSizeQuerySampler.NAME -> new ProbabilityProportionalToSizeQuerySampler(size, client);
            case RandomQuerySampler.NAME -> new RandomQuerySampler(size, client);
            case TopNQuerySampler.NAME -> new TopNQuerySampler(size, client);
            default -> throw new IllegalArgumentException("Unknown sampler type: " + name);
        };
    }
}
