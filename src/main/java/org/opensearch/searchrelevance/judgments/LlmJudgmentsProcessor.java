/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */
package org.opensearch.searchrelevance.judgments;

import static org.opensearch.searchrelevance.common.MLConstants.sanitizeLLMResponse;
import static org.opensearch.searchrelevance.common.MetricsConstants.METRICS_INDEX_AND_QUERY_BODY_FIELD_NAME;
import static org.opensearch.searchrelevance.common.MetricsConstants.METRICS_QUERY_TEXT_FIELD_NAME;
import static org.opensearch.searchrelevance.common.PluginConstants.WILDCARD_QUERY_TEXT;
import static org.opensearch.searchrelevance.model.JudgmentCache.SCORE;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.opensearch.action.StepListener;
import org.opensearch.action.search.SearchRequest;
import org.opensearch.action.search.SearchResponse;
import org.opensearch.common.inject.Inject;
import org.opensearch.core.action.ActionListener;
import org.opensearch.core.rest.RestStatus;
import org.opensearch.index.query.QueryBuilders;
import org.opensearch.search.SearchHit;
import org.opensearch.search.builder.SearchSourceBuilder;
import org.opensearch.searchrelevance.dao.JudgmentCacheDao;
import org.opensearch.searchrelevance.dao.QuerySetDao;
import org.opensearch.searchrelevance.dao.SearchConfigurationDao;
import org.opensearch.searchrelevance.exception.SearchRelevanceException;
import org.opensearch.searchrelevance.ml.MLAccessor;
import org.opensearch.searchrelevance.model.JudgmentCache;
import org.opensearch.searchrelevance.model.JudgmentType;
import org.opensearch.searchrelevance.shared.StashedThreadContext;
import org.opensearch.searchrelevance.utils.TimeUtils;
import org.opensearch.transport.client.Client;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

public class LlmJudgmentsProcessor implements BaseJudgmentsProcessor {
    private static final Logger LOGGER = LogManager.getLogger(LlmJudgmentsProcessor.class);
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final int MAX_RETRY_NUMBER = 3;
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
    public void generateJudgmentScore(Map<String, Object> metadata, ActionListener<Map<String, Map<String, String>>> listener) {
        String querySetId = (String) metadata.get("querySetId");
        String modelId = (String) metadata.get("modelId");
        List<String> searchConfigurationList = (List<String>) metadata.get("searchConfigurationList");
        int size = (int) metadata.get("size");

        Map<String, Object> results = new HashMap<>();

        // Step 1: Get QuerySet
        StepListener<Map<String, Object>> getQuerySetStep = new StepListener<>();
        querySetDao.getQuerySetWithStepListener(querySetId, results, getQuerySetStep);

        // Step 2: Get Search Configurations
        StepListener<Map<String, Object>> getSearchConfigsStep = new StepListener<>();
        getQuerySetStep.whenComplete(querySetResults -> {
            searchConfigurationDao.getSearchConfigsWithStepListener(searchConfigurationList, results, getSearchConfigsStep);
        }, error -> {
            LOGGER.error("Failed to get query set", error);
            listener.onFailure(new SearchRelevanceException("Failed to get query set", error, RestStatus.INTERNAL_SERVER_ERROR));
        });

        // Step 3: Generate LLM Judgments
        getSearchConfigsStep.whenComplete(searchConfigResults -> { generateLLMJudgments(modelId, size, results, listener); }, error -> {
            LOGGER.error("Failed to get search configurations", error);
            listener.onFailure(
                new SearchRelevanceException("Failed to get search configurations", error, RestStatus.INTERNAL_SERVER_ERROR)
            );
        });
    }

    private void generateLLMJudgments(
        String modelId,
        int size,
        Map<String, Object> results,
        ActionListener<Map<String, Map<String, String>>> listener
    ) {
        Map<String, List<String>> indexAndQueryBodies = (Map<String, List<String>>) results.get(METRICS_INDEX_AND_QUERY_BODY_FIELD_NAME);
        List<String> queryTexts = (List<String>) results.get(METRICS_QUERY_TEXT_FIELD_NAME);

        Map<String, Map<String, String>> allJudgments = new HashMap<>();
        AtomicInteger remainingQueries = new AtomicInteger(queryTexts.size());

        for (String queryText : queryTexts) {
            processQueryText(modelId, size, indexAndQueryBodies, queryText, new ActionListener<Map<String, String>>() {
                @Override
                public void onResponse(Map<String, String> docIdToScore) {
                    synchronized (allJudgments) {
                        allJudgments.put(queryText, docIdToScore);
                    }
                    if (remainingQueries.decrementAndGet() == 0) {
                        listener.onResponse(allJudgments);
                    }
                }

                @Override
                public void onFailure(Exception e) {
                    listener.onFailure(
                        new SearchRelevanceException("Failed to generate LLM judgments", e, RestStatus.INTERNAL_SERVER_ERROR)
                    );
                }
            });
        }
    }

    private void processQueryText(
        String modelId,
        int size,
        Map<String, List<String>> indexAndQueryBodies,
        String queryText,
        ActionListener<Map<String, String>> listener
    ) {
        Set<Map<String, String>> unionHits = new HashSet<>();
        Map<String, String> docIdToScore = new HashMap<>();

        AtomicInteger pendingSearches = new AtomicInteger(indexAndQueryBodies.size());
        for (Map.Entry<String, List<String>> entry : indexAndQueryBodies.entrySet()) {
            String index = entry.getValue().get(0);
            String queryBody = entry.getValue().get(1).replace(WILDCARD_QUERY_TEXT, queryText);
            String searchPipeline = entry.getValue().get(2);

            SearchRequest searchRequest = new SearchRequest(index);
            SearchSourceBuilder sourceBuilder = new SearchSourceBuilder();
            sourceBuilder.query(QueryBuilders.wrapperQuery(queryBody));
            sourceBuilder.size(size);
            searchRequest.source(sourceBuilder);
            if (searchPipeline != null && !searchPipeline.isEmpty()) {
                searchRequest.pipeline(searchPipeline);
            }

            StashedThreadContext.run(client, () -> client.search(searchRequest, ActionListener.wrap(response -> {
                SearchHit[] hits = response.getHits().getHits();
                List<String> docIds = Arrays.stream(hits).map(SearchHit::getId).collect(Collectors.toList());

                deduplicateFromProcessedDocs(index, queryText, docIds, docIdToScore, ActionListener.wrap(unprocessedDocIds -> {
                    Arrays.stream(hits).filter(hit -> unprocessedDocIds.contains(hit.getId())).forEach(hit -> {
                        Map<String, String> hitMap = new HashMap<>();
                        hitMap.put("_id", hit.getId());
                        hitMap.put("_index", hit.getIndex());
                        hitMap.put("_source", hit.getSourceAsString());
                        unionHits.add(hitMap);
                    });

                    if (pendingSearches.decrementAndGet() == 0) {
                        generateLLMJudgmentForQueryText(index, modelId, queryText, unionHits, docIdToScore, listener);
                    }
                }, e -> {
                    LOGGER.error("Deduplication failed for index: {}", index, e);
                    if (pendingSearches.decrementAndGet() == 0) {
                        generateLLMJudgmentForQueryText(index, modelId, queryText, unionHits, docIdToScore, listener);
                    }
                }));
            }, e -> {
                LOGGER.error("Search failed for index: {}", index, e);
                if (pendingSearches.decrementAndGet() == 0) {
                    generateLLMJudgmentForQueryText(index, modelId, queryText, unionHits, docIdToScore, listener);
                }
            })));
        }
    }

    private void generateLLMJudgmentForQueryText(
        String index,
        String modelId,
        String queryText,
        Set<Map<String, String>> unprocessedUnionHits,
        Map<String, String> docIdToScore,
        ActionListener<Map<String, String>> listener
    ) {
        LOGGER.debug("calculating LLM evaluation with modelId: {} and unprocessed unionHits: {}", modelId, unprocessedUnionHits);
        LOGGER.debug("processed docIdToScore before llm evaluation: {}", docIdToScore);
        predictWithRetry(index, queryText, modelId, unprocessedUnionHits, docIdToScore, listener, 0);
    }

    private void predictWithRetry(
        String index,
        String queryText,
        String modelId,
        Set<Map<String, String>> unionHits,
        Map<String, String> docIdToScore,
        ActionListener<Map<String, String>> listener,
        int retryCount
    ) {
        mlAccessor.predict(modelId, queryText, null, new ArrayList<>(unionHits), new ActionListener<String>() {
            @Override
            public void onResponse(String response) {
                try {
                    String sanitizedResponse = sanitizeLLMResponse(response);
                    List<Map<String, Object>> scores = OBJECT_MAPPER.readValue(
                        sanitizedResponse,
                        new TypeReference<List<Map<String, Object>>>() {
                        }
                    );
                    for (Map<String, Object> score : scores) {
                        String id = (String) score.get("id");
                        Double ratingScore = ((Number) score.get("rating_score")).doubleValue();
                        docIdToScore.put(id, ratingScore.toString());
                        // add to llm judgment cache
                        updateJudgmentCache(index, queryText, id, ratingScore.toString(), modelId);
                    }
                    listener.onResponse(docIdToScore);
                } catch (Exception e) {
                    handlePredictionError(e);
                }
            }

            @Override
            public void onFailure(Exception e) {
                handlePredictionError(e);
            }

            private void handlePredictionError(Exception e) {
                if (retryCount < MAX_RETRY_NUMBER) {
                    long delay = (long) Math.pow(2, retryCount) * 1000;
                    try {
                        Thread.sleep(delay);
                        predictWithRetry(index, queryText, modelId, unionHits, docIdToScore, listener, retryCount + 1);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        listener.onFailure(
                            new SearchRelevanceException("Prediction retry interrupted", ie, RestStatus.INTERNAL_SERVER_ERROR)
                        );
                    }
                } else {
                    listener.onFailure(
                        new SearchRelevanceException(
                            "Failed to get prediction after " + MAX_RETRY_NUMBER + " attempts",
                            e,
                            RestStatus.INTERNAL_SERVER_ERROR
                        )
                    );
                }
            }
        });
    }

    /**
     * Filter out processed queryText+docId pair from judgment
     * @param targetIndex - index to be searched
     * @param queryText - queryText to be deduplicated
     * @param docIds - overall docIds from search
     * @param docIdToScore - add processed docIds and scores to global docIdToScore map
     */
    private void deduplicateFromProcessedDocs(
        String targetIndex,
        String queryText,
        List<String> docIds,
        Map<String, String> docIdToScore,
        ActionListener<List<String>> listener
    ) {
        AtomicInteger pendingChecks = new AtomicInteger(docIds.size());
        Set<String> unprocessedDocIds = Collections.synchronizedSet(new HashSet<>(docIds));

        for (String docId : docIds) {
            judgmentCacheDao.getJudgmentCache(queryText, combinedIndexAndDocId(targetIndex, docId), new ActionListener<SearchResponse>() {
                @Override
                public void onResponse(SearchResponse response) {
                    if (response.getHits().getTotalHits().value() > 0) {
                        SearchHit hit = response.getHits().getHits()[0];
                        Map<String, Object> source = hit.getSourceAsMap();
                        String score = (String) source.get(SCORE);
                        synchronized (docIdToScore) {
                            docIdToScore.put(docId, score);
                        }
                        unprocessedDocIds.remove(docId);
                    }
                    checkCompletion();
                }

                @Override
                public void onFailure(Exception e) {
                    LOGGER.error("Failed to check judgment cache for queryText: {} and docId: {}", queryText, docId, e);
                    checkCompletion();
                }

                private void checkCompletion() {
                    if (pendingChecks.decrementAndGet() == 0) {
                        listener.onResponse(new ArrayList<>(unprocessedDocIds));
                    }
                }
            });
        }
    }

    /**
     * Add new judgment cache entry with llm judgment score
     */
    private void updateJudgmentCache(String targetIndex, String queryText, String docId, String ratingScore, String modelId) {
        JudgmentCache judgmentCache = new JudgmentCache(
            UUID.randomUUID().toString(),
            TimeUtils.getTimestamp(),
            queryText,
            combinedIndexAndDocId(targetIndex, docId),
            ratingScore,
            modelId
        );
        judgmentCacheDao.putJudgementCache(
            judgmentCache,
            ActionListener.wrap(
                response -> LOGGER.debug("Successfully updated judgment cache for queryText: {} and docId: {}", queryText, docId),
                e -> LOGGER.error("Failed to update judgment cache for queryText: {} and docId: {}", queryText, docId, e)
            )
        );
    }

    private String combinedIndexAndDocId(String index, String docId) {
        if (index == null) {
            return docId;
        }
        return String.join(":", index, docId);
    }

}
