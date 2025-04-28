/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */
package org.opensearch.searchrelevance.rest;

import static java.util.Collections.singletonList;
import static org.opensearch.rest.RestRequest.Method.PUT;
import static org.opensearch.searchrelevance.common.MetricsConstants.DEFAULTED_SEARCH_CONFIG_MAX_COUNT;
import static org.opensearch.searchrelevance.common.MetricsConstants.JUDGMENT_IDS;
import static org.opensearch.searchrelevance.common.MetricsConstants.MODEL_ID;
import static org.opensearch.searchrelevance.common.PluginConstants.EXPERIMENTS_URI;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
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
import org.opensearch.searchrelevance.transport.experiment.PutExperimentAction;
import org.opensearch.searchrelevance.transport.experiment.PutExperimentRequest;
import org.opensearch.searchrelevance.transport.experiment.PutLlmExperimentRequest;
import org.opensearch.searchrelevance.transport.experiment.PutUbiExperimentRequest;
import org.opensearch.searchrelevance.utils.ParserUtils;
import org.opensearch.transport.client.node.NodeClient;

/**
 * Rest Action to facilitate requests to create a experiment.
 */
public class RestPutExperimentAction extends BaseRestHandler {
    private static final Logger LOGGER = LogManager.getLogger(RestPutExperimentAction.class);
    private static final String PUT_EXPERIMENT_ACTION = "put_experiment_action";

    @Override
    public String getName() {
        return PUT_EXPERIMENT_ACTION;
    }

    @Override
    public List<Route> routes() {
        return singletonList(new Route(PUT, EXPERIMENTS_URI));
    }

    @Override
    protected RestChannelConsumer prepareRequest(RestRequest request, NodeClient client) throws IOException {
        XContentParser parser = request.contentParser();
        Map<String, Object> source = parser.map();

        String index = (String) source.get("index");
        String querySetId = (String) source.get("querySetId");
        List<String> searchConfigurationList = ParserUtils.convertObjToList(source, "searchConfigurationList");
        int k = (Integer) source.get("k");

        String typeString = (String) source.get("type");
        ExperimentType type;
        try {
            type = ExperimentType.valueOf(typeString);
        } catch (IllegalArgumentException | NullPointerException e) {
            throw new IllegalArgumentException("Invalid or missing experiment type", e);
        }

        PutExperimentRequest createRequest;
        switch (type) {
            case PAIRWISE_COMPARISON:
                if (searchConfigurationList.size() != 2) {
                    throw new SearchRelevanceException(
                        "two and only two search configurations allowed for PAIRWISE_COMPARISON",
                        RestStatus.BAD_REQUEST
                    );
                }
                createRequest = new PutExperimentRequest(index, type, querySetId, searchConfigurationList, k);
                break;
            case LLM_EVALUATION:
                String modelId = (String) source.get(MODEL_ID);
                if (modelId == null) {
                    throw new SearchRelevanceException("modelId is required for LLM_EVALUATION", RestStatus.BAD_REQUEST);
                }
                if (searchConfigurationList.size() > DEFAULTED_SEARCH_CONFIG_MAX_COUNT) {
                    throw new SearchRelevanceException(
                        "evaluation experiment supports at most: " + DEFAULTED_SEARCH_CONFIG_MAX_COUNT + " searchConfigurations",
                        RestStatus.BAD_REQUEST
                    );
                }
                createRequest = new PutLlmExperimentRequest(index, type, querySetId, searchConfigurationList, k, modelId);
                break;
            case UBI_EVALUATION:
                List<String> judgmentIds = ParserUtils.convertObjToList(source, JUDGMENT_IDS);
                if (judgmentIds == null || judgmentIds.isEmpty()) {
                    throw new SearchRelevanceException("judgmentIds are required for UBI_EVALUATION", RestStatus.BAD_REQUEST);
                }
                if (searchConfigurationList.size() > DEFAULTED_SEARCH_CONFIG_MAX_COUNT) {
                    throw new SearchRelevanceException(
                        "evaluation experiment supports at most: " + DEFAULTED_SEARCH_CONFIG_MAX_COUNT + " searchConfigurations",
                        RestStatus.BAD_REQUEST
                    );
                }
                createRequest = new PutUbiExperimentRequest(index, type, querySetId, searchConfigurationList, k, judgmentIds);
                break;
            default:
                throw new SearchRelevanceException("Unsupported experiment type: " + type, RestStatus.BAD_REQUEST);
        }

        return channel -> client.execute(PutExperimentAction.INSTANCE, createRequest, new ActionListener<IndexResponse>() {
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
                    LOGGER.error("Failed to send error response", ex);
                }
            }
        });
    }
}
