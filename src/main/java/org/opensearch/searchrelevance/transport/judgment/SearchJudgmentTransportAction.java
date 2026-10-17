/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */
package org.opensearch.searchrelevance.transport.judgment;

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
import org.opensearch.searchrelevance.dao.JudgmentDao;
import org.opensearch.searchrelevance.exception.SearchRelevanceException;
import org.opensearch.tasks.Task;
import org.opensearch.transport.TransportService;

/**
 * Transport action for searching judgments.
 */
public class SearchJudgmentTransportAction extends HandledTransportAction<SearchRequest, SearchResponse> {
    private final JudgmentDao judgmentDao;

    private static final Logger LOGGER = LogManager.getLogger(SearchJudgmentTransportAction.class);

    @Inject
    public SearchJudgmentTransportAction(TransportService transportService, ActionFilters actionFilters, JudgmentDao judgmentDao) {
        super(SearchJudgmentAction.NAME, transportService, actionFilters, SearchRequest::new);
        this.judgmentDao = judgmentDao;
    }

    @Override
    protected void doExecute(Task task, SearchRequest request, ActionListener<SearchResponse> listener) {
        try {
            SearchSourceBuilder sourceBuilder = request.source() == null ? new SearchSourceBuilder() : request.source();
            judgmentDao.listJudgment(sourceBuilder, listener);
        } catch (Exception e) {
            LOGGER.error("Failed to process search Judgement request", e);
            listener.onFailure(new SearchRelevanceException("Failed to search Judgment", e, RestStatus.INTERNAL_SERVER_ERROR));
        }
    }
}
