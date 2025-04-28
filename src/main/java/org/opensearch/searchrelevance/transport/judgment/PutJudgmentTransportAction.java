/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */
package org.opensearch.searchrelevance.transport.judgment;

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
import org.opensearch.searchrelevance.ml.MLAccessor;
import org.opensearch.searchrelevance.model.Judgment;
import org.opensearch.searchrelevance.utils.TimeUtils;
import org.opensearch.tasks.Task;
import org.opensearch.transport.TransportService;

public class PutJudgmentTransportAction extends HandledTransportAction<PutJudgmentRequest, IndexResponse> {
    private final ClusterService clusterService;
    private final JudgmentDao judgmentDao;
    private MLAccessor mlAccessor;

    @Inject
    public PutJudgmentTransportAction(
        ClusterService clusterService,
        TransportService transportService,
        ActionFilters actionFilters,
        JudgmentDao judgmentDao,
        MLAccessor mlAccessor
    ) {
        super(PutJudgmentAction.NAME, transportService, actionFilters, PutJudgmentRequest::new);
        this.clusterService = clusterService;
        this.judgmentDao = judgmentDao;
        this.mlAccessor = mlAccessor;
    }

    @Override
    protected void doExecute(Task task, PutJudgmentRequest request, ActionListener<IndexResponse> listener) {
        if (request == null) {
            listener.onFailure(new SearchRelevanceException("Request cannot be null", RestStatus.BAD_REQUEST));
            return;
        }
        String id = UUID.randomUUID().toString();
        String timestamp = TimeUtils.getTimestamp();

        // Execute ML prediction
        mlAccessor.predict(
            request.getModelId(),
            request.getQuestion(),
            request.getContent(),
            request.getReference(),
            ActionListener.wrap(response -> {
                if (response == null) {
                    listener.onFailure(
                        new SearchRelevanceException("ML prediction returned null output", RestStatus.INTERNAL_SERVER_ERROR)
                    );
                    return;
                }

                // Create index if it doesn't exist
                StepListener<Void> createIndexStep = new StepListener<>();
                judgmentDao.createIndexIfAbsent(createIndexStep);

                createIndexStep.whenComplete(v -> {
                    Judgment judgment = new Judgment(id, timestamp, response);
                    judgmentDao.putJudgement(judgment, listener);
                }, listener::onFailure);
            }, listener::onFailure)
        );
    }
}
