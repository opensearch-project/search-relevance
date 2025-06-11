/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */
package org.opensearch.searchrelevance.transport.judgment;

import static org.opensearch.searchrelevance.common.MetricsConstants.MODEL_ID;

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
import org.opensearch.searchrelevance.model.AsyncStatus;
import org.opensearch.searchrelevance.model.Judgment;
import org.opensearch.searchrelevance.utils.TimeUtils;
import org.opensearch.tasks.Task;
import org.opensearch.transport.TransportService;

import com.fasterxml.jackson.databind.ObjectMapper;

public class PutJudgmentTransportAction extends HandledTransportAction<PutJudgmentRequest, IndexResponse> {
    private final ClusterService clusterService;
    private final JudgmentDao judgmentDao;
    private final JudgmentsProcessorFactory judgmentsProcessorFactory;
    private final ObjectMapper objectMapper = new ObjectMapper();

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

    private List<Map<String, Object>> buildMetadata(PutJudgmentRequest request) {
        List<Map<String, Object>> metadata = new ArrayList<>();
        switch (request.getType()) {
            case LLM_JUDGMENT -> {
                PutLlmJudgmentRequest llmRequest = (PutLlmJudgmentRequest) request;
                metadata.add(createMetadataEntry(MODEL_ID, llmRequest.getModelId()));
                metadata.add(createMetadataEntry("querySetId", llmRequest.getQuerySetId()));
                metadata.add(createMetadataEntry("size", llmRequest.getSize()));
                metadata.add(createMetadataEntry("searchConfigurationList", llmRequest.getSearchConfigurationList()));
                metadata.add(createMetadataEntry("tokenLimit", llmRequest.getTokenLimit()));
                metadata.add(createMetadataEntry("contextFields", llmRequest.getContextFields()));
                metadata.add(createMetadataEntry("ignoreFailure", llmRequest.isIgnoreFailure()));
            }
            case UBI_JUDGMENT -> {
                PutUbiJudgmentRequest ubiRequest = (PutUbiJudgmentRequest) request;
                metadata.add(createMetadataEntry("clickModel", ubiRequest.getClickModel()));
                metadata.add(createMetadataEntry("maxRank", ubiRequest.getMaxRank()));
            }
            case IMPORT_JUDGMENT -> {
                PutImportJudgmentRequest importRequest = (PutImportJudgmentRequest) request;
                metadata.add(createMetadataEntry("judgmentRatings", importRequest.getJudgmentRatings()));
            }
        }
        return metadata;
    }

    /**
     * Create a metadata entry with name-value structure
     */
    private Map<String, Object> createMetadataEntry(String name, Object value) {
        // Convert complex objects to JSON strings for text field storage
        if (value instanceof List || value instanceof Map) {
            try {
                value = objectMapper.writeValueAsString(value);
            } catch (Exception e) {
                value = value.toString();
            }
        }
        return Map.of("name", name, "value", value);
    }

    /**
     * Convert nested List<Map<String, Object>> to flat Map<String, Object> format
     */
    private Map<String, Object> convertNestedListToMap(List<Map<String, Object>> nestedList) {
        Map<String, Object> flatMap = new HashMap<>();
        if (nestedList != null) {
            for (Map<String, Object> item : nestedList) {
                Object name = item.get("name");
                Object value = item.get("value");
                if (name != null) {
                    // Try to deserialize JSON strings back to objects
                    if (value instanceof String && (((String) value).startsWith("[") || ((String) value).startsWith("{"))) {
                        try {
                            value = objectMapper.readValue((String) value, Object.class);
                        } catch (Exception e) {
                            // Keep as string if parsing fails
                        }
                    }
                    flatMap.put(name.toString(), value);
                }
            }
        }
        return flatMap;
    }

    private void triggerAsyncProcessing(String judgmentId, PutJudgmentRequest request, List<Map<String, Object>> metadata) {
        LOGGER.info("Starting async processing for judgment: {}, type: {}, metadata: {}", judgmentId, request.getType(), metadata);
        BaseJudgmentsProcessor processor = judgmentsProcessorFactory.getProcessor(request.getType());

        processor.generateJudgmentRating(convertNestedListToMap(metadata), ActionListener.wrap(judgmentRatings -> {
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
        List<Map<String, Object>> metadata,
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
            List.of(createMetadataEntry("error", error.getMessage())),
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
