/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */
package org.opensearch.searchrelevance.judgments;

import static org.opensearch.searchrelevance.common.MLConstants.sanitizeLLMResponse;
import static org.opensearch.searchrelevance.model.QueryWithReference.DELIMITER;
import static org.opensearch.searchrelevance.model.builder.SearchRequestBuilder.buildSearchRequest;
import static org.opensearch.searchrelevance.utils.ParserUtils.combinedIndexAndDocId;
import static org.opensearch.searchrelevance.utils.ParserUtils.generateUniqueId;
import static org.opensearch.searchrelevance.utils.ParserUtils.getDocIdFromCompositeKey;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

import org.opensearch.action.StepListener;
import org.opensearch.action.search.SearchRequest;
import org.opensearch.action.search.SearchResponse;
import org.opensearch.action.support.PlainActionFuture;
import org.opensearch.common.inject.Inject;
import org.opensearch.core.action.ActionListener;
import org.opensearch.core.rest.RestStatus;
import org.opensearch.search.SearchHit;
import org.opensearch.searchrelevance.dao.JudgmentCacheDao;
import org.opensearch.searchrelevance.dao.QuerySetDao;
import org.opensearch.searchrelevance.dao.SearchConfigurationDao;
import org.opensearch.searchrelevance.exception.SearchRelevanceException;
import org.opensearch.searchrelevance.executors.LlmJudgmentTaskManager;
import org.opensearch.searchrelevance.ml.ChunkResult;
import org.opensearch.searchrelevance.ml.MLAccessor;
import org.opensearch.searchrelevance.model.JudgmentCache;
import org.opensearch.searchrelevance.model.JudgmentType;
import org.opensearch.searchrelevance.model.QuerySet;
import org.opensearch.searchrelevance.model.SearchConfiguration;
import org.opensearch.searchrelevance.stats.events.EventStatName;
import org.opensearch.searchrelevance.stats.events.EventStatsManager;
import org.opensearch.searchrelevance.utils.TimeUtils;
import org.opensearch.threadpool.ThreadPool;
import org.opensearch.transport.client.Client;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.extern.log4j.Log4j2;

@Log4j2
public class LlmJudgmentsProcessor implements BaseJudgmentsProcessor {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private final MLAccessor mlAccessor;
    private final QuerySetDao querySetDao;
    private final SearchConfigurationDao searchConfigurationDao;
    private final JudgmentCacheDao judgmentCacheDao;
    private final Client client;
    private final ThreadPool threadPool;
    private final LlmJudgmentTaskManager taskManager;

    @Inject
    public LlmJudgmentsProcessor(
        MLAccessor mlAccessor,
        QuerySetDao querySetDao,
        SearchConfigurationDao searchConfigurationDao,
        JudgmentCacheDao judgmentCacheDao,
        Client client,
        ThreadPool threadPool
    ) {
        this.mlAccessor = mlAccessor;
        this.querySetDao = querySetDao;
        this.searchConfigurationDao = searchConfigurationDao;
        this.judgmentCacheDao = judgmentCacheDao;
        this.client = client;
        this.threadPool = threadPool;
        this.taskManager = new LlmJudgmentTaskManager(threadPool);
    }

    @Override
    public JudgmentType getJudgmentType() {
        return JudgmentType.LLM_JUDGMENT;
    }

    @Override
    public void generateJudgmentRating(Map<String, Object> metadata, ActionListener<List<Map<String, Object>>> listener) {
        // Execute entire method on generic thread pool to avoid transport thread blocking
        threadPool.executor(ThreadPool.Names.GENERIC).execute(() -> { generateJudgmentRatingInternal(metadata, listener); });
    }

    private void generateJudgmentRatingInternal(Map<String, Object> metadata, ActionListener<List<Map<String, Object>>> listener) {
        try {
            EventStatsManager.increment(EventStatName.LLM_JUDGMENT_RATING_GENERATIONS);
            String querySetId = (String) metadata.get("querySetId");
            List<String> searchConfigurationList = (List<String>) metadata.get("searchConfigurationList");
            int size = (int) metadata.get("size");

            String modelId = (String) metadata.get("modelId");
            int tokenLimit = (int) metadata.get("tokenLimit");
            List<String> contextFields = (List<String>) metadata.get("contextFields");
            boolean ignoreFailure = (boolean) metadata.get("ignoreFailure");

            QuerySet querySet = querySetDao.getQuerySetSync(querySetId);
            List<SearchConfiguration> searchConfigurations = searchConfigurationList.stream()
                .map(id -> searchConfigurationDao.getSearchConfigurationSync(id))
                .collect(Collectors.toList());

            generateLLMJudgmentsAsync(modelId, size, tokenLimit, contextFields, querySet, searchConfigurations, ignoreFailure, listener);
        } catch (Exception e) {
            log.error("Failed to generate LLM judgments", e);
            listener.onFailure(new SearchRelevanceException("Failed to generate LLM judgments", e, RestStatus.INTERNAL_SERVER_ERROR));
        }
    }

    private void generateLLMJudgmentsAsync(
        String modelId,
        int size,
        int tokenLimit,
        List<String> contextFields,
        QuerySet querySet,
        List<SearchConfiguration> searchConfigurations,
        boolean ignoreFailure,
        ActionListener<List<Map<String, Object>>> listener
    ) {
        List<String> queryTextWithReferences = querySet.querySetQueries().stream().map(e -> e.queryText()).collect(Collectors.toList());
        int totalQueries = queryTextWithReferences.size();

        log.info("Starting LLM judgment generation for {} total queries", totalQueries);

        // Create judgment cache index upfront to prevent concurrent creation attempts
        StepListener<Void> cacheIndexListener = new StepListener<>();
        judgmentCacheDao.createIndexIfAbsent(cacheIndexListener);
        cacheIndexListener.whenComplete(indexResult -> {
            log.debug("Judgment cache index creation completed, proceeding with task scheduling");

            taskManager.scheduleTasksAsync(queryTextWithReferences, queryTextWithReference -> {
                try {
                    return processQueryTextAsync(
                        modelId,
                        size,
                        tokenLimit,
                        contextFields,
                        searchConfigurations,
                        queryTextWithReference,
                        ignoreFailure
                    );
                } catch (Exception e) {
                    if (ignoreFailure) {
                        log.warn("Query processing failed, returning empty result for: {}", queryTextWithReference, e);
                        return JudgmentDataTransformer.createJudgmentResult(queryTextWithReference, Map.of());
                    } else {
                        log.error("Query processing failed for: {}", queryTextWithReference, e);
                        throw new RuntimeException("Query processing failed: " + queryTextWithReference, e);
                    }
                }
            }, ignoreFailure, ActionListener.wrap(results -> {
                int processedQueries = results.size();
                int successQueries = (int) results.stream().mapToLong(result -> {
                    List<Map<String, String>> ratings = (List<Map<String, String>>) result.get("ratings");
                    return ratings != null && !ratings.isEmpty() ? 1 : 0;
                }).sum();
                int failureQueries = processedQueries - successQueries;

                log.info(
                    "LLM judgment generation completed - Total: {}, Processed: {}, Success: {}, Failure: {}",
                    totalQueries,
                    processedQueries,
                    successQueries,
                    failureQueries
                );
                log.info("Calling final listener.onResponse with {} results", results.size());
                listener.onResponse(results);
            }, error -> {
                log.error("LLM judgment generation failed - Total: {}, All failed", totalQueries, error);
                listener.onFailure(error);
            }));
        }, indexError -> {
            log.warn("Failed to create judgment cache index, proceeding without cache optimization", indexError);

            taskManager.scheduleTasksAsync(queryTextWithReferences, queryTextWithReference -> {
                try {
                    return processQueryTextAsync(
                        modelId,
                        size,
                        tokenLimit,
                        contextFields,
                        searchConfigurations,
                        queryTextWithReference,
                        ignoreFailure
                    );
                } catch (Exception e) {
                    if (ignoreFailure) {
                        log.warn("Query processing failed, returning empty result for: {}", queryTextWithReference, e);
                        return JudgmentDataTransformer.createJudgmentResult(queryTextWithReference, Map.of());
                    } else {
                        log.error("Query processing failed for: {}", queryTextWithReference, e);
                        throw new RuntimeException("Query processing failed: " + queryTextWithReference, e);
                    }
                }
            }, ignoreFailure, ActionListener.wrap(results -> {
                int processedQueries = results.size();
                int successQueries = (int) results.stream().mapToLong(result -> {
                    List<Map<String, String>> ratings = (List<Map<String, String>>) result.get("ratings");
                    return ratings != null && !ratings.isEmpty() ? 1 : 0;
                }).sum();
                int failureQueries = processedQueries - successQueries;

                log.info(
                    "LLM judgment generation completed - Total: {}, Processed: {}, Success: {}, Failure: {}",
                    totalQueries,
                    processedQueries,
                    successQueries,
                    failureQueries
                );
                log.info("Calling final listener.onResponse with {} results", results.size());
                listener.onResponse(results);
            }, error -> {
                log.error("LLM judgment generation failed - Total: {}, All failed", totalQueries, error);
                listener.onFailure(error);
            }));
        });
    }

    private Map<String, Object> processQueryTextAsync(
        String modelId,
        int size,
        int tokenLimit,
        List<String> contextFields,
        List<SearchConfiguration> searchConfigurations,
        String queryTextWithReference,
        boolean ignoreFailure
    ) {
        log.info("Processing query text judgment: {}", queryTextWithReference);

        ConcurrentMap<String, SearchHit> allHits = new ConcurrentHashMap<>();
        ConcurrentMap<String, String> docIdToScore = new ConcurrentHashMap<>();
        String queryText = queryTextWithReference.split(DELIMITER, 2)[0];

        try {
            // Step 1: Execute searches concurrently within this query text task
            processSearchConfigurationsAsync(searchConfigurations, queryText, size, allHits, ignoreFailure);

            // Step 2: Deduplicate from cache
            List<String> docIds = new ArrayList<>(allHits.keySet());
            String index = searchConfigurations.get(0).index();
            List<String> unprocessedDocIds = deduplicateFromCache(
                index,
                queryTextWithReference,
                contextFields,
                docIds,
                docIdToScore,
                ignoreFailure
            );

            // Step 3: Process with LLM if needed
            if (!unprocessedDocIds.isEmpty()) {
                processWithLLM(modelId, queryTextWithReference, tokenLimit, contextFields, unprocessedDocIds, allHits, index, docIdToScore);
            }

            Map<String, Object> result = JudgmentDataTransformer.createJudgmentResult(queryTextWithReference, docIdToScore);
            log.debug("Query processing completed for: {} with {} ratings", queryTextWithReference, docIdToScore.size());
            return result;
        } catch (Exception e) {
            log.warn(
                "Query processing failed for: {} with {} ratings collected. Error: {}",
                queryTextWithReference,
                docIdToScore.size(),
                e.getMessage(),
                e
            );
            // Always return a result with whatever ratings we managed to collect
            return JudgmentDataTransformer.createJudgmentResult(queryTextWithReference, docIdToScore);
        }
    }

    private void processSearchConfigurationsAsync(
        List<SearchConfiguration> searchConfigurations,
        String queryText,
        int size,
        ConcurrentMap<String, SearchHit> allHits,
        boolean ignoreFailure
    ) throws Exception {
        List<CompletableFuture<Void>> searchFutures = searchConfigurations.stream().map(config -> {
            CompletableFuture<SearchResponse> future = new CompletableFuture<>();
            SearchRequest searchRequest = buildSearchRequest(config.index(), config.query(), queryText, config.searchPipeline(), size);
            client.search(searchRequest, ActionListener.wrap(future::complete, future::completeExceptionally));

            return future.thenAccept(response -> {
                if (response.getHits().getTotalHits().value() > 0) {
                    for (SearchHit hit : response.getHits().getHits()) {
                        allHits.put(hit.getId(), hit);
                    }
                    log.debug("Collected {} hits from index: {}", response.getHits().getHits().length, config.index());
                }
            }).exceptionally(e -> {
                log.warn("Search failed for index: {}, continuing with other searches", config.index(), e);
                return null; // Continue processing other searches
            });
        }).toList();

        CompletableFuture.allOf(searchFutures.toArray(new CompletableFuture[0])).join();
        log.info("Search phase completed. Total hits collected: {}", allHits.size());
    }

    private List<String> deduplicateFromCache(
        String index,
        String queryTextWithReference,
        List<String> contextFields,
        List<String> docIds,
        ConcurrentMap<String, String> docIdToScore,
        boolean ignoreFailure
    ) throws Exception {
        List<String> processedDocIds = Collections.synchronizedList(new ArrayList<>());
        AtomicBoolean hasFailure = new AtomicBoolean(false);

        List<CompletableFuture<Void>> cacheFutures = docIds.stream().map(docId -> {
            String compositeKey = combinedIndexAndDocId(index, docId);
            CompletableFuture<SearchResponse> future = new CompletableFuture<>();
            judgmentCacheDao.getJudgmentCache(
                queryTextWithReference,
                compositeKey,
                contextFields,
                ActionListener.wrap(future::complete, future::completeExceptionally)
            );

            return future.thenAccept(response -> {
                if (response.getHits().getTotalHits().value() > 0) {
                    SearchHit hit = response.getHits().getHits()[0];
                    Map<String, Object> source = hit.getSourceAsMap();
                    String rating = (String) source.get("rating");

                    log.debug("Found cached judgment for docId: {}, rating: {}", docId, rating);
                    docIdToScore.put(docId, rating);
                    processedDocIds.add(docId);
                }
            }).exceptionally(e -> {
                log.debug("Cache lookup failed for docId: {} - continuing without cache", docId);
                return null;
            });
        }).toList();

        CompletableFuture.allOf(cacheFutures.toArray(new CompletableFuture[0])).join();

        List<String> unprocessedDocIds = docIds.stream().filter(docId -> !processedDocIds.contains(docId)).collect(Collectors.toList());
        log.info("Cache deduplication completed. Cached: {}, Unprocessed: {}", processedDocIds.size(), unprocessedDocIds.size());
        return unprocessedDocIds;
    }

    private void processWithLLM(
        String modelId,
        String queryTextWithReference,
        int tokenLimit,
        List<String> contextFields,
        List<String> unprocessedDocIds,
        ConcurrentMap<String, SearchHit> allHits,
        String index,
        ConcurrentMap<String, String> docIdToScore
    ) throws Exception {
        Map<String, String> unionHits = new HashMap<>();

        // Prepare union hits for LLM
        for (String docId : unprocessedDocIds) {
            SearchHit hit = allHits.get(docId);
            String compositeKey = combinedIndexAndDocId(index, docId);
            String contextSource = getContextSource(hit, contextFields);
            unionHits.put(compositeKey, contextSource);
        }

        log.info("Processing {} uncached docs with LLM", unionHits.size());

        // Synchronous LLM call
        PlainActionFuture<Map<String, String>> llmFuture = PlainActionFuture.newFuture();
        generateLLMJudgmentForQueryText(modelId, queryTextWithReference, tokenLimit, contextFields, unionHits, new HashMap<>(), llmFuture);

        Map<String, String> llmResults = llmFuture.actionGet();
        docIdToScore.putAll(llmResults);

        log.info("LLM processing completed. Generated {} ratings", llmResults.size());
    }

    private void generateLLMJudgmentForQueryText(
        String modelId,
        String queryTextWithReference,
        int tokenLimit,
        List<String> contextFields,
        Map<String, String> unprocessedUnionHits,
        Map<String, String> docIdToRating,
        ActionListener<Map<String, String>> listener
    ) {
        log.debug("calculating LLM evaluation with modelId: {} and unprocessed unionHits: {}", modelId, unprocessedUnionHits);
        log.debug("processed docIdToRating before llm evaluation: {}", docIdToRating);

        if (unprocessedUnionHits.isEmpty()) {
            log.info("All hits found in cache, returning cached results for query: {}", queryTextWithReference);
            listener.onResponse(docIdToRating);
            return;
        }

        String[] queryTextRefArr = queryTextWithReference.split(DELIMITER);
        String queryText = queryTextRefArr[0];
        String referenceAnswer = queryTextRefArr.length > 1 ? queryTextWithReference.split(DELIMITER, 2)[1] : null;

        ConcurrentMap<String, String> processedRatings = new ConcurrentHashMap<>(docIdToRating);
        ConcurrentMap<Integer, List<Map<String, Object>>> combinedResponses = new ConcurrentHashMap<>();
        AtomicBoolean hasFailure = new AtomicBoolean(false);

        mlAccessor.predict(modelId, tokenLimit, queryText, referenceAnswer, unprocessedUnionHits, new ActionListener<ChunkResult>() {
            @Override
            public void onResponse(ChunkResult chunkResult) {
                try {
                    // Process all chunks, let query level decide on failures

                    Map<Integer, String> succeededChunks = chunkResult.getSucceededChunks();
                    for (Map.Entry<Integer, String> entry : succeededChunks.entrySet()) {
                        Integer chunkIndex = entry.getKey();
                        if (combinedResponses.containsKey(chunkIndex)) {
                            continue;
                        }

                        log.debug("response before sanitization: {}", entry.getValue());
                        String sanitizedResponse = sanitizeLLMResponse(entry.getValue());
                        log.debug("response after sanitization: {}", sanitizedResponse);
                        List<Map<String, Object>> scores = OBJECT_MAPPER.readValue(
                            sanitizedResponse,
                            new TypeReference<List<Map<String, Object>>>() {
                            }
                        );
                        combinedResponses.put(chunkIndex, scores);
                    }

                    logFailedChunks(chunkResult);

                    if (chunkResult.isLastChunk() && !hasFailure.get()) {
                        log.info(
                            "Processing final results for query: {}. Successful chunks: {}, Failed chunks: {}",
                            queryTextWithReference,
                            chunkResult.getSuccessfulChunksCount(),
                            chunkResult.getFailedChunksCount()
                        );

                        for (List<Map<String, Object>> ratings : combinedResponses.values()) {
                            for (Map<String, Object> rating : ratings) {
                                String compositeKey = (String) rating.get("id");
                                Double ratingScore = ((Number) rating.get("rating_score")).doubleValue();
                                String docId = getDocIdFromCompositeKey(compositeKey);
                                processedRatings.put(docId, ratingScore.toString());
                                updateJudgmentCache(compositeKey, queryTextWithReference, contextFields, ratingScore.toString(), modelId);
                            }
                        }

                        listener.onResponse(processedRatings);
                    }
                } catch (Exception e) {
                    handleProcessingError(e, chunkResult.isLastChunk());
                }
            }

            @Override
            public void onFailure(Exception e) {
                handleProcessingError(e, true);
            }

            private void handleProcessingError(Exception e, boolean isLastChunk) {
                if (!hasFailure.getAndSet(true)) {
                    log.error("Failed to process chunk response", e);
                    listener.onFailure(
                        new SearchRelevanceException("Failed to process chunk response", e, RestStatus.INTERNAL_SERVER_ERROR)
                    );
                }
            }
        });
    }

    private void updateJudgmentCache(String compositeKey, String queryText, List<String> contextFields, String rating, String modelId) {
        try {
            JudgmentCache judgmentCache = new JudgmentCache(
                generateUniqueId(queryText, compositeKey, contextFields),
                TimeUtils.getTimestamp(),
                queryText,
                compositeKey,
                contextFields,
                rating,
                modelId
            );
            StepListener<Void> createIndexStep = new StepListener<>();
            judgmentCacheDao.createIndexIfAbsent(createIndexStep);

            createIndexStep.whenComplete(v -> {
                judgmentCacheDao.upsertJudgmentCache(
                    judgmentCache,
                    ActionListener.wrap(
                        response -> log.debug(
                            "Successfully processed judgment cache for queryText: {} and compositeKey: {}, contextFields: {}",
                            queryText,
                            compositeKey,
                            contextFields
                        ),
                        e -> log.warn(
                            "Failed to process judgment cache for queryText: {} and compositeKey: {}, contextFields: {} - continuing without cache",
                            queryText,
                            compositeKey,
                            contextFields
                        )
                    )
                );
            }, e -> {
                log.warn(
                    "Failed to create judgment cache index for queryText: {} and compositeKey: {}, contextFields: {} - continuing without cache",
                    queryText,
                    compositeKey,
                    contextFields
                );
            });
        } catch (Exception e) {
            log.warn("Cache operation failed for queryText: {} - continuing without cache", queryText);
        }
    }

    private void logFailedChunks(ChunkResult chunkResult) {
        chunkResult.getFailedChunks().forEach((index, error) -> log.warn("Chunk {} failed: {}", index, error));
    }

    private String getContextSource(SearchHit hit, List<String> contextFields) {
        try {
            if (contextFields != null && !contextFields.isEmpty()) {
                Map<String, Object> filteredSource = new HashMap<>();
                Map<String, Object> sourceAsMap = hit.getSourceAsMap();

                for (String field : contextFields) {
                    if (sourceAsMap.containsKey(field)) {
                        filteredSource.put(field, sourceAsMap.get(field));
                    }
                }
                return OBJECT_MAPPER.writeValueAsString(filteredSource);
            }
            return hit.getSourceAsString();

        } catch (JsonProcessingException e) {
            log.error("Failed to process context source for hit: {}", hit.getId(), e);
            throw new RuntimeException("Failed to process context source", e);
        }
    }
}
