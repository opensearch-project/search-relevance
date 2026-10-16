/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */
package org.opensearch.searchrelevance.ubi;

import static org.opensearch.searchrelevance.common.PluginConstants.UBI_QUERIES_INDEX;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.opensearch.core.rest.RestStatus;
import org.opensearch.searchrelevance.exception.SearchRelevanceException;
import org.opensearch.transport.client.Client;

import reactor.util.annotation.NonNull;

public abstract class QuerySampler {
    private static final Logger LOGGER = LogManager.getLogger(QuerySampler.class);
    private final Client client;
    private final int size;
    private final String ubiQueriesIndex;

    protected QuerySampler(int size, @NonNull Client client, String ubiQueriesIndex) {
        this.client = client;
        this.size = size;
        this.ubiQueriesIndex = ubiQueriesIndex != null ? ubiQueriesIndex : UBI_QUERIES_INDEX;
    }

    protected Client getClient() {
        return client;
    }

    protected int getSize() {
        return size;
    }

    protected String getUbiQueriesIndex() {
        return ubiQueriesIndex;
    }

    public abstract CompletableFuture<Map<String, Integer>> sample();

    public static QuerySampler create(String name, int size, Client client, String ubiQueriesIndex) {
        return switch (name) {
            case ProbabilityProportionalToSizeQuerySampler.NAME -> new ProbabilityProportionalToSizeQuerySampler(
                size,
                client,
                ubiQueriesIndex
            );
            case RandomQuerySampler.NAME -> new RandomQuerySampler(size, client, ubiQueriesIndex);
            case TopNQuerySampler.NAME -> new TopNQuerySampler(size, client, ubiQueriesIndex);
            default -> throw new SearchRelevanceException("Unknown sampler type: " + name, RestStatus.BAD_REQUEST);
        };
    }
}
