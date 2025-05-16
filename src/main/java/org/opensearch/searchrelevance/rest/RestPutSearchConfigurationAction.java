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
import static org.opensearch.searchrelevance.common.PluginConstants.SEARCH_CONFIGURATIONS_URL;

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
import org.opensearch.searchrelevance.transport.searchConfiguration.PutSearchConfigurationAction;
import org.opensearch.searchrelevance.transport.searchConfiguration.PutSearchConfigurationRequest;
import org.opensearch.transport.client.node.NodeClient;

/**
 * Rest Action to facilitate requests to create a search configuration.
 */
public class RestPutSearchConfigurationAction extends BaseRestHandler {
    private static final Logger LOGGER = LogManager.getLogger(RestPutSearchConfigurationAction.class);
    private static final String PUT_SEARCH_CONFIGURATION_ACTION = "put_search_configuration_action";

    @Override
    public String getName() {
        return PUT_SEARCH_CONFIGURATION_ACTION;
    }

    @Override
    public List<Route> routes() {
        return singletonList(new Route(PUT, SEARCH_CONFIGURATIONS_URL));
    }

    @Override
    protected RestChannelConsumer prepareRequest(RestRequest request, NodeClient client) throws IOException {
        XContentParser parser = request.contentParser();
        Map<String, Object> source = parser.map();

        String name = (String) source.get("name");
        String index = (String) source.get("index");
        String queryBody = (String) source.get("query");
        String searchPipeline = (String) source.getOrDefault("searchPipeline", "");

        PutSearchConfigurationRequest createRequest = new PutSearchConfigurationRequest(name, index, queryBody, searchPipeline);

        return channel -> client.execute(PutSearchConfigurationAction.INSTANCE, createRequest, new ActionListener<IndexResponse>() {
            @Override
            public void onResponse(IndexResponse response) {
                try {
                    XContentBuilder builder = channel.newBuilder();
                    builder.startObject();
                    builder.field("search_configuration_id", response.getId());
                    builder.field("search_configuration_result", response.getResult());
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
