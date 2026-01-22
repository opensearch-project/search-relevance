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

            // 2. Delete evaluation results corresponding to the experiment.
            evaluationResultDao.deleteEvaluationResultByExperimentId(experimentId, new ActionListener<>() {
                @Override
                public void onResponse(BulkByScrollResponse bulkByScrollResponse) {
                    if (hasBulkFailures(bulkByScrollResponse)) {
                        listener.onFailure(
                            new SearchRelevanceException(
                                "Failed to delete the evaluation results of the experiment, due to failures in deleting evaluation results "
                                    + bulkByScrollResponse.getBulkFailures(),
                                RestStatus.INTERNAL_SERVER_ERROR
                            )
                        );
                    }

                    if (hasSearchFailures(bulkByScrollResponse)) {
                        listener.onFailure(
                            new SearchRelevanceException(
                                "Failed to delete the evaluation results of the experiment, due to failures in deleting evaluation results "
                                    + bulkByScrollResponse.getSearchFailures(),
                                RestStatus.INTERNAL_SERVER_ERROR
                            )
                        );
                    }
                }

                // In case of the index does not exist, it results in a failure.
                @Override
                public void onFailure(Exception e) {
                    log.error("Failed to delete evaluation results for experiment [{}]", experimentId, e);
                }
            });

            // 3. Delete Experiment Variants corresponding to the experiment.
            experimentVariantDao.deleteExperimentVariantByExperimentId(experimentId, new ActionListener<>() {
                @Override
                public void onResponse(BulkByScrollResponse bulkByScrollResponse) {
                    if (hasBulkFailures(bulkByScrollResponse)) {
                        listener.onFailure(
                            new SearchRelevanceException(
                                "Failed to delete experiment, due to failures in deleting experiment variants "
                                    + bulkByScrollResponse.getBulkFailures(),
                                RestStatus.INTERNAL_SERVER_ERROR
                            )
                        );
                    }

                    if (hasSearchFailures(bulkByScrollResponse)) {
                        listener.onFailure(
                            new SearchRelevanceException(
                                "Failed to delete experiment, due to failures in deleting experiment variants "
                                    + bulkByScrollResponse.getSearchFailures(),
                                RestStatus.INTERNAL_SERVER_ERROR
                            )
                        );
                    }
                }

                // In case of the index does not exist, it results in a failure.
                @Override
                public void onFailure(Exception e) {
                    log.error("Failed to delete experiment variants results for experiment [{}]", experimentId, e);
                }
            });

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

    private boolean hasBulkFailures(BulkByScrollResponse bulkByScrollResponse) {
        return (bulkByScrollResponse.getBulkFailures() != null && !bulkByScrollResponse.getBulkFailures().isEmpty());
    }

    private boolean hasSearchFailures(BulkByScrollResponse bulkByScrollResponse) {
        return (bulkByScrollResponse.getSearchFailures() != null && !bulkByScrollResponse.getSearchFailures().isEmpty());
    }
}
