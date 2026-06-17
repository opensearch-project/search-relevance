/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */
package org.opensearch.searchrelevance.transport.abTest;

import java.util.UUID;

import org.opensearch.action.StepListener;
import org.opensearch.action.index.IndexResponse;
import org.opensearch.action.search.SearchResponse;
import org.opensearch.action.support.ActionFilters;
import org.opensearch.action.support.HandledTransportAction;
import org.opensearch.common.inject.Inject;
import org.opensearch.core.action.ActionListener;
import org.opensearch.core.rest.RestStatus;
import org.opensearch.searchrelevance.dao.ABTestDao;
import org.opensearch.searchrelevance.dao.SearchConfigurationDao;
import org.opensearch.searchrelevance.exception.SearchRelevanceException;
import org.opensearch.searchrelevance.model.ABTest;
import org.opensearch.searchrelevance.utils.TimeUtils;
import org.opensearch.tasks.Task;
import org.opensearch.transport.TransportService;

public class PutABTestTransportAction extends HandledTransportAction<PutABTestRequest, IndexResponse> {

    private final ABTestDao abTestDao;
    private final SearchConfigurationDao searchConfigurationDao;

    @Inject
    public PutABTestTransportAction(
        TransportService transportService,
        ActionFilters actionFilters,
        ABTestDao abTestDao,
        SearchConfigurationDao searchConfigurationDao
    ) {
        super(PutABTestAction.NAME, transportService, actionFilters, PutABTestRequest::new);
        this.abTestDao = abTestDao;
        this.searchConfigurationDao = searchConfigurationDao;
    }

    @Override
    protected void doExecute(Task task, PutABTestRequest request, ActionListener<IndexResponse> listener) {
        if (request == null) {
            listener.onFailure(new SearchRelevanceException("Request cannot be null", RestStatus.BAD_REQUEST));
            return;
        }

        if (request.getSearchConfigurationA().equals(request.getSearchConfigurationB())) {
            listener.onFailure(
                new SearchRelevanceException("search_configuration_a and search_configuration_b must be different", RestStatus.BAD_REQUEST)
            );
            return;
        }

        // Validate config A exists
        searchConfigurationDao.getSearchConfiguration(request.getSearchConfigurationA(), new ActionListener<SearchResponse>() {
            @Override
            public void onResponse(SearchResponse responseA) {
                // Validate config B exists
                searchConfigurationDao.getSearchConfiguration(request.getSearchConfigurationB(), new ActionListener<SearchResponse>() {
                    @Override
                    public void onResponse(SearchResponse responseB) {
                        // Both configs exist → proceed with creation
                        createABTest(request, listener);
                    }

                    @Override
                    public void onFailure(Exception e) {
                        listener.onFailure(
                            new SearchRelevanceException(
                                "search_configuration_b '" + request.getSearchConfigurationB() + "' not found",
                                RestStatus.BAD_REQUEST
                            )
                        );
                    }
                });
            }

            @Override
            public void onFailure(Exception e) {
                listener.onFailure(
                    new SearchRelevanceException(
                        "search_configuration_a '" + request.getSearchConfigurationA() + "' not found",
                        RestStatus.BAD_REQUEST
                    )
                );
            }
        });
    }

    private void createABTest(PutABTestRequest request, ActionListener<IndexResponse> listener) {
        String testId = request.getTestId();
        String configAUuid = UUID.randomUUID().toString();
        String configBUuid = UUID.randomUUID().toString();
        String timestamp = TimeUtils.getTimestamp();

        ABTest abTest = new ABTest(
            testId,
            request.getSearchConfigurationA(),
            request.getSearchConfigurationB(),
            configAUuid,
            configBUuid,
            true,
            0,
            timestamp,
            timestamp
        );

        StepListener<Void> createIndexStep = new StepListener<>();
        abTestDao.createIndexIfAbsent(createIndexStep);
        createIndexStep.whenComplete(v -> abTestDao.putABTest(abTest, listener), listener::onFailure);
    }
}
