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
import org.opensearch.action.support.GroupedActionListener;
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

        // Validate both configs exist in parallel
        GroupedActionListener<SearchResponse> groupedListener = new GroupedActionListener<>(
            ActionListener.wrap(responses -> createABTest(request, listener), e -> {
                if (e instanceof org.opensearch.ResourceNotFoundException) {
                    listener.onFailure(new SearchRelevanceException("One or more search configurations not found", RestStatus.BAD_REQUEST));
                } else {
                    listener.onFailure(e);
                }
            }),
            2
        );

        searchConfigurationDao.getSearchConfiguration(request.getSearchConfigurationA(), groupedListener);
        searchConfigurationDao.getSearchConfiguration(request.getSearchConfigurationB(), groupedListener);
    }

    private String generateUuid() {
        return UUID.randomUUID().toString();
    }

    private void createABTest(PutABTestRequest request, ActionListener<IndexResponse> listener) {
        String testId = request.getTestId();
        String configAUuid = generateUuid();
        String configBUuid = generateUuid();
        String timestamp = TimeUtils.getTimestamp();

        boolean enabled = request.getEnabled() != null ? request.getEnabled() : true;

        ABTest abTest = new ABTest(
            testId,
            request.getSearchConfigurationA(),
            request.getSearchConfigurationB(),
            configAUuid,
            configBUuid,
            enabled,
            0,
            timestamp,
            timestamp
        );

        StepListener<Void> createIndexStep = new StepListener<>();
        abTestDao.createIndexIfAbsent(createIndexStep);
        createIndexStep.whenComplete(v -> abTestDao.putABTest(abTest, listener), listener::onFailure);
    }
}
