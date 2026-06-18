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
import org.opensearch.action.support.GroupedActionListener;
import org.opensearch.action.support.HandledTransportAction;
import org.opensearch.common.inject.Inject;
import org.opensearch.core.action.ActionListener;
import org.opensearch.core.rest.RestStatus;
import org.opensearch.searchrelevance.dao.ABTestDao;
import org.opensearch.searchrelevance.dao.SearchConfigurationDao;
import org.opensearch.searchrelevance.exception.SearchRelevanceException;
import org.opensearch.searchrelevance.model.ABTest;
import org.opensearch.searchrelevance.model.ABTestSnapshot;
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
        java.util.List<String> configsToValidate = new java.util.ArrayList<>();
        if (request.getSearchConfigurationA() != null) {
            configsToValidate.add(request.getSearchConfigurationA());
        }
        if (request.getSearchConfigurationB() != null) {
            configsToValidate.add(request.getSearchConfigurationB());
        }

        GroupedActionListener<SearchResponse> groupedListener = new GroupedActionListener<>(
            ActionListener.wrap(responses -> performUpdate(request, listener), e -> {
                if (e instanceof org.opensearch.ResourceNotFoundException) {
                    listener.onFailure(new SearchRelevanceException("One or more search configurations not found", RestStatus.BAD_REQUEST));
                } else {
                    listener.onFailure(e);
                }
            }),
            configsToValidate.size()
        );

        for (String configId : configsToValidate) {
            searchConfigurationDao.getSearchConfiguration(configId, groupedListener);
        }
    }

    private static final int MAX_RETRIES = 3;

    private void performUpdate(UpdateABTestRequest request, ActionListener<IndexResponse> listener) {
        performUpdateWithRetry(request, listener, 0);
    }

    private void performUpdateWithRetry(UpdateABTestRequest request, ActionListener<IndexResponse> listener, int attempt) {
        abTestDao.getABTest(request.getTestId(), new ActionListener<SearchResponse>() {
            @Override
            public void onResponse(SearchResponse searchResponse) {
                try {
                    org.opensearch.search.SearchHit hit = searchResponse.getHits().getHits()[0];
                    long seqNo = hit.getSeqNo();
                    long primaryTerm = hit.getPrimaryTerm();
                    Map<String, Object> source = hit.getSourceAsMap();

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

                    // Validate: resulting configs must be different
                    if (newConfigA.equals(newConfigB)) {
                        listener.onFailure(
                            new SearchRelevanceException(
                                "search_configuration_a and search_configuration_b must be different",
                                RestStatus.BAD_REQUEST
                            )
                        );
                        return;
                    }

                    // Skip if nothing changed
                    if (newEnabled == currentTest.isEnabled()
                        && newConfigA.equals(currentTest.getSearchConfigurationA())
                        && newConfigB.equals(currentTest.getSearchConfigurationB())) {
                        listener.onResponse((IndexResponse) null);
                        return;
                    }

                    // Save snapshot of current state before updating
                    String snapshotId = currentTest.getTestId() + "_" + currentTest.getVersion();
                    String now = TimeUtils.getTimestamp();

                    Map<String, Object> record = new HashMap<>();
                    record.put(ABTest.SEARCH_CONFIGURATION_A, currentTest.getSearchConfigurationA());
                    record.put(ABTest.SEARCH_CONFIGURATION_B, currentTest.getSearchConfigurationB());
                    record.put(ABTest.CONFIG_A_UUID, currentTest.getConfigAUuid());
                    record.put(ABTest.CONFIG_B_UUID, currentTest.getConfigBUuid());
                    record.put(ABTest.ENABLED, currentTest.isEnabled());

                    ABTestSnapshot snapshot = new ABTestSnapshot(snapshotId, currentTest.getTestId(), record, now);

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

                    // Save snapshot then update live doc with optimistic concurrency
                    ActionListener<IndexResponse> concurrencyListener = new ActionListener<IndexResponse>() {
                        @Override
                        public void onResponse(IndexResponse response) {
                            listener.onResponse(response);
                        }

                        @Override
                        public void onFailure(Exception e) {
                            if (e instanceof org.opensearch.index.engine.VersionConflictEngineException && attempt < MAX_RETRIES) {
                                performUpdateWithRetry(request, listener, attempt + 1);
                            } else {
                                listener.onFailure(e);
                            }
                        }
                    };
                    abTestDao.putSnapshot(
                        snapshot,
                        ActionListener.wrap(
                            snapshotResponse -> abTestDao.updateABTestWithConcurrencyControl(
                                updatedTest,
                                seqNo,
                                primaryTerm,
                                concurrencyListener
                            ),
                            listener::onFailure
                        )
                    );
                } catch (Exception e) {
                    listener.onFailure(new SearchRelevanceException("Failed to update ABTest", e, RestStatus.INTERNAL_SERVER_ERROR));
                }
            }

            @Override
            public void onFailure(Exception e) {
                if (e instanceof org.opensearch.ResourceNotFoundException) {
                    listener.onFailure(new SearchRelevanceException("AB test not found", RestStatus.NOT_FOUND));
                } else {
                    listener.onFailure(new SearchRelevanceException("Failed to retrieve ABTest", e, RestStatus.INTERNAL_SERVER_ERROR));
                }
            }
        });
    }
}
