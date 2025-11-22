/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */
package org.opensearch.searchrelevance.transport.judgment;

import static org.opensearch.searchrelevance.common.MLConstants.CONNECTOR_TYPE;
import static org.opensearch.searchrelevance.common.MLConstants.DEFAULT_PROMPT_TEMPLATE;
import static org.opensearch.searchrelevance.common.MLConstants.LLM_JUDGMENT_RATING_TYPE;
import static org.opensearch.searchrelevance.common.MLConstants.OVERWRITE_CACHE;
import static org.opensearch.searchrelevance.common.MLConstants.PROMPT_TEMPLATE;
import static org.opensearch.searchrelevance.common.MLConstants.RATE_LIMIT;
import static org.opensearch.searchrelevance.ubi.UbiValidator.checkUbiIndicesExist;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.opensearch.action.index.IndexResponse;
import org.opensearch.action.support.ActionFilters;
import org.opensearch.action.support.HandledTransportAction;
import org.opensearch.cluster.service.ClusterService;
import org.opensearch.common.inject.Inject;
import org.opensearch.core.action.ActionListener;
import org.opensearch.core.rest.RestStatus;
import org.opensearch.searchrelevance.dao.JudgmentDao;
import org.opensearch.searchrelevance.exception.SearchRelevanceException;
import org.opensearch.searchrelevance.judgments.BaseJudgmentsProcessor;
import org.opensearch.searchrelevance.judgments.JudgmentsProcessorFactory;
import org.opensearch.searchrelevance.ml.connector.ConnectorType;
import org.opensearch.searchrelevance.model.AsyncStatus;
import org.opensearch.searchrelevance.model.Judgment;
import org.opensearch.searchrelevance.model.LLMJudgmentRatingType;
import org.opensearch.searchrelevance.utils.TimeUtils;
import org.opensearch.tasks.Task;
import org.opensearch.transport.TransportService;

public class PutJudgmentTransportAction extends HandledTransportAction<PutJudgmentRequest, IndexResponse> {
    private final ClusterService clusterService;
    private final JudgmentDao judgmentDao;
    private final JudgmentsProcessorFactory judgmentsProcessorFactory;

    private static final Logger LOGGER = LogManager.getLogger(PutJudgmentTransportAction.class);

    // Metadata keys
    private static final String QUERY_SET_ID = "querySetId";
    private static final String SEARCH_CONFIGURATION_LIST = "searchConfigurationList";
    private static final String MODEL_ID = "modelId";
    private static final String SIZE = "size";
    private static final String TOKEN_LIMIT = "tokenLimit";
    private static final String CONTEXT_FIELDS = "contextFields";
    private static final String IGNORE_FAILURE = "ignoreFailure";
    private static final String CLICK_MODEL = "clickModel";
    private static final String MAX_RANK = "maxRank";
    private static final String START_DATE = "startDate";
    private static final String END_DATE = "endDate";
    private static final String JUDGMENT_RATINGS = "judgmentRatings";

    @Inject
    public PutJudgmentTransportAction(
        ClusterService clusterService,
        TransportService transportService,
        ActionFilters actionFilters,
        JudgmentDao judgmentDao,
        JudgmentsProcessorFactory judgmentsProcessorFactory
    ) {
        super(PutJudgmentAction.NAME, transportService, actionFilters, PutUbiJudgmentRequest::new);
        this.clusterService = clusterService;
        this.judgmentDao = judgmentDao;
        this.judgmentsProcessorFactory = judgmentsProcessorFactory;
    }

    @Override
    protected void doExecute(Task task, PutJudgmentRequest request, ActionListener<IndexResponse> listener) {
        if (request == null) {
            listener.onFailure(new SearchRelevanceException("Request cannot be null", RestStatus.BAD_REQUEST));
            return;
        }
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
            LOGGER.error("Failed to process judgment request", e);
            listener.onFailure(new SearchRelevanceException("Failed to process judgment request", e, RestStatus.INTERNAL_SERVER_ERROR));
        }
    }

    private Map<String, Object> buildMetadata(PutJudgmentRequest request) {
        Map<String, Object> metadata = new HashMap<>();
        switch (request.getType()) {
            case LLM_JUDGMENT -> {
                PutLlmJudgmentRequest llmRequest = (PutLlmJudgmentRequest) request;

                // Store flat metadata fields for compatibility
                metadata.put(QUERY_SET_ID, llmRequest.getQuerySetId());
                metadata.put(SEARCH_CONFIGURATION_LIST, llmRequest.getSearchConfigurationList());
                metadata.put(MODEL_ID, llmRequest.getModelId());
                metadata.put(SIZE, llmRequest.getSize());
                metadata.put(TOKEN_LIMIT, llmRequest.getTokenLimit());
                metadata.put(CONTEXT_FIELDS, llmRequest.getContextFields());
                metadata.put(IGNORE_FAILURE, llmRequest.isIgnoreFailure());
                metadata.put(
                    PROMPT_TEMPLATE,
                    llmRequest.getPromptTemplate() != null ? llmRequest.getPromptTemplate() : DEFAULT_PROMPT_TEMPLATE
                );
                metadata.put(
                    LLM_JUDGMENT_RATING_TYPE,
                    llmRequest.getLlmJudgmentRatingType() != null ? llmRequest.getLlmJudgmentRatingType() : LLMJudgmentRatingType.SCORE0_1
                );
                metadata.put(OVERWRITE_CACHE, llmRequest.isOverwriteCache());
                metadata.put(
                    CONNECTOR_TYPE,
                    llmRequest.getConnectorType() != null ? llmRequest.getConnectorType().name() : ConnectorType.OPENAI.name()
                );
                metadata.put(RATE_LIMIT, llmRequest.getRateLimit());
            }
            case UBI_JUDGMENT -> {
                if (!checkUbiIndicesExist(clusterService)) {
                    throw new SearchRelevanceException("UBI is not initialized", RestStatus.CONFLICT);
                }
                PutUbiJudgmentRequest ubiRequest = (PutUbiJudgmentRequest) request;
                metadata.put(CLICK_MODEL, ubiRequest.getClickModel());
                metadata.put(MAX_RANK, ubiRequest.getMaxRank());
                metadata.put(START_DATE, ubiRequest.getStartDate());
                metadata.put(END_DATE, ubiRequest.getEndDate());
            }
            case IMPORT_JUDGMENT -> {
                PutImportJudgmentRequest importRequest = (PutImportJudgmentRequest) request;
                metadata.put(JUDGMENT_RATINGS, importRequest.getJudgmentRatings());
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
