/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */
package org.opensearch.searchrelevance.transport.judgment;

import static org.opensearch.searchrelevance.indices.SearchRelevanceIndices.JUDGMENT;

import org.opensearch.ResourceNotFoundException;
import org.opensearch.action.delete.DeleteResponse;
import org.opensearch.action.support.ActionFilters;
import org.opensearch.action.support.HandledTransportAction;
import org.opensearch.cluster.service.ClusterService;
import org.opensearch.common.inject.Inject;
import org.opensearch.core.action.ActionListener;
import org.opensearch.core.rest.RestStatus;
import org.opensearch.searchrelevance.dao.JudgmentDao;
import org.opensearch.searchrelevance.exception.SearchRelevanceException;
import org.opensearch.searchrelevance.transport.OpenSearchDocRequest;
import org.opensearch.tasks.Task;
import org.opensearch.transport.TransportService;

public class DeleteJudgmentTransportAction extends HandledTransportAction<OpenSearchDocRequest, DeleteResponse> {
    private final ClusterService clusterService;
    private final JudgmentDao judgmentDao;

    @Inject
    public DeleteJudgmentTransportAction(
        ClusterService clusterService,
        TransportService transportService,
        ActionFilters actionFilters,
        JudgmentDao judgmentDao
    ) {
        super(DeleteJudgmentAction.NAME, transportService, actionFilters, OpenSearchDocRequest::new);
        this.clusterService = clusterService;
        this.judgmentDao = judgmentDao;
    }

    @Override
    protected void doExecute(Task task, OpenSearchDocRequest request, ActionListener<DeleteResponse> listener) {
        // Validate cluster health first
        if (!clusterService.state().routingTable().hasIndex(JUDGMENT.getIndexName())) {
            listener.onFailure(new ResourceNotFoundException("Index [" + JUDGMENT.getIndexName() + "] not found"));
            return;
        }

        try {
            String judgmentId = request.getId();
            if (judgmentId == null || judgmentId.trim().isEmpty()) {
                listener.onFailure(new SearchRelevanceException("judgmentId cannot be null or empty", RestStatus.BAD_REQUEST));
                return;
            }
            judgmentDao.deleteJudgment(judgmentId, listener);
        } catch (Exception e) {
            listener.onFailure(e);
        }
    }
}
