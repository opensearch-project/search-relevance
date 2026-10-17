/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */
package org.opensearch.searchrelevance.rest;

import static java.util.Collections.singletonList;
import static org.opensearch.rest.RestRequest.Method.POST;
import static org.opensearch.searchrelevance.common.MetricsConstants.PAIRWISE_FIELD_NAME_DOC_IDS;
import static org.opensearch.searchrelevance.common.MetricsConstants.PAIRWISE_FIELD_NAME_SNAPSHOTS;
import static org.opensearch.searchrelevance.common.MetricsConstants.QUERY_TEXT;
import static org.opensearch.searchrelevance.common.PluginConstants.EVALUATION_RESULT_LIST;
import static org.opensearch.searchrelevance.common.PluginConstants.EXPERIMENTS_URI;
import static org.opensearch.searchrelevance.common.PluginConstants.JUDGMENT_LIST;
import static org.opensearch.searchrelevance.common.PluginConstants.QUERYSET_ID;
import static org.opensearch.searchrelevance.common.PluginConstants.RESULTS;
import static org.opensearch.searchrelevance.common.PluginConstants.SEARCH_CONFIGURATION_LIST;
import static org.opensearch.searchrelevance.common.PluginConstants.SIZE;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import org.opensearch.action.index.IndexResponse;
import org.opensearch.core.action.ActionListener;
import org.opensearch.core.rest.RestStatus;
import org.opensearch.core.xcontent.XContentBuilder;
import org.opensearch.core.xcontent.XContentParser;
import org.opensearch.rest.BaseRestHandler;
import org.opensearch.rest.BytesRestResponse;
import org.opensearch.rest.RestRequest;
import org.opensearch.searchrelevance.exception.SearchRelevanceException;
import org.opensearch.searchrelevance.model.ExperimentType;
import org.opensearch.searchrelevance.settings.SearchRelevanceSettingsAccessor;
import org.opensearch.searchrelevance.transport.experiment.PostExperimentAction;
import org.opensearch.searchrelevance.transport.experiment.PostExperimentRequest;
import org.opensearch.searchrelevance.utils.ParserUtils;
import org.opensearch.searchrelevance.utils.TextValidationUtil;
import org.opensearch.transport.client.node.NodeClient;

import lombok.AllArgsConstructor;
import lombok.extern.log4j.Log4j2;

@Log4j2
/**
 * Rest Action to facilitate requests to import a experiment.
 */
@AllArgsConstructor
public class RestPostExperimentAction extends BaseRestHandler {
    private static final String POST_EXPERIMENT_ACTION = "post_experiment_action";
    private SearchRelevanceSettingsAccessor settingsAccessor;

    @Override
    public String getName() {
        return POST_EXPERIMENT_ACTION;
    }

    @Override
    public List<Route> routes() {
        return singletonList(new Route(POST, EXPERIMENTS_URI));
    }

    @Override
    protected RestChannelConsumer prepareRequest(RestRequest request, NodeClient client) throws IOException {
        if (!settingsAccessor.isWorkbenchEnabled()) {
            return channel -> channel.sendResponse(new BytesRestResponse(RestStatus.FORBIDDEN, "Search Relevance Workbench is disabled"));
        }

        PostExperimentRequest createRequest = null;
        XContentParser parser = request.contentParser();
        Map<String, Object> source = parser.map();

        String typeString = (String) source.get("type");
        ExperimentType type;
        try {
            type = ExperimentType.valueOf(typeString);
        } catch (IllegalArgumentException | NullPointerException e) {
            throw new IllegalArgumentException("Invalid or missing experiment type", e);
        }

        if (type == ExperimentType.POINTWISE_EVALUATION) {
            String querySetId = (String) source.get(QUERYSET_ID);
            List<String> searchConfigurationList = ParserUtils.convertObjToList(source, SEARCH_CONFIGURATION_LIST);
            if (searchConfigurationList.size() != 1) {
                throw new IOException("Must have exactly one search configuration. Had " + searchConfigurationList.size() + " size.");
            }
            Integer sizeObj = (Integer) source.get(SIZE);
            int size = sizeObj != null ? sizeObj.intValue() : 10; // Default size to 10 if not provided

            List<String> judgmentList = ParserUtils.convertObjToList(source, JUDGMENT_LIST);
            if (judgmentList.size() != 1) {
                throw new IOException("Must have exactly one judgment list. Had " + judgmentList.size() + " size.");
            }

            List<Map<String, Object>> evaluationResultList = ParserUtils.convertObjToListOfMaps(source, EVALUATION_RESULT_LIST);

            BytesRestResponse limitError = validateQueryCount(evaluationResultList);
            if (limitError != null) {
                return channel -> channel.sendResponse(limitError);
            }
            BytesRestResponse fieldError = validatePointwiseEntries(evaluationResultList);
            if (fieldError != null) {
                return channel -> channel.sendResponse(fieldError);
            }

            createRequest = new PostExperimentRequest(
                type,
                querySetId,
                searchConfigurationList,
                judgmentList,
                size,
                evaluationResultList,
                null
            );
        } else if (type == ExperimentType.PAIRWISE_COMPARISON) {
            String querySetId = (String) source.get(QUERYSET_ID);
            List<String> searchConfigurationList = ParserUtils.convertObjToList(source, SEARCH_CONFIGURATION_LIST);
            if (searchConfigurationList.size() != 2) {
                throw new IOException("Must have exactly two search configurations. Had " + searchConfigurationList.size() + " size.");
            }
            Integer sizeObj = (Integer) source.get(SIZE);
            int size = sizeObj != null ? sizeObj.intValue() : 10; // Default size to 10 if not provided

            List<Map<String, Object>> results = ParserUtils.convertObjToListOfMaps(source, RESULTS);

            BytesRestResponse limitError = validateQueryCount(results);
            if (limitError != null) {
                return channel -> channel.sendResponse(limitError);
            }
            BytesRestResponse fieldError = validatePairwiseEntries(results);
            if (fieldError != null) {
                return channel -> channel.sendResponse(fieldError);
            }

            createRequest = new PostExperimentRequest(type, querySetId, searchConfigurationList, List.of(), size, null, results);
        } else {
            throw new SearchRelevanceException("Importing experimentType" + type + " is not supported", RestStatus.BAD_REQUEST);
        }

        PostExperimentRequest finalCreateRequest = createRequest;
        return channel -> client.execute(PostExperimentAction.INSTANCE, finalCreateRequest, new ActionListener<IndexResponse>() {
            @Override
            public void onResponse(IndexResponse response) {
                try {
                    XContentBuilder builder = channel.newBuilder();
                    builder.startObject();
                    builder.field("experiment_id", response.getId());
                    builder.field("experiment_result", response.getResult());
                    builder.endObject();
                    channel.sendResponse(new BytesRestResponse(RestStatus.OK, builder));
                } catch (IOException e) {
                    onFailure(e);
                }
            }

            @Override
            public void onFailure(Exception e) {
                try {
                    channel.sendResponse(new BytesRestResponse(channel, RestStatus.INTERNAL_SERVER_ERROR, e));
                } catch (IOException ex) {
                    log.error("Failed to send error response", ex);
                }
            }
        });
    }

    /**
     * Rejects the import if it carries more per-query result entries than
     * {@link SearchRelevanceSettingsAccessor#getMaxExperimentImportQueriesAllowed()} allows.
     * Checked before any per-entry parsing/validation, so an oversized import is rejected
     * as cheaply as possible.
     */
    private BytesRestResponse validateQueryCount(List<Map<String, Object>> entries) {
        int maxQueries = settingsAccessor.getMaxExperimentImportQueriesAllowed();
        if (entries != null && entries.size() > maxQueries) {
            return new BytesRestResponse(
                RestStatus.FORBIDDEN,
                "Experiment Import Query Limit Exceeded. Maximum allowed: " + maxQueries
            );
        }
        return null;
    }

    /**
     * Validates each POINTWISE_EVALUATION entry's query text and document ID count.
     */
    private BytesRestResponse validatePointwiseEntries(List<Map<String, Object>> entries) {
        for (Map<String, Object> entry : entries) {
            Object searchText = entry.get("searchText") != null ? entry.get("searchText") : entry.get("queryText");
            TextValidationUtil.ValidationResult textResult = TextValidationUtil.validateText(
                searchText != null ? searchText.toString() : null
            );
            if (!textResult.isValid()) {
                return new BytesRestResponse(RestStatus.BAD_REQUEST, "Invalid searchText: " + textResult.getErrorMessage());
            }

            BytesRestResponse resultSizeError = validateResultsPerQuery((List<?>) entry.get("documentIds"));
            if (resultSizeError != null) {
                return resultSizeError;
            }
        }
        return null;
    }

    /**
     * Validates each PAIRWISE_COMPARISON entry's query text and, for every snapshot, its
     * document ID count.
     */
    @SuppressWarnings("unchecked")
    private BytesRestResponse validatePairwiseEntries(List<Map<String, Object>> entries) {
        for (Map<String, Object> entry : entries) {
            Object queryText = entry.get(QUERY_TEXT) != null ? entry.get(QUERY_TEXT) : entry.get("searchText");
            TextValidationUtil.ValidationResult textResult = TextValidationUtil.validateText(
                queryText != null ? queryText.toString() : null
            );
            if (!textResult.isValid()) {
                return new BytesRestResponse(RestStatus.BAD_REQUEST, "Invalid queryText: " + textResult.getErrorMessage());
            }

            List<Map<String, Object>> snapshots = (List<Map<String, Object>>) entry.get(PAIRWISE_FIELD_NAME_SNAPSHOTS);
            if (snapshots != null) {
                for (Map<String, Object> snapshot : snapshots) {
                    BytesRestResponse resultSizeError = validateResultsPerQuery((List<?>) snapshot.get(PAIRWISE_FIELD_NAME_DOC_IDS));
                    if (resultSizeError != null) {
                        return resultSizeError;
                    }
                }
            }
        }
        return null;
    }

    private BytesRestResponse validateResultsPerQuery(List<?> documentIds) {
        int maxResultsPerQuery = settingsAccessor.getMaxExperimentImportResultsPerQueryAllowed();
        if (documentIds != null && documentIds.size() > maxResultsPerQuery) {
            return new BytesRestResponse(
                RestStatus.FORBIDDEN,
                "Experiment Import Results Per Query Limit Exceeded. Maximum allowed: " + maxResultsPerQuery
            );
        }
        return null;
    }
}
