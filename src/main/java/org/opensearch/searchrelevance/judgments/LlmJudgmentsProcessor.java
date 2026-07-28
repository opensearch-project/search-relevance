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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

import org.opensearch.action.get.GetResponse;
import org.opensearch.action.get.MultiGetItemResponse;
import org.opensearch.action.get.MultiGetRequest;
import org.opensearch.action.get.MultiGetResponse;
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

    /**
     * Parses the scoring configuration shared by the initial-generation and retry paths from a
     * judgment's metadata map, and resolves the referenced search configurations.
     *
     * <p>Both {@link #generateJudgmentRatingInternal} and {@link #retryFailedDocs} read the same
     * fields from the same metadata shape, so this keeps that parsing (and its defaults) in one
     * place. Required fields (modelId, querySetId, searchConfigurationList) throw a
     * {@link SearchRelevanceException} with {@link RestStatus#BAD_REQUEST} when missing, since a
     * judgment cannot be scored without them.
     */
    static final class ScoringConfig {
        final String modelId;
        final String querySetId;
        final List<SearchConfiguration> searchConfigurations;
        final String index;
        final int size;
        final int tokenLimit;
        final List<String> contextFields;
        final boolean ignoreFailure;
        final String promptTemplate;
        final LLMJudgmentRatingType ratingType;
        final List<String> existingJudgmentIds;

        @SuppressWarnings("unchecked")
        ScoringConfig(Map<String, Object> metadata, SearchConfigurationDao searchConfigurationDao) {
            this.modelId = (String) metadata.get("modelId");
            if (modelId == null || modelId.isEmpty()) {
                throw new SearchRelevanceException("modelId is missing from judgment metadata", RestStatus.BAD_REQUEST);
            }

            this.querySetId = (String) metadata.get("querySetId");
            if (querySetId == null || querySetId.isEmpty()) {
                throw new SearchRelevanceException("querySetId is missing from judgment metadata", RestStatus.BAD_REQUEST);
            }

            List<String> searchConfigurationList = (List<String>) metadata.get("searchConfigurationList");
            if (searchConfigurationList == null || searchConfigurationList.isEmpty()) {
                throw new SearchRelevanceException("searchConfigurationList is missing from judgment metadata", RestStatus.BAD_REQUEST);
            }
            this.searchConfigurations = searchConfigurationList.stream()
                .map(searchConfigurationDao::getSearchConfigurationSync)
                .collect(Collectors.toList());
            if (searchConfigurations.isEmpty()) {
                throw new SearchRelevanceException("No valid search configurations found", RestStatus.BAD_REQUEST);
            }
            this.index = searchConfigurations.get(0).index();

            Number sizeNum = (Number) metadata.get("size");
            this.size = sizeNum != null ? sizeNum.intValue() : 0;
            Number tokenLimitNum = (Number) metadata.get("tokenLimit");
            this.tokenLimit = tokenLimitNum != null ? tokenLimitNum.intValue() : 4000;
            this.contextFields = (List<String>) metadata.get("contextFields");
            this.ignoreFailure = Boolean.TRUE.equals(metadata.get("ignoreFailure"));
            this.promptTemplate = (String) metadata.get(PROMPT_TEMPLATE);
            this.existingJudgmentIds = (List<String>) metadata.get("existingJudgments");

            // ratingType may be stored as an enum (in-memory generation) or a String (loaded back
            // from the index during retry); accept either and fall back to the shared default.
            Object ratingTypeObj = metadata.get(LLM_JUDGMENT_RATING_TYPE);
            LLMJudgmentRatingType parsed = null;
            if (ratingTypeObj instanceof LLMJudgmentRatingType) {
                parsed = (LLMJudgmentRatingType) ratingTypeObj;
            } else if (ratingTypeObj instanceof String) {
                parsed = LLMJudgmentRatingType.valueOf((String) ratingTypeObj);
            }
            this.ratingType = parsed != null ? parsed : LLMJudgmentRatingType.DEFAULT;
        }
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
     * @param onProgress invoked after each query is processed, so the caller can refresh the
     *        judgment's heartbeat timestamp; signals that the retry is still alive. May be null.
     * @param listener callback with per-query results (ratings + remaining failures)
     *
     * <p>This method makes blocking calls (multiGet, sync LLM), so it must be invoked off the
     * transport thread. The caller (RetryFailedJudgmentTransportAction) already dispatches to the
     * GENERIC thread pool before calling this.
     */
    public void retryFailedDocs(
        Map<String, List<String>> failedQueries,
        Map<String, Object> metadata,
        Runnable onProgress,
        ActionListener<List<Map<String, Object>>> listener
    ) {
        try {
            // Parse the shared scoring configuration (modelId, prompt, search configs, etc.)
            // from the judgment's own metadata — same parsing used by the initial generation.
            ScoringConfig config = new ScoringConfig(metadata, searchConfigurationDao);
            String index = config.index;
            List<Map<String, Object>> results = new ArrayList<>();

            // Pass 1: fetch every failed doc up front and detect any that no longer exist. If a
            // failed doc has been deleted from the index since the judgment was created, it can
            // never be re-scored, so we fail the entire retry rather than silently returning a
            // partial result. The reason names the missing docs and is stored on the judgment's
            // failure reason (the caller flips the status to ERROR). Fetching first also avoids
            // burning LLM calls on a retry that is going to fail anyway.
            Map<String, ConcurrentMap<String, SearchHit>> hitsByQuery = new LinkedHashMap<>();
            List<String> missingDocIds = new ArrayList<>();
            for (Map.Entry<String, List<String>> entry : failedQueries.entrySet()) {
                // Heartbeat: refresh the judgment's timestamp so it stays fresh. If the heartbeat
                // write fails it throws, aborting the retry (surfaced below as a failure).
                notifyProgress(onProgress);

                String queryTextWithCustomInput = entry.getKey();
                List<String> failedDocIds = entry.getValue();

                // Fetch the failed docs directly by their IDs (no search — we already know the docIds).
                // Docs that no longer exist are skipped by fetchDocsByIds, so they won't be in allHits.
                ConcurrentMap<String, SearchHit> allHits = fetchDocsByIds(index, failedDocIds);
                hitsByQuery.put(queryTextWithCustomInput, allHits);
                failedDocIds.stream().filter(id -> !allHits.containsKey(id)).forEach(missingDocIds::add);
            }
            if (!missingDocIds.isEmpty()) {
                listener.onFailure(
                    new SearchRelevanceException(
                        "Cannot retry: document(s) no longer exist in index [" + index + "]: " + missingDocIds,
                        RestStatus.NOT_FOUND
                    )
                );
                return;
            }

            // Pass 2: all failed docs still exist — re-score each query's failed docs with the LLM.
            for (Map.Entry<String, List<String>> entry : failedQueries.entrySet()) {
                notifyProgress(onProgress);

                String queryTextWithCustomInput = entry.getKey();
                List<String> failedDocIds = entry.getValue();

                // Reconstruct the queryText and customFields from the stored key. This is
                // self-contained (no QuerySet lookup) and safe: the '#' is only treated as a
                // delimiter when the suffix is valid JSON, so a query like "What is C#?" stays
                // intact.
                QuerySetEntry parsedEntry = QuerySetEntry.fromCombinedKey(queryTextWithCustomInput);
                String queryText = parsedEntry.queryText();
                Map<String, String> customFields = parsedEntry.customFields();

                log.info("Retrying {} failed docs for query: {}", failedDocIds.size(), queryText);

                ConcurrentMap<String, SearchHit> allHits = hitsByQuery.get(queryTextWithCustomInput);
                ConcurrentMap<String, String> docIdToScore = new ConcurrentHashMap<>();

                // Score the failed docs with the LLM (all are known to exist after pass 1).
                String llmFailureReason = processWithLLM(
                    config.modelId,
                    queryText,
                    queryTextWithCustomInput,
                    customFields,
                    config.tokenLimit,
                    config.contextFields,
                    failedDocIds,
                    allHits,
                    index,
                    docIdToScore,
                    config.promptTemplate,
                    config.ratingType
                );

                // Build result: use queryTextWithCustomInput as the key so it matches the original judgment
                Map<String, Object> result = buildResultWithFailures(queryTextWithCustomInput, new HashSet<>(failedDocIds), docIdToScore);
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
    }

    /**
     * Runs the progress callback if one was provided. The callback (a heartbeat write) is expected
     * to throw if it fails; we let that propagate so the caller can abort the retry. If we can no
     * longer write progress, continuing is unsafe — the final result write would likely fail too,
     * and a stale heartbeat could let a concurrent retry take over.
     */
    private void notifyProgress(Runnable onProgress) {
        if (onProgress != null) {
            onProgress.run();
        }
    }

    private void generateJudgmentRatingInternal(Map<String, Object> metadata, ActionListener<List<Map<String, Object>>> listener) {
        try {
            EventStatsManager.increment(EventStatName.LLM_JUDGMENT_RATING_GENERATIONS);

            // Parse the shared scoring configuration (modelId, prompt, search configs, etc.) — same
            // parsing used by the retry path.
            ScoringConfig config = new ScoringConfig(metadata, searchConfigurationDao);
            QuerySet querySet = querySetDao.getQuerySetSync(config.querySetId);

            // Fetch all reusable ratings from the referenced judgments once, up front, so each is
            // read a single time and reused across all queries (rather than re-fetched per query).
            Map<String, Map<String, String>> existingRatingsByQuery = fetchAllRatings(config.existingJudgmentIds);

            // Record a per-run overview (total/successful/failed counts and the last failure reason)
            // into the judgment metadata before handing the ratings back.
            ActionListener<List<Map<String, Object>>> summaryListener = ActionListener.wrap(results -> {
                JudgmentDataTransformer.applyJudgmentSummary(metadata, results);
                listener.onResponse(results);
            }, listener::onFailure);

            generateLLMJudgmentsAsync(
                config.modelId,
                config.size,
                config.tokenLimit,
                config.contextFields,
                querySet,
                config.searchConfigurations,
                config.ignoreFailure,
                config.promptTemplate,
                config.ratingType,
                existingRatingsByQuery,
                summaryListener
            );
        } catch (Exception e) {
            log.error("Failed to generate LLM judgments", e);
            listener.onFailure(new SearchRelevanceException("Failed to generate LLM judgments", e, RestStatus.INTERNAL_SERVER_ERROR));
        }
    }

    /**
     * Fetches all reusable ratings from the referenced judgments up front, so each referenced
     * judgment is read once (not re-fetched per query). Returns a nested map of
     * queryText → (docId → rating) for O(1) lookup during deduplication.
     *
     * <p>When multiple referenced judgments rate the same (queryText, docId), the first judgment in
     * the list wins. Package-private for testing.
     */
    @SuppressWarnings("unchecked")
    Map<String, Map<String, String>> fetchAllRatings(List<String> existingJudgmentIds) {
        Map<String, Map<String, String>> ratingsByQuery = new HashMap<>();
        if (existingJudgmentIds == null || existingJudgmentIds.isEmpty()) {
            return ratingsByQuery;
        }

        for (String judgmentId : existingJudgmentIds) {
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

                for (Map<String, Object> queryEntry : judgmentRatings) {
                    String query = (String) queryEntry.get("query");
                    if (query == null) {
                        continue;
                    }
                    List<Map<String, String>> ratings = (List<Map<String, String>>) queryEntry.get("ratings");
                    if (ratings == null) {
                        continue;
                    }
                    Map<String, String> ratingsByDocId = ratingsByQuery.computeIfAbsent(query, k -> new HashMap<>());
                    for (Map<String, String> rating : ratings) {
                        String docId = rating.get("docId");
                        String ratingValue = rating.get("rating");
                        if (docId != null && ratingValue != null) {
                            ratingsByDocId.putIfAbsent(docId, ratingValue); // first judgment wins
                        }
                    }
                }
            } catch (Exception e) {
                log.warn("Failed to load existing judgment: {}, skipping", judgmentId, e);
            }
        }

        return ratingsByQuery;
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
        Map<String, Map<String, String>> existingRatingsByQuery,
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
                    existingRatingsByQuery
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

            // When ignoreFailure is false, every query must produce a result. The executor drops a
            // query that threw (it collects non-null results only), so fewer results than queries
            // means at least one query failed — fail the whole run instead of silently completing
            // with a partial judgment.
            if (!ignoreFailure && processedQueries < totalQueries) {
                listener.onFailure(
                    new SearchRelevanceException(
                        String.format(
                            Locale.ROOT,
                            "LLM judgment generation failed: %d of %d queries could not be processed",
                            totalQueries - processedQueries,
                            totalQueries
                        ),
                        RestStatus.INTERNAL_SERVER_ERROR
                    )
                );
                return;
            }

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
        Map<String, Map<String, String>> existingRatingsByQuery
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

            // Step 1.5: Deduplicate from existing judgements (if provided). Ratings for all
            // referenced judgments were fetched once up front, so this is just a map lookup.
            List<String> docIds = new ArrayList<>(allHits.keySet());
            Map<String, String> existingRatings = existingRatingsByQuery.get(queryTextWithCustomInput);
            if (existingRatings != null && !existingRatings.isEmpty()) {
                List<String> remainingDocIds = new ArrayList<>();
                for (String docId : docIds) {
                    String rating = existingRatings.get(docId);
                    if (rating != null) {
                        docIdToScore.put(docId, rating);
                    } else {
                        remainingDocIds.add(docId);
                    }
                }
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

    /**
     * Fetches documents directly by their IDs using a multi-get request.
     * Used by the retry flow where the exact failed docIds are already known —
     * avoids running a search (which re-ranks and may not return the failed docs).
     *
     * @param index the index to fetch from
     * @param docIds the document IDs to fetch
     * @return a map of docId to its SearchHit (only docs that exist are included)
     */
    private ConcurrentMap<String, SearchHit> fetchDocsByIds(String index, List<String> docIds) {
        ConcurrentMap<String, SearchHit> hits = new ConcurrentHashMap<>();
        if (docIds.isEmpty()) {
            return hits;
        }

        MultiGetRequest multiGetRequest = new MultiGetRequest();
        for (String docId : docIds) {
            multiGetRequest.add(new MultiGetRequest.Item(index, docId));
        }

        MultiGetResponse response = client.multiGet(multiGetRequest).actionGet();
        for (MultiGetItemResponse itemResponse : response.getResponses()) {
            if (itemResponse.isFailed() || !itemResponse.getResponse().isExists()) {
                // A failed doc no longer exists in the index, so it won't appear in the returned
                // hits. The retry caller detects these missing docs up front and fails the whole
                // retry with a reason naming them (a deleted doc can never be re-scored).
                log.warn("Failed doc [{}] not found in index [{}] during retry", itemResponse.getId(), index);
                continue;
            }
            GetResponse getResponse = itemResponse.getResponse();
            // Build a SearchHit from the get result so it can flow through the existing scoring pipeline
            SearchHit hit = new SearchHit(-1, getResponse.getId(), Map.of(), Map.of());
            hit.sourceRef(getResponse.getSourceAsBytesRef());
            hits.put(getResponse.getId(), hit);
        }

        log.info("Fetched {} docs by ID for retry (requested {})", hits.size(), docIds.size());
        return hits;
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
