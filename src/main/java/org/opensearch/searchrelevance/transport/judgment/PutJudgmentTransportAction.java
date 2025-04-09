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
import org.opensearch.searchrelevance.dao.JudgmentDao;
import org.opensearch.searchrelevance.model.Judgment;
import org.opensearch.searchrelevance.utils.TimeUtils;
import org.opensearch.tasks.Task;
import org.opensearch.transport.TransportService;

public class PutJudgmentTransportAction extends HandledTransportAction<PutJudgmentRequest, IndexResponse> {
    private final ClusterService clusterService;
    private final JudgmentDao judgmentDao;

    @Inject
    public PutJudgmentTransportAction(
        ClusterService clusterService,
        TransportService transportService,
        ActionFilters actionFilters,
        JudgmentDao judgmentDao
    ) {
        super(PutJudgmentAction.NAME, transportService, actionFilters, PutJudgmentRequest::new);
        this.clusterService = clusterService;
        this.judgmentDao = judgmentDao;
    }

    @Override
    protected void doExecute(Task task, PutJudgmentRequest request, ActionListener<IndexResponse> listener) {
        if (request == null) {
            listener.onFailure(new IllegalArgumentException("Request cannot be null"));
            return;
        }
        String id = UUID.randomUUID().toString();
        String timestamp = TimeUtils.getTimestamp();

        StepListener<Void> createIndexStep = new StepListener<>();
        judgmentDao.createIndexIfAbsent(createIndexStep);
        createIndexStep.whenComplete(v -> {
            Judgment judgment = new Judgment(id, timestamp);
            judgmentDao.putJudgement(judgment, listener);
        }, listener::onFailure);
    }
}
