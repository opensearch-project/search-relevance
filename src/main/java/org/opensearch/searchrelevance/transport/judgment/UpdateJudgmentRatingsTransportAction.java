/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */
package org.opensearch.searchrelevance.transport.judgment;

import static org.opensearch.searchrelevance.common.MLConstants.LLM_JUDGMENT_RATING_TYPE;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

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
import org.opensearch.searchrelevance.model.LLMJudgmentRatingType;
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

            // Check each rating against the scale this judgment was generated on.
            validateRatingsMatchScale(resolveRatingType(metadata), request.getAdjustments());

            List<Map<String, Object>> currentRatings = (List<Map<String, Object>>) source.get(Judgment.JUDGMENT_RATINGS);
            if (currentRatings == null) {
                listener.onFailure(new SearchRelevanceException("Judgment has no ratings to update", RestStatus.BAD_REQUEST));
                return;
            }

            // Apply every (query, docId) rating adjustment in place. Throws a SearchRelevanceException
            // (404 unknown query, 400 unknown docId) if any adjustment does not target an existing
            // entry, in which case nothing is written (the whole request fails).
            List<Map<String, Object>> updatedRatings = applyRatingAdjustments(currentRatings, request.getAdjustments());

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
     * Resolve the rating scale recorded on the judgment. Stored as an enum while in memory or as a
     * String once read back from the index; a judgment that records none predates the field and is
     * treated as the default (SCORE0_1), mirroring {@code LlmJudgmentsProcessor}.
     *
     * @throws SearchRelevanceException with 400 if the recorded rating type is unrecognized
     */
    private LLMJudgmentRatingType resolveRatingType(Map<String, Object> metadata) {
        Object ratingTypeObj = metadata.get(LLM_JUDGMENT_RATING_TYPE);
        if (ratingTypeObj instanceof LLMJudgmentRatingType) {
            return (LLMJudgmentRatingType) ratingTypeObj;
        }
        if (ratingTypeObj instanceof String) {
            try {
                return LLMJudgmentRatingType.valueOf((String) ratingTypeObj);
            } catch (IllegalArgumentException e) {
                throw new SearchRelevanceException(
                    "Judgment records an unrecognized rating type [" + ratingTypeObj + "]; cannot edit ratings",
                    e,
                    RestStatus.BAD_REQUEST
                );
            }
        }
        return LLMJudgmentRatingType.DEFAULT;
    }

    /**
     * Reject ratings that do not fit the judgment's scale:
     * <ul>
     *   <li>SCORE0_1: any finite number in [0, 1]</li>
     *   <li>RELEVANT_IRRELEVANT: exactly 0 (irrelevant) or 1 (relevant), the values the LLM output is stored as</li>
     * </ul>
     *
     * @throws SearchRelevanceException with 400 if any rating is not a number or is off the judgment's scale
     */
    private void validateRatingsMatchScale(LLMJudgmentRatingType ratingType, List<RatingAdjustment> adjustments) {
        for (RatingAdjustment adjustment : adjustments) {
            double value;
            try {
                value = Double.parseDouble(adjustment.getRating());
            } catch (NumberFormatException e) {
                throw invalidRating(adjustment, "must be a number", ratingType);
            }
            // Exhaustive switches: a new rating type fails to compile until it defines its valid values.
            boolean valid = switch (ratingType) {
                case SCORE0_1 -> Double.isFinite(value) && value >= 0.0 && value <= 1.0;
                case RELEVANT_IRRELEVANT -> value == 0.0 || value == 1.0;
            };
            if (!valid) {
                String requirement = switch (ratingType) {
                    case SCORE0_1 -> "must be a number between 0 and 1";
                    case RELEVANT_IRRELEVANT -> "must be 0 or 1";
                };
                throw invalidRating(adjustment, requirement, ratingType);
            }
        }
    }

    private static SearchRelevanceException invalidRating(
        RatingAdjustment adjustment,
        String requirement,
        LLMJudgmentRatingType ratingType
    ) {
        return new SearchRelevanceException(
            "rating '"
                + adjustment.getRating()
                + "' for docId "
                + adjustment.getDocId()
                + " "
                + requirement
                + " for a "
                + ratingType
                + " judgment",
            RestStatus.BAD_REQUEST
        );
    }

    /**
     * Apply every (query, docId) rating adjustment to the judgment's ratings. For each adjustment,
     * overwrites docId's rating if it is already rated, or moves it from the query's "failures" list
     * into "ratings" if it previously failed. Only docs that are already part of the judgment can be
     * edited; new docIds are rejected so manual edits cannot change the set of judged documents.
     *
     * <p>Each query's ratings and failures are indexed once, so the cost is linear in the size of the
     * judgment plus the number of adjustments.
     *
     * <p>Mutates {@code currentRatings} in place — the nested rating and failure collections are
     * modified directly, and the returned list is the same instance that was passed in, not a copy.
     *
     * @param currentRatings the judgment's ratings list, modified in place
     * @param adjustments the rating adjustments to apply
     * @return the same {@code currentRatings} instance, now including every adjustment
     * @throws SearchRelevanceException with 404 if a query is not part of the judgment, or 400 if a
     *         docId is neither rated nor failed under its query
     */
    private List<Map<String, Object>> applyRatingAdjustments(List<Map<String, Object>> currentRatings, List<RatingAdjustment> adjustments) {
        // First entry wins for a query, matching how lookups resolve elsewhere.
        Map<String, Map<String, Object>> queryEntries = new HashMap<>();
        for (Map<String, Object> queryEntry : currentRatings) {
            Object query = queryEntry.get("query");
            if (query != null) {
                queryEntries.putIfAbsent(query.toString(), queryEntry);
            }
        }

        Map<String, QueryIndex> indexes = new HashMap<>();
        for (RatingAdjustment adjustment : adjustments) {
            String query = adjustment.getQuery();
            String docId = adjustment.getDocId();
            Map<String, Object> queryEntry = queryEntries.get(query);
            if (queryEntry == null) {
                throw new SearchRelevanceException("Query not found in judgment: " + query, RestStatus.NOT_FOUND);
            }
            QueryIndex index = indexes.computeIfAbsent(query, q -> new QueryIndex(queryEntry));

            Map<String, Object> ratingEntry = index.ratingsByDocId.get(docId);
            if (ratingEntry != null) {
                ratingEntry.put("rating", adjustment.getRating());
            } else if (index.failedDocIds.contains(docId)) {
                // The doc previously failed; rate it now and drop it from failures below.
                Map<String, Object> newRating = new HashMap<>();
                newRating.put("docId", docId);
                newRating.put("rating", adjustment.getRating());
                index.ratings.add(newRating);
                index.ratingsByDocId.put(docId, newRating);
                index.rescuedDocIds.add(docId);
            } else {
                throw new SearchRelevanceException(
                    "Document " + docId + " is not part of query [" + query + "] in this judgment",
                    RestStatus.BAD_REQUEST
                );
            }
        }

        for (QueryIndex index : indexes.values()) {
            if (!index.rescuedDocIds.isEmpty() && index.failures != null) {
                index.failures.removeIf(f -> f.get("docId") != null && index.rescuedDocIds.contains(f.get("docId").toString()));
            }
        }
        return currentRatings;
    }

    /**
     * Lookup view over one query entry's ratings and failures, built once per query touched by a request.
     */
    private static final class QueryIndex {
        private final List<Map<String, Object>> ratings;
        private final List<Map<String, Object>> failures;
        private final Map<String, Map<String, Object>> ratingsByDocId = new HashMap<>();
        private final Set<String> failedDocIds = new HashSet<>();
        private final Set<String> rescuedDocIds = new HashSet<>();

        @SuppressWarnings("unchecked")
        QueryIndex(Map<String, Object> queryEntry) {
            List<Map<String, Object>> existingRatings = (List<Map<String, Object>>) queryEntry.get("ratings");
            if (existingRatings == null) {
                existingRatings = new ArrayList<>();
                queryEntry.put("ratings", existingRatings);
            }
            this.ratings = existingRatings;
            this.failures = (List<Map<String, Object>>) queryEntry.get("failures");

            for (Map<String, Object> ratingEntry : ratings) {
                Object docId = ratingEntry.get("docId");
                if (docId != null) {
                    ratingsByDocId.putIfAbsent(docId.toString(), ratingEntry);
                }
            }
            if (failures != null) {
                for (Map<String, Object> failure : failures) {
                    Object docId = failure.get("docId");
                    if (docId != null) {
                        failedDocIds.add(docId.toString());
                    }
                }
            }
        }
    }
}
