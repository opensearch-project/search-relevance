/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */
package org.opensearch.searchrelevance.transport.abTest;

import java.util.HashMap;
import java.util.Map;

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

public class UpdateABTestTransportAction extends HandledTransportAction<UpdateABTestRequest, IndexResponse> {

    private final ABTestDao abTestDao;
    private final SearchConfigurationDao searchConfigurationDao;

    @Inject
    public UpdateABTestTransportAction(
        TransportService transportService,
        ActionFilters actionFilters,
        ABTestDao abTestDao,
        SearchConfigurationDao searchConfigurationDao
    ) {
        super(UpdateABTestAction.NAME, transportService, actionFilters, UpdateABTestRequest::new);
        this.abTestDao = abTestDao;
        this.searchConfigurationDao = searchConfigurationDao;
    }

    @Override
    protected void doExecute(Task task, UpdateABTestRequest request, ActionListener<IndexResponse> listener) {
        if (request == null || request.getTestId() == null) {
            listener.onFailure(new SearchRelevanceException("Request and testId cannot be null", RestStatus.BAD_REQUEST));
            return;
        }

        // Validate config IDs if they are being changed
        if (request.getSearchConfigurationA() != null || request.getSearchConfigurationB() != null) {
            validateAndUpdate(request, listener);
        } else {
            // Only enabled toggle — no config validation needed
            performUpdate(request, listener);
        }
    }

    private void validateAndUpdate(UpdateABTestRequest request, ActionListener<IndexResponse> listener) {
        ActionListener<SearchResponse> afterValidation = ActionListener.wrap(
            response -> performUpdate(request, listener),
            listener::onFailure
        );

        if (request.getSearchConfigurationA() != null && request.getSearchConfigurationB() != null) {
            // Both configs changing — validate A then B
            searchConfigurationDao.getSearchConfiguration(request.getSearchConfigurationA(), new ActionListener<SearchResponse>() {
                @Override
                public void onResponse(SearchResponse responseA) {
                    searchConfigurationDao.getSearchConfiguration(request.getSearchConfigurationB(), new ActionListener<SearchResponse>() {
                        @Override
                        public void onResponse(SearchResponse responseB) {
                            performUpdate(request, listener);
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
        } else if (request.getSearchConfigurationA() != null) {
            searchConfigurationDao.getSearchConfiguration(request.getSearchConfigurationA(), new ActionListener<SearchResponse>() {
                @Override
                public void onResponse(SearchResponse response) {
                    performUpdate(request, listener);
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
        } else {
            searchConfigurationDao.getSearchConfiguration(request.getSearchConfigurationB(), new ActionListener<SearchResponse>() {
                @Override
                public void onResponse(SearchResponse response) {
                    performUpdate(request, listener);
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
    }

    private void performUpdate(UpdateABTestRequest request, ActionListener<IndexResponse> listener) {
        abTestDao.getABTest(request.getTestId(), new ActionListener<SearchResponse>() {
            @Override
            public void onResponse(SearchResponse searchResponse) {
                try {
                    Map<String, Object> source = searchResponse.getHits().getHits()[0].getSourceAsMap();
                    ABTest currentTest = new ABTest(
                        (String) source.get(ABTest.TEST_ID),
                        (String) source.get(ABTest.SEARCH_CONFIGURATION_A),
                        (String) source.get(ABTest.SEARCH_CONFIGURATION_B),
                        (String) source.get(ABTest.CONFIG_A_UUID),
                        (String) source.get(ABTest.CONFIG_B_UUID),
                        (Boolean) source.get(ABTest.ENABLED),
                        (Integer) source.get(ABTest.VERSION),
                        (String) source.get(ABTest.CREATED_AT),
                        (String) source.get(ABTest.UPDATED_AT)
                    );

                    // Apply updates
                    boolean newEnabled = request.getEnabled() != null ? request.getEnabled() : currentTest.isEnabled();
                    String newConfigA = request.getSearchConfigurationA() != null
                        ? request.getSearchConfigurationA()
                        : currentTest.getSearchConfigurationA();
                    String newConfigB = request.getSearchConfigurationB() != null
                        ? request.getSearchConfigurationB()
                        : currentTest.getSearchConfigurationB();

                    // Skip if nothing changed
                    if (newEnabled == currentTest.isEnabled()
                        && newConfigA.equals(currentTest.getSearchConfigurationA())
                        && newConfigB.equals(currentTest.getSearchConfigurationB())) {
                        listener.onResponse((IndexResponse) null);
                        return;
                    }

                    // Save snapshot of current state before updating
                    Map<String, Object> record = new HashMap<>();
                    record.put(ABTest.SEARCH_CONFIGURATION_A, currentTest.getSearchConfigurationA());
                    record.put(ABTest.SEARCH_CONFIGURATION_B, currentTest.getSearchConfigurationB());
                    record.put(ABTest.CONFIG_A_UUID, currentTest.getConfigAUuid());
                    record.put(ABTest.CONFIG_B_UUID, currentTest.getConfigBUuid());
                    record.put(ABTest.ENABLED, currentTest.isEnabled());

                    String snapshotId = currentTest.getTestId() + "_" + currentTest.getVersion();
                    String now = TimeUtils.getTimestamp();

                    ABTest updatedTest = new ABTest(
                        currentTest.getTestId(),
                        newConfigA,
                        newConfigB,
                        currentTest.getConfigAUuid(),
                        currentTest.getConfigBUuid(),
                        newEnabled,
                        currentTest.getVersion() + 1,
                        currentTest.getCreatedAt(),
                        now
                    );

                    // Save snapshot then update live doc
                    abTestDao.putSnapshot(
                        snapshotId,
                        currentTest.getTestId(),
                        record,
                        now,
                        ActionListener.wrap(snapshotResponse -> abTestDao.updateABTest(updatedTest, listener), listener::onFailure)
                    );
                } catch (Exception e) {
                    listener.onFailure(new SearchRelevanceException("Failed to update ABTest", e, RestStatus.INTERNAL_SERVER_ERROR));
                }
            }

            @Override
            public void onFailure(Exception e) {
                listener.onFailure(new SearchRelevanceException("Failed to retrieve ABTest", e, RestStatus.INTERNAL_SERVER_ERROR));
            }
        });
    }
}
