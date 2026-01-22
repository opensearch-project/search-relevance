/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */
package org.opensearch.searchrelevance.dao;

import static org.opensearch.searchrelevance.indices.SearchRelevanceIndices.EXPERIMENT;

import java.io.IOException;
import java.util.Map;
import java.util.Optional;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.opensearch.action.StepListener;
import org.opensearch.action.delete.DeleteResponse;
import org.opensearch.action.search.SearchResponse;
import org.opensearch.common.xcontent.XContentFactory;
import org.opensearch.core.action.ActionListener;
import org.opensearch.core.rest.RestStatus;
import org.opensearch.core.xcontent.ToXContent;
import org.opensearch.index.query.QueryBuilders;
import org.opensearch.search.builder.SearchSourceBuilder;
import org.opensearch.searchrelevance.exception.SearchRelevanceException;
import org.opensearch.searchrelevance.indices.SearchRelevanceIndicesManager;
import org.opensearch.searchrelevance.model.Experiment;
import org.opensearch.searchrelevance.model.ExperimentType;
import org.opensearch.searchrelevance.stats.events.EventStatName;
import org.opensearch.searchrelevance.stats.events.EventStatsManager;

public class ExperimentDao {
    private static final Logger LOGGER = LogManager.getLogger(ExperimentDao.class);
    private final SearchRelevanceIndicesManager searchRelevanceIndicesManager;
    private static Map<ExperimentType, Runnable> experimentTypeIncrementers = Map.of(
        ExperimentType.PAIRWISE_COMPARISON,
        () -> EventStatsManager.increment(EventStatName.EXPERIMENT_PAIRWISE_COMPARISON_EXECUTIONS),
        ExperimentType.POINTWISE_EVALUATION,
        () -> EventStatsManager.increment(EventStatName.EXPERIMENT_POINTWISE_EVALUATION_EXECUTIONS),
        ExperimentType.HYBRID_OPTIMIZER,
        () -> EventStatsManager.increment(EventStatName.EXPERIMENT_HYBRID_OPTIMIZER_EXECUTIONS)
    );

    public ExperimentDao(SearchRelevanceIndicesManager searchRelevanceIndicesManager) {
        this.searchRelevanceIndicesManager = searchRelevanceIndicesManager;
    }

    /**
     * Create experiment index if not exists
     * @param stepListener - step listener for async operation
     */
    public void createIndexIfAbsent(final StepListener<Void> stepListener) {
        searchRelevanceIndicesManager.createIndexIfAbsent(EXPERIMENT, stepListener);
    }

    /**
     * Stores experiment to in the system index
     * @param experiment - Experiment content to be stored
     * @param listener - action listener for async operation
     */
    public void putExperiment(final Experiment experiment, final ActionListener listener) {
        if (experiment == null) {
            listener.onFailure(new SearchRelevanceException("Experiment cannot be null", RestStatus.BAD_REQUEST));
            return;
        }
        // Increment stats
        recordStats(experiment);
        try {
            searchRelevanceIndicesManager.putDoc(
                experiment.id(),
                experiment.toXContent(XContentFactory.jsonBuilder(), ToXContent.EMPTY_PARAMS),
                EXPERIMENT,
                listener
            );
        } catch (IOException e) {
            throw new SearchRelevanceException("Failed to store experiment", e, RestStatus.INTERNAL_SERVER_ERROR);
        }
    }

    public void updateExperiment(final Experiment experiment, final ActionListener listener) {
        if (experiment == null) {
            listener.onFailure(new SearchRelevanceException("Experiment cannot be null", RestStatus.BAD_REQUEST));
            return;
        }
        try {
            searchRelevanceIndicesManager.updateDoc(
                experiment.id(),
                experiment.toXContent(XContentFactory.jsonBuilder(), ToXContent.EMPTY_PARAMS),
                EXPERIMENT,
                listener
            );
        } catch (IOException e) {
            throw new SearchRelevanceException("Failed to store experiment", e, RestStatus.INTERNAL_SERVER_ERROR);
        }
    }

    /**
     * Delete experiment by experimentId
     * @param experimentId - id to be deleted
     * @param listener - action listener for async operation
     */
    public void deleteExperiment(final String experimentId, final ActionListener<DeleteResponse> listener) {
        searchRelevanceIndicesManager.deleteDocByDocId(experimentId, EXPERIMENT, listener);
    }

    /**
     * Get experiment by experimentId
     * @param experimentId - id to be deleted
     * @param listener - action listener for async operation
     */
    public SearchResponse getExperiment(String experimentId, ActionListener<SearchResponse> listener) {
        if (experimentId == null || experimentId.isEmpty()) {
            listener.onFailure(new SearchRelevanceException("experimentId must not be null or empty", RestStatus.BAD_REQUEST));
            return null;
        }
        return searchRelevanceIndicesManager.getDocByDocId(experimentId, EXPERIMENT, listener);
    }

    /**
     * Get experiment by fieldId (async version)
     * @param fieldId - id on which experiment is to be retrieved
     * @param fieldName - field on which experiment is to be retrieved
     * @param listener - action listener for async operation
     */
    public void getExperimentByFieldId(String fieldId, String fieldName, int size, ActionListener<SearchResponse> listener) {
        if (fieldId == null || fieldId.isEmpty()) {
            listener.onFailure(new SearchRelevanceException("fieldId cannot be null or empty", RestStatus.BAD_REQUEST));
            return;
        }

        if (fieldName == null || fieldName.isEmpty()) {
            listener.onFailure(new SearchRelevanceException("fieldName cannot be null or empty", RestStatus.BAD_REQUEST));
            return;
        }

        SearchSourceBuilder sourceBuilder = new SearchSourceBuilder().query(QueryBuilders.termQuery(fieldName, fieldId)).size(size);
        searchRelevanceIndicesManager.listDocsBySearchRequest(sourceBuilder, EXPERIMENT, listener);
    }

    /**
     * List experiment by source builder
     * @param sourceBuilder - source builder to be searched
     * @param listener - action listener for async operation
     */
    public SearchResponse listExperiment(SearchSourceBuilder sourceBuilder, ActionListener<SearchResponse> listener) {
        // Apply default values if not set
        if (sourceBuilder == null) {
            sourceBuilder = new SearchSourceBuilder();
        }

        // Ensure we have a query
        if (sourceBuilder.query() == null) {
            sourceBuilder.query(QueryBuilders.matchAllQuery());
        }

        return searchRelevanceIndicesManager.listDocsBySearchRequest(sourceBuilder, EXPERIMENT, listener);
    }

    private void recordStats(Experiment experiment) {
        EventStatsManager.increment(EventStatName.EXPERIMENT_EXECUTIONS);
        Optional.ofNullable(experimentTypeIncrementers.get(experiment.type())).ifPresent(Runnable::run);
    }
}
