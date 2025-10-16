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
import static org.opensearch.searchrelevance.common.PluginConstants.REMOTE_SEARCH_CONFIGURATIONS_URL;

import java.io.IOException;
import java.util.Map;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.opensearch.ExceptionsHelper;
import org.opensearch.action.index.IndexResponse;
import org.opensearch.common.xcontent.XContentFactory;
import org.opensearch.core.action.ActionListener;
import org.opensearch.core.rest.RestStatus;
import org.opensearch.core.xcontent.XContentBuilder;
import org.opensearch.core.xcontent.XContentParser;
import org.opensearch.rest.BaseRestHandler;
import org.opensearch.rest.BytesRestResponse;
import org.opensearch.rest.RestRequest;
import org.opensearch.searchrelevance.dao.RemoteSearchConfigurationDao;
import org.opensearch.searchrelevance.model.RemoteSearchConfiguration;
import org.opensearch.searchrelevance.settings.SearchRelevanceSettingsAccessor;
import org.opensearch.searchrelevance.utils.TimeUtils;
import org.opensearch.transport.client.node.NodeClient;

/**
 * REST action to create or update a RemoteSearchConfiguration.
 * Route: POST /_plugins/_search_relevance/remote_search_configurations
 */
public class RestPutRemoteSearchConfigurationAction extends BaseRestHandler {
    private static final Logger LOGGER = LogManager.getLogger(RestPutRemoteSearchConfigurationAction.class);
    private static final String ACTION_NAME = "put_remote_search_configuration_action";

    private final SearchRelevanceSettingsAccessor settingsAccessor;
    private final RemoteSearchConfigurationDao remoteSearchConfigurationDao;

    public RestPutRemoteSearchConfigurationAction(
        SearchRelevanceSettingsAccessor settingsAccessor,
        RemoteSearchConfigurationDao remoteSearchConfigurationDao
    ) {
        this.settingsAccessor = settingsAccessor;
        this.remoteSearchConfigurationDao = remoteSearchConfigurationDao;
    }

    @Override
    public String getName() {
        return ACTION_NAME;
    }

    @Override
    public java.util.List<Route> routes() {
        return singletonList(new Route(POST, REMOTE_SEARCH_CONFIGURATIONS_URL));
    }

    @Override
    protected RestChannelConsumer prepareRequest(RestRequest request, NodeClient client) throws IOException {
        if (!settingsAccessor.isWorkbenchEnabled()) {
            return channel -> channel.sendResponse(new BytesRestResponse(RestStatus.FORBIDDEN, "Search Relevance Workbench is disabled"));
        }

        XContentParser parser = request.contentParser();
        Map<String, Object> source = parser.map();

        String id = asString(source.get(RemoteSearchConfiguration.ID));
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("id is required");
        }

        String name = asString(source.get(RemoteSearchConfiguration.NAME));
        String description = asString(source.get(RemoteSearchConfiguration.DESCRIPTION));
        String connectionUrl = asString(source.get(RemoteSearchConfiguration.CONNECTION_URL));
        String username = asString(source.get(RemoteSearchConfiguration.USERNAME));
        String password = asString(source.get(RemoteSearchConfiguration.PASSWORD));
        String queryTemplate = asString(source.get(RemoteSearchConfiguration.QUERY_TEMPLATE));
        String responseTemplate = asString(source.get(RemoteSearchConfiguration.RESPONSE_TEMPLATE));
        int maxRps = asInt(
            source.getOrDefault(
                RemoteSearchConfiguration.MAX_REQUESTS_PER_SECOND,
                RemoteSearchConfiguration.DEFAULT_MAX_REQUESTS_PER_SECOND
            )
        );
        int maxConcurrent = asInt(
            source.getOrDefault(
                RemoteSearchConfiguration.MAX_CONCURRENT_REQUESTS,
                RemoteSearchConfiguration.DEFAULT_MAX_CONCURRENT_REQUESTS
            )
        );
        long cacheMinutes = asLong(
            source.getOrDefault(RemoteSearchConfiguration.CACHE_DURATION_MINUTES, RemoteSearchConfiguration.DEFAULT_CACHE_DURATION_MINUTES)
        );
        boolean refreshCache = asBoolean(source.getOrDefault(RemoteSearchConfiguration.REFRESH_CACHE, Boolean.FALSE));
        @SuppressWarnings("unchecked")
        Map<String, Object> metadata = (Map<String, Object>) source.get(RemoteSearchConfiguration.METADATA);

        String timestamp = TimeUtils.getTimestamp();

        RemoteSearchConfiguration configuration = new RemoteSearchConfiguration(
            id,
            name,
            description,
            connectionUrl,
            username,
            password,
            queryTemplate,
            responseTemplate,
            maxRps,
            maxConcurrent,
            cacheMinutes,
            refreshCache,
            metadata,
            timestamp
        );

        return channel -> remoteSearchConfigurationDao.createRemoteSearchConfiguration(configuration, new ActionListener<IndexResponse>() {
            @Override
            public void onResponse(IndexResponse response) {
                try {
                    XContentBuilder builder = channel.newBuilder();
                    builder.startObject();
                    builder.field("id", id);
                    builder.field("result", response.getResult().name());
                    builder.field("status", response.status().name());
                    builder.endObject();
                    channel.sendResponse(new BytesRestResponse(RestStatus.OK, builder));
                } catch (IOException e) {
                    onFailure(e);
                }
            }

            @Override
            public void onFailure(Exception e) {
                try {
                    LOGGER.error("Failed to create remote search configuration {}", id, e);
                    channel.sendResponse(new BytesRestResponse(channel, ExceptionsHelper.status(e), e));
                } catch (IOException ex) {
                    try {
                        channel.sendResponse(new BytesRestResponse(channel, ex));
                    } catch (IOException ioEx) {
                        // ignored secondary failure
                    }
                }
            }
        });
    }

    private static String asString(Object o) {
        if (o == null) {
            return null;
        }
        if (o instanceof String) {
            return (String) o;
        }
        if (o instanceof Map) {
            // Convert Map to proper JSON string
            try (XContentBuilder builder = XContentFactory.jsonBuilder()) {
                builder.map((Map<String, Object>) o);
                return builder.toString();
            } catch (IOException e) {
                LOGGER.warn("Failed to serialize Map to JSON, falling back to toString(): {}", e.getMessage());
                return String.valueOf(o);
            }
        }
        return String.valueOf(o);
    }

    private static int asInt(Object o) {
        if (o == null) return 0;
        if (o instanceof Number) return ((Number) o).intValue();
        try {
            return Integer.parseInt(String.valueOf(o));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private static long asLong(Object o) {
        if (o == null) return 0L;
        if (o instanceof Number) return ((Number) o).longValue();
        try {
            return Long.parseLong(String.valueOf(o));
        } catch (NumberFormatException e) {
            return 0L;
        }
    }

    private static boolean asBoolean(Object o) {
        if (o == null) return false;
        if (o instanceof Boolean) return (Boolean) o;
        return Boolean.parseBoolean(String.valueOf(o));
    }
}
