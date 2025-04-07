/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */
package org.opensearch.searchrelevance.indices;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.opensearch.ResourceAlreadyExistsException;
import org.opensearch.action.StepListener;
import org.opensearch.action.admin.indices.create.CreateIndexRequest;
import org.opensearch.action.admin.indices.create.CreateIndexResponse;
import org.opensearch.cluster.service.ClusterService;
import org.opensearch.common.io.Streams;
import org.opensearch.core.action.ActionListener;
import org.opensearch.searchrelevance.shared.StashedThreadContext;
import org.opensearch.transport.client.Client;

import reactor.util.annotation.NonNull;

/**
 * Manager for common search relevance index operations.
 */
public class SearchRelevanceIndicesManager {
    private static final Logger LOGGER = LogManager.getLogger(SearchRelevanceIndicesManager.class);

    private final ClusterService clusterService;
    private final Client client;

    public SearchRelevanceIndicesManager(@NonNull ClusterService clusterService, @NonNull Client client) {
        this.clusterService = clusterService;
        this.client = client;
    }

    /**
     * Create a search relevance index if not exists
     * @param index - index to be created
     * @param stepListener - step lister
     */
    public void createIndexIfAbsent(final SearchRelevanceIndices index, final StepListener<Void> stepListener) {
        String indexName = index.getIndexName();
        String mapping = index.getMapping();

        if (clusterService.state().metadata().hasIndex(indexName)) {
            LOGGER.debug("Index [{}] already exists, skipping creation", indexName);
            stepListener.onResponse(null);
            return;
        }
        final CreateIndexRequest createIndexRequest = new CreateIndexRequest(indexName).mapping(mapping);
        StashedThreadContext.run(client, () -> client.admin().indices().create(createIndexRequest, new ActionListener<>() {
            @Override
            public void onResponse(final CreateIndexResponse createIndexResponse) {
                LOGGER.info("Successfully created index [{}]", indexName);
                stepListener.onResponse(null);
            }

            @Override
            public void onFailure(final Exception e) {
                if (e instanceof ResourceAlreadyExistsException) {
                    LOGGER.info("index[{}] already exist", indexName);
                    stepListener.onResponse(null);
                    return;
                }
                LOGGER.error("Failed to create index [{}]", indexName, e);
                stepListener.onFailure(e);
            }
        }));
    }

    /**
     * Gets index mapping JSON content from the classpath
     *
     * @param mapping type of the index to fetch the specific mapping file
     * @return index mapping
     * @throws IOException IOException if mapping file can't be read correctly
     */
    public static String getIndexMappings(final String mapping) throws IOException {
        if (mapping == null || mapping.trim().isEmpty()) {
            throw new IllegalArgumentException("Mapping path cannot be null or empty");
        }

        final String path = mapping.startsWith("/") ? mapping : "/" + mapping;

        try (InputStream is = SearchRelevanceIndicesManager.class.getResourceAsStream(path)) {
            if (is == null) {
                throw new FileNotFoundException("Resource [" + path + "] not found in classpath");
            }
            final StringBuilder sb = new StringBuilder();
            // Read as UTF-8
            Streams.readAllLines(is, sb::append);
            return sb.toString();
        }
    }
}
