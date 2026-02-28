/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */
package org.opensearch.searchrelevance.transport.experiment;

import java.util.ArrayList;
import java.util.Objects;
import java.util.UUID;

import org.opensearch.action.index.IndexResponse;
import org.opensearch.action.support.ActionFilters;
import org.opensearch.action.support.HandledTransportAction;
import org.opensearch.common.inject.Inject;
import org.opensearch.core.action.ActionListener;
import org.opensearch.core.rest.RestStatus;
import org.opensearch.searchrelevance.dao.ExperimentDao;
import org.opensearch.searchrelevance.dao.JudgmentDao;
import org.opensearch.searchrelevance.dao.QuerySetDao;
import org.opensearch.searchrelevance.dao.SearchConfigurationDao;
import org.opensearch.searchrelevance.exception.SearchRelevanceException;
import org.opensearch.searchrelevance.executors.ExperimentRunningManager;
import org.opensearch.searchrelevance.executors.ExperimentTaskManager;
import org.opensearch.searchrelevance.experiment.HybridOptimizerExperimentProcessor;
import org.opensearch.searchrelevance.experiment.PointwiseExperimentProcessor;
import org.opensearch.searchrelevance.metrics.MetricsHelper;
import org.opensearch.searchrelevance.model.AsyncStatus;
import org.opensearch.searchrelevance.model.Experiment;
import org.opensearch.searchrelevance.model.ExperimentType;
import org.opensearch.searchrelevance.settings.SearchRelevanceSettingsAccessor;
import org.opensearch.searchrelevance.utils.TimeUtils;
import org.opensearch.tasks.Task;
import org.opensearch.threadpool.ThreadPool;
import org.opensearch.transport.TransportService;

import lombok.extern.log4j.Log4j2;

/**
 * Handles transport actions for creating experiments in the system.
 */
@Log4j2
public class PutExperimentTransportAction extends HandledTransportAction<PutExperimentRequest, IndexResponse> {

    /** Length of the short ID used in default experiment names */
    private static final int SHORT_ID_LENGTH = 8;

    private final ExperimentDao experimentDao;
    private final QuerySetDao querySetDao;
    private final SearchConfigurationDao searchConfigurationDao;
    private final MetricsHelper metricsHelper;
    private final HybridOptimizerExperimentProcessor hybridOptimizerExperimentProcessor;
    private final PointwiseExperimentProcessor pointwiseExperimentProcessor;
    private final ExperimentRunningManager experimentRunningManager;

    @Inject
    public PutExperimentTransportAction(
        TransportService transportService,
        ActionFilters actionFilters,
        ExperimentDao experimentDao,
        QuerySetDao querySetDao,
        SearchConfigurationDao searchConfigurationDao,
        MetricsHelper metricsHelper,
        JudgmentDao judgmentDao,
        ExperimentTaskManager experimentTaskManager,
        ExperimentRunningManager experimentRunningManager,
        ThreadPool threadPool,
        SearchRelevanceSettingsAccessor settingsAccessor
    ) {
        super(PutExperimentAction.NAME, transportService, actionFilters, PutExperimentRequest::new);
        this.experimentDao = experimentDao;
        this.querySetDao = querySetDao;
        this.searchConfigurationDao = searchConfigurationDao;
        this.metricsHelper = metricsHelper;
        this.hybridOptimizerExperimentProcessor = new HybridOptimizerExperimentProcessor(judgmentDao, experimentTaskManager);
        this.pointwiseExperimentProcessor = new PointwiseExperimentProcessor(judgmentDao, experimentTaskManager);
        this.experimentRunningManager = experimentRunningManager;
    }

    @Override
    protected void doExecute(Task task, PutExperimentRequest request, ActionListener<IndexResponse> listener) {
        if (request == null) {
            listener.onFailure(new SearchRelevanceException("Request cannot be null", RestStatus.BAD_REQUEST));
            return;
        }

        try {
            String id = UUID.randomUUID().toString();
            final String experimentName = (request.getName() != null && !request.getName().trim().isEmpty())
                ? request.getName()
                : generateDefaultExperimentName(request.getType(), id);

            Experiment initialExperiment = new Experiment(
                id,
                TimeUtils.getTimestamp(),
                experimentName,
                request.getDescription(),
                request.getType(),
                AsyncStatus.PROCESSING,
                request.getQuerySetId(),
                request.getSearchConfigurationList(),
                request.getJudgmentList(),
                request.getSize(),
                new ArrayList<>()
            );

            // Store initial experiment and return ID immediately
            experimentDao.putExperiment(initialExperiment, ActionListener.wrap(response -> {
                // Return response immediately
                listener.onResponse((IndexResponse) response);

                // Start experiment with async processing
                experimentRunningManager.startExperimentRun(id, request, experimentName, request.getDescription(), null, null);
            }, e -> {
                log.error("Failed to create initial experiment", e);
                listener.onFailure(
                    new SearchRelevanceException("Failed to create initial experiment", e, RestStatus.INTERNAL_SERVER_ERROR)
                );
            }));

        } catch (Exception e) {
            log.error("Failed to process experiment request", e);
            listener.onFailure(new SearchRelevanceException("Failed to process experiment request", e, RestStatus.INTERNAL_SERVER_ERROR));
        }
    }

    /**
     * Generates a default experiment name based on the experiment type and a short
     * ID.
     * Format: "{ExperimentType}-{shortId}" e.g., "PAIRWISE_COMPARISON-a1b2c3d4"
     *
     * @param type the experiment type (must not be null)
     * @param id   the full experiment ID (must not be null)
     * @return a default name for the experiment
     * @throws NullPointerException if type or id is null
     */
    public static String generateDefaultExperimentName(ExperimentType type, String id) {
        Objects.requireNonNull(type, "Experiment type must not be null");
        Objects.requireNonNull(id, "Experiment ID must not be null");
        String shortId = id.length() > SHORT_ID_LENGTH ? id.substring(0, SHORT_ID_LENGTH) : id;
        return type.name() + "-" + shortId;
    }
}
