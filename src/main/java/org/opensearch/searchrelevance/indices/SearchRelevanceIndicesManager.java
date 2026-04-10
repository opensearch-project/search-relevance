/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */
package org.opensearch.searchrelevance.indices;

import static org.opensearch.searchrelevance.common.PluginConstants.BATCH_SIZE_FOR_DELETE_BY_QUERY;
import static org.opensearch.searchrelevance.common.PluginConstants.PROCEED;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.BiConsumer;

import org.opensearch.ResourceAlreadyExistsException;
import org.opensearch.ResourceNotFoundException;
import org.opensearch.action.DocWriteRequest.OpType;
import org.opensearch.action.StepListener;
import org.opensearch.action.admin.indices.create.CreateIndexRequest;
import org.opensearch.action.admin.indices.create.CreateIndexResponse;
import org.opensearch.action.admin.indices.mapping.put.PutMappingRequest;
import org.opensearch.action.delete.DeleteResponse;
import org.opensearch.action.index.IndexResponse;
import org.opensearch.action.search.SearchRequest;
import org.opensearch.action.search.SearchResponse;
import org.opensearch.action.search.ShardSearchFailure;
import org.opensearch.action.support.WriteRequest;
import org.opensearch.action.update.UpdateRequest;
import org.opensearch.action.update.UpdateResponse;
import org.opensearch.cluster.metadata.MappingMetadata;
import org.opensearch.cluster.service.ClusterService;
import org.opensearch.common.io.Streams;
import org.opensearch.common.util.concurrent.ThreadContext;
import org.opensearch.core.action.ActionListener;
import org.opensearch.core.rest.RestStatus;
import org.opensearch.core.xcontent.XContentBuilder;
import org.opensearch.index.IndexNotFoundException;
import org.opensearch.index.query.QueryBuilders;
import org.opensearch.index.reindex.BulkByScrollResponse;
import org.opensearch.index.reindex.DeleteByQueryAction;
import org.opensearch.index.reindex.DeleteByQueryRequest;
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
 * Manager for common search relevance system indices actions.
 */
@Log4j2
public class SearchRelevanceIndicesManager {

    private final ClusterService clusterService;
    private final Client client;

    public SearchRelevanceIndicesManager(@NonNull ClusterService clusterService, @NonNull Client client) {
        this.clusterService = clusterService;
        this.client = client;
    }

    private static final int MAX_MAPPING_UPDATE_RETRIES = 3;

    /**
     * Create a search relevance index if not exists, or update mapping if index exists but has older schema version.
     * @param index - index to be created or updated
     * @param stepListener - step listener
     */
    public void createIndexIfAbsent(final SearchRelevanceIndices index, final StepListener<Void> stepListener) {
        String indexName = index.getIndexName();
        String mapping = index.getMapping();

        if (clusterService.state().metadata().hasIndex(indexName)) {
            // Index exists - check if we need to update the mapping
            try {
                int existingVersion = getExistingSchemaVersion(indexName);
                int currentVersion = index.getSchemaVersion();

                if (existingVersion >= currentVersion) {
                    log.debug("Index [{}] already exists with schema version [{}], skipping update", indexName, existingVersion);
                    stepListener.onResponse(null);
                    return;
                }

                // Existing version is older - update mapping with retry
                log.info("Updating index [{}] mapping from schema version [{}] to [{}]", indexName, existingVersion, currentVersion);
                updateMappingWithRetry(index, MAX_MAPPING_UPDATE_RETRIES, stepListener);
            } catch (Exception e) {
                log.error("Failed to check schema version for index [{}]: {}", indexName, e.getMessage());
                stepListener.onFailure(
                    new SearchRelevanceException(
                        String.format(Locale.ROOT, "Failed to check schema version for index [%s]", indexName),
                        e,
                        RestStatus.INTERNAL_SERVER_ERROR
                    )
                );
            }
            return;
        }

        final CreateIndexRequest createIndexRequest = new CreateIndexRequest(indexName).mapping(mapping)
            .settings(org.opensearch.common.settings.Settings.builder().put("index.auto_expand_replicas", "0-1").build());
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
                log.warn("Failed to create index [{}] - continuing without cache optimization", indexName);
                stepListener.onResponse(null);
            }
        }));
    }

    /**
     * Create a search relevance index if not exists, or update mapping if index exists but has older schema version.
     * Uses synchronous calls.
     * @param index the index to create or update
     */
    private void createIndexIfAbsentSync(final SearchRelevanceIndices index) {
        String indexName = index.getIndexName();
        String mapping = index.getMapping();

        if (!clusterService.state().metadata().hasIndex(indexName)) {
            // Index does not exist - create new index
            log.info("Creating new index [{}] with schema version [{}]", indexName, index.getSchemaVersion());
            final CreateIndexRequest createIndexRequest = new CreateIndexRequest(indexName).mapping(mapping)
                .settings(org.opensearch.common.settings.Settings.builder().put("index.auto_expand_replicas", "0-1").build());
            StashedThreadContext.run(client, () -> client.admin().indices().create(createIndexRequest));
            return;
        }

        // Index exists - check schema version
        int existingVersion = getExistingSchemaVersion(indexName);
        int currentVersion = index.getSchemaVersion();

        // Skip update if already up-to-date
        if (existingVersion >= currentVersion) {
            return;
        }

        // Existing version is older - update mapping (best-effort)
        // If the update fails or times out (e.g., during rolling upgrade cluster transitions),
        // log a warning and continue. Reads work fine with the old mapping, and the next
        // write operation will retry the update.
        try {
            log.info("Updating index [{}] mapping from schema version [{}] to [{}]", indexName, existingVersion, currentVersion);
            updateMappingSync(index);
        } catch (Exception e) {
            log.warn(
                "Failed to update mapping for index [{}] from version [{}] to [{}]: {}. " + "Will retry on next operation.",
                indexName,
                existingVersion,
                currentVersion,
                e.getMessage()
            );
        }
    }

    /**
     * Get the schema version from an existing index's _meta field.
     * Returns 0 for legacy indices without _meta.schema_version OR initial version.
     * Throws exception if there was an error reading the mapping.
     * @param indexName the name of the index
     * @return the schema version (0 for legacy or initial)
     * @throws SearchRelevanceException if mapping cannot be read
     */
    private int getExistingSchemaVersion(final String indexName) {
        try {
            MappingMetadata mappingMetadata = clusterService.state().metadata().index(indexName).mapping();
            if (mappingMetadata == null) {
                throw new SearchRelevanceException(
                    String.format(Locale.ROOT, "Index [%s] exists but has no mapping", indexName),
                    RestStatus.INTERNAL_SERVER_ERROR
                );
            }

            Map<String, Object> mappingSource = mappingMetadata.sourceAsMap();
            if (mappingSource == null) {
                throw new SearchRelevanceException(
                    String.format(Locale.ROOT, "Index [%s] exists but mapping source is null", indexName),
                    RestStatus.INTERNAL_SERVER_ERROR
                );
            }

            Object metaObj = mappingSource.get("_meta");
            if (!(metaObj instanceof Map<?, ?>)) {
                return 0; // Legacy index - no _meta field
            }

            Map<?, ?> meta = (Map<?, ?>) metaObj;
            Object versionObj = meta.get(SearchRelevanceIndices.META_SCHEMA_VERSION_KEY);
            if (versionObj instanceof Number) {
                return ((Number) versionObj).intValue();
            }
            return 0; // Legacy index - no schema_version in _meta
        } catch (Exception e) {
            throw new SearchRelevanceException(
                String.format(Locale.ROOT, "Failed to get schema version for index [%s]", indexName),
                e,
                RestStatus.INTERNAL_SERVER_ERROR
            );
        }
    }

    /**
     * Update the mapping for an existing index synchronously.
     * Uses a CompletableFuture to wait for the async putMapping to complete,
     * ensuring the mapping is fully applied before any document write occurs.
     * Note: CompletableFuture.get() is used instead of ActionFuture.actionGet()
     * because actionGet() is forbidden on transport threads.
     * @param index the index whose mapping should be updated
     * @throws SearchRelevanceException if the mapping update fails or times out
     */
    private void updateMappingSync(final SearchRelevanceIndices index) {
        final PutMappingRequest putMappingRequest = new PutMappingRequest(index.getIndexName()).source(
            index.getMapping(),
            org.opensearch.common.xcontent.XContentType.JSON
        );
        final CompletableFuture<Void> future = new CompletableFuture<>();
        try (ThreadContext.StoredContext context = client.threadPool().getThreadContext().stashContext()) {
            client.admin().indices().putMapping(putMappingRequest, new ActionListener<>() {
                @Override
                public void onResponse(org.opensearch.action.support.clustermanager.AcknowledgedResponse response) {
                    future.complete(null);
                }

                @Override
                public void onFailure(Exception e) {
                    future.completeExceptionally(e);
                }
            });
            future.get(30, TimeUnit.SECONDS);
        } catch (TimeoutException e) {
            throw new SearchRelevanceException(
                String.format(Locale.ROOT, "Timeout waiting for mapping update on index [%s]", index.getIndexName()),
                RestStatus.INTERNAL_SERVER_ERROR
            );
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new SearchRelevanceException(
                String.format(Locale.ROOT, "Interrupted waiting for mapping update on index [%s]", index.getIndexName()),
                e,
                RestStatus.INTERNAL_SERVER_ERROR
            );
        } catch (ExecutionException e) {
            throw new SearchRelevanceException(
                String.format(Locale.ROOT, "Failed to update mapping for index [%s]", index.getIndexName()),
                e.getCause(),
                RestStatus.INTERNAL_SERVER_ERROR
            );
        }
    }

    /**
     * Update the mapping for an existing index with retry logic.
     * Retries up to maxRetries times before failing.
     * @param index the index whose mapping should be updated
     * @param maxRetries maximum number of retry attempts
     * @param stepListener listener to notify on success or failure
     */
    private void updateMappingWithRetry(final SearchRelevanceIndices index, final int maxRetries, final StepListener<Void> stepListener) {
        String indexName = index.getIndexName();
        Exception lastException = null;

        for (int attempt = 1; attempt <= maxRetries; attempt++) {
            try {
                updateMappingSync(index);
                log.info("Successfully updated mapping for index [{}] on attempt {}", indexName, attempt);
                stepListener.onResponse(null);
                return;
            } catch (Exception e) {
                lastException = e;
                log.warn("Failed to update mapping for index [{}] on attempt {}/{}: {}", indexName, attempt, maxRetries, e.getMessage());
                if (attempt < maxRetries) {
                    try {
                        Thread.sleep(1000L * attempt); // Exponential backoff: 1s, 2s, 3s
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }
        }

        // All retries exhausted - fail the service
        log.error("Failed to update mapping for index [{}] after {} attempts", indexName, maxRetries);
        stepListener.onFailure(
            new SearchRelevanceException(
                String.format(Locale.ROOT, "Failed to update mapping for index [%s] after %d attempts", indexName, maxRetries),
                lastException,
                RestStatus.INTERNAL_SERVER_ERROR
            )
        );
    }

    public SearchResponse getDocByDocIdSync(final String docId, final SearchRelevanceIndices index) {
        SearchRequest searchRequest = new SearchRequest(index.getIndexName());
        SearchSourceBuilder sourceBuilder = new SearchSourceBuilder().query(QueryBuilders.termQuery("_id", docId)).size(1);
        searchRequest.source(sourceBuilder);

        return client.search(searchRequest).actionGet();
    }

    /**
     * Put a doc to the system index
     * @param docId - document id need to be executed
     * @param xContentBuilder - content need to be executed
     * @param index - system index
     * @param listener - action listener for async action
     */
    public void putDoc(
        final String docId,
        final XContentBuilder xContentBuilder,
        final SearchRelevanceIndices index,
        final ActionListener<?> listener
    ) {
        putDocWithRefreshPolicy(docId, xContentBuilder, index, WriteRequest.RefreshPolicy.IMMEDIATE, listener);
    }

    /**
     * Put a doc to the system index with efficient refresh policy (recommended for experiments)
     * @param docId - document id need to be executed
     * @param xContentBuilder - content need to be executed
     * @param index - system index
     * @param listener - action listener for async action
     */
    public void putDocEfficient(
        final String docId,
        final XContentBuilder xContentBuilder,
        final SearchRelevanceIndices index,
        final ActionListener<?> listener
    ) {
        putDocWithRefreshPolicy(docId, xContentBuilder, index, WriteRequest.RefreshPolicy.WAIT_UNTIL, listener);
    }

    /**
     * Put a doc to the system index with specified refresh policy
     * @param docId - document id need to be executed
     * @param xContentBuilder - content need to be executed
     * @param index - system index
     * @param refreshPolicy - refresh policy to use
     * @param listener - action listener for async action
     */
    public void putDocWithRefreshPolicy(
        final String docId,
        final XContentBuilder xContentBuilder,
        final SearchRelevanceIndices index,
        final WriteRequest.RefreshPolicy refreshPolicy,
        final ActionListener<?> listener
    ) {
        SearchOperationContext searchOperationContext = SearchOperationContext.builder()
            .documentId(docId)
            .xContentBuilder(xContentBuilder)
            .index(index)
            .build();
        BiConsumer<SearchOperationContext, ActionListener<?>> action = (context, actionListener) -> StashedThreadContext.run(client, () -> {
            try {
                @SuppressWarnings("unchecked")
                ActionListener<IndexResponse> typedListener = (ActionListener<IndexResponse>) actionListener;
                client.prepareIndex(context.getIndex().getIndexName())
                    .setId(context.getDocumentId())
                    .setOpType(OpType.CREATE)
                    .setRefreshPolicy(refreshPolicy)
                    .setSource(context.getXContentBuilder())
                    .execute(typedListener);
            } catch (Exception e) {
                throw new SearchRelevanceException("Failed to store doc", e, RestStatus.INTERNAL_SERVER_ERROR);
            }
        });
        executeAction(listener, searchOperationContext, action);
    }

    /**
     * Update a doc to the system index
     * @param docId - document id need to be executed
     * @param xContentBuilder - content need to be executed
     * @param index - system index
     * @param listener - action listener for async action
     */
    public void updateDoc(
        final String docId,
        final XContentBuilder xContentBuilder,
        final SearchRelevanceIndices index,
        final ActionListener listener
    ) {
        updateDocWithRefreshPolicy(docId, xContentBuilder, index, WriteRequest.RefreshPolicy.IMMEDIATE, listener);
    }

    /**
     * Update a doc to the system index with efficient refresh policy (recommended for experiments)
     * @param docId - document id need to be executed
     * @param xContentBuilder - content need to be executed
     * @param index - system index
     * @param listener - action listener for async action
     */
    public void updateDocEfficient(
        final String docId,
        final XContentBuilder xContentBuilder,
        final SearchRelevanceIndices index,
        final ActionListener listener
    ) {
        updateDocWithRefreshPolicy(docId, xContentBuilder, index, WriteRequest.RefreshPolicy.WAIT_UNTIL, listener);
    }

    /**
     * Update a doc to the system index with specified refresh policy
     * @param docId - document id need to be executed
     * @param xContentBuilder - content need to be executed
     * @param index - system index
     * @param refreshPolicy - refresh policy to use
     * @param listener - action listener for async action
     */
    public void updateDocWithRefreshPolicy(
        final String docId,
        final XContentBuilder xContentBuilder,
        final SearchRelevanceIndices index,
        final WriteRequest.RefreshPolicy refreshPolicy,
        final ActionListener listener
    ) {
        SearchOperationContext searchOperationContext = SearchOperationContext.builder()
            .index(index)
            .xContentBuilder(xContentBuilder)
            .documentId(docId)
            .build();
        BiConsumer<SearchOperationContext, ActionListener<?>> action = (searchOperationContext1, actionListener) -> StashedThreadContext
            .run(client, () -> {
                try {
                    client.prepareIndex(searchOperationContext1.getIndex().getIndexName())
                        .setId(searchOperationContext1.getDocumentId())
                        .setOpType(OpType.INDEX)
                        .setRefreshPolicy(refreshPolicy)
                        .setSource(searchOperationContext1.getXContentBuilder())
                        .execute((ActionListener) actionListener);
                } catch (Exception e) {
                    throw new SearchRelevanceException("Failed to store doc", e, RestStatus.INTERNAL_SERVER_ERROR);
                }
            });
        executeAction(listener, searchOperationContext, action);
    }

    /**
     * Delete a doc by doc id
     * @param docId - document id need to be executed
     * @param index - system index
     * @param listener - action listener for async action
     */
    public void deleteDocByDocId(final String docId, final SearchRelevanceIndices index, final ActionListener<DeleteResponse> listener) {
        SearchOperationContext searchOperationContext = SearchOperationContext.builder().index(index).documentId(docId).build();
        BiConsumer<SearchOperationContext, ActionListener<?>> action = (searchOperationContextArg, actionListener) -> StashedThreadContext
            .run(client, () -> {
                try {
                    @SuppressWarnings("unchecked")
                    ActionListener<DeleteResponse> typedListener = (ActionListener<DeleteResponse>) actionListener;
                    client.prepareDelete(searchOperationContext.getIndex().getIndexName(), searchOperationContext.getDocumentId())
                        .setRefreshPolicy(WriteRequest.RefreshPolicy.IMMEDIATE)
                        .execute(new ActionListener<>() {  // Specify the generic type
                            @Override
                            public void onResponse(DeleteResponse deleteResponse) {  // Properly typed parameter
                                typedListener.onResponse(deleteResponse);
                            }

                            @Override
                            public void onFailure(Exception e) {
                                typedListener.onFailure(
                                    new SearchRelevanceException("Failed to delete doc", e, RestStatus.INTERNAL_SERVER_ERROR)
                                );
                            }
                        });
                } catch (Exception e) {
                    actionListener.onFailure(new SearchRelevanceException("Failed to delete doc", e, RestStatus.INTERNAL_SERVER_ERROR));
                }
            });
        executeAction(listener, searchOperationContext, action);
    }

    /**
     * Get a doc by doc id
     * @param docId - document id need to be executed
     * @param index - system index
     * @param listener - action listener for async action
     */
    public SearchResponse getDocByDocId(final String docId, final SearchRelevanceIndices index, final ActionListener<?> listener) {
        SearchOperationContext searchOperationContext = SearchOperationContext.builder().index(index).documentId(docId).build();
        BiConsumer<SearchOperationContext, ActionListener<?>> action = (searchOperationContextArg, actionListener) -> {
            SearchRequest searchRequest = new SearchRequest(searchOperationContextArg.getIndex().getIndexName());
            SearchSourceBuilder sourceBuilder = new SearchSourceBuilder().query(
                QueryBuilders.termQuery("_id", searchOperationContextArg.getDocumentId())
            ).size(1);

            searchRequest.source(sourceBuilder);

            StashedThreadContext.run(client, () -> {
                try {
                    @SuppressWarnings("unchecked")
                    ActionListener<SearchResponse> typedListener = (ActionListener<SearchResponse>) actionListener;
                    client.search(searchRequest, new ActionListener<>() {
                        @Override
                        public void onResponse(SearchResponse response) {
                            log.info("Successfully get doc id [{}]", searchOperationContextArg.getDocumentId());
                            if (response.getHits().getTotalHits().value() == 0) {
                                typedListener.onFailure(
                                    new ResourceNotFoundException(
                                        "Document not found: " + searchOperationContextArg.getDocumentId(),
                                        RestStatus.NOT_FOUND
                                    )
                                );
                                return;
                            }
                            typedListener.onResponse(response);
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
        };
        executeAction(listener, searchOperationContext, action);
        return null;
    }

    /**
     * List docs by search request
     * @param searchSourceBuilder - search source builder to be executed
     * @param index - index to be executed
     * @param listener - action listener for async action
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
        BiConsumer<SearchOperationContext, ActionListener<?>> action = (context, actionListener) -> {
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
        };
        executeAction(listener, searchOperationContext, action);
        return null;
    }

    /**
     * Patch (partially update) a document in the system index.
     * Only updates the specified fields, leaving other fields unchanged.
     *
     * @param docId - document id to be updated
     * @param updates - map of field names to new values
     * @param index - system index
     * @param listener - action listener for async action
     */
    public void patchDoc(
        final String docId,
        final Map<String, Object> updates,
        final SearchRelevanceIndices index,
        final ActionListener<UpdateResponse> listener
    ) {
        if (docId == null || docId.isEmpty()) {
            listener.onFailure(new SearchRelevanceException("Document ID cannot be null or empty", RestStatus.BAD_REQUEST));
            return;
        }
        if (updates == null || updates.isEmpty()) {
            listener.onFailure(new SearchRelevanceException("Updates map cannot be null or empty", RestStatus.BAD_REQUEST));
            return;
        }
        if (index == null) {
            listener.onFailure(new SearchRelevanceException("Index cannot be null", RestStatus.BAD_REQUEST));
            return;
        }

        SearchOperationContext searchOperationContext = SearchOperationContext.builder().index(index).documentId(docId).build();
        BiConsumer<SearchOperationContext, ActionListener<?>> action = (context, actionListener) -> StashedThreadContext.run(client, () -> {
            try {
                @SuppressWarnings("unchecked")
                ActionListener<UpdateResponse> typedListener = (ActionListener<UpdateResponse>) actionListener;
                UpdateRequest updateRequest = new UpdateRequest(context.getIndex().getIndexName(), context.getDocumentId()).doc(updates)
                    .setRefreshPolicy(WriteRequest.RefreshPolicy.IMMEDIATE);

                client.update(updateRequest, new ActionListener<>() {
                    @Override
                    public void onResponse(UpdateResponse updateResponse) {
                        log.info("Successfully patched doc id [{}]", context.getDocumentId());
                        typedListener.onResponse(updateResponse);
                    }

                    @Override
                    public void onFailure(Exception e) {
                        typedListener.onFailure(
                            new SearchRelevanceException("Failed to patch document", e, RestStatus.INTERNAL_SERVER_ERROR)
                        );
                    }
                });
            } catch (Exception e) {
                actionListener.onFailure(new SearchRelevanceException("Failed to patch doc", e, RestStatus.INTERNAL_SERVER_ERROR));
            }
        });
        executeAction(listener, searchOperationContext, action);
    }

    /**
     * Delete by query
     * @param fieldId - field id need to be deleted
     * @param fieldName - field name need to be deleted
     * @param index - index on which delete operation has to be performed
     * @param listener - action listener for async action
     */
    public void deleteByQuery(
        final String fieldId,
        final String fieldName,
        final SearchRelevanceIndices index,
        final ActionListener<BulkByScrollResponse> listener
    ) {
        DeleteByQueryRequest deleteByQueryRequest = new DeleteByQueryRequest(index.getIndexName());
        deleteByQueryRequest.setConflicts(PROCEED);
        deleteByQueryRequest.setBatchSize(BATCH_SIZE_FOR_DELETE_BY_QUERY);
        deleteByQueryRequest.setQuery(QueryBuilders.termQuery(fieldName, fieldId));

        client.execute(DeleteByQueryAction.INSTANCE, deleteByQueryRequest, listener);
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
     * Execute action that wraps it in "create index if absent" function
     * @param listener
     * @param searchOperationContext
     * @param action
     */
    private void executeAction(
        ActionListener<?> listener,
        SearchOperationContext searchOperationContext,
        BiConsumer<SearchOperationContext, ActionListener<?>> action
    ) {
        SearchRelevanceIndices index = searchOperationContext.getIndex();
        if (Objects.isNull(index)) {
            throw new SearchRelevanceException("index cannot be null", RestStatus.BAD_REQUEST);
        }
        if (index.isProtected()) {
            executeWithIndexCreationAsynchronizedMode(searchOperationContext, action, listener);
        } else {
            executeWithIndexCreationSynchronizedMode(searchOperationContext, action, listener);
        }
    }

    /**
     * Execute Dao method wrapping it in "create index if absent" function
     * @param context
     * @param action
     * @param listener
     */
    private <T> void executeWithIndexCreationSynchronizedMode(
        SearchOperationContext context,
        BiConsumer<SearchOperationContext, ActionListener<?>> action,
        ActionListener<T> listener
    ) {
        createIndexIfAbsentSync(context.getIndex());
        action.accept(context, listener);
    }

    /**
     * Execute Dao method wrapping it in "create index if absent" function
     * @param context
     * @param action
     * @param listener
     */
    private <T> void executeWithIndexCreationAsynchronizedMode(
        SearchOperationContext context,
        BiConsumer<SearchOperationContext, ActionListener<?>> action,
        ActionListener<T> listener
    ) {
        StepListener<Void> createIndexStep = new StepListener<>();
        createIndexIfAbsent(context.getIndex(), createIndexStep);
        createIndexStep.whenComplete(v -> action.accept(context, listener), e -> {
            throw e instanceof RuntimeException ? (RuntimeException) e : new IllegalStateException(e);
        });
    }

    /**
     *  DTO for search action context
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
