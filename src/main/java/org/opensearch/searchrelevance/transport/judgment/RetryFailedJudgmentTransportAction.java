/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */
package org.opensearch.searchrelevance.transport.judgment;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.opensearch.action.index.IndexResponse;
import org.opensearch.action.search.SearchResponse;
import org.opensearch.action.support.ActionFilters;
import org.opensearch.action.support.HandledTransportAction;
import org.opensearch.common.inject.Inject;
import org.opensearch.core.action.ActionListener;
import org.opensearch.core.rest.RestStatus;
import org.opensearch.searchrelevance.dao.JudgmentDao;
import org.opensearch.searchrelevance.exception.SearchRelevanceException;
import org.opensearch.searchrelevance.judgments.JudgmentDataTransformer;
import org.opensearch.searchrelevance.judgments.LlmJudgmentsProcessor;
import org.opensearch.searchrelevance.model.AsyncStatus;
import org.opensearch.searchrelevance.model.Judgment;
import org.opensearch.searchrelevance.model.JudgmentType;
import org.opensearch.searchrelevance.utils.TimeUtils;
import org.opensearch.tasks.Task;
import org.opensearch.threadpool.ThreadPool;
import org.opensearch.transport.TransportService;

/**
 * Transport action that retries failed documents in an existing LLM judgment.
 *
 * When an LLM judgment completes with some documents in the "failures" list (due to
 * throttling, timeouts, etc.), this action re-scores only those failed documents using
 * the judgment's own stored configuration (modelId, prompt, etc.) and merges the new
 * ratings back into the same judgment document.
 */
public class RetryFailedJudgmentTransportAction extends HandledTransportAction<RetryFailedJudgmentRequest, IndexResponse> {
    private static final Logger LOGGER = LogManager.getLogger(RetryFailedJudgmentTransportAction.class);
    private final JudgmentDao judgmentDao;
    private final LlmJudgmentsProcessor llmJudgmentsProcessor;
    private final ThreadPool threadPool;

    @Inject
    public RetryFailedJudgmentTransportAction(
        TransportService transportService,
        ActionFilters actionFilters,
        JudgmentDao judgmentDao,
        LlmJudgmentsProcessor llmJudgmentsProcessor,
        ThreadPool threadPool
    ) {
        super(RetryFailedJudgmentAction.NAME, transportService, actionFilters, RetryFailedJudgmentRequest::new);
        this.judgmentDao = judgmentDao;
        this.llmJudgmentsProcessor = llmJudgmentsProcessor;
        this.threadPool = threadPool;
    }

    /**
     * Dispatches the work to a GENERIC thread to avoid blocking the transport thread,
     * since the retry involves synchronous index reads and async LLM calls.
     */
    @Override
    protected void doExecute(Task task, RetryFailedJudgmentRequest request, ActionListener<IndexResponse> listener) {
        threadPool.executor(ThreadPool.Names.GENERIC).execute(() -> doExecuteInternal(request, listener));
    }

    /**
     * Main retry logic:
     * 1. Load the judgment from the system index
     * 2. Validate it (must be LLM_JUDGMENT, not currently PROCESSING, has failures)
     * 3. Extract the scoring configuration from its metadata
     * 4. Set status to PROCESSING to prevent concurrent retries
     * 5. Re-run the scoring pipeline for the failed docs
     * 6. Merge new ratings back and update the judgment
     */
    @SuppressWarnings("unchecked")
    private void doExecuteInternal(RetryFailedJudgmentRequest request, ActionListener<IndexResponse> listener) {
        String judgmentId = request.getJudgmentId();

        try {
            // Step 1: Load the judgment document from the system index
            SearchResponse searchResponse = judgmentDao.getJudgmentSync(judgmentId);
            if (searchResponse.getHits().getTotalHits().value() == 0) {
                listener.onFailure(new SearchRelevanceException("Judgment not found: " + judgmentId, RestStatus.NOT_FOUND));
                return;
            }

            Map<String, Object> source = searchResponse.getHits().getHits()[0].getSourceAsMap();

            // Step 2a: Validate judgment type — retry only works for LLM judgments
            String type = (String) source.get(Judgment.TYPE);
            if (!JudgmentType.LLM_JUDGMENT.name().equals(type)) {
                listener.onFailure(new SearchRelevanceException("Retry is only supported for LLM_JUDGMENT type", RestStatus.BAD_REQUEST));
                return;
            }

            // Step 2b: Validate status — can't retry if still processing
            String status = (String) source.get(Judgment.STATUS);
            if (AsyncStatus.PROCESSING.name().equals(status)) {
                listener.onFailure(new SearchRelevanceException("Cannot retry a judgment that is still PROCESSING", RestStatus.CONFLICT));
                return;
            }

            // Step 2c: Check that there are actually failures to retry
            List<Map<String, Object>> judgmentRatings = (List<Map<String, Object>>) source.get(Judgment.JUDGMENT_RATINGS);
            if (judgmentRatings == null || judgmentRatings.isEmpty()) {
                listener.onFailure(new SearchRelevanceException("No judgment ratings found", RestStatus.BAD_REQUEST));
                return;
            }

            List<Map<String, Object>> queriesWithFailures = new ArrayList<>();
            for (Map<String, Object> queryEntry : judgmentRatings) {
                Object failures = queryEntry.get("failures");
                if (failures instanceof List && !((List<?>) failures).isEmpty()) {
                    queriesWithFailures.add(queryEntry);
                }
            }

            if (queriesWithFailures.isEmpty()) {
                listener.onFailure(new SearchRelevanceException("No failed documents to retry", RestStatus.BAD_REQUEST));
                return;
            }

            // Step 3: Extract scoring config from the judgment's own metadata
            Map<String, Object> metadata = (Map<String, Object>) source.get(Judgment.METADATA);
            if (metadata == null) {
                listener.onFailure(new SearchRelevanceException("Judgment metadata is missing", RestStatus.BAD_REQUEST));
                return;
            }

            // Step 3b: Collect the exact (query, docId) pairs that need retrying
            Map<String, List<String>> failedQueriesMap = new HashMap<>();
            for (Map<String, Object> queryEntry : queriesWithFailures) {
                String query = (String) queryEntry.get("query");
                List<Map<String, String>> failures = (List<Map<String, String>>) queryEntry.get("failures");
                List<String> failedDocIds = failures.stream().map(f -> f.get("docId")).collect(Collectors.toList());
                failedQueriesMap.put(query, failedDocIds);
            }

            // Step 4: Set status to PROCESSING to prevent concurrent retries
            String name = (String) source.get(Judgment.NAME);
            Judgment processingJudgment = new Judgment(
                judgmentId,
                TimeUtils.getTimestamp(),
                name,
                AsyncStatus.PROCESSING,
                JudgmentType.LLM_JUDGMENT,
                metadata,
                judgmentRatings
            );

            // Save PROCESSING status, return 200 to caller, then start retry in background
            judgmentDao.updateJudgment(processingJudgment, ActionListener.wrap(updateResponse -> {
                listener.onResponse((IndexResponse) updateResponse);
                retryFailedDocsAsync(judgmentId, name, metadata, judgmentRatings, failedQueriesMap);
            }, listener::onFailure));

        } catch (Exception e) {
            LOGGER.error("Failed to retry judgment: {}", judgmentId, e);
            listener.onFailure(new SearchRelevanceException("Failed to retry judgment", e, RestStatus.INTERNAL_SERVER_ERROR));
        }
    }

    /**
     * Calls the processor to re-score only the failed docs, then merges new results back
     * into the original judgment. Successfully scored docs move from "failures" to
     * "ratings"; docs that still fail remain in "failures".
     */
    private void retryFailedDocsAsync(
        String judgmentId,
        String name,
        Map<String, Object> metadata,
        List<Map<String, Object>> judgmentRatings,
        Map<String, List<String>> failedQueriesMap
    ) {
        llmJudgmentsProcessor.retryFailedDocs(failedQueriesMap, metadata, ActionListener.wrap(newResults -> {
            // Merge new ratings into the original judgment
            List<Map<String, Object>> mergedRatings = mergeRetryResults(judgmentRatings, newResults);

            // Recompute metadata counts (totalQueries, successfulQueries, failedQueries)
            Map<String, Object> updatedMetadata = new HashMap<>(metadata);
            Map<String, Object> summary = JudgmentDataTransformer.buildJudgmentSummary(mergedRatings);
            updatedMetadata.putAll(summary);

            // Save the updated judgment back to the index
            Judgment completedJudgment = new Judgment(
                judgmentId,
                TimeUtils.getTimestamp(),
                name,
                AsyncStatus.COMPLETED,
                JudgmentType.LLM_JUDGMENT,
                updatedMetadata,
                mergedRatings
            );

            judgmentDao.updateJudgment(
                completedJudgment,
                ActionListener.wrap(
                    response -> LOGGER.info("Successfully retried judgment: {}", judgmentId),
                    error -> LOGGER.error("Failed to update judgment after retry: {}", judgmentId, error)
                )
            );
        }, error -> {
            // If the entire retry fails, mark the judgment as ERROR but preserve metadata
            LOGGER.error("Retry processing failed for judgment: {}", judgmentId, error);
            Map<String, Object> errorMetadata = new HashMap<>(metadata);
            errorMetadata.put("error", Objects.toString(error.getMessage(), "Unknown error"));

            Judgment errorJudgment = new Judgment(
                judgmentId,
                TimeUtils.getTimestamp(),
                name,
                AsyncStatus.ERROR,
                JudgmentType.LLM_JUDGMENT,
                errorMetadata,
                judgmentRatings
            );
            judgmentDao.updateJudgment(
                errorJudgment,
                ActionListener.wrap(
                    response -> LOGGER.info("Updated judgment {} status to ERROR", judgmentId),
                    e -> LOGGER.error("Failed to update error status for judgment: {}", judgmentId, e)
                )
            );
        }));
    }

    /**
     * Merges retry results into the original judgment ratings.
     * For each query:
     * - Keeps all original successful ratings unchanged
     * - Adds newly scored docs (that were previously in "failures") to "ratings"
     * - Docs that still fail after retry remain in "failures"
     */
    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> mergeRetryResults(List<Map<String, Object>> originalRatings, List<Map<String, Object>> newResults) {
        // Build a lookup from query text to new results for easy matching
        Map<String, Map<String, Object>> newResultsByQuery = new HashMap<>();
        for (Map<String, Object> result : newResults) {
            String query = (String) result.get("query");
            newResultsByQuery.put(query, result);
        }

        List<Map<String, Object>> merged = new ArrayList<>();
        for (Map<String, Object> original : originalRatings) {
            String query = (String) original.get("query");
            Map<String, Object> newResult = newResultsByQuery.get(query);

            if (newResult != null) {
                // Start with original ratings that already succeeded
                List<Map<String, String>> existingRatings = new ArrayList<>(
                    (List<Map<String, String>>) original.getOrDefault("ratings", List.of())
                );
                List<Map<String, String>> newRatings = (List<Map<String, String>>) newResult.getOrDefault("ratings", List.of());

                // Add newly scored docs that weren't already in ratings
                for (Map<String, String> newRating : newRatings) {
                    String docId = newRating.get("docId");
                    boolean alreadyExists = existingRatings.stream().anyMatch(r -> docId.equals(r.get("docId")));
                    if (!alreadyExists) {
                        existingRatings.add(newRating);
                    }
                }

                Map<String, Object> mergedEntry = new HashMap<>();
                mergedEntry.put("query", query);
                mergedEntry.put("ratings", existingRatings);

                // Keep failures from new result (docs that still failed after retry)
                Object newFailures = newResult.get("failures");
                if (newFailures instanceof List && !((List<?>) newFailures).isEmpty()) {
                    mergedEntry.put("failures", newFailures);
                }

                merged.add(mergedEntry);
            } else {
                // No retry result for this query — keep original as-is
                merged.add(original);
            }
        }
        return merged;
    }
}
