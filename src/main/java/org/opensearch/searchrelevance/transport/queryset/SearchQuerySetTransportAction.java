/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */
package org.opensearch.searchrelevance.transport.queryset;

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
import org.opensearch.searchrelevance.dao.QuerySetDao;
import org.opensearch.searchrelevance.exception.SearchRelevanceException;
import org.opensearch.tasks.Task;
import org.opensearch.transport.TransportService;

/**
 * Transport action for searching query sets.
 */
public class SearchQuerySetTransportAction extends HandledTransportAction<SearchRequest, SearchResponse> {
    private final QuerySetDao querySetDao;

    private static final Logger LOGGER = LogManager.getLogger(SearchQuerySetTransportAction.class);

    @Inject
    public SearchQuerySetTransportAction(TransportService transportService, ActionFilters actionFilters, QuerySetDao querySetDao) {
        super(SearchQuerySetAction.NAME, transportService, actionFilters, SearchRequest::new);
        this.querySetDao = querySetDao;
    }

    @Override
    protected void doExecute(Task task, SearchRequest request, ActionListener<SearchResponse> listener) {
        try {
            SearchSourceBuilder sourceBuilder = request.source() == null ? new SearchSourceBuilder() : request.source();
            querySetDao.listQuerySet(sourceBuilder, listener);
        } catch (Exception e) {
            LOGGER.error("Failed to process search QuerySet request", e);
            listener.onFailure(new SearchRelevanceException("Failed to search QuerySet", e, RestStatus.INTERNAL_SERVER_ERROR));
        }
    }
}
