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
import java.util.concurrent.atomic.AtomicBoolean;

import org.opensearch.action.search.SearchResponse;
import org.opensearch.common.annotation.ExperimentalApi;
import org.opensearch.core.action.ActionListener;
import org.opensearch.core.rest.RestStatus;
import org.opensearch.searchrelevance.dao.ExperimentDao;
import org.opensearch.searchrelevance.dao.ScheduledExperimentHistoryDao;
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
public enum ScheduledExperimentRunnerManager {
    INSTANCE;

    private ExperimentDao experimentDao;
    private ScheduledExperimentHistoryDao scheduledExperimentHistoryDao;
    private ExperimentRunningManager experimentRunningManager;

    public void setExperimentDao(ExperimentDao experimentDao) {
        this.experimentDao = experimentDao;
    }

    public void setScheduledExperimentHistoryDao(ScheduledExperimentHistoryDao scheduledExperimentHistoryDao) {
        this.scheduledExperimentHistoryDao = scheduledExperimentHistoryDao;
    }

    public void setExperimentRunningManager(ExperimentRunningManager experimentRunningManager) {
        this.experimentRunningManager = experimentRunningManager;
    }

    public void runScheduledExperiment(
        SearchRelevanceJobParameters parameter,
        AtomicBoolean hasStarted,
        String scheduledExperimentResultId
    ) {
        String experimentId = parameter.getExperimentId();
        try {
            experimentDao.getExperiment(experimentId, ActionListener.wrap(experimentResponse -> {
                try {
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
                        hasStarted.compareAndSet(false, true);
                        experimentRunningManager.startExperimentRun(experimentId, request);
                    }, e -> { handleAsyncFailure(experimentId, request, "Failed to put ScheduledExperimentResult", e); }));
                } catch (Exception e) {
                    log.error("Scheduled experiment result for: {} cannot be added.", experimentId);
                }
            }, e -> { log.error("Experiment id: {} is not found.", experimentId); }));
        } catch (Exception e) {
            throw new IllegalStateException("Experiment not found.");
        }
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
                e -> log.error("Failed to update error status for scheduled experiment: " + request.getScheduledExperimentResultId(), e)
            )
        );
    }

    public void cleanupResources(String experimentId, String scheduledExperimentResultId, AtomicBoolean hasStarted) {
        ScheduledExperimentResult finalExperiment;
        if (hasStarted.get() == false) {
            log.info("While the experiment has not been added to the index, it will still be marked as TIMEOUT.");
            finalExperiment = new ScheduledExperimentResult(
                scheduledExperimentResultId,
                experimentId,
                TimeUtils.getTimestamp(),
                AsyncStatus.TIMEOUT,
                null
            );
            scheduledExperimentHistoryDao.putScheduledExperimentResult(
                finalExperiment,
                ActionListener.wrap(
                    response -> log.info("Updated scheduled experiment {} status to TIMEOUT", scheduledExperimentResultId),
                    e -> log.error("Failed to update error status for scheduled experiment: " + scheduledExperimentResultId, e)
                )
            );
        } else {
            finalExperiment = new ScheduledExperimentResult(
                scheduledExperimentResultId,
                experimentId,
                TimeUtils.getTimestamp(),
                AsyncStatus.TIMEOUT,
                null
            );

            scheduledExperimentHistoryDao.updateScheduledExperimentResult(
                finalExperiment,
                ActionListener.wrap(
                    response -> log.info("Updated scheduled experiment {} status to TIMEOUT", scheduledExperimentResultId),
                    e -> log.error("Failed to update error status for scheduled experiment: " + scheduledExperimentResultId, e)
                )
            );
        }
    }
}
