/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */
package org.opensearch.searchrelevance.scheduler;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;

import org.opensearch.action.delete.DeleteResponse;
import org.opensearch.action.search.SearchResponse;
import org.opensearch.common.annotation.ExperimentalApi;
import org.opensearch.common.inject.Inject;
import org.opensearch.core.action.ActionListener;
import org.opensearch.core.rest.RestStatus;
import org.opensearch.searchrelevance.dao.ExperimentDao;
import org.opensearch.searchrelevance.dao.ScheduledExperimentHistoryDao;
import org.opensearch.searchrelevance.dao.ScheduledJobsDao;
import org.opensearch.searchrelevance.exception.SearchRelevanceException;
import org.opensearch.searchrelevance.executors.ExperimentRunningManager;
import org.opensearch.searchrelevance.model.AsyncStatus;
import org.opensearch.searchrelevance.model.Experiment;
import org.opensearch.searchrelevance.model.ExperimentType;
import org.opensearch.searchrelevance.model.ScheduledExperimentResult;
import org.opensearch.searchrelevance.transport.experiment.PutExperimentRequest;
import org.opensearch.searchrelevance.utils.TimeUtils;

import lombok.extern.log4j.Log4j2;

@Log4j2
@ExperimentalApi
public class ScheduledExperimentRunnerManager {

    private ExperimentDao experimentDao;
    private ScheduledExperimentHistoryDao scheduledExperimentHistoryDao;
    private ExperimentRunningManager experimentRunningManager;
    private ScheduledJobsDao scheduledJobsDao;

    @Inject
    public ScheduledExperimentRunnerManager(
        ExperimentDao experimentDao,
        ScheduledExperimentHistoryDao scheduledExperimentHistoryDao,
        ExperimentRunningManager experimentRunningManager,
        ScheduledJobsDao scheduledJobsDao
    ) {
        this.experimentDao = experimentDao;
        this.scheduledExperimentHistoryDao = scheduledExperimentHistoryDao;
        this.experimentRunningManager = experimentRunningManager;
        this.scheduledJobsDao = scheduledJobsDao;
    }

    /**
     * Manager for fetching the experiment that is scheduled then running the experiment.
     * Additionally, this class puts the incomplete {@link ScheduledExperimentResult}
     * into its index.
     *
     * @param parameter Parameters for running the scheduled job
     * @param scheduledExperimentResultId The id in the {@link ScheduledExperimentResult} index for this experiment
     * @param cancellationToken The token to indicate whether this scheduled experiment run has been cancelled
     * @param actuallyFinished A countdown latch to indicate whether all asynchronous operations for a scheduled experiment run is complete
     */
    public void runScheduledExperiment(
        SearchRelevanceJobParameters parameter,
        String scheduledExperimentResultId,
        ExperimentCancellationToken cancellationToken,
        CountDownLatch actuallyFinished
    ) {
        String experimentId = parameter.getExperimentId();
        try {
            experimentDao.getExperiment(experimentId, ActionListener.wrap(experimentResponse -> {
                try {
                    if (checkIfCancelled(cancellationToken)) {
                        log.info("Scheduled experiment for {} timed out before placing scheduled experiment into index.", experimentId);
                        actuallyFinished.countDown();
                        return;
                    }
                    Experiment experiment = convertToExperiment(experimentResponse);
                    String timestamp = TimeUtils.getTimestamp();
                    // What I will do here is add a new request parameter to replace the Experiment object so I can store the id
                    // of the running experiment to record the end time when finished.
                    ScheduledExperimentResult scheduledExperimentResult = new ScheduledExperimentResult(
                        scheduledExperimentResultId,
                        experimentId,
                        timestamp,
                        AsyncStatus.PROCESSING,
                        null
                    );
                    PutExperimentRequest request = new PutExperimentRequest(
                        experiment.type(),
                        scheduledExperimentResultId,
                        experiment.querySetId(),
                        experiment.searchConfigurationList(),
                        experiment.judgmentList(),
                        experiment.size()
                    );
                    scheduledExperimentHistoryDao.putScheduledExperimentResult(scheduledExperimentResult, ActionListener.wrap(response -> {
                        if (checkIfCancelled(cancellationToken)) {
                            log.info("Scheduled experiment for {} timed out after placing scheduled experiment into index.", experimentId);
                            actuallyFinished.countDown();
                            return;
                        }
                        experimentRunningManager.startExperimentRun(experimentId, request, cancellationToken, actuallyFinished);
                    }, e -> {
                        handleAsyncFailure(experimentId, request, "Failed to put ScheduledExperimentResult", e);
                        actuallyFinished.countDown();
                    }));
                } catch (Exception e) {
                    log.error("Scheduled experiment result for: {} cannot be added.", experimentId);
                    actuallyFinished.countDown();
                }
            }, e -> {
                // There will always be an attempt to retrieve the underlying experimentId.
                log.error("Experiment id: {} is not found.", experimentId);
                scheduledJobsDao.deleteScheduledJob(experimentId, new ActionListener<DeleteResponse>() {
                    @Override
                    public void onResponse(DeleteResponse deleteResponse) {
                        log.info("Non existent experiment. Deleting scheduled job {}", experimentId);
                    }

                    @Override
                    public void onFailure(Exception e) {
                        log.error(
                            "Somehow scheduled experiment job was deleted while experiment {} was in scheduling queue.",
                            experimentId
                        );
                    }
                });
                actuallyFinished.countDown();
            }));
        } catch (Exception e) {
            actuallyFinished.countDown();
            throw new IllegalStateException("Experiment not found.");
        }
    }

    private boolean checkIfCancelled(ExperimentCancellationToken cancellationToken) {
        if (cancellationToken != null && cancellationToken.isCancelled()) {
            return true;
        }
        return false;
    }

    private Experiment convertToExperiment(SearchResponse response) {
        if (response.getHits().getTotalHits().value() == 0) {
            throw new SearchRelevanceException("QuerySet not found", RestStatus.NOT_FOUND);
        }

        Map<String, Object> sourceMap = response.getHits().getHits()[0].getSourceAsMap();

        return new Experiment(
            "",
            "",
            ExperimentType.valueOf((String) sourceMap.get("type")),
            AsyncStatus.valueOf((String) sourceMap.get("status")),
            (String) sourceMap.get("querySetId"),
            (List<String>) sourceMap.get("searchConfigurationList"),
            (List<String>) sourceMap.get("judgmentList"),
            (int) sourceMap.get("size"),
            List.of()
        );
    }

    private void handleAsyncFailure(String experimentId, PutExperimentRequest request, String message, Exception error) {
        log.error(message + " for scheduled experiment: " + experimentId, error);

        ScheduledExperimentResult finalExperiment = new ScheduledExperimentResult(
            request.getScheduledExperimentResultId(),
            experimentId,
            TimeUtils.getTimestamp(),
            AsyncStatus.ERROR,
            null
        );

        scheduledExperimentHistoryDao.updateScheduledExperimentResult(
            finalExperiment,
            ActionListener.wrap(
                response -> log.info("Updated scheduled experiment {} status to ERROR", request.getScheduledExperimentResultId()),
                e -> log.error("Failed to update error status for scheduled experiment: {}", request.getScheduledExperimentResultId(), e)
            )
        );
    }

    /**
     *
     * @param experimentId Id of experiment that is scheduled to run
     * @param scheduledExperimentResultId Id of scheduled experiment result
     * @param cancellationToken The token to indicate whether this scheduled experiment run has been cancelled
     */
    public void cleanupResources(String experimentId, String scheduledExperimentResultId, ExperimentCancellationToken cancellationToken) {
        log.info("Cleaning up all resources for {}", experimentId);
        ScheduledExperimentResult finalExperiment = new ScheduledExperimentResult(
            scheduledExperimentResultId,
            experimentId,
            TimeUtils.getTimestamp(),
            AsyncStatus.TIMEOUT,
            null
        );

        if (cancellationToken.isCancelled()) {
            scheduledExperimentHistoryDao.updateScheduledExperimentResult(
                finalExperiment,
                ActionListener.wrap(
                    response -> log.info("Updated scheduled experiment {} status to TIMEOUT", scheduledExperimentResultId),
                    e -> log.error("Failed to update error status for scheduled experiment: {}", scheduledExperimentResultId, e)
                )
            );
        }
    }
}
