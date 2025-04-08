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

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.opensearch.action.StepListener;
import org.opensearch.cluster.service.ClusterService;
import org.opensearch.common.xcontent.XContentFactory;
import org.opensearch.core.action.ActionListener;
import org.opensearch.core.rest.RestStatus;
import org.opensearch.core.xcontent.ToXContent;
import org.opensearch.searchrelevance.exception.SearchRelevanceException;
import org.opensearch.searchrelevance.indices.SearchRelevanceIndicesManager;
import org.opensearch.searchrelevance.model.Experiment;
import org.opensearch.transport.client.Client;

import reactor.util.annotation.NonNull;

public class ExperimentDao {
    private static final Logger LOGGER = LogManager.getLogger(ExperimentDao.class);
    private final Client client;
    private final ClusterService clusterService;
    private final SearchRelevanceIndicesManager searchRelevanceIndicesManager;

    public ExperimentDao(@NonNull Client client, @NonNull ClusterService clusterService) {
        this.client = client;
        this.clusterService = clusterService;
        this.searchRelevanceIndicesManager = new SearchRelevanceIndicesManager(clusterService, client);
    }

    /**
     * Create experiment index if not exists
     * @param stepListener - step lister for async operation
     */
    public void createIndexIfAbsent(final StepListener<Void> stepListener) {
        searchRelevanceIndicesManager.createIndexIfAbsent(EXPERIMENT, stepListener);
    }

    /**
     * Stores experiment to in the system index
     * @param experiment - Experiment content to be stored
     * @param listener - action lister for async operation
     */
    public void putExperiment(final Experiment experiment, final ActionListener listener) {
        if (experiment == null) {
            listener.onFailure(new IllegalArgumentException("Experiment cannot be null"));
            return;
        }
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
}
