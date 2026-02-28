/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */
package org.opensearch.searchrelevance.transport.experiment;

import org.opensearch.action.support.ActionFilters;
import org.opensearch.action.support.HandledTransportAction;
import org.opensearch.action.update.UpdateResponse;
import org.opensearch.common.inject.Inject;
import org.opensearch.core.action.ActionListener;
import org.opensearch.core.rest.RestStatus;
import org.opensearch.searchrelevance.dao.ExperimentDao;
import org.opensearch.searchrelevance.exception.SearchRelevanceException;
import org.opensearch.tasks.Task;
import org.opensearch.transport.TransportService;

import lombok.extern.log4j.Log4j2;

/**
 * Handles transport actions for patching (partially updating) experiments.
 * Supports updating name and description fields only.
 * Uses partial update (UpdateRequest.doc) so only provided fields are changed;
 * no need to read-then-write.
 */
@Log4j2
public class PatchExperimentTransportAction extends HandledTransportAction<PatchExperimentRequest, UpdateResponse> {

    private final ExperimentDao experimentDao;

    @Inject
    public PatchExperimentTransportAction(TransportService transportService, ActionFilters actionFilters, ExperimentDao experimentDao) {
        super(PatchExperimentAction.NAME, transportService, actionFilters, PatchExperimentRequest::new);
        this.experimentDao = experimentDao;
    }

    @Override
    protected void doExecute(Task task, PatchExperimentRequest request, ActionListener<UpdateResponse> listener) {
        if (listener == null) {
            throw new IllegalArgumentException("Listener cannot be null");
        }
        if (request == null) {
            listener.onFailure(new SearchRelevanceException("Request cannot be null", RestStatus.BAD_REQUEST));
            return;
        }

        String experimentId = request.getExperimentId();
        log.debug("Patching experiment [{}] with name: [{}], description: [{}]", experimentId, request.getName(), request.getDescription());

        experimentDao.patchExperiment(experimentId, request.getName(), request.getDescription(), ActionListener.wrap(updateResponse -> {
            log.debug("Successfully patched experiment: {}", experimentId);
            listener.onResponse(updateResponse);
        }, e -> {
            log.error("Failed to patch experiment [{}]: {}", experimentId, e.getMessage());
            listener.onFailure(e);
        }));
    }
}
