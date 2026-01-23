/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */
package org.opensearch.searchrelevance.rest;

import static org.opensearch.rest.RestRequest.Method.GET;
import static org.opensearch.rest.RestRequest.Method.POST;
import static org.opensearch.searchrelevance.common.PluginConstants.EXPERIMENTS_URI;

import java.io.IOException;
import java.util.List;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.opensearch.ExceptionsHelper;
import org.opensearch.action.search.SearchRequest;
import org.opensearch.action.search.SearchResponse;
import org.opensearch.core.action.ActionListener;
import org.opensearch.core.rest.RestStatus;
import org.opensearch.core.xcontent.ToXContent;
import org.opensearch.core.xcontent.XContentBuilder;
import org.opensearch.index.query.QueryBuilders;
import org.opensearch.rest.BaseRestHandler;
import org.opensearch.rest.BytesRestResponse;
import org.opensearch.rest.RestRequest;
import org.opensearch.search.builder.SearchSourceBuilder;
import org.opensearch.searchrelevance.common.PluginConstants;
import org.opensearch.searchrelevance.settings.SearchRelevanceSettingsAccessor;
import org.opensearch.searchrelevance.transport.experiment.SearchExperimentAction;
import org.opensearch.transport.client.node.NodeClient;

import lombok.AllArgsConstructor;

/**
 * Rest action that searches experiments using OpenSearch query DSL.
 */
@AllArgsConstructor
public class RestSearchExperimentAction extends BaseRestHandler {
    private static final Logger LOGGER = LogManager.getLogger(RestSearchExperimentAction.class);
    private static final String SEARCH_EXPERIMENT_ACTION = "search_experiment_action";
    private final SearchRelevanceSettingsAccessor settingsAccessor;

    @Override
    public String getName() {
        return SEARCH_EXPERIMENT_ACTION;
    }

    @Override
    public List<Route> routes() {
        String searchPath = EXPERIMENTS_URI + "/_search";
        return List.of(new Route(GET, searchPath), new Route(POST, searchPath));
    }

    @Override
    protected RestChannelConsumer prepareRequest(RestRequest request, NodeClient client) throws IOException {
        if (!settingsAccessor.isWorkbenchEnabled()) {
            return channel -> channel.sendResponse(new BytesRestResponse(RestStatus.FORBIDDEN, "Search Relevance Workbench is disabled"));
        }

        SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder();
        if (request.hasContentOrSourceParam()) {
            searchSourceBuilder.parseXContent(request.contentOrSourceParamParser());
            // If _source was not explicitly set, exclude results field by default
            if (searchSourceBuilder.fetchSource() == null) {
                searchSourceBuilder.fetchSource(null, new String[] { "results" });
            }
        } else {
            searchSourceBuilder.query(QueryBuilders.matchAllQuery())
                .size(PluginConstants.DEFAULTED_QUERY_SET_SIZE)
                .fetchSource(null, new String[] { "results" });
        }

        SearchRequest searchRequest = new SearchRequest().source(searchSourceBuilder);

        return channel -> client.execute(SearchExperimentAction.INSTANCE, searchRequest, new ActionListener<>() {
            @Override
            public void onResponse(SearchResponse response) {
                try {
                    XContentBuilder builder = channel.newBuilder();
                    response.toXContent(builder, ToXContent.EMPTY_PARAMS);
                    channel.sendResponse(new BytesRestResponse(response.status(), builder));
                } catch (IOException e) {
                    onFailure(e);
                }
            }

            @Override
            public void onFailure(Exception e) {
                try {
                    channel.sendResponse(new BytesRestResponse(channel, ExceptionsHelper.status(e), e));
                } catch (IOException ex) {
                    LOGGER.error("Failed to send error response", ex);
                }
            }
        });
    }
}
