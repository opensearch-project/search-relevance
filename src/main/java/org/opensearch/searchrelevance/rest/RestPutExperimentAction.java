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
import static org.opensearch.searchrelevance.common.PluginConstants.EXPERIMENTS_URI;
import static org.opensearch.searchrelevance.common.PluginConstants.JUDGMENT_LIST;
import static org.opensearch.searchrelevance.common.PluginConstants.QUERYSET_ID;
import static org.opensearch.searchrelevance.common.PluginConstants.SEARCH_CONFIGURATION_LIST;
import static org.opensearch.searchrelevance.common.PluginConstants.SIZE;
import static org.opensearch.searchrelevance.common.PluginConstants.TYPE;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import org.opensearch.ExceptionsHelper;
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
import org.opensearch.searchrelevance.transport.experiment.PutExperimentAction;
import org.opensearch.searchrelevance.transport.experiment.PutExperimentRequest;
import org.opensearch.searchrelevance.utils.ParserUtils;
import org.opensearch.transport.client.node.NodeClient;

import lombok.AllArgsConstructor;
import lombok.extern.log4j.Log4j2;

@Log4j2
/**
 * Rest Action to facilitate requests to create a experiment.
 */
@AllArgsConstructor
public class RestPutExperimentAction extends BaseRestHandler {
    private static final String PUT_EXPERIMENT_ACTION = "put_experiment_action";
    private SearchRelevanceSettingsAccessor settingsAccessor;

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
        if (!settingsAccessor.isWorkbenchEnabled()) {
            return channel -> channel.sendResponse(new BytesRestResponse(RestStatus.FORBIDDEN, "Search Relevance Workbench is disabled"));
        }
        XContentParser parser = request.contentParser();
        Map<String, Object> source = parser.map();

        String querySetId = (String) source.get(QUERYSET_ID);
        List<String> searchConfigurationList = ParserUtils.convertObjToList(source, SEARCH_CONFIGURATION_LIST);
        int size = (Integer) source.get(SIZE);
        List<String> judgmentList = ParserUtils.convertObjToList(source, JUDGMENT_LIST);

        String typeString = (String) source.get(TYPE);
        ExperimentType experimentType;

        try {
            experimentType = ExperimentType.valueOf(typeString);
        } catch (IllegalArgumentException | NullPointerException e) {
            throw new IllegalArgumentException("Invalid or missing experiment type");
        }

        if (searchConfigurationList == null || searchConfigurationList.isEmpty()) {
            throw new IllegalArgumentException("searchConfigurationList cannot be null or empty");
        }

        switch (experimentType) {
            case PAIRWISE_COMPARISON:
                if (searchConfigurationList.size() != 2) {
                    throw new SearchRelevanceException(
                        "PAIRWISE_COMPARISON requires exactly 2 search configurations",
                        RestStatus.BAD_REQUEST
                    );
                }
                if (searchConfigurationList.get(0).equals(searchConfigurationList.get(1))) {
                    throw new SearchRelevanceException(
                        "PAIRWISE_COMPARISON requires distinct search configurations",
                        RestStatus.BAD_REQUEST
                    );
                }
                break;

            case POINTWISE_EVALUATION:
                if (searchConfigurationList.size() != 1) {
                    throw new SearchRelevanceException(
                        "POINTWISE_EVALUATION requires exactly 1 search configuration",
                        RestStatus.BAD_REQUEST
                    );
                }
                break;

            case HYBRID_OPTIMIZER:
                if (searchConfigurationList.size() != 1) {
                    throw new SearchRelevanceException("HYBRID_OPTIMIZER requires exactly 1 search configuration", RestStatus.BAD_REQUEST);
                }
                break;

            default:
                throw new IllegalArgumentException("Unsupported experiment type: " + experimentType);
        }

        PutExperimentRequest createRequest = new PutExperimentRequest(
            experimentType,
            null,
            querySetId,
            searchConfigurationList,
            judgmentList,
            size
        );

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
                    channel.sendResponse(new BytesRestResponse(channel, ExceptionsHelper.status(e), e));
                } catch (IOException ex) {
                    log.error("Failed to send error response", ex);
                }
            }
        });
    }
}
