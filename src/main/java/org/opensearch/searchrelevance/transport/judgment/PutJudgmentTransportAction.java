/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */
package org.opensearch.searchrelevance.transport.judgment;

import static org.opensearch.searchrelevance.common.MetricsConstants.MODEL_ID;
import static org.opensearch.searchrelevance.model.JudgmentType.UBI_JUDGMENT;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.opensearch.action.StepListener;
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
import org.opensearch.searchrelevance.model.AsyncStatus;
import org.opensearch.searchrelevance.model.Judgment;
import org.opensearch.searchrelevance.model.JudgmentType;
import org.opensearch.searchrelevance.utils.TimeUtils;
import org.opensearch.tasks.Task;
import org.opensearch.transport.TransportService;

public class PutJudgmentTransportAction extends HandledTransportAction<PutJudgmentRequest, IndexResponse> {
    private final ClusterService clusterService;
    private final JudgmentDao judgmentDao;
    private final JudgmentsProcessorFactory judgmentsProcessorFactory;

    private static final Logger LOGGER = LogManager.getLogger(PutJudgmentTransportAction.class);

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
        String id = UUID.randomUUID().toString();
        String timestamp = TimeUtils.getTimestamp();
        String name = request.getName();
        JudgmentType type = request.getType();
        Map<String, Object> metadata = buildMetadata(request);

        StepListener<Void> createIndexStep = new StepListener<>();
        judgmentDao.createIndexIfAbsent(createIndexStep);

        createIndexStep.whenComplete(v -> {
            Judgment initialJudgment = new Judgment(id, timestamp, name, AsyncStatus.PROCESSING, type, metadata, new HashMap<>());
            judgmentDao.putJudgement(initialJudgment, ActionListener.wrap(response -> {
                // Trigger async processing and return initial response
                triggerAsyncProcessing(id, request, metadata);
                listener.onResponse((IndexResponse) response);
            }, listener::onFailure));
        }, listener::onFailure);
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
            }
            case UBI_JUDGMENT -> {
                PutUbiJudgmentRequest ubiRequest = (PutUbiJudgmentRequest) request;
                metadata.put("clickModel", ubiRequest.getClickModel());
                metadata.put("maxRank", ubiRequest.getMaxRank());
            }
            case IMPORT_JUDGMENT -> {
                PutImportJudgmentRequest importRequest = (PutImportJudgmentRequest) request;
                metadata.put("judgmentScores", importRequest.getJudgmentScores());
            }
        }
        return metadata;
    }

    private void triggerAsyncProcessing(String judgmentId, PutJudgmentRequest request, Map<String, Object> metadata) {
        LOGGER.info("Starting async processing for judgment: {}, type: {}, metadata: {}", judgmentId, request.getType(), metadata);
        BaseJudgmentsProcessor processor = judgmentsProcessorFactory.getProcessor(request.getType());

        processor.generateJudgmentScore(metadata, ActionListener.wrap(judgmentScores -> {
            LOGGER.info(
                "Generated judgment scores for {}, scores size: {}",
                judgmentId,
                judgmentScores != null ? judgmentScores.size() : 0
            );
            updateFinalJudgment(judgmentId, request, metadata, judgmentScores);
        }, error -> handleAsyncFailure(judgmentId, request, "Failed to generate judgment scores", error)));
    }

    private void updateFinalJudgment(
        String judgmentId,
        PutJudgmentRequest request,
        Map<String, Object> metadata,
        Map<String, Map<String, String>> judgmentScores
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
            new HashMap<>()
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
