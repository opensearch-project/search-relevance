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
import java.util.function.BiConsumer;

import org.opensearch.ResourceAlreadyExistsException;
import org.opensearch.ResourceNotFoundException;
import org.opensearch.action.DocWriteRequest.OpType;
import org.opensearch.action.StepListener;
import org.opensearch.action.admin.indices.create.CreateIndexRequest;
import org.opensearch.action.admin.indices.create.CreateIndexResponse;
import org.opensearch.action.delete.DeleteResponse;
import org.opensearch.action.search.SearchRequest;
import org.opensearch.action.search.SearchResponse;
import org.opensearch.action.search.ShardSearchFailure;
import org.opensearch.action.support.WriteRequest;
import org.opensearch.cluster.service.ClusterService;
import org.opensearch.common.io.Streams;
import org.opensearch.core.action.ActionListener;
import org.opensearch.core.rest.RestStatus;
import org.opensearch.core.xcontent.XContentBuilder;
import org.opensearch.index.IndexNotFoundException;
import org.opensearch.index.query.QueryBuilders;
import org.opensearch.search.builder.SearchSourceBuilder;
import org.opensearch.search.internal.InternalSearchResponse;
import org.opensearch.searchrelevance.exception.SearchRelevanceException;
import org.opensearch.searchrelevance.shared.StashedThreadContext;
import org.opensearch.transport.client.Client;

import lombok.Builder;
import lombok.Getter;
import lombok.extern.log4j.Log4j2;
import reactor.util.annotation.NonNull;

/**
 * Manager for common search relevance system indices operations.
 */
@Log4j2
public class SearchRelevanceIndicesManager {

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
            log.debug("Index [{}] already exists, skipping creation", indexName);
            stepListener.onResponse(null);
            return;
        }
        final CreateIndexRequest createIndexRequest = new CreateIndexRequest(indexName).mapping(mapping);
        StashedThreadContext.run(client, () -> client.admin().indices().create(createIndexRequest, new ActionListener<>() {
            @Override
            public void onResponse(final CreateIndexResponse createIndexResponse) {
                log.info("Successfully created index [{}]", indexName);
                stepListener.onResponse(null);
            }

            @Override
            public void onFailure(final Exception e) {
                if (e instanceof ResourceAlreadyExistsException) {
                    log.debug("index[{}] already exist", indexName);
                    stepListener.onResponse(null);
                    return;
                }
                log.error("Failed to create index [{}]", indexName, e);
                stepListener.onFailure(e);
            }
        }));
    }

    /**
     * Create a search relevance index if not exists, using synchronize calls
     * @param index
     */
    private void createIndexIfAbsentSync(final SearchRelevanceIndices index) {
        String indexName = index.getIndexName();
        String mapping = index.getMapping();

        if (clusterService.state().metadata().hasIndex(indexName)) {
            log.debug("Index [{}] already exists, skipping creation", indexName);
            return;
        }
        final CreateIndexRequest createIndexRequest = new CreateIndexRequest(indexName).mapping(mapping);
        StashedThreadContext.run(client, () -> client.admin().indices().create(createIndexRequest));
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
        SearchOperationContext searchOperationContext = SearchOperationContext.builder()
            .documentId(docId)
            .xContentBuilder(xContentBuilder)
            .index(index)
            .build();
        executeWithIndexCreation(searchOperationContext, (context, actionListener) -> StashedThreadContext.run(client, () -> {
            try {
                client.prepareIndex(context.getIndex().getIndexName())
                    .setId(context.getDocumentId())
                    .setOpType(OpType.CREATE)
                    .setRefreshPolicy(WriteRequest.RefreshPolicy.IMMEDIATE)
                    .setSource(context.getXContentBuilder())
                    .execute(listener);
            } catch (Exception e) {
                throw new SearchRelevanceException("Failed to store doc", e, RestStatus.INTERNAL_SERVER_ERROR);
            }
        }), listener);
    }

    /**
     * Execute Dao method wrapping it in "create index if absent" function
     * @param context
     * @param operation
     * @param listener
     */
    private <T> void executeWithIndexCreation(
        SearchOperationContext context,
        BiConsumer<SearchOperationContext, ActionListener<T>> operation,
        ActionListener<T> listener
    ) {
        createIndexIfAbsentSync(context.getIndex());
        operation.accept(context, listener);
    }

    /**
     * Update a doc to the system index
     * @param docId - document id need to be executed
     * @param xContentBuilder - content need to be executed
     * @param index - system index
     * @param listener - action lister for async operation
     */
    public void updateDoc(
        final String docId,
        final XContentBuilder xContentBuilder,
        final SearchRelevanceIndices index,
        final ActionListener listener
    ) {
        SearchOperationContext searchOperationContext = SearchOperationContext.builder()
            .index(index)
            .xContentBuilder(xContentBuilder)
            .documentId(docId)
            .build();
        executeWithIndexCreation(
            searchOperationContext,
            (searchOperationContext1, actionListener) -> StashedThreadContext.run(client, () -> {
                try {
                    client.prepareIndex(searchOperationContext1.getIndex().getIndexName())
                        .setId(searchOperationContext1.getDocumentId())
                        .setOpType(OpType.INDEX)
                        .setRefreshPolicy(WriteRequest.RefreshPolicy.IMMEDIATE)
                        .setSource(searchOperationContext1.getXContentBuilder())
                        .execute((ActionListener) actionListener);
                } catch (Exception e) {
                    throw new SearchRelevanceException("Failed to store doc", e, RestStatus.INTERNAL_SERVER_ERROR);
                }
            }),
            listener
        );
    }

    /**
     * Delete a doc by doc id
     * @param docId - document id need to be executed
     * @param index - system index
     * @param listener - action lister for async operation
     */
    public void deleteDocByDocId(final String docId, final SearchRelevanceIndices index, final ActionListener<DeleteResponse> listener) {
        SearchOperationContext searchOperationContext = SearchOperationContext.builder().index(index).documentId(docId).build();
        executeWithIndexCreation(
            searchOperationContext,
            (searchOperationContextArg, actionListener) -> StashedThreadContext.run(client, () -> {
                try {
                    client.prepareDelete(searchOperationContext.getIndex().getIndexName(), searchOperationContext.getDocumentId())
                        .setRefreshPolicy(WriteRequest.RefreshPolicy.IMMEDIATE)
                        .execute(new ActionListener<DeleteResponse>() {  // Specify the generic type
                            @Override
                            public void onResponse(DeleteResponse deleteResponse) {  // Properly typed parameter
                                actionListener.onResponse(deleteResponse);
                            }

                            @Override
                            public void onFailure(Exception e) {
                                actionListener.onFailure(
                                    new SearchRelevanceException("Failed to delete doc", e, RestStatus.INTERNAL_SERVER_ERROR)
                                );
                            }
                        });
                } catch (Exception e) {
                    actionListener.onFailure(new SearchRelevanceException("Failed to delete doc", e, RestStatus.INTERNAL_SERVER_ERROR));
                }
            }),
            listener
        );
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
        SearchOperationContext searchOperationContext = SearchOperationContext.builder().index(index).documentId(docId).build();
        executeWithIndexCreation(searchOperationContext, (searchOperationContextArg, actionListener) -> {
            SearchRequest searchRequest = new SearchRequest(searchOperationContextArg.getIndex().getIndexName());
            SearchSourceBuilder sourceBuilder = new SearchSourceBuilder().query(
                QueryBuilders.termQuery("_id", searchOperationContextArg.getDocumentId())
            ).size(1);

            searchRequest.source(sourceBuilder);

            StashedThreadContext.run(client, () -> {
                try {
                    client.search(searchRequest, new ActionListener<SearchResponse>() {
                        @Override
                        public void onResponse(SearchResponse response) {
                            log.info("Successfully get doc id [{}]", searchOperationContextArg.getDocumentId());
                            if (response.getHits().getTotalHits().value() == 0) {
                                actionListener.onFailure(
                                    new ResourceNotFoundException(
                                        "Document not found: " + searchOperationContextArg.getDocumentId(),
                                        RestStatus.NOT_FOUND
                                    )
                                );
                                return;
                            }
                            actionListener.onResponse(response);
                        }

                        @Override
                        public void onFailure(Exception e) {
                            actionListener.onFailure(
                                new SearchRelevanceException("Failed to get document", e, RestStatus.INTERNAL_SERVER_ERROR)
                            );
                        }
                    });
                } catch (Exception e) {
                    actionListener.onFailure(new SearchRelevanceException("Failed to get doc", e, RestStatus.INTERNAL_SERVER_ERROR));
                }
            });
        }, listener);
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
        SearchOperationContext searchOperationContext = SearchOperationContext.builder()
            .searchSourceBuilder(searchSourceBuilder)
            .index(index)
            .build();
        executeWithIndexCreation(searchOperationContext, (context, actionListener) -> {
            SearchRequest searchRequest = new SearchRequest(context.getIndex().getIndexName());
            searchRequest.source(context.getSearchSourceBuilder());
            StashedThreadContext.run(client, () -> {
                try {
                    client.search(searchRequest, new ActionListener<SearchResponse>() {
                        @Override
                        public void onResponse(SearchResponse response) {
                            log.info("Successfully list documents with search request [{}]", searchRequest);
                            ((ActionListener<SearchResponse>) actionListener).onResponse(response);
                        }

                        @Override
                        public void onFailure(Exception e) {
                            if (e instanceof IndexNotFoundException) {
                                final InternalSearchResponse internalSearchResponse = InternalSearchResponse.empty();
                                final SearchResponse emptySearchResponse = new SearchResponse(
                                    internalSearchResponse,
                                    null,
                                    0,
                                    0,
                                    0,
                                    0,
                                    null,
                                    new ShardSearchFailure[] {},
                                    SearchResponse.Clusters.EMPTY,
                                    null
                                );
                                ((ActionListener<SearchResponse>) actionListener).onResponse(emptySearchResponse);
                            } else {
                                actionListener.onFailure(
                                    new SearchRelevanceException("Failed to list documents", e, RestStatus.INTERNAL_SERVER_ERROR)
                                );
                            }
                        }
                    });
                } catch (Exception e) {
                    actionListener.onFailure(new SearchRelevanceException("Failed to list docs", e, RestStatus.INTERNAL_SERVER_ERROR));
                }
            });
        }, listener);
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

    /**
     *  DTO for search operation context
     */
    @Builder
    @Getter
    static class SearchOperationContext {
        private final SearchRelevanceIndices index;
        private final SearchSourceBuilder searchSourceBuilder;
        private final XContentBuilder xContentBuilder;
        private final String documentId;
    }
}
