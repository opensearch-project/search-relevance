/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */
package org.opensearch.searchrelevance.transport.judgment;

import static org.opensearch.searchrelevance.common.MLConstants.LLM_JUDGMENT_RATING_TYPE;
import static org.opensearch.searchrelevance.common.MLConstants.PROMPT_TEMPLATE;
import static org.opensearch.searchrelevance.common.MetricsConstants.MODEL_ID;
import static org.opensearch.searchrelevance.common.PluginConstants.UBI_EVENTS_INDEX_PARAM;
import static org.opensearch.searchrelevance.ubi.UbiValidator.checkUbiEventsIndexExists;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.opensearch.action.index.IndexResponse;
import org.opensearch.action.support.ActionFilters;
import org.opensearch.action.support.GroupedActionListener;
import org.opensearch.action.support.HandledTransportAction;
import org.opensearch.cluster.service.ClusterService;
import org.opensearch.common.inject.Inject;
import org.opensearch.core.action.ActionListener;
import org.opensearch.core.rest.RestStatus;
import org.opensearch.searchrelevance.dao.JudgmentDao;
import org.opensearch.searchrelevance.dao.QuerySetDao;
import org.opensearch.searchrelevance.dao.SearchConfigurationDao;
import org.opensearch.searchrelevance.exception.SearchRelevanceException;
import org.opensearch.searchrelevance.judgments.BaseJudgmentsProcessor;
import org.opensearch.searchrelevance.judgments.JudgmentsProcessorFactory;
import org.opensearch.searchrelevance.model.AsyncStatus;
import org.opensearch.searchrelevance.model.Judgment;
import org.opensearch.searchrelevance.model.JudgmentType;
import org.opensearch.searchrelevance.model.LLMJudgmentRatingType;
import org.opensearch.searchrelevance.model.SearchConfiguration;
import org.opensearch.searchrelevance.utils.ReferenceValidationUtil;
import org.opensearch.searchrelevance.utils.TimeUtils;
import org.opensearch.tasks.Task;
import org.opensearch.threadpool.ThreadPool;
import org.opensearch.transport.TransportService;

public class PutJudgmentTransportAction extends HandledTransportAction<PutJudgmentRequest, IndexResponse> {
    private final ClusterService clusterService;
    private final JudgmentDao judgmentDao;
    private final QuerySetDao querySetDao;
    private final SearchConfigurationDao searchConfigurationDao;
    private final JudgmentsProcessorFactory judgmentsProcessorFactory;
    private final ThreadPool threadPool;

    private static final Logger LOGGER = LogManager.getLogger(PutJudgmentTransportAction.class);

    /**
     * Maximum number of existing judgments a single request may reference for rating reuse.
     * Each referenced judgment triggers an index lookup per query, so this bounds the work
     * a single request can generate.
     */
    private static final int MAX_EXISTING_JUDGMENTS = 5;

    /**
     * Metadata key under which a judgment records the search configurations it was generated
     * against. Used to recover a referenced judgment's target index for reuse validation.
     */
    private static final String METADATA_SEARCH_CONFIGURATION_LIST = "searchConfigurationList";

    @Inject
    public PutJudgmentTransportAction(
        ClusterService clusterService,
        TransportService transportService,
        ActionFilters actionFilters,
        JudgmentDao judgmentDao,
        QuerySetDao querySetDao,
        SearchConfigurationDao searchConfigurationDao,
        JudgmentsProcessorFactory judgmentsProcessorFactory,
        ThreadPool threadPool
    ) {
        super(PutJudgmentAction.NAME, transportService, actionFilters, PutUbiJudgmentRequest::new);
        this.clusterService = clusterService;
        this.judgmentDao = judgmentDao;
        this.querySetDao = querySetDao;
        this.searchConfigurationDao = searchConfigurationDao;
        this.judgmentsProcessorFactory = judgmentsProcessorFactory;
        this.threadPool = threadPool;
    }

    @Override
    protected void doExecute(Task task, PutJudgmentRequest request, ActionListener<IndexResponse> listener) {
        if (request == null) {
            listener.onFailure(new SearchRelevanceException("Request cannot be null", RestStatus.BAD_REQUEST));
            return;
        }
        try {
            // Validate references for LLM_JUDGMENT type
            if (request.getType() == JudgmentType.LLM_JUDGMENT) {
                PutLlmJudgmentRequest llmRequest = (PutLlmJudgmentRequest) request;
                validateLlmJudgmentReferences(
                    llmRequest,
                    ActionListener.wrap(v -> { createJudgment(request, listener); }, listener::onFailure)
                );
            } else if (request.getType() == JudgmentType.UBI_JUDGMENT) {
                PutUbiJudgmentRequest ubiRequest = (PutUbiJudgmentRequest) request;
                String ubiEventsIndex = ubiRequest.getUbiEventsIndex();
                if (!checkUbiEventsIndexExists(clusterService, ubiEventsIndex)) {
                    listener.onFailure(invalidUbiEventsIndexException(ubiEventsIndex, ubiRequest.isUbiEventsIndexProvided()));
                    return;
                }
                createJudgment(request, listener);
            } else {
                createJudgment(request, listener);
            }
        } catch (Exception e) {
            LOGGER.error("Failed to process judgment request", e);
            listener.onFailure(new SearchRelevanceException("Failed to process judgment request", e, RestStatus.INTERNAL_SERVER_ERROR));
        }
    }

    private static SearchRelevanceException invalidUbiEventsIndexException(String ubiEventsIndex, boolean ubiEventsIndexProvided) {
        String requiredFields = "query_id, action_name, event_attributes.object.object_id";
        String message;
        if (ubiEventsIndexProvided) {
            message = String.format(
                Locale.ROOT,
                "The UBI events index [%s] set by the '%s' parameter does not exist or is missing required UBI event "
                    + "fields (%s). Ingest UBI events data into it, or set the '%s' parameter to an existing UBI events index.",
                ubiEventsIndex,
                UBI_EVENTS_INDEX_PARAM,
                requiredFields,
                UBI_EVENTS_INDEX_PARAM
            );
        } else {
            message = String.format(
                Locale.ROOT,
                "No '%s' parameter was provided and the default UBI events index [%s] does not exist or is missing "
                    + "required UBI event fields (%s). Ingest UBI events data into [%s], or set the '%s' parameter to an "
                    + "existing UBI events index.",
                UBI_EVENTS_INDEX_PARAM,
                ubiEventsIndex,
                requiredFields,
                ubiEventsIndex,
                UBI_EVENTS_INDEX_PARAM
            );
        }
        return new SearchRelevanceException(message, RestStatus.BAD_REQUEST);
    }

    private void createJudgment(PutJudgmentRequest request, ActionListener<IndexResponse> listener) {
        try {
            String id = UUID.randomUUID().toString();
            Judgment initialJudgment = new Judgment(
                id,
                TimeUtils.getTimestamp(),
                request.getName(),
                AsyncStatus.PROCESSING,
                request.getType(),
                buildMetadata(request),
                new ArrayList<>()
            );

            judgmentDao.putJudgement(initialJudgment, ActionListener.wrap(response -> {
                // Return response immediately
                listener.onResponse((IndexResponse) response);

                // Trigger async processing in the background
                triggerAsyncProcessing(id, request, initialJudgment.getMetadata());
            }, e -> {
                LOGGER.error("Failed to create initial judgment", e);
                listener.onFailure(new SearchRelevanceException("Failed to create initial judgment", e, RestStatus.INTERNAL_SERVER_ERROR));
            }));
        } catch (Exception e) {
            LOGGER.error("Failed to create judgment", e);
            listener.onFailure(new SearchRelevanceException("Failed to create judgment", e, RestStatus.INTERNAL_SERVER_ERROR));
        }
    }

    private void validateLlmJudgmentReferences(PutLlmJudgmentRequest request, ActionListener<Void> listener) {
        List<String> existingJudgments = request.getExistingJudgments();

        // Reject requests that reference too many existing judgments, to bound the number of
        // index lookups a single judgment generation can trigger.
        if (existingJudgments != null && existingJudgments.size() > MAX_EXISTING_JUDGMENTS) {
            listener.onFailure(
                new SearchRelevanceException(
                    "Too many existing judgments referenced: "
                        + existingJudgments.size()
                        + ". Maximum allowed is "
                        + MAX_EXISTING_JUDGMENTS,
                    RestStatus.BAD_REQUEST
                )
            );
            return;
        }

        int totalValidations = 1; // QuerySet
        if (request.getSearchConfigurationList() != null && !request.getSearchConfigurationList().isEmpty()) {
            totalValidations += request.getSearchConfigurationList().size();
        }

        // Once every referenced entity is confirmed to exist, validate that any reused existing
        // judgments were generated against the same target index as this request. Reuse merges
        // ratings by (query, docId), and a matched docId skips the LLM call, so reusing a judgment
        // built on a different index would silently suppress correct ratings for this index.
        GroupedActionListener<Void> groupedListener = new GroupedActionListener<>(
            ActionListener.wrap(results -> validateExistingJudgmentIndexes(request, listener), listener::onFailure),
            totalValidations
        );

        // Validate QuerySet
        ReferenceValidationUtil.validateEntityExists(
            request.getQuerySetId(),
            "QuerySet",
            querySetDao::checkQuerySetExists,
            groupedListener
        );

        // Validate Search Configurations
        if (request.getSearchConfigurationList() != null && !request.getSearchConfigurationList().isEmpty()) {
            for (String configId : request.getSearchConfigurationList()) {
                ReferenceValidationUtil.validateEntityExists(
                    configId,
                    "SearchConfiguration",
                    searchConfigurationDao::checkSearchConfigurationExists,
                    groupedListener
                );
            }
        }
    }

    /**
     * Validate that every reused existing judgment targets the same index as this request.
     *
     * <p>Rating reuse merges an existing judgment's ratings into this judgment by (query, docId),
     * and a docId matched from a reused judgment skips the LLM call entirely. If a reused judgment
     * was generated against a different index, its ratings do not describe this index's documents,
     * so reusing it would silently substitute wrong ratings rather than merely adding junk. We
     * therefore require the reused judgment's target index set to be a superset of this request's.
     *
     * <p>Per product decision, an index that cannot be resolved (a referenced judgment with no
     * {@code searchConfigurationList} metadata, or whose search configuration has since been
     * deleted) is rejected with 400 rather than skipped — we cannot prove the reuse is safe.
     *
     * <p>The resolution reads the judgment and its search configuration docs synchronously, so it
     * is dispatched to the GENERIC thread pool to avoid blocking a transport thread.
     */
    private void validateExistingJudgmentIndexes(PutLlmJudgmentRequest request, ActionListener<Void> listener) {
        List<String> existingJudgments = request.getExistingJudgments();
        if (existingJudgments == null || existingJudgments.isEmpty()) {
            listener.onResponse(null);
            return;
        }

        threadPool.executor(ThreadPool.Names.GENERIC).execute(() -> {
            try {
                Set<String> requestIndexes = resolveIndexes(request.getSearchConfigurationList());
                if (requestIndexes.isEmpty()) {
                    listener.onFailure(
                        new SearchRelevanceException(
                            "Cannot determine the target index for this judgment; existing judgments cannot be reused",
                            RestStatus.BAD_REQUEST
                        )
                    );
                    return;
                }

                LLMJudgmentRatingType requestRatingType = request.getLlmJudgmentRatingType() != null
                    ? request.getLlmJudgmentRatingType()
                    : LLMJudgmentRatingType.DEFAULT;

                for (String judgmentId : existingJudgments) {
                    Set<String> referencedIndexes = resolveExistingJudgmentIndexes(judgmentId, requestRatingType);
                    if (!referencedIndexes.containsAll(requestIndexes)) {
                        listener.onFailure(
                            new SearchRelevanceException(
                                "Existing judgment ["
                                    + judgmentId
                                    + "] was generated for a different target index and cannot be reused. "
                                    + "Requested index(es): "
                                    + requestIndexes
                                    + ", existing judgment index(es): "
                                    + referencedIndexes,
                                RestStatus.BAD_REQUEST
                            )
                        );
                        return;
                    }
                }

                listener.onResponse(null);
            } catch (SearchRelevanceException e) {
                // Already carries the intended status (e.g. 400 unresolvable index); surface it as-is.
                listener.onFailure(e);
            } catch (Exception e) {
                LOGGER.error("Failed to validate existing judgment target indexes", e);
                listener.onFailure(
                    new SearchRelevanceException("Failed to validate existing judgment target indexes", e, RestStatus.INTERNAL_SERVER_ERROR)
                );
            }
        });
    }

    /**
     * Resolve the target index set that a referenced existing judgment was generated against, from
     * its {@code searchConfigurationList} metadata, and check that its rating scale is compatible
     * with this request's. Throws a 400 SearchRelevanceException if the judgment does not exist,
     * records no search configurations, or references a search configuration that can no longer be
     * resolved — in every such case reuse cannot be proven safe.
     */
    @SuppressWarnings("unchecked")
    private Set<String> resolveExistingJudgmentIndexes(String judgmentId, LLMJudgmentRatingType requestRatingType) {
        var response = judgmentDao.getJudgmentSync(judgmentId);
        if (response.getHits().getTotalHits().value() == 0) {
            throw new SearchRelevanceException("Existing judgment [" + judgmentId + "] does not exist", RestStatus.BAD_REQUEST);
        }

        Map<String, Object> source = response.getHits().getHits()[0].getSourceAsMap();
        Map<String, Object> metadata = (Map<String, Object>) source.get(Judgment.METADATA);
        List<String> searchConfigurationList = metadata == null ? null : (List<String>) metadata.get(METADATA_SEARCH_CONFIGURATION_LIST);

        if (searchConfigurationList == null || searchConfigurationList.isEmpty()) {
            throw new SearchRelevanceException(
                "Existing judgment [" + judgmentId + "] does not record its target index (no search configurations) and cannot be reused",
                RestStatus.BAD_REQUEST
            );
        }

        validateRatingScaleCompatible(judgmentId, metadata, requestRatingType);

        return resolveIndexes(searchConfigurationList);
    }

    /**
     * Reject reusing a referenced judgment generated on a different rating scale.
     *
     * <p>Reuse merges the referenced judgment's stored ratings in as-is, and a merged docId skips the
     * LLM call, so the two judgments must share a rating scale for the resulting ratings to be
     * comparable. SCORE0_1 is continuous and RELEVANT_IRRELEVANT is binary; mixing them produces a
     * single ratings list on two incompatible scales, so any mismatch is rejected with 400.
     *
     * <p>A referenced judgment that records no rating type predates the field and is treated as the
     * default (SCORE0_1), matching how {@code LlmJudgmentsProcessor} reads it back.
     */
    private void validateRatingScaleCompatible(String judgmentId, Map<String, Object> metadata, LLMJudgmentRatingType requestRatingType) {
        // Stored as an enum when the judgment is still in memory, or as a String once it has been
        // read back from the index; accept either, mirroring LlmJudgmentsProcessor.
        Object ratingTypeObj = metadata.get(LLM_JUDGMENT_RATING_TYPE);
        LLMJudgmentRatingType referencedRatingType = null;
        if (ratingTypeObj instanceof LLMJudgmentRatingType) {
            referencedRatingType = (LLMJudgmentRatingType) ratingTypeObj;
        } else if (ratingTypeObj instanceof String) {
            try {
                referencedRatingType = LLMJudgmentRatingType.valueOf((String) ratingTypeObj);
            } catch (IllegalArgumentException e) {
                throw new SearchRelevanceException(
                    "Existing judgment ["
                        + judgmentId
                        + "] records an unrecognized rating type ["
                        + ratingTypeObj
                        + "] and cannot be reused",
                    e,
                    RestStatus.BAD_REQUEST
                );
            }
        }
        if (referencedRatingType == null) {
            referencedRatingType = LLMJudgmentRatingType.DEFAULT;
        }

        if (referencedRatingType != requestRatingType) {
            throw new SearchRelevanceException(
                "Existing judgment ["
                    + judgmentId
                    + "] uses rating type "
                    + referencedRatingType
                    + " and cannot be reused in a "
                    + requestRatingType
                    + " judgment; the rating scales are not comparable",
                RestStatus.BAD_REQUEST
            );
        }
    }

    /**
     * Resolve the set of target indexes backing the given search configuration ids. A search
     * configuration that can no longer be resolved (e.g. it was deleted) is rejected with 400,
     * since we cannot confirm the index it targeted.
     */
    private Set<String> resolveIndexes(List<String> searchConfigurationList) {
        if (searchConfigurationList == null || searchConfigurationList.isEmpty()) {
            return Collections.emptySet();
        }
        Set<String> indexes = new HashSet<>();
        for (String configId : searchConfigurationList) {
            SearchConfiguration searchConfiguration;
            try {
                searchConfiguration = searchConfigurationDao.getSearchConfigurationSync(configId);
            } catch (Exception e) {
                throw new SearchRelevanceException(
                    "Search configuration [" + configId + "] could not be resolved to a target index",
                    e,
                    RestStatus.BAD_REQUEST
                );
            }
            if (searchConfiguration == null || searchConfiguration.index() == null) {
                throw new SearchRelevanceException(
                    "Search configuration [" + configId + "] could not be resolved to a target index",
                    RestStatus.BAD_REQUEST
                );
            }
            indexes.add(searchConfiguration.index());
        }
        return indexes;
    }

    private Map<String, Object> buildMetadata(PutJudgmentRequest request) {
        Map<String, Object> metadata = new HashMap<>();
        switch (request.getType()) {
            case LLM_JUDGMENT -> {
                PutLlmJudgmentRequest llmRequest = (PutLlmJudgmentRequest) request;
                metadata.put(MODEL_ID, llmRequest.getModelId());
                metadata.put("querySetId", llmRequest.getQuerySetId());
                metadata.put("size", llmRequest.getSize());
                metadata.put("searchConfigurationList", llmRequest.getSearchConfigurationList());
                metadata.put("tokenLimit", llmRequest.getTokenLimit());
                metadata.put("contextFields", llmRequest.getContextFields());
                metadata.put("ignoreFailure", llmRequest.isIgnoreFailure());
                metadata.put(PROMPT_TEMPLATE, llmRequest.getPromptTemplate());
                metadata.put(LLM_JUDGMENT_RATING_TYPE, llmRequest.getLlmJudgmentRatingType());
                if (llmRequest.getExistingJudgments() != null && !llmRequest.getExistingJudgments().isEmpty()) {
                    metadata.put("existingJudgments", llmRequest.getExistingJudgments());
                }
            }
            case UBI_JUDGMENT -> {
                PutUbiJudgmentRequest ubiRequest = (PutUbiJudgmentRequest) request;
                metadata.put("clickModel", ubiRequest.getClickModel());
                metadata.put("maxRank", ubiRequest.getMaxRank());
                metadata.put("startDate", ubiRequest.getStartDate());
                metadata.put("endDate", ubiRequest.getEndDate());
                metadata.put("ubiEventsIndex", ubiRequest.getUbiEventsIndex());
            }
            case IMPORT_JUDGMENT -> {
                PutImportJudgmentRequest importRequest = (PutImportJudgmentRequest) request;
                metadata.put("judgmentRatings", importRequest.getJudgmentRatings());
            }
        }
        return metadata;
    }

    private void triggerAsyncProcessing(String judgmentId, PutJudgmentRequest request, Map<String, Object> metadata) {
        LOGGER.info("Starting async processing for judgment: {}, type: {}, metadata: {}", judgmentId, request.getType(), metadata);
        BaseJudgmentsProcessor processor = judgmentsProcessorFactory.getProcessor(request.getType());

        processor.generateJudgmentRating(metadata, ActionListener.wrap(judgmentRatings -> {
            LOGGER.info(
                "Generated judgment ratings for {}, ratings size: {}",
                judgmentId,
                judgmentRatings != null ? judgmentRatings.size() : 0
            );
            updateFinalJudgment(judgmentId, request, metadata, judgmentRatings);
        }, error -> handleAsyncFailure(judgmentId, request, "Failed to generate judgment ratings", error)));
    }

    private void updateFinalJudgment(
        String judgmentId,
        PutJudgmentRequest request,
        Map<String, Object> metadata,
        List<Map<String, Object>> judgmentScores
    ) {
        Judgment finalJudgment = new Judgment(
            judgmentId,
            TimeUtils.getTimestamp(),
            request.getName(),
            AsyncStatus.COMPLETED,
            request.getType(),
            metadata,
            judgmentScores
        );

        judgmentDao.updateJudgment(
            finalJudgment,
            ActionListener.wrap(
                response -> LOGGER.debug("Updated final judgment: {}", judgmentId),
                error -> handleAsyncFailure(judgmentId, request, "Failed to update final judgment", error)
            )
        );
    }

    private void handleAsyncFailure(String judgmentId, PutJudgmentRequest request, String message, Exception error) {
        LOGGER.error(message + " for judgment: " + judgmentId, error);

        Judgment errorJudgment = new Judgment(
            judgmentId,
            TimeUtils.getTimestamp(),
            request.getName(),
            AsyncStatus.ERROR,
            request.getType(),
            Map.of("error", error.getMessage()),
            new ArrayList<>()
        );

        judgmentDao.updateJudgment(
            errorJudgment,
            ActionListener.wrap(
                response -> LOGGER.info("Updated judgment {} status to ERROR", judgmentId),
                e -> LOGGER.error("Failed to update error status for judgment: " + judgmentId, e)
            )
        );
    }
}
