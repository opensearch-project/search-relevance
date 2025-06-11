/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */
package org.opensearch.searchrelevance.judgments;

import static org.opensearch.searchrelevance.common.MLConstants.sanitizeLLMResponse;
import static org.opensearch.searchrelevance.model.JudgmentCache.CONTEXT_FIELDS_STR;
import static org.opensearch.searchrelevance.model.JudgmentCache.RATING;
import static org.opensearch.searchrelevance.model.QueryWithReference.DELIMITER;
import static org.opensearch.searchrelevance.model.builder.SearchRequestBuilder.buildSearchRequest;
import static org.opensearch.searchrelevance.utils.ParserUtils.combinedIndexAndDocId;
import static org.opensearch.searchrelevance.utils.ParserUtils.generateUniqueId;
import static org.opensearch.searchrelevance.utils.ParserUtils.getDocIdFromCompositeKey;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
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
import org.opensearch.searchrelevance.ml.ChunkResult;
import org.opensearch.searchrelevance.ml.MLAccessor;
import org.opensearch.searchrelevance.model.JudgmentCache;
import org.opensearch.searchrelevance.model.JudgmentType;
import org.opensearch.searchrelevance.model.QuerySet;
import org.opensearch.searchrelevance.model.SearchConfiguration;
import org.opensearch.searchrelevance.stats.events.EventStatName;
import org.opensearch.searchrelevance.stats.events.EventStatsManager;
import org.opensearch.searchrelevance.utils.TimeUtils;
import org.opensearch.transport.client.Client;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

public class LlmJudgmentsProcessor implements BaseJudgmentsProcessor {
    private static final Logger LOGGER = LogManager.getLogger(LlmJudgmentsProcessor.class);
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private final MLAccessor mlAccessor;
    private final QuerySetDao querySetDao;
    private final SearchConfigurationDao searchConfigurationDao;
    private final JudgmentCacheDao judgmentCacheDao;
    private final Client client;

    @Inject
    public LlmJudgmentsProcessor(
        MLAccessor mlAccessor,
        QuerySetDao querySetDao,
        SearchConfigurationDao searchConfigurationDao,
        JudgmentCacheDao judgmentCacheDao,
        Client client
    ) {
        this.mlAccessor = mlAccessor;
        this.querySetDao = querySetDao;
        this.searchConfigurationDao = searchConfigurationDao;
        this.judgmentCacheDao = judgmentCacheDao;
        this.client = client;
    }

    @Override
    public JudgmentType getJudgmentType() {
        return JudgmentType.LLM_JUDGMENT;
    }

    @Override
    public void generateJudgmentRating(Map<String, Object> metadata, ActionListener<List<Map<String, Object>>> listener) {
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

            List<Map<String, Object>> judgments = generateLLMJudgments(
                modelId,
                size,
                tokenLimit,
                contextFields,
                querySet,
                searchConfigurations,
                ignoreFailure
            );

            listener.onResponse(judgments);
        } catch (Exception e) {
            LOGGER.error("Failed to generate LLM judgments", e);
            listener.onFailure(new SearchRelevanceException("Failed to generate LLM judgments", e, RestStatus.INTERNAL_SERVER_ERROR));
        }
    }

    private List<Map<String, Object>> generateLLMJudgments(
        String modelId,
        int size,
        int tokenLimit,
        List<String> contextFields,
        QuerySet querySet,
        List<SearchConfiguration> searchConfigurations,
        boolean ignoreFailure
    ) {
        List<String> queryTextWithReferences = querySet.querySetQueries().stream().map(e -> e.queryText()).collect(Collectors.toList());

        List<Map<String, Object>> allJudgments = new ArrayList<>();

        for (String queryTextWithReference : queryTextWithReferences) {
            try {
                Map<String, String> docIdToScore = processQueryText(
                    modelId,
                    size,
                    tokenLimit,
                    contextFields,
                    searchConfigurations,
                    queryTextWithReference,
                    ignoreFailure
                );

                Map<String, Object> judgmentForQuery = new HashMap<>();
                judgmentForQuery.put("query", queryTextWithReference);
                List<Map<String, String>> docIdRatings = docIdToScore.entrySet()
                    .stream()
                    .map(entry -> Map.of("docId", entry.getKey(), "rating", entry.getValue()))
                    .collect(Collectors.toList());
                judgmentForQuery.put("ratings", docIdRatings);
                allJudgments.add(judgmentForQuery);

                LOGGER.debug("Processed query: {} with {} ratings", queryTextWithReference, docIdRatings.size());
            } catch (Exception e) {
                LOGGER.error("Failed to process query: {}", queryTextWithReference, e);
                if (!ignoreFailure) {
                    throw new SearchRelevanceException("Failed to generate LLM judgments", e, RestStatus.INTERNAL_SERVER_ERROR);
                }
            }
        }

        LOGGER.info("Completed processing {} queries", queryTextWithReferences.size());
        return allJudgments;
    }

    private Map<String, String> processQueryText(
        String modelId,
        int size,
        int tokenLimit,
        List<String> contextFields,
        List<SearchConfiguration> searchConfigurations,
        String queryTextWithReference,
        boolean ignoreFailure
    ) {
        Map<String, String> unionHits = new HashMap<>();
        ConcurrentMap<String, String> docIdToScore = new ConcurrentHashMap<>();
        Map<String, SearchHit> allHits = new HashMap<>();

        String queryText = queryTextWithReference.split(DELIMITER, 2)[0];

        // Collect all hits
        for (SearchConfiguration searchConfiguration : searchConfigurations) {
            String index = searchConfiguration.index();
            String query = searchConfiguration.query();
            String searchPipeline = searchConfiguration.searchPipeline();

            try {
                SearchRequest searchRequest = buildSearchRequest(index, query, queryText, searchPipeline, size);
                SearchResponse response = client.search(searchRequest).actionGet();

                for (SearchHit hit : response.getHits().getHits()) {
                    allHits.put(hit.getId(), hit);
                }
            } catch (Exception e) {
                LOGGER.error("Search failed for index: {}", index, e);
                if (!ignoreFailure) {
                    throw new SearchRelevanceException("Search failed", e, RestStatus.INTERNAL_SERVER_ERROR);
                }
            }
        }

        // Process all collected hits
        try {
            String index = searchConfigurations.get(0).index(); // All configs use same index
            List<String> docIds = new ArrayList<>(allHits.keySet());

            // Deduplicate against cache
            List<String> unprocessedDocIds = deduplicateFromProcessedDocs(
                index,
                queryTextWithReference,
                docIds,
                contextFields,
                docIdToScore
            );

            LOGGER.info("Cached docIds: {}", docIdToScore.keySet());
            LOGGER.info("Unprocessed docIds: {}", unprocessedDocIds);

            // Add unprocessed hits to unionHits
            for (String docId : unprocessedDocIds) {
                SearchHit hit = allHits.get(docId);
                String compositeKey = combinedIndexAndDocId(index, docId);
                String contextSource = getContextSource(hit, contextFields);
                unionHits.put(compositeKey, contextSource);
            }

            LOGGER.info("UnionHits size: {}", unionHits.size());

            // Process unprocessed hits with LLM
            if (!unionHits.isEmpty()) {
                LOGGER.info("Processing {} uncached docs with LLM for query: {}", unionHits.size(), queryText);
                Map<String, String> llmRatings;
                PlainActionFuture<Map<String, String>> llmFuture = PlainActionFuture.newFuture();
                generateLLMJudgmentForQueryText(
                    modelId,
                    queryTextWithReference,
                    tokenLimit,
                    contextFields,
                    unionHits,
                    docIdToScore,
                    ignoreFailure,
                    llmFuture
                );
                llmRatings = llmFuture.actionGet();
                LOGGER.info("LLM returned ratings: {}", llmRatings);
                docIdToScore.putAll(llmRatings);
            }

        } catch (Exception e) {
            LOGGER.error("Failed to process hits for query: {}", queryText, e);
            if (!ignoreFailure) {
                throw new SearchRelevanceException("Failed to process hits", e, RestStatus.INTERNAL_SERVER_ERROR);
            }
        }

        LOGGER.info("Final docIdToScore size: {}, contents: {}", docIdToScore.size(), docIdToScore);
        return docIdToScore;
    }

    /**
     * Generate LLM judgment for each queryText.
     * @param modelId - modelId to be used for the judgment generation
     * @param queryTextWithReference - queryText with its referenceAnswer
     * @param tokenLimit - llm model token limit
     * @param contextFields - filters on specific context fields
     * @param unprocessedUnionHits - hits pending judged
     * @param docIdToRating - map to store the judgment ratings
     * @param ignoreFailure - boolean to determine how to error handling
     */
    private void generateLLMJudgmentForQueryText(
        String modelId,
        String queryTextWithReference,
        int tokenLimit,
        List<String> contextFields,
        Map<String, String> unprocessedUnionHits,
        Map<String, String> docIdToRating,
        boolean ignoreFailure,
        ActionListener<Map<String, String>> listener
    ) {
        LOGGER.debug("calculating LLM evaluation with modelId: {} and unprocessed unionHits: {}", modelId, unprocessedUnionHits);
        LOGGER.debug("processed docIdToRating before llm evaluation: {}", docIdToRating);

        // If there are no unprocessed hits, return the cached results immediately
        if (unprocessedUnionHits.isEmpty()) {
            LOGGER.info("All hits found in cache, returning cached results for query: {}", queryTextWithReference);
            listener.onResponse(docIdToRating);
            return;
        }

        String[] queryTextRefArr = queryTextWithReference.split(DELIMITER);
        String queryText = queryTextRefArr[0];
        String referenceAnswer = queryTextRefArr.length > 1 ? queryTextWithReference.split(DELIMITER, 2)[1] : null;

        ConcurrentMap<String, String> processedRatings = new ConcurrentHashMap<>(docIdToRating);
        ConcurrentMap<Integer, List<Map<String, Object>>> combinedResponses = new ConcurrentHashMap<>();
        AtomicBoolean hasFailure = new AtomicBoolean(false); // Add flag to track if any failure has occurred

        mlAccessor.predict(
            modelId,
            tokenLimit,
            queryText,
            referenceAnswer,
            unprocessedUnionHits,
            ignoreFailure,
            new ActionListener<ChunkResult>() {
                @Override
                public void onResponse(ChunkResult chunkResult) {
                    try {
                        // If not ignoring failures and there are failed chunks, fail immediately
                        if (shouldFailImmediately(ignoreFailure, chunkResult)) {
                            String firstError = chunkResult.getFailedChunks().values().iterator().next();
                            handleProcessingError(new Exception(firstError), true);
                            return;
                        }

                        // Process succeeded chunks
                        Map<Integer, String> succeededChunks = chunkResult.getSucceededChunks();
                        for (Map.Entry<Integer, String> entry : succeededChunks.entrySet()) {
                            Integer chunkIndex = entry.getKey();
                            if (combinedResponses.containsKey(chunkIndex)) {
                                continue;
                            }

                            LOGGER.debug("response before sanitization: {}", entry.getValue());
                            String sanitizedResponse = sanitizeLLMResponse(entry.getValue());
                            LOGGER.debug("response after sanitization: {}", sanitizedResponse);
                            List<Map<String, Object>> scores = OBJECT_MAPPER.readValue(
                                sanitizedResponse,
                                new TypeReference<List<Map<String, Object>>>() {
                                }
                            );
                            combinedResponses.put(chunkIndex, scores);
                        }

                        logFailedChunks(ignoreFailure, chunkResult);

                        // Process final results only if we haven't failed and this is the last chunk
                        if (chunkResult.isLastChunk() && !hasFailure.get()) {
                            LOGGER.info(
                                "Processing final results for query: {}. Successful chunks: {}, Failed chunks: {}",
                                queryTextWithReference,
                                chunkResult.getSuccessfulChunksCount(),
                                chunkResult.getFailedChunksCount()
                            );

                            // Process combined responses
                            for (List<Map<String, Object>> ratings : combinedResponses.values()) {
                                for (Map<String, Object> rating : ratings) {
                                    String compositeKey = (String) rating.get("id");
                                    Double ratingScore = ((Number) rating.get("rating_score")).doubleValue();
                                    String docId = getDocIdFromCompositeKey(compositeKey);
                                    processedRatings.put(docId, ratingScore.toString());
                                    updateJudgmentCache(
                                        compositeKey,
                                        queryTextWithReference,
                                        contextFields,
                                        ratingScore.toString(),
                                        modelId
                                    );
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
                    if (!ignoreFailure || isLastChunk) {
                        if (!hasFailure.getAndSet(true)) {  // Only fail once
                            LOGGER.error("Failed to process chunk response", e);
                            listener.onFailure(
                                new SearchRelevanceException("Failed to process chunk response", e, RestStatus.INTERNAL_SERVER_ERROR)
                            );
                        }
                    } else {
                        LOGGER.warn("Error processing chunk, continuing due to ignoreFailure=true", e);
                    }
                }
            }
        );
    }

    /**
     * Filter out processed queryText+docId+contextFields tuple from judgment
     * @param targetIndex - index to be searched
     * @param queryTextWithReference - queryTextWithReference to be deduplicated
     * @param contextFields - contextFields to be deduplicated
     * @param docIds - overall docIds from search
     * @param docIdToRating - add processed docIds and ratings to global docIdToRating map
     */
    private List<String> deduplicateFromProcessedDocs(
        String targetIndex,
        String queryTextWithReference,
        List<String> docIds,
        List<String> contextFields,
        ConcurrentMap<String, String> docIdToRating
    ) {
        Set<String> unprocessedDocIds = new HashSet<>(docIds);

        for (String docId : docIds) {
            String compositeKey = combinedIndexAndDocId(targetIndex, docId);

            try {
                PlainActionFuture<SearchResponse> future = PlainActionFuture.newFuture();
                judgmentCacheDao.getJudgmentCache(queryTextWithReference, compositeKey, contextFields, future);
                SearchResponse response = future.actionGet();

                if (response.getHits().getTotalHits().value() > 0) {
                    SearchHit hit = response.getHits().getHits()[0];
                    Map<String, Object> source = hit.getSourceAsMap();
                    String rating = (String) source.get(RATING);
                    String storedContextFields = (String) source.get(CONTEXT_FIELDS_STR);

                    LOGGER.info(
                        "Found existing judgment for docId: {}, rating: {}, storedContextFields: {}",
                        docId,
                        rating,
                        storedContextFields
                    );

                    docIdToRating.put(docId, rating);
                    unprocessedDocIds.remove(docId);
                }
            } catch (Exception e) {
                LOGGER.error(
                    "Failed to check judgment cache for queryTextWithReference: {} and docId: {}",
                    queryTextWithReference,
                    docId,
                    e
                );
            }
        }

        return new ArrayList<>(unprocessedDocIds);
    }

    /**
     * Add new judgment cache entry with llm judgment rating
     */
    private void updateJudgmentCache(String compositeKey, String queryText, List<String> contextFields, String rating, String modelId) {
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
                    response -> LOGGER.debug(
                        "Successfully processed judgment cache for queryText: {} and compositeKey: {}, contextFields: {}",
                        queryText,
                        compositeKey,
                        contextFields
                    ),
                    e -> LOGGER.error(
                        "Failed to process judgment cache for queryText: {} and compositeKey: {}, contextFields: {}",
                        queryText,
                        compositeKey,
                        contextFields,
                        e
                    )
                )
            );
        }, e -> {
            LOGGER.error(
                "Failed to create judgment cache index for queryText: {} and compositeKey: {}, contextFields: {}",
                queryText,
                compositeKey,
                contextFields,
                e
            );
        });
    }

    private boolean shouldFailImmediately(boolean ignoreFailure, ChunkResult chunkResult) {
        return !ignoreFailure && !chunkResult.getFailedChunks().isEmpty();
    }

    private void logFailedChunks(boolean ignoreFailure, ChunkResult chunkResult) {
        if (ignoreFailure) {
            chunkResult.getFailedChunks().forEach((index, error) -> LOGGER.warn("Chunk {} failed: {}", index, error));
        }
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
            LOGGER.error("Failed to process context source for hit: {}", hit.getId(), e);
            throw new RuntimeException("Failed to process context source", e);
        }
    }

}
