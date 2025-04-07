/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */
package org.opensearch.searchrelevance.rest;

import static org.opensearch.rest.RestRequest.Method.GET;
import static org.opensearch.searchrelevance.common.PluginConstants.QUERYSETS_URL;
import static org.opensearch.searchrelevance.common.PluginConstants.QUERYSET_ID;

import java.io.IOException;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.opensearch.action.search.SearchResponse;
import org.opensearch.core.action.ActionListener;
import org.opensearch.core.rest.RestStatus;
import org.opensearch.core.xcontent.ToXContent;
import org.opensearch.core.xcontent.XContentBuilder;
import org.opensearch.core.xcontent.XContentParser;
import org.opensearch.index.query.QueryBuilders;
import org.opensearch.rest.BaseRestHandler;
import org.opensearch.rest.BytesRestResponse;
import org.opensearch.rest.RestRequest;
import org.opensearch.search.builder.SearchSourceBuilder;
import org.opensearch.search.sort.SortOrder;
import org.opensearch.searchrelevance.transport.GetQuerySetAction;
import org.opensearch.searchrelevance.transport.QuerySetRequest;
import org.opensearch.transport.client.node.NodeClient;

/**
 * Rest Action to facilitate requests to get/list query sets.
 */
public class RestGetQuerySetAction extends BaseRestHandler {
    private static final Logger LOGGER = LogManager.getLogger(RestGetQuerySetAction.class);
    private static final String GET_QUERYSET_ACTION = "get_queryset_action";
    private static final Integer DEFAULTED_LIST_SIZE = 1000;
    private static final SortOrder DEFAULTED_LIST_SORT = SortOrder.DESC;

    @Override
    public String getName() {
        return GET_QUERYSET_ACTION;
    }

    @Override
    public List<Route> routes() {
        return List.of(new Route(GET, String.format(Locale.ROOT, "%s/{%s}", QUERYSETS_URL, QUERYSET_ID)), new Route(GET, QUERYSETS_URL));
    }

    @Override
    protected RestChannelConsumer prepareRequest(RestRequest request, NodeClient client) throws IOException {
        final String querySetId = request.param(QUERYSET_ID);
        // If id is provided, get specific query set
        if (querySetId != null && !querySetId.isEmpty()) {
            QuerySetRequest getRequest = new QuerySetRequest(querySetId);
            return executeGetRequest(client, getRequest);
        }

        // Otherwise, handle list request
        SearchParams searchParams = parseSearchParams(request);
        SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder().query(QueryBuilders.matchAllQuery())
            .size(searchParams.getSize())
            .sort("_id", searchParams.getSortOrder());

        QuerySetRequest getRequest = new QuerySetRequest(searchSourceBuilder);
        return executeGetRequest(client, getRequest);
    }

    private static class SearchParams {
        private Integer size;
        private SortOrder sortOrder;

        public int getSize() {
            return Objects.requireNonNullElse(size, DEFAULTED_LIST_SIZE);
        }

        public SortOrder getSortOrder() {
            return Objects.requireNonNullElse(sortOrder, DEFAULTED_LIST_SORT);
        }

        public void setSize(Integer size) {
            this.size = size > 0 ? size : null;
        }

        public void setSortOrder(String sort) {
            if ("asc".equals(sort)) {
                this.sortOrder = SortOrder.ASC;
            }
        }
    }

    private SearchParams parseSearchParams(RestRequest request) throws IOException {
        SearchParams params = new SearchParams();

        if (request.hasContent()) {
            XContentParser parser = request.contentParser();

            XContentParser.Token token = parser.currentToken();
            if (token == null) {
                token = parser.nextToken();
            }

            if (token == XContentParser.Token.START_OBJECT) {
                while ((token = parser.nextToken()) != XContentParser.Token.END_OBJECT) {
                    if (token == XContentParser.Token.FIELD_NAME) {
                        String fieldName = parser.currentName();
                        token = parser.nextToken();

                        switch (fieldName) {
                            case "size":
                                if (token == XContentParser.Token.VALUE_NUMBER) {
                                    params.setSize(parser.intValue());
                                }
                                break;
                            case "sort":
                                if (token == XContentParser.Token.VALUE_STRING) {
                                    params.setSortOrder(parser.text());
                                }
                                break;
                        }
                    }
                }
            }
        }
        return params;
    }

    private RestChannelConsumer executeGetRequest(NodeClient client, QuerySetRequest request) {
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
