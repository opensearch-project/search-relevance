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

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
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
import org.opensearch.searchrelevance.dao.QuerySetDao;
import org.opensearch.searchrelevance.dao.SearchConfigurationDao;
import org.opensearch.searchrelevance.exception.SearchRelevanceException;
import org.opensearch.searchrelevance.ml.MLAccessor;
import org.opensearch.searchrelevance.model.JudgmentType;
import org.opensearch.searchrelevance.shared.StashedThreadContext;
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
    private final Client client;

    @Inject
    public LlmJudgmentsProcessor(
        MLAccessor mlAccessor,
        QuerySetDao querySetDao,
        SearchConfigurationDao searchConfigurationDao,
        Client client
    ) {
        this.mlAccessor = mlAccessor;
        this.querySetDao = querySetDao;
        this.searchConfigurationDao = searchConfigurationDao;
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
            Set<Map<String, String>> unionHits = new HashSet<>();

            // Create StepListener for search configurations
            StepListener<Void> searchConfigStep = new StepListener<>();
            processSearchConfigurations(queryText, size, indexAndQueryBodies, unionHits, searchConfigStep);

            // Chain the LLM judgment generation after search configurations complete
            searchConfigStep.whenComplete(ignored -> {
                generateLLMJudgmentForQueryText(modelId, queryText, unionHits, new ActionListener<Map<String, Object>>() {
                    @Override
                    public void onResponse(Map<String, Object> judgment) {
                        synchronized (allJudgments) {
                            @SuppressWarnings("unchecked")
                            Map<String, String> scores = (Map<String, String>) judgment.get("scores");
                            allJudgments.put(queryText, scores);
                        }

                        if (remainingQueries.decrementAndGet() == 0) {
                            listener.onResponse(allJudgments);
                        }
                    }

                    @Override
                    public void onFailure(Exception e) {
                        LOGGER.error("Failed to generate judgment for query: " + queryText, e);
                        listener.onFailure(
                            new SearchRelevanceException("Failed to generate LLM judgments", e, RestStatus.INTERNAL_SERVER_ERROR)
                        );
                    }
                });
            }, error -> {
                LOGGER.error("Failed to process search configurations for query: " + queryText, error);
                listener.onFailure(
                    new SearchRelevanceException("Failed to process search configurations", error, RestStatus.INTERNAL_SERVER_ERROR)
                );
            });
        }
    }

    private void generateLLMJudgmentForQueryText(
        String modelId,
        String queryText,
        Set<Map<String, String>> unionHits,
        ActionListener<Map<String, Object>> listener
    ) {
        LOGGER.debug("calculating LLM evaluation with modelId: {} and unionHits: {}", modelId, unionHits);

        Map<String, String> docIdToScore = new HashMap<>();
        predictWithRetry(queryText, modelId, unionHits, docIdToScore, listener, 0);
    }

    private void predictWithRetry(
        String queryText,
        String modelId,
        Set<Map<String, String>> unionHits,
        Map<String, String> docIdToScore,
        ActionListener<Map<String, Object>> listener,
        int retryCount
    ) {
        mlAccessor.predict(modelId, queryText, null, unionHits.stream().toList(), ActionListener.wrap(response -> {
            if (response == null) {
                handleError(
                    queryText,
                    modelId,
                    unionHits,
                    docIdToScore,
                    listener,
                    retryCount,
                    new SearchRelevanceException("ML prediction returned null output", RestStatus.INTERNAL_SERVER_ERROR)
                );
                return;
            }

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
                }

                // Notify listener of success
                listener.onResponse(Collections.singletonMap("scores", docIdToScore));
            } catch (Exception e) {
                handleError(
                    queryText,
                    modelId,
                    unionHits,
                    docIdToScore,
                    listener,
                    retryCount,
                    new SearchRelevanceException(
                        "Failed to parse ML prediction response: " + e.getMessage(),
                        RestStatus.INTERNAL_SERVER_ERROR
                    )
                );
            }
        },
            e -> handleError(
                queryText,
                modelId,
                unionHits,
                docIdToScore,
                listener,
                retryCount,
                new SearchRelevanceException("ML prediction failed: " + e.getMessage(), RestStatus.INTERNAL_SERVER_ERROR)
            )
        ));
    }

    private void handleError(
        String queryText,
        String modelId,
        Set<Map<String, String>> unionHits,
        Map<String, String> docIdToScore,
        ActionListener<Map<String, Object>> listener,
        int retryCount,
        SearchRelevanceException exception
    ) {
        if (retryCount < MAX_RETRY_NUMBER) {
            // Retry with exponential backoff
            long delay = (long) Math.pow(2, retryCount) * 1000;
            LOGGER.debug("Retrying prediction after {} ms (attempt {}/{})", delay, retryCount + 1, MAX_RETRY_NUMBER);

            try {
                Thread.sleep(delay);
                predictWithRetry(queryText, modelId, unionHits, docIdToScore, listener, retryCount + 1);
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                listener.onFailure(exception);
            }
        } else {
            LOGGER.error("Failed to get prediction after {} attempts", MAX_RETRY_NUMBER);
            listener.onFailure(exception);
        }
    }

    private void processSearchConfigurations(
        String queryText,
        int size,
        Map<String, List<String>> indexAndQueryBodies,
        Set<Map<String, String>> unionHits,
        ActionListener<Void> listener
    ) {
        AtomicInteger pendingQueries = new AtomicInteger(indexAndQueryBodies.size());
        Map<String, List<String>> indexToDocIdMap = new ConcurrentHashMap<>();

        try {
            for (Map.Entry<String, List<String>> entry : indexAndQueryBodies.entrySet()) {
                String configId = entry.getKey();
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

                StashedThreadContext.run(client, () -> {
                    try {
                        client.search(searchRequest, new ActionListener<SearchResponse>() {
                            @Override
                            public void onResponse(SearchResponse response) {
                                try {
                                    if (response.getHits().getTotalHits().value() == 0) {
                                        LOGGER.warn("No hits found for search configuration: {}", configId);
                                        indexToDocIdMap.put(configId, Collections.emptyList());
                                    } else {
                                        SearchHit[] hits = response.getHits().getHits();

                                        List<String> docIds = Arrays.stream(hits).map(SearchHit::getId).collect(Collectors.toList());
                                        indexToDocIdMap.put(configId, docIds);

                                        synchronized (unionHits) {
                                            Arrays.stream(hits).forEach(hit -> {
                                                Map<String, String> hitMap = new HashMap<>();
                                                hitMap.put("_id", hit.getId());
                                                hitMap.put("_index", hit.getIndex());
                                                hitMap.put("_source", hit.getSourceAsString());
                                                unionHits.add(hitMap);
                                            });
                                        }
                                    }
                                } catch (Exception e) {
                                    LOGGER.error("Error processing response for search configuration: " + configId, e);
                                    indexToDocIdMap.put(configId, Collections.singletonList("Error: " + e.getMessage()));
                                } finally {
                                    checkCompletion();
                                }
                            }

                            @Override
                            public void onFailure(Exception e) {
                                LOGGER.error("Search failed for search configuration: " + configId, e);
                                indexToDocIdMap.put(configId, Collections.singletonList("Error: " + e.getMessage()));
                                checkCompletion();
                            }

                            private void checkCompletion() {
                                if (pendingQueries.decrementAndGet() == 0) {
                                    listener.onResponse(null);
                                }
                            }
                        });
                    } catch (Exception e) {
                        LOGGER.error("Failed to execute search for search configuration: " + configId, e);
                        indexToDocIdMap.put(configId, Collections.singletonList("Error: " + e.getMessage()));
                        if (pendingQueries.decrementAndGet() == 0) {
                            listener.onResponse(null);
                        }
                    }
                });
            }
        } catch (Exception e) {
            LOGGER.error("Error initiating searches for query text: " + queryText, e);
            listener.onFailure(new SearchRelevanceException("Failed to initiate searches", e, RestStatus.INTERNAL_SERVER_ERROR));
        }
    }

}
