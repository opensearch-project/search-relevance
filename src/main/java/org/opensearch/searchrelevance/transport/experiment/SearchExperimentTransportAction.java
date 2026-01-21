/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */
package org.opensearch.searchrelevance.transport.experiment;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.opensearch.action.search.SearchRequest;
import org.opensearch.action.search.SearchResponse;
import org.opensearch.action.support.ActionFilters;
import org.opensearch.action.support.HandledTransportAction;
import org.opensearch.common.inject.Inject;
import org.opensearch.core.action.ActionListener;
import org.opensearch.core.rest.RestStatus;
import org.opensearch.search.builder.SearchSourceBuilder;
import org.opensearch.searchrelevance.dao.ExperimentDao;
import org.opensearch.searchrelevance.exception.SearchRelevanceException;
import org.opensearch.tasks.Task;
import org.opensearch.transport.TransportService;

/**
 * Transport action for searching experiments.
 */
public class SearchExperimentTransportAction extends HandledTransportAction<SearchRequest, SearchResponse> {
    private final ExperimentDao experimentDao;

    private static final Logger LOGGER = LogManager.getLogger(SearchExperimentTransportAction.class);

    @Inject
    public SearchExperimentTransportAction(TransportService transportService, ActionFilters actionFilters, ExperimentDao experimentDao) {
        super(SearchExperimentAction.NAME, transportService, actionFilters, SearchRequest::new);
        this.experimentDao = experimentDao;
    }

    @Override
    protected void doExecute(Task task, SearchRequest request, ActionListener<SearchResponse> listener) {
        try {
            SearchSourceBuilder sourceBuilder = request.source() == null ? new SearchSourceBuilder() : request.source();
            experimentDao.listExperiment(sourceBuilder, listener);
        } catch (Exception e) {
            LOGGER.error("Failed to process search experiment request", e);
            listener.onFailure(new SearchRelevanceException("Failed to search Experiment", e, RestStatus.INTERNAL_SERVER_ERROR));
        }
    }
}
