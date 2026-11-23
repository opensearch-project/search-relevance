/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */
package org.opensearch.searchrelevance.transport.experiment;

import org.opensearch.action.delete.DeleteResponse;
import org.opensearch.action.support.ActionFilters;
import org.opensearch.action.support.HandledTransportAction;
import org.opensearch.cluster.service.ClusterService;
import org.opensearch.common.inject.Inject;
import org.opensearch.core.action.ActionListener;
import org.opensearch.core.rest.RestStatus;
import org.opensearch.index.reindex.BulkByScrollResponse;
import org.opensearch.searchrelevance.dao.EvaluationResultDao;
import org.opensearch.searchrelevance.dao.ExperimentDao;
import org.opensearch.searchrelevance.dao.ExperimentVariantDao;
import org.opensearch.searchrelevance.dao.ScheduledExperimentHistoryDao;
import org.opensearch.searchrelevance.dao.ScheduledJobsDao;
import org.opensearch.searchrelevance.exception.SearchRelevanceException;
import org.opensearch.searchrelevance.transport.OpenSearchDocRequest;
import org.opensearch.tasks.Task;
import org.opensearch.transport.TransportService;

import lombok.extern.log4j.Log4j2;

/**
 * Transport action to delete an experiment.
 */
@Log4j2
public class DeleteExperimentTransportAction extends HandledTransportAction<OpenSearchDocRequest, DeleteResponse> {
    private final ClusterService clusterService;
    private final ExperimentDao experimentDao;
    private final ExperimentVariantDao experimentVariantDao;
    private final EvaluationResultDao evaluationResultDao;
    private final ScheduledJobsDao scheduledJobsDao;
    private final ScheduledExperimentHistoryDao scheduledExperimentHistoryDao;

    @Inject
    public DeleteExperimentTransportAction(
        ClusterService clusterService,
        TransportService transportService,
        ActionFilters actionFilters,
        ExperimentDao experimentDao,
        EvaluationResultDao evaluationResultDao,
        ExperimentVariantDao experimentVariantDao,
        ScheduledJobsDao scheduledJobsDao,
        ScheduledExperimentHistoryDao scheduledExperimentHistoryDao
    ) {
        super(DeleteExperimentAction.NAME, transportService, actionFilters, OpenSearchDocRequest::new);
        this.clusterService = clusterService;
        this.experimentDao = experimentDao;
        this.experimentVariantDao = experimentVariantDao;
        this.evaluationResultDao = evaluationResultDao;
        this.scheduledJobsDao = scheduledJobsDao;
        this.scheduledExperimentHistoryDao = scheduledExperimentHistoryDao;
    }

    @Override
    protected void doExecute(Task task, OpenSearchDocRequest request, ActionListener<DeleteResponse> listener) {
        try {
            String experimentId = request.getId();
            if (experimentId == null || experimentId.trim().isEmpty()) {
                listener.onFailure(new SearchRelevanceException("Experiment ID cannot be null or empty", RestStatus.BAD_REQUEST));
                return;
            }

            // 1. Delete Experiment. It can be possible that experiment ran into error and there are no entries in evaluation-results,
            // variants etc.
            experimentDao.deleteExperiment(experimentId, listener);

            // 2 and 3. Delete the evaluation results and experiment variants of the experiment. The response comes from the
            // experiment delete above, so these only log what they could not delete: answering the listener here as well
            // would send a second response. A missing index also ends here as a failure.
            evaluationResultDao.deleteEvaluationResultByExperimentId(experimentId, logFailures("evaluation results", experimentId));
            experimentVariantDao.deleteExperimentVariantByExperimentId(experimentId, logFailures("experiment variants", experimentId));

            // 4. Delete schedule jobs corresponding to the experiment.
            scheduledJobsDao.deleteScheduledJob(experimentId, new ActionListener<>() {
                @Override
                public void onResponse(DeleteResponse deleteResponse) {}

                @Override
                public void onFailure(Exception e) {
                    log.error("Failed to delete schedule job for the experiment [{}]", experimentId, e);
                }
            });

            // 5. Delete scheduled experiment history corresponding to the experiment.
            scheduledExperimentHistoryDao.deleteScheduledExperimentHistoryByExperimentId(experimentId, new ActionListener<>() {
                @Override
                public void onResponse(BulkByScrollResponse bulkByScrollResponse) {}

                @Override
                public void onFailure(Exception e) {
                    log.error("Failed to delete schedule experiment history for the experiment [{}]", experimentId, e);
                }
            });
        } catch (Exception e) {
            listener.onFailure(e);
        }
    }

    private ActionListener<BulkByScrollResponse> logFailures(String what, String experimentId) {
        return ActionListener.wrap(response -> {
            if (hasBulkFailures(response)) {
                log.error("Failed to delete the {} of experiment [{}]: {}", what, experimentId, response.getBulkFailures());
            }
            if (hasSearchFailures(response)) {
                log.error("Failed to delete the {} of experiment [{}]: {}", what, experimentId, response.getSearchFailures());
            }
        }, e -> log.error("Failed to delete the {} of experiment [{}]", what, experimentId, e));
    }

    private boolean hasBulkFailures(BulkByScrollResponse bulkByScrollResponse) {
        return (bulkByScrollResponse.getBulkFailures() != null && !bulkByScrollResponse.getBulkFailures().isEmpty());
    }

    private boolean hasSearchFailures(BulkByScrollResponse bulkByScrollResponse) {
        return (bulkByScrollResponse.getSearchFailures() != null && !bulkByScrollResponse.getSearchFailures().isEmpty());
    }
}
