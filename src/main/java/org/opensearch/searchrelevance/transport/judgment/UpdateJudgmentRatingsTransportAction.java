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

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.opensearch.action.index.IndexResponse;
import org.opensearch.action.search.SearchResponse;
import org.opensearch.action.support.ActionFilters;
import org.opensearch.action.support.HandledTransportAction;
import org.opensearch.common.inject.Inject;
import org.opensearch.core.action.ActionListener;
import org.opensearch.core.rest.RestStatus;
import org.opensearch.search.SearchHit;
import org.opensearch.searchrelevance.dao.JudgmentDao;
import org.opensearch.searchrelevance.exception.SearchRelevanceException;
import org.opensearch.searchrelevance.judgments.JudgmentDataTransformer;
import org.opensearch.searchrelevance.model.AsyncStatus;
import org.opensearch.searchrelevance.model.Judgment;
import org.opensearch.searchrelevance.model.JudgmentType;
import org.opensearch.searchrelevance.utils.TimeUtils;
import org.opensearch.tasks.Task;
import org.opensearch.threadpool.ThreadPool;
import org.opensearch.transport.TransportService;

/**
 * Transport action that updates the judgmentRatings of an existing LLM judgment in place.
 *
 * <p>Used for manual edits: the client fetches the judgment, then submits one or more rating
 * adjustments (e.g. moving docs from failures to ratings, or overwriting already-rated values).
 * This action applies every adjustment to the stored judgment, recomputes the metadata summary
 * counts once, and saves it back to the same document id under a single optimistic-concurrency
 * write. No model call is made.
 */
public class UpdateJudgmentRatingsTransportAction extends HandledTransportAction<UpdateJudgmentRatingsRequest, IndexResponse> {
    private static final Logger LOGGER = LogManager.getLogger(UpdateJudgmentRatingsTransportAction.class);

    private final JudgmentDao judgmentDao;
    private final ThreadPool threadPool;

    /**
     * @param transportService - transport service for action registration
     * @param actionFilters - action filters applied to this action
     * @param judgmentDao - DAO used to load and persist the judgment
     * @param threadPool - thread pool; the blocking load is dispatched to the GENERIC pool
     */
    @Inject
    public UpdateJudgmentRatingsTransportAction(
        TransportService transportService,
        ActionFilters actionFilters,
        JudgmentDao judgmentDao,
        ThreadPool threadPool
    ) {
        super(UpdateJudgmentRatingsAction.NAME, transportService, actionFilters, UpdateJudgmentRatingsRequest::new);
        this.judgmentDao = judgmentDao;
        this.threadPool = threadPool;
    }

    /**
     * Dispatch to a GENERIC thread because the load is a synchronous index read.
     */
    @Override
    protected void doExecute(Task task, UpdateJudgmentRatingsRequest request, ActionListener<IndexResponse> listener) {
        threadPool.executor(ThreadPool.Names.GENERIC).execute(() -> doExecuteInternal(request, listener));
    }

    /**
     * Load the judgment, validate it, replace its ratings, recompute the summary counts, and save
     * it back under optimistic concurrency control. Fails the request with:
     * <ul>
     *   <li>404 if the judgment does not exist,</li>
     *   <li>400 if it is not an LLM_JUDGMENT,</li>
     *   <li>409 if it is currently PROCESSING/RETRYING, or if the doc changed since it was read
     *       (version conflict),</li>
     *   <li>500 on any other error.</li>
     * </ul>
     *
     * @param request - carries the judgment id and the list of rating adjustments
     * @param listener - receives the IndexResponse on success, or the failure above
     */
    @SuppressWarnings("unchecked")
    private void doExecuteInternal(UpdateJudgmentRatingsRequest request, ActionListener<IndexResponse> listener) {
        String judgmentId = request.getJudgmentId();
        try {
            // Load the existing judgment.
            SearchResponse searchResponse = judgmentDao.getJudgmentSync(judgmentId);
            if (searchResponse.getHits().getTotalHits().value() == 0) {
                listener.onFailure(new SearchRelevanceException("Judgment not found: " + judgmentId, RestStatus.NOT_FOUND));
                return;
            }

            SearchHit hit = searchResponse.getHits().getHits()[0];
            Map<String, Object> source = hit.getSourceAsMap();
            // Capture the version info so the write can be guarded against a concurrent edit or an
            // in-flight retry via optimistic concurrency control (see updateJudgment below).
            long seqNo = hit.getSeqNo();
            long primaryTerm = hit.getPrimaryTerm();

            // Only LLM judgments carry the ratings/failures structure we edit here.
            String type = (String) source.get(Judgment.TYPE);
            if (!JudgmentType.LLM_JUDGMENT.name().equals(type)) {
                listener.onFailure(
                    new SearchRelevanceException("Rating update is only supported for LLM_JUDGMENT type", RestStatus.BAD_REQUEST)
                );
                return;
            }

            // Reject edits while the judgment is mid-flight (generating or retrying). Editing now
            // would race the in-flight write and could clobber scored results.
            String status = (String) source.get(Judgment.STATUS);
            if (AsyncStatus.PROCESSING.name().equals(status) || AsyncStatus.RETRYING.name().equals(status)) {
                listener.onFailure(
                    new SearchRelevanceException(
                        "Judgment is currently " + status + "; cannot edit ratings until it completes",
                        RestStatus.CONFLICT
                    )
                );
                return;
            }

            String name = (String) source.get(Judgment.NAME);
            Map<String, Object> metadata = (Map<String, Object>) source.get(Judgment.METADATA);
            if (metadata == null) {
                metadata = new HashMap<>();
            }

            List<Map<String, Object>> currentRatings = (List<Map<String, Object>>) source.get(Judgment.JUDGMENT_RATINGS);
            if (currentRatings == null) {
                listener.onFailure(new SearchRelevanceException("Judgment has no ratings to update", RestStatus.BAD_REQUEST));
                return;
            }

            // Apply every (query, docId) rating adjustment in place. Throws a 404
            // SearchRelevanceException if any adjustment names a query not part of this judgment,
            // in which case nothing is written (the whole request fails).
            List<Map<String, Object>> updatedRatings = currentRatings;
            for (RatingAdjustment adjustment : request.getAdjustments()) {
                updatedRatings = applyRatingAdjustment(
                    updatedRatings,
                    adjustment.getQuery(),
                    adjustment.getDocId(),
                    adjustment.getRating()
                );
            }

            // Recompute the summary counts so metadata stays consistent with the edited ratings. Also
            // clears a stale failure reason once the edit has rated every previously failed doc.
            Map<String, Object> updatedMetadata = new HashMap<>(metadata);
            JudgmentDataTransformer.applyJudgmentSummary(updatedMetadata, updatedRatings);

            Judgment updatedJudgment = new Judgment(
                judgmentId,
                TimeUtils.getTimestamp(),
                name,
                AsyncStatus.COMPLETED,
                JudgmentType.LLM_JUDGMENT,
                updatedMetadata,
                updatedRatings
            );

            // Guard the write with optimistic concurrency: it succeeds only if the doc hasn't
            // changed since we read it. A concurrent edit or retry -> VersionConflictEngineException,
            // surfaced to the client as 409 rather than silently overwriting their change.
            judgmentDao.updateJudgment(updatedJudgment, seqNo, primaryTerm, ActionListener.wrap(response -> {
                LOGGER.info("Updated ratings for judgment: {}", judgmentId);
                listener.onResponse((IndexResponse) response);
            }, listener::onFailure));

        } catch (SearchRelevanceException e) {
            // Already carries the intended status (e.g. 404 query-not-found); surface it as-is.
            listener.onFailure(e);
        } catch (Exception e) {
            LOGGER.error("Failed to update ratings for judgment: {}", judgmentId, e);
            listener.onFailure(new SearchRelevanceException("Failed to update judgment ratings", e, RestStatus.INTERNAL_SERVER_ERROR));
        }
    }

    /**
     * Apply a single (query, docId) rating adjustment to the judgment's ratings. Locates the query
     * entry, sets docId's rating (adding it to "ratings" if absent), and removes docId from that
     * query's "failures" list if present.
     *
     * <p>Mutates {@code currentRatings} in place — the nested rating and failure collections are
     * modified directly, and the returned list is the same instance that was passed in, not a copy.
     * Callers applying several adjustments can therefore chain calls on the same list; the return
     * value exists for readability, not to signal a new object.
     *
     * @param currentRatings the judgment's ratings list, modified in place
     * @return the same {@code currentRatings} instance, now including this adjustment
     * @throws SearchRelevanceException with 404 if the query is not part of the judgment
     */
    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> applyRatingAdjustment(
        List<Map<String, Object>> currentRatings,
        String query,
        String docId,
        String rating
    ) {
        for (Map<String, Object> queryEntry : currentRatings) {
            if (!query.equals(queryEntry.get("query"))) {
                continue;
            }

            // Update (or add) the rating for this docId under the matched query.
            List<Map<String, Object>> ratings = (List<Map<String, Object>>) queryEntry.get("ratings");
            if (ratings == null) {
                ratings = new ArrayList<>();
                queryEntry.put("ratings", ratings);
            }
            boolean found = false;
            for (Map<String, Object> ratingEntry : ratings) {
                if (docId.equals(ratingEntry.get("docId"))) {
                    ratingEntry.put("rating", rating);
                    found = true;
                    break;
                }
            }
            if (!found) {
                Map<String, Object> newRating = new HashMap<>();
                newRating.put("docId", docId);
                newRating.put("rating", rating);
                ratings.add(newRating);
            }

            // The doc is now rated, so drop it from this query's failures list if present.
            List<Map<String, Object>> failures = (List<Map<String, Object>>) queryEntry.get("failures");
            if (failures != null) {
                failures.removeIf(f -> docId.equals(f.get("docId")));
            }

            return currentRatings;
        }

        throw new SearchRelevanceException("Query not found in judgment: " + query, RestStatus.NOT_FOUND);
    }
}
