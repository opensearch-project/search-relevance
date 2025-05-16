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
import static org.opensearch.searchrelevance.common.PluginConstants.QUERYSETS_URL;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

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
import org.opensearch.searchrelevance.model.QueryWithReference;
import org.opensearch.searchrelevance.transport.queryset.PutQuerySetAction;
import org.opensearch.searchrelevance.transport.queryset.PutQuerySetRequest;
import org.opensearch.transport.client.node.NodeClient;

/**
 * Rest Action to facilitate requests to put a query set from manual input.
 */
public class RestPutQuerySetAction extends BaseRestHandler {
    private static final Logger LOGGER = LogManager.getLogger(RestPutQuerySetAction.class);
    private static final String PUT_QUERYSET_ACTION = "put_queryset_action";

    @Override
    public String getName() {
        return PUT_QUERYSET_ACTION;
    }

    @Override
    public List<Route> routes() {
        return singletonList(new Route(PUT, QUERYSETS_URL));
    }

    @Override
    protected RestChannelConsumer prepareRequest(RestRequest request, NodeClient client) throws IOException {
        XContentParser parser = request.contentParser();
        Map<String, Object> source = parser.map();

        String name = (String) source.get("name");
        String description = (String) source.get("description");
        // Default values for sampling as manual
        String sampling = (String) source.getOrDefault("sampling", "manual");

        List<QueryWithReference> querySetQueries;
        if (sampling.equals("manual")) {
            List<Object> rawQueries = (List<Object>) source.get("querySetQueries");
            querySetQueries = rawQueries.stream().map(obj -> {
                Map<String, String> queryMap = (Map<String, String>) obj;
                return new QueryWithReference(queryMap.get("queryText"), queryMap.getOrDefault("referenceAnswer", ""));
            }).collect(Collectors.toList());
        } else {
            querySetQueries = Collections.emptyList();
        }

        PutQuerySetRequest putRequest = new PutQuerySetRequest(name, description, sampling, querySetQueries);

        return channel -> client.execute(PutQuerySetAction.INSTANCE, putRequest, new ActionListener<IndexResponse>() {
            @Override
            public void onResponse(IndexResponse response) {
                try {
                    XContentBuilder builder = channel.newBuilder();
                    builder.startObject();
                    builder.field("query_set_id", response.getId());
                    builder.field("query_set_result", response.getResult());
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
