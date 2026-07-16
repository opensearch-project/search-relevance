/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */
package org.opensearch.searchrelevance.judgments;

import static org.opensearch.searchrelevance.common.MLConstants.LLM_JUDGMENT_RATING_TYPE;
import static org.opensearch.searchrelevance.common.MLConstants.PROMPT_TEMPLATE;
import static org.opensearch.searchrelevance.model.builder.SearchRequestBuilder.buildSearchRequest;
import static org.opensearch.searchrelevance.utils.ParserUtils.combinedIndexAndDocId;
import static org.opensearch.searchrelevance.utils.ParserUtils.getDocIdFromCompositeKey;
import static org.opensearch.searchrelevance.utils.RatingOutputProcessor.convertRatingScore;
import static org.opensearch.searchrelevance.utils.RatingOutputProcessor.sanitizeLLMResponse;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

import org.opensearch.action.search.SearchRequest;
import org.opensearch.action.search.SearchResponse;
import org.opensearch.action.support.PlainActionFuture;
import org.opensearch.common.inject.Inject;
import org.opensearch.core.action.ActionListener;
import org.opensearch.core.rest.RestStatus;
import org.opensearch.search.SearchHit;
import org.opensearch.searchrelevance.dao.JudgmentDao;
import org.opensearch.searchrelevance.dao.QuerySetDao;
import org.opensearch.searchrelevance.dao.SearchConfigurationDao;
import org.opensearch.searchrelevance.exception.SearchRelevanceException;
import org.opensearch.searchrelevance.executors.LlmJudgmentTaskManager;
import org.opensearch.searchrelevance.ml.ChunkResult;
import org.opensearch.searchrelevance.ml.MLAccessor;
import org.opensearch.searchrelevance.model.Judgment;
import org.opensearch.searchrelevance.model.JudgmentType;
import org.opensearch.searchrelevance.model.LLMJudgmentRatingType;
import org.opensearch.searchrelevance.model.QuerySet;
import org.opensearch.searchrelevance.model.QuerySetEntry;
import org.opensearch.searchrelevance.model.SearchConfiguration;
import org.opensearch.searchrelevance.stats.events.EventStatName;
import org.opensearch.searchrelevance.stats.events.EventStatsManager;
import org.opensearch.threadpool.ThreadPool;
import org.opensearch.transport.client.Client;

import lombok.extern.log4j.Log4j2;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.introspect.DefaultAccessorNamingStrategy;
import tools.jackson.databind.json.JsonMapper;

@Log4j2
public class LlmJudgmentsProcessor implements BaseJudgmentsProcessor {
    private static final ObjectMapper OBJECT_MAPPER = JsonMapper.builder()
        .accessorNaming(new DefaultAccessorNamingStrategy.Provider().withFirstCharAcceptance(true, true))
        .configure(DeserializationFeature.FAIL_ON_TRAILING_TOKENS, false)
        .build();
    private final MLAccessor mlAccessor;
    private final QuerySetDao querySetDao;
    private final SearchConfigurationDao searchConfigurationDao;
    private final JudgmentDao judgmentDao;
    private final Client client;
    private final ThreadPool threadPool;
    private final LlmJudgmentTaskManager taskManager;

    @Inject
    public LlmJudgmentsProcessor(
        MLAccessor mlAccessor,
        QuerySetDao querySetDao,
        SearchConfigurationDao searchConfigurationDao,
        JudgmentDao judgmentDao,
        Client client,
        ThreadPool threadPool
    ) {
        this.mlAccessor = mlAccessor;
        this.querySetDao = querySetDao;
        this.searchConfigurationDao = searchConfigurationDao;
        this.judgmentDao = judgmentDao;
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

    /**
     * Retries only the failed documents from an existing judgment.
     * For each query that has failures, fetches the failed docs' content from the index
     * and re-scores only those docs with the LLM. Returns results per query.
     *
     * @param failedQueries map of queryText → list of failed docIds
     * @param metadata the judgment's stored metadata (modelId, prompt, config, etc.)
     * @param listener callback with per-query results (ratings + remaining failures)
     */
    @SuppressWarnings("unchecked")
    public void retryFailedDocs(
        Map<String, List<String>> failedQueries,
        Map<String, Object> metadata,
        ActionListener<List<Map<String, Object>>> listener
    ) {
        threadPool.executor(ThreadPool.Names.GENERIC).execute(() -> {
            try {
                String modelId = (String) metadata.get("modelId");
                if (modelId == null || modelId.isEmpty()) {
                    listener.onFailure(new SearchRelevanceException("modelId is missing from judgment metadata", RestStatus.BAD_REQUEST));
                    return;
                }

                Number tokenLimitNum = (Number) metadata.get("tokenLimit");
                int tokenLimit = tokenLimitNum != null ? tokenLimitNum.intValue() : 4000;
                Number sizeNum = (Number) metadata.get("size");
                int size = sizeNum != null ? sizeNum.intValue() : 10;
                List<String> contextFields = (List<String>) metadata.get("contextFields");
                String promptTemplate = (String) metadata.get(PROMPT_TEMPLATE);
                List<String> searchConfigurationList = (List<String>) metadata.get("searchConfigurationList");

                if (searchConfigurationList == null || searchConfigurationList.isEmpty()) {
                    listener.onFailure(
                        new SearchRelevanceException("searchConfigurationList is missing from judgment metadata", RestStatus.BAD_REQUEST)
                    );
                    return;
                }

                LLMJudgmentRatingType ratingType = null;
                Object ratingTypeObj = metadata.get(LLM_JUDGMENT_RATING_TYPE);
                if (ratingTypeObj instanceof LLMJudgmentRatingType) {
                    ratingType = (LLMJudgmentRatingType) ratingTypeObj;
                } else if (ratingTypeObj instanceof String) {
                    ratingType = LLMJudgmentRatingType.valueOf((String) ratingTypeObj);
                }
                if (ratingType == null) {
                    ratingType = LLMJudgmentRatingType.SCORE0_1;
                }

                List<SearchConfiguration> searchConfigurations = searchConfigurationList.stream()
                    .map(id -> searchConfigurationDao.getSearchConfigurationSync(id))
                    .collect(Collectors.toList());

                if (searchConfigurations.isEmpty()) {
                    listener.onFailure(new SearchRelevanceException("No valid search configurations found", RestStatus.BAD_REQUEST));
                    return;
                }

                String index = searchConfigurations.get(0).index();
                List<Map<String, Object>> results = new ArrayList<>();

                // Process each query that has failures — score only the failed docs
                for (Map.Entry<String, List<String>> entry : failedQueries.entrySet()) {
                    String queryTextWithCustomInput = entry.getKey();
                    List<String> failedDocIds = entry.getValue();

                    // Parse the stored key back into queryText + customFields
                    // Stored format is "queryText#{"key":"value"}" or just "queryText" if no custom fields
                    String queryText;
                    Map<String, String> customFields = Map.of();
                    if (queryTextWithCustomInput.contains("#")) {
                        String[] parts = queryTextWithCustomInput.split("#", 2);
                        queryText = parts[0];
                        try {
                            customFields = OBJECT_MAPPER.readValue(parts[1], new TypeReference<Map<String, String>>() {
                            });
                        } catch (Exception e) {
                            log.warn("Failed to parse custom fields from query key: {}, using empty", queryTextWithCustomInput);
                            customFields = Map.of();
                        }
                    } else {
                        queryText = queryTextWithCustomInput;
                    }

                    log.info("Retrying {} failed docs for query: {}", failedDocIds.size(), queryText);

                    // Fetch the failed docs' content by running the search with the clean queryText
                    ConcurrentMap<String, SearchHit> allHits = new ConcurrentHashMap<>();
                    processSearchConfigurationsAsync(searchConfigurations, queryText, size, allHits, true);

                    // Filter to only the failed docIds
                    ConcurrentMap<String, String> docIdToScore = new ConcurrentHashMap<>();
                    List<String> docsToScore = failedDocIds.stream().filter(allHits::containsKey).collect(Collectors.toList());

                    if (docsToScore.isEmpty()) {
                        log.warn("None of the failed docs found in search results for query: {}", queryText);
                        Map<String, Object> result = new HashMap<>();
                        result.put("query", queryTextWithCustomInput);
                        result.put("ratings", List.of());
                        result.put("failures", failedDocIds.stream().map(id -> Map.of("docId", id)).collect(Collectors.toList()));
                        results.add(result);
                        continue;
                    }

                    // Score only the failed docs with the LLM using the correct queryText and customFields
                    String llmFailureReason = processWithLLM(
                        modelId,
                        queryText,
                        queryTextWithCustomInput,
                        customFields,
                        tokenLimit,
                        contextFields,
                        docsToScore,
                        allHits,
                        index,
                        docIdToScore,
                        promptTemplate,
                        ratingType
                    );

                    // Build result: use queryTextWithCustomInput as the key so it matches the original judgment
                    Map<String, Object> result = buildResultWithFailures(
                        queryTextWithCustomInput,
                        new HashSet<>(failedDocIds),
                        docIdToScore
                    );
                    if (llmFailureReason != null) {
                        result.put(JudgmentDataTransformer.RESULT_FAILURE_REASON, llmFailureReason);
                    }
                    results.add(result);
                }

                listener.onResponse(results);
            } catch (Exception e) {
                log.error("Failed to retry failed docs", e);
                listener.onFailure(new SearchRelevanceException("Failed to retry failed docs", e, RestStatus.INTERNAL_SERVER_ERROR));
            }
        });
    }

    @SuppressWarnings("unchecked")
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
            String promptTemplate = (String) metadata.get(PROMPT_TEMPLATE);
            LLMJudgmentRatingType ratingType = (LLMJudgmentRatingType) metadata.get(LLM_JUDGMENT_RATING_TYPE);
            // Default to SCORE0_1 if ratingType is not provided
            if (ratingType == null) {
                ratingType = LLMJudgmentRatingType.SCORE0_1;
                log.debug("No ratingType provided, defaulting to SCORE0_1");
            }
            // Pass existing judgement IDs for per-query deduplication (queried on demand, not loaded upfront)
            List<String> existingJudgementIds = (List<String>) metadata.get("existingJudgements");

            QuerySet querySet = querySetDao.getQuerySetSync(querySetId);
            List<SearchConfiguration> searchConfigurations = searchConfigurationList.stream()
                .map(id -> searchConfigurationDao.getSearchConfigurationSync(id))
                .collect(Collectors.toList());

            // Record a per-run overview (total/successful/failed counts and the last failure reason)
            // into the judgment metadata before handing the ratings back.
            ActionListener<List<Map<String, Object>>> summaryListener = ActionListener.wrap(results -> {
                metadata.putAll(JudgmentDataTransformer.buildJudgmentSummary(results));
                listener.onResponse(results);
            }, listener::onFailure);

            generateLLMJudgmentsAsync(
                modelId,
                size,
                tokenLimit,
                contextFields,
                querySet,
                searchConfigurations,
                ignoreFailure,
                promptTemplate,
                ratingType,
                existingJudgementIds,
                summaryListener
            );
        } catch (Exception e) {
            log.error("Failed to generate LLM judgments", e);
            listener.onFailure(new SearchRelevanceException("Failed to generate LLM judgments", e, RestStatus.INTERNAL_SERVER_ERROR));
        }
    }

    /**
     * Fetches ratings for a specific queryText from the referenced judgments by querying
     * the system index. Only returns ratings for the matching query — not the entire judgment.
     * This keeps memory usage minimal: only one query's worth of ratings at a time.
     * Package-private for testing.
     */
    @SuppressWarnings("unchecked")
    List<Map<String, String>> fetchRatingsForQuery(List<String> existingJudgementIds, String queryText) {
        List<Map<String, String>> allRatings = new ArrayList<>();

        for (String judgmentId : existingJudgementIds) {
            try {
                SearchResponse response = judgmentDao.getJudgmentSync(judgmentId);
                if (response.getHits().getTotalHits().value() == 0) {
                    log.warn("Referenced judgment not found: {}, skipping", judgmentId);
                    continue;
                }

                Map<String, Object> source = response.getHits().getHits()[0].getSourceAsMap();
                List<Map<String, Object>> judgmentRatings = (List<Map<String, Object>>) source.get(Judgment.JUDGMENT_RATINGS);
                if (judgmentRatings == null) {
                    continue;
                }

                // Find only the entry matching the current queryText
                for (Map<String, Object> queryEntry : judgmentRatings) {
                    if (queryText.equals(queryEntry.get("query"))) {
                        List<Map<String, String>> ratings = (List<Map<String, String>>) queryEntry.get("ratings");
                        if (ratings != null) {
                            allRatings.addAll(ratings);
                        }
                        break;
                    }
                }
            } catch (Exception e) {
                log.warn("Failed to load existing judgment: {}, skipping", judgmentId, e);
            }
        }

        return allRatings;
    }

    /**
     * Searches a small ratings list for a specific docId.
     * Returns the rating value if found, null otherwise.
     * Package-private for testing.
     */
    String findRatingForDoc(List<Map<String, String>> ratings, String docId) {
        for (Map<String, String> rating : ratings) {
            if (docId.equals(rating.get("docId"))) {
                return rating.get("rating");
            }
        }
        return null;
    }

    private void generateLLMJudgmentsAsync(
        String modelId,
        int size,
        int tokenLimit,
        List<String> contextFields,
        QuerySet querySet,
        List<SearchConfiguration> searchConfigurations,
        boolean ignoreFailure,
        String promptTemplate,
        LLMJudgmentRatingType ratingType,
        List<String> existingJudgementIds,
        ActionListener<List<Map<String, Object>>> listener
    ) {
        List<QuerySetEntry> querySetEntries = querySet.querySetQueries();
        int totalQueries = querySetEntries.size();

        log.info("Starting LLM judgment generation for {} total queries", totalQueries);

        taskManager.scheduleTasksAsync(querySetEntries, querySetEntry -> {
            try {
                return processQueryTextAsync(
                    modelId,
                    size,
                    tokenLimit,
                    contextFields,
                    searchConfigurations,
                    querySetEntry,
                    ignoreFailure,
                    promptTemplate,
                    ratingType,
                    existingJudgementIds
                );
            } catch (Exception e) {
                if (ignoreFailure) {
                    log.warn("Query processing failed, returning empty result for: {}", querySetEntry.queryText(), e);
                    return JudgmentDataTransformer.createJudgmentResult(querySetEntry.queryText(), Map.of());
                } else {
                    log.error("Query processing failed for: {}", querySetEntry.queryText(), e);
                    throw new RuntimeException("Query processing failed: " + querySetEntry.queryText(), e);
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
    }

    private Map<String, Object> processQueryTextAsync(
        String modelId,
        int size,
        int tokenLimit,
        List<String> contextFields,
        List<SearchConfiguration> searchConfigurations,
        QuerySetEntry querySetEntry,
        boolean ignoreFailure,
        String promptTemplate,
        LLMJudgmentRatingType ratingType,
        List<String> existingJudgementIds
    ) {
        String queryText = querySetEntry.queryText();
        Map<String, String> customFields = querySetEntry.customFields();
        String queryTextWithCustomInput = buildQueryTextWithCustomInput(queryText, customFields);

        log.info("Processing query text judgment: {}", queryText);

        ConcurrentMap<String, SearchHit> allHits = new ConcurrentHashMap<>();
        ConcurrentMap<String, String> docIdToScore = new ConcurrentHashMap<>();

        try {
            // Step 1: Execute searches concurrently within this query text task
            processSearchConfigurationsAsync(searchConfigurations, queryText, size, allHits, ignoreFailure);

            // Step 1.5: Deduplicate from existing judgements (if provided)
            // For the current queryText, fetch only that query's ratings from referenced judgments
            List<String> docIds = new ArrayList<>(allHits.keySet());
            if (existingJudgementIds != null && !existingJudgementIds.isEmpty()) {
                List<Map<String, String>> existingRatings = fetchRatingsForQuery(existingJudgementIds, queryTextWithCustomInput);
                List<String> remainingDocIds = new ArrayList<>();
                for (String docId : docIds) {
                    String rating = findRatingForDoc(existingRatings, docId);
                    if (rating != null) {
                        docIdToScore.put(docId, rating);
                        log.debug("Reused rating from existing judgment for query: {}, docId: {}", queryText, docId);
                    } else {
                        remainingDocIds.add(docId);
                    }
                }
                log.info(
                    "Reused {} ratings from existing judgments, {} remaining for query: {}",
                    docIds.size() - remainingDocIds.size(),
                    remainingDocIds.size(),
                    queryText
                );
                docIds = remainingDocIds;
            }

            // Step 2: Process with LLM if needed
            String index = searchConfigurations.get(0).index();
            String llmFailureReason = null;
            if (!docIds.isEmpty()) {
                llmFailureReason = processWithLLM(
                    modelId,
                    queryText,
                    queryTextWithCustomInput,
                    customFields,
                    tokenLimit,
                    contextFields,
                    docIds,
                    allHits,
                    index,
                    docIdToScore,
                    promptTemplate,
                    ratingType
                );
            }

            Map<String, Object> result = buildResultWithFailures(queryTextWithCustomInput, allHits.keySet(), docIdToScore);
            // A remote error can come back as a failed chunk rather than a thrown exception; carry its
            // message so the metadata overview can report why the docs went unrated.
            if (llmFailureReason != null) {
                result.put(JudgmentDataTransformer.RESULT_FAILURE_REASON, llmFailureReason);
            }
            return result;
        } catch (Exception e) {
            log.warn(
                "Query processing failed for: {} with {} ratings collected. Error: {}",
                queryTextWithCustomInput,
                docIdToScore.size(),
                e.getMessage(),
                e
            );
            // Return whatever ratings we collected; every doc we sent but did not get a score for is
            // listed under "failures" so it is visible instead of silently dropped. The reason is
            // tagged for the metadata overview but not persisted on the entry.
            Map<String, Object> result = buildResultWithFailures(queryTextWithCustomInput, allHits.keySet(), docIdToScore);
            result.put(JudgmentDataTransformer.RESULT_FAILURE_REASON, e.getMessage());
            return result;
        }
    }

    /**
     * Builds the per-query result and attaches a "failures" list for every sent doc that never got a
     * rating (real scores stay in "ratings"; failed docs are listed, not given a placeholder rating).
     * Package-private for testing.
     */
    static Map<String, Object> buildResultWithFailures(
        String queryTextWithCustomInput,
        Set<String> sentDocIds,
        Map<String, String> docIdToScore
    ) {
        Map<String, Object> result = JudgmentDataTransformer.createJudgmentResult(queryTextWithCustomInput, docIdToScore);
        List<Map<String, String>> failures = JudgmentDataTransformer.buildFailedDocs(sentDocIds, docIdToScore.keySet());
        if (!failures.isEmpty()) {
            result.put("failures", failures);
        }
        return result;
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

    /**
     * @return the reason a chunk failed (when the remote reported an error without throwing), or null when the call succeeded
     */
    private String processWithLLM(
        String modelId,
        String queryText,
        String queryTextWithCustomInput,
        Map<String, String> customFields,
        int tokenLimit,
        List<String> contextFields,
        List<String> unprocessedDocIds,
        ConcurrentMap<String, SearchHit> allHits,
        String index,
        ConcurrentMap<String, String> docIdToScore,
        String promptTemplate,
        LLMJudgmentRatingType ratingType
    ) throws Exception {
        Map<String, String> unionHits = new HashMap<>();

        // Prepare union hits for LLM
        for (String docId : unprocessedDocIds) {
            SearchHit hit = allHits.get(docId);
            String compositeKey = combinedIndexAndDocId(index, docId);
            String contextSource = getContextSource(hit, contextFields);
            unionHits.put(compositeKey, contextSource);
        }

        log.info("Processing {} docs with LLM", unionHits.size());
        log.debug("DEBUG: unionHits keys being sent to LLM: {}", unionHits.keySet());
        log.debug("DEBUG: queryText: {}", queryText);
        log.debug("DEBUG: modelId: {}, tokenLimit: {}, ratingType: {}", modelId, tokenLimit, ratingType);

        // Synchronous LLM call
        PlainActionFuture<Map<String, String>> llmFuture = PlainActionFuture.newFuture();
        AtomicReference<String> failureReason = new AtomicReference<>();
        generateLLMJudgmentForQueryText(
            modelId,
            queryText,
            queryTextWithCustomInput,
            customFields,
            tokenLimit,
            contextFields,
            unionHits,
            new HashMap<>(),
            promptTemplate,
            ratingType,
            failureReason,
            llmFuture
        );

        Map<String, String> llmResults = llmFuture.actionGet();
        docIdToScore.putAll(llmResults);

        log.info("LLM processing completed. Generated {} ratings", llmResults.size());
        return failureReason.get();
    }

    private void generateLLMJudgmentForQueryText(
        String modelId,
        String queryText,
        String queryTextWithCustomInput,
        Map<String, String> customFields,
        int tokenLimit,
        List<String> contextFields,
        Map<String, String> unprocessedUnionHits,
        Map<String, String> docIdToRating,
        String promptTemplate,
        LLMJudgmentRatingType ratingType,
        AtomicReference<String> failureReasonOut,
        ActionListener<Map<String, String>> listener
    ) {
        log.debug("calculating LLM evaluation with modelId: {} and unprocessed unionHits: {}", modelId, unprocessedUnionHits);
        log.debug("processed docIdToRating before llm evaluation: {}", docIdToRating);

        if (unprocessedUnionHits.isEmpty()) {
            log.info("No hits to process, returning existing results for query: {}", queryText);
            listener.onResponse(docIdToRating);
            return;
        }

        // Reference data comes directly from customFields — no parsing needed
        Map<String, String> referenceData = customFields;

        ConcurrentMap<String, String> processedRatings = new ConcurrentHashMap<>(docIdToRating);
        ConcurrentMap<Integer, List<Map<String, Object>>> combinedResponses = new ConcurrentHashMap<>();
        AtomicBoolean hasFailure = new AtomicBoolean(false);

        mlAccessor.predict(
            modelId,
            tokenLimit,
            queryText,
            referenceData,
            unprocessedUnionHits,
            promptTemplate,
            ratingType,
            new ActionListener<ChunkResult>() {
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

                        // Capture the first chunk error as the query's failure reason. The remote can
                        // report an error via a failed chunk without throwing, so this is how the
                        // reason reaches the metadata overview.
                        Map<Integer, String> failedChunks = chunkResult.getFailedChunks();
                        if (!failedChunks.isEmpty()) {
                            failureReasonOut.compareAndSet(null, failedChunks.values().iterator().next());
                        }

                        if (chunkResult.isLastChunk() && !hasFailure.get()) {
                            log.info(
                                "Processing final results for query: {}. Successful chunks: {}, Failed chunks: {}",
                                queryText,
                                chunkResult.getSuccessfulChunksCount(),
                                chunkResult.getFailedChunksCount()
                            );

                            log.debug("DEBUG: combinedResponses size: {}", combinedResponses.size());
                            for (List<Map<String, Object>> ratings : combinedResponses.values()) {
                                log.debug("DEBUG: Processing ratings batch with {} ratings", ratings.size());
                                for (Map<String, Object> rating : ratings) {
                                    String compositeKey = (String) rating.get("id");
                                    Object rawRatingScore = rating.get("rating_score");
                                    log.debug(
                                        "DEBUG: Processing rating - compositeKey: {}, rawRatingScore: {}",
                                        compositeKey,
                                        rawRatingScore
                                    );
                                    Double ratingScore = convertRatingScore(rawRatingScore, ratingType);
                                    String docId = getDocIdFromCompositeKey(compositeKey);
                                    log.debug("DEBUG: Converted rating - docId: {}, ratingScore: {}", docId, ratingScore);
                                    processedRatings.put(docId, ratingScore.toString());
                                }
                            }

                            log.debug("DEBUG: Final processedRatings size: {}, ratings: {}", processedRatings.size(), processedRatings);
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
            }
        );
    }

    /**
     * Builds a query text identifier that includes custom fields when present.
     * This is used for matching against existing judgement ratings.
     * Format: "queryText" when no custom fields, or "queryText#{\"key\":\"value\"}" with custom fields.
     */
    private String buildQueryTextWithCustomInput(String queryText, Map<String, String> customFields) {
        if (customFields == null || customFields.isEmpty()) {
            return queryText;
        }
        try {
            String jsonFields = OBJECT_MAPPER.writeValueAsString(customFields);
            return queryText + "#" + jsonFields;
        } catch (JacksonException e) {
            log.warn("Failed to serialize custom fields, using queryText only", e);
            return queryText;
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

        } catch (JacksonException e) {
            log.error("Failed to process context source for hit: {}", hit.getId(), e);
            throw new RuntimeException("Failed to process context source", e);
        }
    }

}
