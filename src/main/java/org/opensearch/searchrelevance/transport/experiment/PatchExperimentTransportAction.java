/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */
package org.opensearch.searchrelevance.transport.experiment;

import java.util.Map;

import org.opensearch.action.support.ActionFilters;
import org.opensearch.action.support.HandledTransportAction;
import org.opensearch.action.update.UpdateResponse;
import org.opensearch.common.inject.Inject;
import org.opensearch.core.action.ActionListener;
import org.opensearch.core.rest.RestStatus;
import org.opensearch.searchrelevance.dao.ExperimentDao;
import org.opensearch.searchrelevance.exception.SearchRelevanceException;
import org.opensearch.searchrelevance.model.AsyncStatus;
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
        String experimentId = request.getExperimentId();
        log.debug("Patching experiment [{}] with name: [{}], description: [{}]", experimentId, request.getName(), request.getDescription());

        // First check experiment status to prevent patching while PROCESSING
        experimentDao.getExperiment(experimentId, ActionListener.wrap(searchResponse -> {
            try {
                if (searchResponse.getHits().getTotalHits().value() == 0) {
                    listener.onFailure(new SearchRelevanceException("Experiment not found: " + experimentId, RestStatus.NOT_FOUND));
                    return;
                }
                Map<String, Object> sourceMap = searchResponse.getHits().getHits()[0].getSourceAsMap();
                String statusStr = (String) sourceMap.get("status");
                // Note: This is a TOCTOU (Time-Of-Check to Time-Of-Use) race condition as the status check happens before the partial
                // update.
                if (AsyncStatus.PROCESSING.name().equals(statusStr)) {
                    listener.onFailure(
                        new SearchRelevanceException(
                            "Cannot patch experiment while it is in PROCESSING status. Please wait for it to complete.",
                            RestStatus.CONFLICT
                        )
                    );
                    return;
                }

                experimentDao.patchExperiment(
                    experimentId,
                    request.getName(),
                    request.getDescription(),
                    ActionListener.wrap(updateResponse -> {
                        log.debug("Successfully patched experiment: {}", experimentId);
                        listener.onResponse(updateResponse);
                    }, e -> {
                        log.error("Failed to patch experiment [{}]: {}", experimentId, e.getMessage());
                        listener.onFailure(e);
                    })
                );
            } catch (Exception e) {
                log.error("Failed to process experiment status check for [{}]", experimentId, e);
                listener.onFailure(new SearchRelevanceException("Failed to check experiment status", e, RestStatus.INTERNAL_SERVER_ERROR));
            }
        }, e -> {
            log.error("Failed to fetch experiment [{}] for status check: {}", experimentId, e.getMessage());
            listener.onFailure(
                new SearchRelevanceException("Failed to fetch experiment for status check", e, RestStatus.INTERNAL_SERVER_ERROR)
            );
        }));
    }
}
