/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */
package org.opensearch.searchrelevance.transport.queryset;

import static org.opensearch.searchrelevance.common.PluginConstants.QUERYSET_ID;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;

import org.opensearch.action.delete.DeleteResponse;
import org.opensearch.action.search.SearchResponse;
import org.opensearch.action.support.ActionFilters;
import org.opensearch.action.support.HandledTransportAction;
import org.opensearch.cluster.service.ClusterService;
import org.opensearch.common.inject.Inject;
import org.opensearch.core.action.ActionListener;
import org.opensearch.core.rest.RestStatus;
import org.opensearch.search.SearchHit;
import org.opensearch.searchrelevance.dao.ExperimentDao;
import org.opensearch.searchrelevance.dao.QuerySetDao;
import org.opensearch.searchrelevance.exception.SearchRelevanceException;
import org.opensearch.searchrelevance.transport.OpenSearchDocRequest;
import org.opensearch.tasks.Task;
import org.opensearch.transport.TransportService;

import lombok.extern.log4j.Log4j2;

/**
 * Transport action to delete a query set
 */
@Log4j2
public class DeleteQuerySetTransportAction extends HandledTransportAction<OpenSearchDocRequest, DeleteResponse> {
    private final ClusterService clusterService;
    private final QuerySetDao querySetDao;
    private final ExperimentDao experimentDao;

    @Inject
    public DeleteQuerySetTransportAction(
        ClusterService clusterService,
        TransportService transportService,
        ActionFilters actionFilters,
        QuerySetDao querySetDao,
        ExperimentDao experimentDao
    ) {
        super(DeleteQuerySetAction.NAME, transportService, actionFilters, OpenSearchDocRequest::new);
        this.clusterService = clusterService;
        this.querySetDao = querySetDao;
        this.experimentDao = experimentDao;
    }

    @Override
    protected void doExecute(Task task, OpenSearchDocRequest request, ActionListener<DeleteResponse> listener) {
        try {
            String querySetId = request.getId();
            if (querySetId == null || querySetId.trim().isEmpty()) {
                listener.onFailure(new SearchRelevanceException("Query set ID cannot be null or empty", RestStatus.BAD_REQUEST));
                return;
            }

            experimentDao.getExperimentByFieldId(querySetId, QUERYSET_ID, 3, new ActionListener<>() {
                @Override
                public void onResponse(SearchResponse experiments) {
                    if (experiments != null && experiments.getHits().getTotalHits().value() > 0) {
                        List<String> ids = Arrays.stream(experiments.getHits().getHits()).map(SearchHit::getId).toList();
                        listener.onFailure(
                            new SearchRelevanceException(
                                String.format(
                                    Locale.ROOT,
                                    "query set cannot be deleted as it is currently used by experiments with ids %s",
                                    String.join(", ", ids)
                                ),
                                RestStatus.CONFLICT
                            )
                        );
                        return;
                    }
                    querySetDao.deleteQuerySet(querySetId, listener);
                }

                @Override
                public void onFailure(Exception e) {
                    listener.onFailure(e);
                }
            });
        } catch (Exception e) {
            listener.onFailure(e);
        }
    }
}
