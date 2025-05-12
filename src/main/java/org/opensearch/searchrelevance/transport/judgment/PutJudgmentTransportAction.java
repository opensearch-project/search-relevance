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
import org.opensearch.searchrelevance.model.Judgment;
import org.opensearch.searchrelevance.model.JudgmentType;
import org.opensearch.searchrelevance.utils.TimeUtils;
import org.opensearch.tasks.Task;
import org.opensearch.transport.TransportService;

public class PutJudgmentTransportAction extends HandledTransportAction<PutJudgmentRequest, IndexResponse> {
    private final ClusterService clusterService;
    private final JudgmentDao judgmentDao;
    private final JudgmentsProcessorFactory judgmentsProcessorFactory;

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

        BaseJudgmentsProcessor processor = judgmentsProcessorFactory.getProcessor(type);
        Map<String, Object> metadata = new HashMap<>();

        switch (type) {
            case LLM_JUDGMENT -> {
                PutLlmJudgmentRequest llmRequest = (PutLlmJudgmentRequest) request;
                metadata.put(MODEL_ID, llmRequest.getModelId());
                metadata.put("querySetId", llmRequest.getQuerySetId());
                metadata.put("size", llmRequest.getSize());
                metadata.put("searchConfigurationList", llmRequest.getSearchConfigurationList());
            }
            case UBI_JUDGMENT -> {
                PutUbiJudgmentRequest ubiRequest = (PutUbiJudgmentRequest) request;
                metadata.put("clickModel", ubiRequest.getClickModel());
                metadata.put("maxRank", ubiRequest.getMaxRank());
            }
        }

        // Step 1: process judgment scores
        StepListener<Map<String, Map<String, String>>> processJudgmentScoresStep = new StepListener<>();
        processor.generateJudgmentScore(metadata, processJudgmentScoresStep);

        // Step 2: create index
        StepListener<Void> createIndexStep = new StepListener<>();

        processJudgmentScoresStep.whenComplete(judgmentScores -> {
            judgmentDao.createIndexIfAbsent(createIndexStep);

            createIndexStep.whenComplete(v -> {
                // step3: create judgment
                Judgment judgment = new Judgment(id, timestamp, name, type, metadata, judgmentScores);
                judgmentDao.putJudgement(judgment, listener);
            }, listener::onFailure);
        }, listener::onFailure);
    }
}
