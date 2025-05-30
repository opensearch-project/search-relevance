/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */
package org.opensearch.searchrelevance.rest;

import static org.opensearch.rest.RestRequest.Method.GET;
import static org.opensearch.searchrelevance.common.PluginConstants.DOCUMENT_ID;
import static org.opensearch.searchrelevance.common.PluginConstants.QUERYSETS_URL;

import java.io.IOException;
import java.util.List;
import java.util.Locale;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
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
import org.opensearch.searchrelevance.model.SearchParams;
import org.opensearch.searchrelevance.settings.SearchRelevanceSettingsAccessor;
import org.opensearch.searchrelevance.transport.OpenSearchDocRequest;
import org.opensearch.searchrelevance.transport.queryset.GetQuerySetAction;
import org.opensearch.searchrelevance.utils.ParserUtils;
import org.opensearch.transport.client.node.NodeClient;

import lombok.AllArgsConstructor;

/**
 * Rest Action to facilitate requests to get/list query sets.
 */
@AllArgsConstructor
public class RestGetQuerySetAction extends BaseRestHandler {
    private static final Logger LOGGER = LogManager.getLogger(RestGetQuerySetAction.class);
    private static final String GET_QUERYSET_ACTION = "get_queryset_action";
    private SearchRelevanceSettingsAccessor settingsAccessor;

    @Override
    public String getName() {
        return GET_QUERYSET_ACTION;
    }

    @Override
    public List<Route> routes() {
        return List.of(new Route(GET, String.format(Locale.ROOT, "%s/{%s}", QUERYSETS_URL, DOCUMENT_ID)), new Route(GET, QUERYSETS_URL));
    }

    @Override
    protected RestChannelConsumer prepareRequest(RestRequest request, NodeClient client) throws IOException {
        if (!settingsAccessor.isWorkbenchEnabled()) {
            return channel -> channel.sendResponse(new BytesRestResponse(RestStatus.FORBIDDEN, "Search Relevance Workbench is disabled"));
        }
        final String querySetId = request.param(DOCUMENT_ID);
        // If id is provided, get specific query set
        if (querySetId != null && !querySetId.isEmpty()) {
            OpenSearchDocRequest getRequest = new OpenSearchDocRequest(querySetId);
            return executeGetRequest(client, getRequest);
        }

        // Otherwise, handle list request
        SearchParams searchParams = ParserUtils.parseSearchParams(request);
        SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder().query(QueryBuilders.matchAllQuery())
            .size(searchParams.getSize())
            .sort(searchParams.getSortField(), searchParams.getSortOrder());

        OpenSearchDocRequest getRequest = new OpenSearchDocRequest(searchSourceBuilder);
        return executeGetRequest(client, getRequest);
    }

    private RestChannelConsumer executeGetRequest(NodeClient client, OpenSearchDocRequest request) {
        return channel -> client.execute(GetQuerySetAction.INSTANCE, request, new ActionListener<SearchResponse>() {
            @Override
            public void onResponse(SearchResponse response) {
                try {
                    XContentBuilder builder = channel.newBuilder();
                    response.toXContent(builder, ToXContent.EMPTY_PARAMS);
                    RestStatus status = response.status();
                    channel.sendResponse(new BytesRestResponse(status, builder));
                } catch (IOException e) {
                    onFailure(e);
                }
            }

            @Override
            public void onFailure(Exception e) {
                try {
                    channel.sendResponse(new BytesRestResponse(channel, e));
                } catch (IOException ex) {
                    logger.error("Failed to send error response", ex);
                }
            }
        });
    }
}
