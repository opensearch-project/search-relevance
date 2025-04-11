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
import org.opensearch.ResourceNotFoundException;
import org.opensearch.action.DocWriteRequest;
import org.opensearch.action.StepListener;
import org.opensearch.action.admin.indices.create.CreateIndexRequest;
import org.opensearch.action.admin.indices.create.CreateIndexResponse;
import org.opensearch.action.delete.DeleteResponse;
import org.opensearch.action.search.SearchRequest;
import org.opensearch.action.search.SearchResponse;
import org.opensearch.action.support.WriteRequest;
import org.opensearch.cluster.service.ClusterService;
import org.opensearch.common.io.Streams;
import org.opensearch.core.action.ActionListener;
import org.opensearch.core.rest.RestStatus;
import org.opensearch.core.xcontent.XContentBuilder;
import org.opensearch.index.query.QueryBuilders;
import org.opensearch.search.builder.SearchSourceBuilder;
import org.opensearch.searchrelevance.exception.SearchRelevanceException;
import org.opensearch.searchrelevance.shared.StashedThreadContext;
import org.opensearch.transport.client.Client;

import reactor.util.annotation.NonNull;

/**
 * Manager for common search relevance system indices operations.
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
                    LOGGER.debug("index[{}] already exist", indexName);
                    stepListener.onResponse(null);
                    return;
                }
                LOGGER.error("Failed to create index [{}]", indexName, e);
                stepListener.onFailure(e);
            }
        }));
    }

    /**
     * Put a doc to the system index
     * @param docId - document id need to be executed
     * @param xContentBuilder - content need to be executed
     * @param index - system index
     * @param listener - action lister for async operation
     */
    public void putDoc(
        final String docId,
        final XContentBuilder xContentBuilder,
        final SearchRelevanceIndices index,
        final ActionListener listener
    ) {
        StashedThreadContext.run(client, () -> {
            try {
                client.prepareIndex(index.getIndexName())
                    .setId(docId)
                    .setOpType(DocWriteRequest.OpType.CREATE)
                    .setRefreshPolicy(WriteRequest.RefreshPolicy.IMMEDIATE)
                    .setSource(xContentBuilder)
                    .execute(listener);
            } catch (Exception e) {
                throw new SearchRelevanceException("Failed to store doc", e, RestStatus.INTERNAL_SERVER_ERROR);
            }
        });
    }

    /**
     * Delete a doc by doc id
     * @param docId - document id need to be executed
     * @param index - system index
     * @param listener - action lister for async operation
     */
    public void deleteDocByDocId(final String docId, final SearchRelevanceIndices index, final ActionListener<DeleteResponse> listener) {
        StashedThreadContext.run(client, () -> {
            try {
                client.prepareDelete(index.getIndexName(), docId)
                    .setRefreshPolicy(WriteRequest.RefreshPolicy.IMMEDIATE)
                    .execute(new ActionListener<DeleteResponse>() {
                        @Override
                        public void onResponse(DeleteResponse deleteResponse) {
                            LOGGER.info("Successfully delete doc id [{}]", docId);
                            listener.onResponse(deleteResponse);
                        }

                        @Override
                        public void onFailure(Exception e) {
                            listener.onFailure(new SearchRelevanceException("Failed to delete doc", e, RestStatus.INTERNAL_SERVER_ERROR));
                        }
                    });
            } catch (Exception e) {
                listener.onFailure(new SearchRelevanceException("Failed to delete doc", e, RestStatus.INTERNAL_SERVER_ERROR));
            }
        });
    }

    /**
     * Get a doc by doc id
     * @param docId - document id need to be executed
     * @param index - system index
     * @param listener - action lister for async operation
     */
    public SearchResponse getDocByDocId(
        final String docId,
        final SearchRelevanceIndices index,
        final ActionListener<SearchResponse> listener
    ) {
        SearchRequest searchRequest = new SearchRequest(index.getIndexName());
        SearchSourceBuilder sourceBuilder = new SearchSourceBuilder().query(QueryBuilders.termQuery("_id", docId)).size(1);

        searchRequest.source(sourceBuilder);

        StashedThreadContext.run(client, () -> {
            try {
                client.search(searchRequest, new ActionListener<SearchResponse>() {
                    @Override
                    public void onResponse(SearchResponse response) {
                        LOGGER.info("Successfully get doc id [{}]", docId);
                        if (response.getHits().getTotalHits().value() == 0) {
                            listener.onFailure(new ResourceNotFoundException("Document not found: " + docId));
                            return;
                        }
                        listener.onResponse(response);
                    }

                    @Override
                    public void onFailure(Exception e) {
                        listener.onFailure(new SearchRelevanceException("Failed to get document", e, RestStatus.INTERNAL_SERVER_ERROR));
                    }
                });
            } catch (Exception e) {
                listener.onFailure(new SearchRelevanceException("Failed to get doc", e, RestStatus.INTERNAL_SERVER_ERROR));
            }
        });
        return null;
    }

    /**
     * List docs by search request
     * @param searchSourceBuilder - search source builder to be executed
     * @param index - index to be executed
     * @param listener - action lister for async operation
     */
    public SearchResponse listDocsBySearchRequest(
        final SearchSourceBuilder searchSourceBuilder,
        final SearchRelevanceIndices index,
        final ActionListener<SearchResponse> listener
    ) {
        SearchRequest searchRequest = new SearchRequest(index.getIndexName());
        searchRequest.source(searchSourceBuilder);
        StashedThreadContext.run(client, () -> {
            try {
                client.search(searchRequest, new ActionListener<SearchResponse>() {
                    @Override
                    public void onResponse(SearchResponse response) {
                        LOGGER.info("Successfully list documents with search request [{}]", searchRequest);
                        listener.onResponse(response);
                    }

                    @Override
                    public void onFailure(Exception e) {
                        listener.onFailure(new SearchRelevanceException("Failed to list documents", e, RestStatus.INTERNAL_SERVER_ERROR));
                    }
                });
            } catch (Exception e) {
                listener.onFailure(new SearchRelevanceException("Failed to list docs", e, RestStatus.INTERNAL_SERVER_ERROR));
            }
        });
        return null;
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
            throw new SearchRelevanceException("Mapping path cannot be null or empty", RestStatus.INTERNAL_SERVER_ERROR);
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
