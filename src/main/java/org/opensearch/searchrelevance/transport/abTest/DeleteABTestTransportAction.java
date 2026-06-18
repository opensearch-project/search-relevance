/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */
package org.opensearch.searchrelevance.transport.abTest;

import org.opensearch.action.delete.DeleteResponse;
import org.opensearch.action.support.ActionFilters;
import org.opensearch.action.support.HandledTransportAction;
import org.opensearch.common.inject.Inject;
import org.opensearch.core.action.ActionListener;
import org.opensearch.core.rest.RestStatus;
import org.opensearch.searchrelevance.dao.ABTestDao;
import org.opensearch.searchrelevance.exception.SearchRelevanceException;
import org.opensearch.tasks.Task;
import org.opensearch.transport.TransportService;

public class DeleteABTestTransportAction extends HandledTransportAction<DeleteABTestRequest, DeleteResponse> {

    private final ABTestDao abTestDao;

    @Inject
    public DeleteABTestTransportAction(TransportService transportService, ActionFilters actionFilters, ABTestDao abTestDao) {
        super(DeleteABTestAction.NAME, transportService, actionFilters, DeleteABTestRequest::new);
        this.abTestDao = abTestDao;
    }

    @Override
    protected void doExecute(Task task, DeleteABTestRequest request, ActionListener<DeleteResponse> listener) {
        if (request == null || request.getTestId() == null) {
            listener.onFailure(new SearchRelevanceException("Request and testId cannot be null", RestStatus.BAD_REQUEST));
            return;
        }

        String testId = request.getTestId();

        // Verify test exists before deleting
        abTestDao.getABTest(testId, ActionListener.wrap(searchResponse -> {
            // Test exists — delete snapshots first, then live doc
            abTestDao.deleteSnapshotsByTestId(
                testId,
                ActionListener.wrap(bulkResponse -> abTestDao.deleteABTest(testId, listener), listener::onFailure)
            );
        }, e -> listener.onFailure(new SearchRelevanceException("AB test '" + testId + "' not found", RestStatus.NOT_FOUND))));
    }
}
