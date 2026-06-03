/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */
package org.opensearch.searchrelevance.transport.experiment;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import org.opensearch.action.support.ActionFilters;
import org.opensearch.action.support.HandledTransportAction;
import org.opensearch.common.inject.Inject;
import org.opensearch.core.action.ActionListener;
import org.opensearch.core.rest.RestStatus;
import org.opensearch.searchrelevance.dao.ExperimentDao;
import org.opensearch.searchrelevance.dao.JudgmentDao;
import org.opensearch.searchrelevance.dao.QuerySetDao;
import org.opensearch.searchrelevance.dao.SearchConfigurationDao;
import org.opensearch.searchrelevance.exception.SearchRelevanceException;
import org.opensearch.searchrelevance.experiment.signature.ExperimentInputSignatureComputer;
import org.opensearch.searchrelevance.model.Experiment;
import org.opensearch.searchrelevance.model.ExperimentDocumentParser;
import org.opensearch.searchrelevance.model.ExperimentInputSignature;
import org.opensearch.searchrelevance.model.Judgment;
import org.opensearch.searchrelevance.model.QuerySet;
import org.opensearch.searchrelevance.model.SearchConfiguration;
import org.opensearch.searchrelevance.model.SearchConfigurationDetails;
import org.opensearch.searchrelevance.model.SystemIndexConverters;
import org.opensearch.searchrelevance.transport.OpenSearchDocRequest;
import org.opensearch.tasks.Task;
import org.opensearch.transport.TransportService;

/**
 * Loads current inputs and compares them to the stored {@link ExperimentInputSignature}.
 */
public class ValidateExperimentTransportAction extends HandledTransportAction<OpenSearchDocRequest, ValidateExperimentResponse> {

    private final ExperimentDao experimentDao;
    private final QuerySetDao querySetDao;
    private final SearchConfigurationDao searchConfigurationDao;
    private final JudgmentDao judgmentDao;

    @Inject
    public ValidateExperimentTransportAction(
        TransportService transportService,
        ActionFilters actionFilters,
        ExperimentDao experimentDao,
        QuerySetDao querySetDao,
        SearchConfigurationDao searchConfigurationDao,
        JudgmentDao judgmentDao
    ) {
        super(ValidateExperimentAction.NAME, transportService, actionFilters, OpenSearchDocRequest::new);
        this.experimentDao = experimentDao;
        this.querySetDao = querySetDao;
        this.searchConfigurationDao = searchConfigurationDao;
        this.judgmentDao = judgmentDao;
    }

    @Override
    protected void doExecute(Task task, OpenSearchDocRequest request, ActionListener<ValidateExperimentResponse> listener) {
        String experimentId = request.getId();
        if (experimentId == null || experimentId.isBlank()) {
            listener.onFailure(new SearchRelevanceException("Experiment id is required", RestStatus.BAD_REQUEST));
            return;
        }
        experimentDao.getExperiment(experimentId, ActionListener.wrap(response -> {
            if (response.getHits().getTotalHits().value() == 0) {
                listener.onFailure(new SearchRelevanceException("Experiment not found: " + experimentId, RestStatus.NOT_FOUND));
                return;
            }
            Map<String, Object> source = response.getHits().getHits()[0].getSourceAsMap();
            Experiment experiment = ExperimentDocumentParser.fromSourceMap(source);
            if (experiment.inputSignature() == null) {
                listener.onResponse(
                    ValidateExperimentResponse.unavailable(
                        "Experiment input signature is not available for this experiment (legacy or in-flight run)."
                    )
                );
                return;
            }
            validateLiveInputs(experiment, listener);
        }, listener::onFailure));
    }

    private void validateLiveInputs(Experiment experiment, ActionListener<ValidateExperimentResponse> listener) {
        querySetDao.getQuerySet(experiment.querySetId(), ActionListener.wrap(qsResponse -> {
            try {
                QuerySet querySet = SystemIndexConverters.toQuerySet(qsResponse);
                fetchSearchConfigurationsInParallel(experiment, querySet, listener);
            } catch (SearchRelevanceException e) {
                if (e.status() == RestStatus.NOT_FOUND) {
                    listener.onResponse(
                        ValidateExperimentResponse.drifted(
                            List.of(ExperimentInputSignature.QUERY_SET),
                            "QuerySet has changed or is no longer available."
                        )
                    );
                } else {
                    listener.onFailure(e);
                }
            } catch (Exception e) {
                listener.onFailure(e);
            }
        },
            e -> listener.onFailure(
                new SearchRelevanceException("Failed to load query set for drift validation", e, RestStatus.INTERNAL_SERVER_ERROR)
            )
        ));
    }

    private void fetchSearchConfigurationsInParallel(
        Experiment experiment,
        QuerySet querySet,
        ActionListener<ValidateExperimentResponse> listener
    ) {
        List<String> ids = experiment.searchConfigurationList() == null ? List.of() : experiment.searchConfigurationList();
        if (ids.isEmpty()) {
            loadJudgmentsThenRespond(experiment, querySet, Map.of(), listener);
            return;
        }
        List<SearchConfiguration> buffer = Collections.synchronizedList(new ArrayList<>(Collections.nCopies(ids.size(), null)));
        AtomicInteger pending = new AtomicInteger(ids.size());
        AtomicBoolean failed = new AtomicBoolean(false);
        for (int i = 0; i < ids.size(); i++) {
            final int idx = i;
            String configId = ids.get(i);
            searchConfigurationDao.getSearchConfiguration(configId, ActionListener.wrap(sr -> {
                if (failed.get()) {
                    return;
                }
                try {
                    buffer.set(idx, SystemIndexConverters.toSearchConfiguration(sr));
                } catch (SearchRelevanceException e) {
                    if (failed.compareAndSet(false, true)) {
                        if (e.status() == RestStatus.NOT_FOUND) {
                            listener.onResponse(
                                ValidateExperimentResponse.drifted(
                                    List.of(ExperimentInputSignature.SEARCH_CONFIGURATIONS),
                                    "One or more search configurations have changed or are no longer available."
                                )
                            );
                        } else {
                            listener.onFailure(e);
                        }
                    }
                    return;
                } catch (Exception e) {
                    if (failed.compareAndSet(false, true)) {
                        listener.onFailure(e);
                    }
                    return;
                }
                if (pending.decrementAndGet() == 0 && !failed.get()) {
                    Map<String, SearchConfigurationDetails> detailsById = new HashMap<>();
                    for (SearchConfiguration c : buffer) {
                        if (c != null) {
                            detailsById.put(
                                c.id(),
                                SearchConfigurationDetails.builder().index(c.index()).query(c.query()).pipeline(c.searchPipeline()).build()
                            );
                        }
                    }
                    loadJudgmentsThenRespond(experiment, querySet, detailsById, listener);
                }
            }, e -> {
                if (failed.compareAndSet(false, true)) {
                    listener.onFailure(
                        new SearchRelevanceException(
                            "Failed to load search configuration for drift validation: " + configId,
                            e,
                            RestStatus.INTERNAL_SERVER_ERROR
                        )
                    );
                }
            }));
        }
    }

    private void loadJudgmentsThenRespond(
        Experiment experiment,
        QuerySet querySet,
        Map<String, SearchConfigurationDetails> searchConfigurationsById,
        ActionListener<ValidateExperimentResponse> listener
    ) {
        List<String> judgmentIds = experiment.judgmentList() == null ? List.of() : experiment.judgmentList();
        if (judgmentIds.isEmpty()) {
            respondWithComparison(experiment, querySet, searchConfigurationsById, List.of(), listener);
            return;
        }
        List<Judgment> buffer = Collections.synchronizedList(new ArrayList<>(Collections.nCopies(judgmentIds.size(), null)));
        AtomicInteger pending = new AtomicInteger(judgmentIds.size());
        AtomicBoolean failed = new AtomicBoolean(false);
        for (int i = 0; i < judgmentIds.size(); i++) {
            final int idx = i;
            String jid = judgmentIds.get(i);
            judgmentDao.getJudgment(jid, ActionListener.wrap(jr -> {
                if (failed.get()) {
                    return;
                }
                try {
                    buffer.set(idx, SystemIndexConverters.toJudgment(jr));
                } catch (SearchRelevanceException e) {
                    if (failed.compareAndSet(false, true)) {
                        if (e.status() == RestStatus.NOT_FOUND) {
                            listener.onResponse(
                                ValidateExperimentResponse.drifted(
                                    List.of(ExperimentInputSignature.JUDGMENT_LIST),
                                    "One or more judgments have changed or are no longer available."
                                )
                            );
                        } else {
                            listener.onFailure(e);
                        }
                    }
                    return;
                } catch (Exception e) {
                    if (failed.compareAndSet(false, true)) {
                        listener.onFailure(e);
                    }
                    return;
                }
                if (pending.decrementAndGet() == 0 && !failed.get()) {
                    respondWithComparison(experiment, querySet, searchConfigurationsById, new ArrayList<>(buffer), listener);
                }
            }, e -> {
                if (failed.compareAndSet(false, true)) {
                    listener.onFailure(
                        new SearchRelevanceException(
                            "Failed to load judgment for drift validation: " + jid,
                            e,
                            RestStatus.INTERNAL_SERVER_ERROR
                        )
                    );
                }
            }));
        }
    }

    private void respondWithComparison(
        Experiment experiment,
        QuerySet querySet,
        Map<String, SearchConfigurationDetails> searchConfigurationsById,
        List<Judgment> judgments,
        ActionListener<ValidateExperimentResponse> listener
    ) {
        try {
            ExperimentInputSignature live = ExperimentInputSignatureComputer.compute(
                querySet,
                experiment.searchConfigurationList(),
                searchConfigurationsById,
                judgments
            );
            ExperimentInputSignature stored = experiment.inputSignature();
            List<String> drifted = new ArrayList<>();
            if (!stored.querySetSha256().equals(live.querySetSha256())) {
                drifted.add(ExperimentInputSignature.QUERY_SET);
            }
            if (!stored.judgmentListSha256().equals(live.judgmentListSha256())) {
                drifted.add(ExperimentInputSignature.JUDGMENT_LIST);
            }
            if (!stored.searchConfigurationsSha256().equals(live.searchConfigurationsSha256())) {
                drifted.add(ExperimentInputSignature.SEARCH_CONFIGURATIONS);
            }
            if (drifted.isEmpty()) {
                listener.onResponse(ValidateExperimentResponse.valid());
            } else {
                listener.onResponse(ValidateExperimentResponse.drifted(drifted, buildDriftMessage(drifted)));
            }
        } catch (Exception e) {
            listener.onFailure(new SearchRelevanceException("Failed to validate experiment inputs", e, RestStatus.INTERNAL_SERVER_ERROR));
        }
    }

    private static String buildDriftMessage(List<String> drifted) {
        if (drifted.contains(ExperimentInputSignature.QUERY_SET) && drifted.size() == 1) {
            return "QuerySet has changed since execution.";
        }
        if (drifted.contains(ExperimentInputSignature.JUDGMENT_LIST) && drifted.size() == 1) {
            return "Judgment data has changed since execution.";
        }
        if (drifted.contains(ExperimentInputSignature.SEARCH_CONFIGURATIONS) && drifted.size() == 1) {
            return "Search configuration data has changed since execution.";
        }
        return "One or more experiment inputs have changed since execution.";
    }
}
