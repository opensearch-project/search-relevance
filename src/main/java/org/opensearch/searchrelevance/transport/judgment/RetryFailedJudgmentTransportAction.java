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
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.opensearch.action.index.IndexResponse;
import org.opensearch.action.search.SearchResponse;
import org.opensearch.action.support.ActionFilters;
import org.opensearch.action.support.HandledTransportAction;
import org.opensearch.action.support.PlainActionFuture;
import org.opensearch.common.inject.Inject;
import org.opensearch.core.action.ActionListener;
import org.opensearch.core.rest.RestStatus;
import org.opensearch.index.engine.VersionConflictEngineException;
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

    /**
     * How long a judgment may stay in PROCESSING without its timestamp advancing before we treat
     * it as stale (i.e. the previous retry process likely died) and allow a new retry to take over.
     */
    private static final long STALE_PROCESSING_THRESHOLD_MS = 5 * 60 * 1000L; // 5 minutes

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
     * 2. Validate it (must be LLM_JUDGMENT, not currently PROCESSING/RETRYING, has failures)
     * 3. Extract the scoring configuration from its metadata
     * 4. Set status to RETRYING to prevent concurrent retries
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

            org.opensearch.search.SearchHit hit = searchResponse.getHits().getHits()[0];
            Map<String, Object> source = hit.getSourceAsMap();
            // Capture the version info so we can guard the status transition against concurrent
            // retries via optimistic concurrency control on the update below.
            long seqNo = hit.getSeqNo();
            long primaryTerm = hit.getPrimaryTerm();

            // Step 2a: Validate judgment type — retry only works for LLM judgments
            String type = (String) source.get(Judgment.TYPE);
            if (!JudgmentType.LLM_JUDGMENT.name().equals(type)) {
                listener.onFailure(new SearchRelevanceException("Retry is only supported for LLM_JUDGMENT type", RestStatus.BAD_REQUEST));
                return;
            }

            // Step 2b: Validate status.
            // - PROCESSING means the initial generation is still running. We must NOT retry it: a
            // generation that has not COMPLETED may not have discovered the full set of documents
            // to rate yet, so its "failures" list is not trustworthy.
            // - RETRYING means another retry is already running. A retry refreshes the judgment's
            // timestamp after each query (a heartbeat); if that timestamp has not advanced for
            // longer than STALE_PROCESSING_THRESHOLD_MS, we assume that run died and allow this
            // one to take over. If the heartbeat is still fresh, reject with 409.
            // - COMPLETED / ERROR / TIMEOUT are all retryable.
            String status = (String) source.get(Judgment.STATUS);
            if (AsyncStatus.PROCESSING.name().equals(status)) {
                listener.onFailure(
                    new SearchRelevanceException(
                        "Cannot retry a judgment whose initial generation is still PROCESSING",
                        RestStatus.CONFLICT
                    )
                );
                return;
            }
            if (AsyncStatus.RETRYING.name().equals(status)) {
                String lastUpdated = (String) source.get(Judgment.TIME_STAMP);
                if (!isProcessingStale(lastUpdated)) {
                    listener.onFailure(
                        new SearchRelevanceException("A retry is already in progress for this judgment", RestStatus.CONFLICT)
                    );
                    return;
                }
                LOGGER.warn(
                    "Judgment {} has been RETRYING with no progress since {}; assuming stale and retrying",
                    judgmentId,
                    lastUpdated
                );
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

            // Step 4: Set status to RETRYING to prevent concurrent retries. RETRYING (not PROCESSING)
            // marks this as a retry of an already-completed judgment, so it is distinguishable from
            // an initial generation.
            String name = (String) source.get(Judgment.NAME);
            Judgment retryingJudgment = new Judgment(
                judgmentId,
                TimeUtils.getTimestamp(),
                name,
                AsyncStatus.RETRYING,
                JudgmentType.LLM_JUDGMENT,
                metadata,
                judgmentRatings
            );

            // Save RETRYING status guarded by optimistic concurrency: the write only succeeds if the
            // judgment's seqNo/primaryTerm are unchanged since we read it. If a concurrent retry won
            // the race and already flipped the status, this write fails with a version conflict, which
            // we surface as 409 so only one retry proceeds. On success, return 200 and start the retry.
            judgmentDao.updateJudgment(retryingJudgment, seqNo, primaryTerm, ActionListener.wrap(updateResponse -> {
                listener.onResponse((IndexResponse) updateResponse);
                retryFailedDocsAsync(judgmentId, name, metadata, judgmentRatings, failedQueriesMap);
            }, error -> {
                if (error instanceof VersionConflictEngineException) {
                    listener.onFailure(
                        new SearchRelevanceException("A concurrent retry is already in progress for this judgment", RestStatus.CONFLICT)
                    );
                } else {
                    listener.onFailure(error);
                }
            }));

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
        // Heartbeat: before each query is processed, rewrite the judgment (still RETRYING) with a
        // fresh timestamp. This tells any concurrent retry that this run is alive; if the timestamp
        // stops advancing, isProcessingStale() lets another retry take over.
        //
        // The write is synchronous (we block on it) and any failure is thrown: if we can no longer
        // update the judgment, continuing is unsafe — the final write would likely fail too, and a
        // stale heartbeat could let a concurrent retry start. Throwing aborts the retry, which the
        // processor surfaces as a failure so the judgment is flipped to ERROR.
        Runnable heartbeat = () -> {
            Judgment stillRetrying = new Judgment(
                judgmentId,
                TimeUtils.getTimestamp(),
                name,
                AsyncStatus.RETRYING,
                JudgmentType.LLM_JUDGMENT,
                metadata,
                judgmentRatings
            );
            PlainActionFuture<Object> future = PlainActionFuture.newFuture();
            judgmentDao.updateJudgment(stillRetrying, future);
            future.actionGet(); // blocks; throws if the heartbeat write fails
            LOGGER.debug("Refreshed heartbeat for judgment: {}", judgmentId);
        };

        llmJudgmentsProcessor.retryFailedDocs(failedQueriesMap, metadata, heartbeat, ActionListener.wrap(newResults -> {
            try {
                // Merge new ratings into the original judgment
                List<Map<String, Object>> mergedRatings = mergeRetryResults(judgmentRatings, newResults);

                // Recompute metadata counts (totalQueries, successfulQueries, failedQueries) and clear
                // the recorded failure reason once the retry has rated every previously failed doc.
                Map<String, Object> updatedMetadata = new HashMap<>(metadata);
                JudgmentDataTransformer.applyJudgmentSummary(updatedMetadata, mergedRatings);

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
                        // If the final write fails, flip to ERROR so it doesn't stay stuck in PROCESSING
                        error -> markJudgmentAsError(judgmentId, name, metadata, judgmentRatings, error)
                    )
                );
            } catch (Exception e) {
                // Any failure while merging/building results must flip the judgment to ERROR,
                // otherwise it would stay stuck in PROCESSING forever
                markJudgmentAsError(judgmentId, name, metadata, judgmentRatings, e);
            }
        }, error -> markJudgmentAsError(judgmentId, name, metadata, judgmentRatings, error)));
    }

    /**
     * Marks a judgment as ERROR (preserving its metadata) so it never stays stuck in PROCESSING
     * after a retry failure.
     */
    private void markJudgmentAsError(
        String judgmentId,
        String name,
        Map<String, Object> metadata,
        List<Map<String, Object>> judgmentRatings,
        Exception error
    ) {
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
    }

    /**
     * Decides whether a RETRYING judgment is stale — i.e. its timestamp (refreshed as a heartbeat
     * on each rating update) has not advanced within STALE_PROCESSING_THRESHOLD_MS. A stale judgment
     * is assumed to belong to a died retry process, so a new retry is allowed to take over.
     *
     * <p>If the timestamp is missing or unparseable, we treat it as stale rather than blocking the
     * user forever on a judgment we cannot reason about.
     *
     * @param lastUpdated the judgment's timestamp in {@link TimeUtils} format
     * @return true if the judgment should be considered stale and safe to retry
     */
    private boolean isProcessingStale(String lastUpdated) {
        if (lastUpdated == null || lastUpdated.isBlank()) {
            return true;
        }
        try {
            long lastUpdatedMs = TimeUtils.parseTimestamp(lastUpdated);
            return (TimeUtils.currentTimeMillis() - lastUpdatedMs) > STALE_PROCESSING_THRESHOLD_MS;
        } catch (Exception e) {
            LOGGER.warn("Could not parse judgment timestamp '{}', treating as stale", lastUpdated, e);
            return true;
        }
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

                // Track existing docIds in a set for O(1) membership checks (avoids an O(n^2) scan
                // when merging many newly-scored docs into a query with many existing ratings).
                Set<String> existingDocIds = new HashSet<>();
                for (Map<String, String> rating : existingRatings) {
                    existingDocIds.add(rating.get("docId"));
                }

                // Add newly scored docs that weren't already in ratings
                for (Map<String, String> newRating : newRatings) {
                    if (existingDocIds.add(newRating.get("docId"))) {
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
