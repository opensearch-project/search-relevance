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
import static org.opensearch.searchrelevance.common.PluginConstants.REMOTE_SEARCH_EXECUTE_URL;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.opensearch.ExceptionsHelper;
import org.opensearch.core.action.ActionListener;
import org.opensearch.core.rest.RestStatus;
import org.opensearch.core.xcontent.XContentBuilder;
import org.opensearch.core.xcontent.XContentParser;
import org.opensearch.rest.BaseRestHandler;
import org.opensearch.rest.BytesRestResponse;
import org.opensearch.rest.RestRequest;
import org.opensearch.searchrelevance.dao.RemoteSearchCacheDao;
import org.opensearch.searchrelevance.dao.RemoteSearchConfigurationDao;
import org.opensearch.searchrelevance.dao.RemoteSearchFailureDao;
import org.opensearch.searchrelevance.executors.RemoteResponseMapper;
import org.opensearch.searchrelevance.executors.RemoteSearchExecutor;
import org.opensearch.searchrelevance.settings.SearchRelevanceSettingsAccessor;
import org.opensearch.searchrelevance.utils.ResponseValidationUtils;
import org.opensearch.transport.client.node.NodeClient;

/**
 * REST action for executing a remote search via configured remote search configuration.
 * Route: POST /_plugins/_search_relevance/remote_search/execute
 * Body: { "remoteConfigId": "...", "queryText": "...", "size": 3, "query": "{...}" }
 *
 * If "query" is omitted, the configuration's queryTemplate should produce the outbound payload.
 */
public class RestRemoteSearchExecuteAction extends BaseRestHandler {

    private static final String ACTION_NAME = "remote_search_execute_action";
    private final SearchRelevanceSettingsAccessor settingsAccessor;
    private final RemoteSearchConfigurationDao remoteSearchConfigurationDao;
    private final RemoteSearchCacheDao remoteSearchCacheDao;
    private final RemoteSearchFailureDao remoteSearchFailureDao;

    public RestRemoteSearchExecuteAction(
        SearchRelevanceSettingsAccessor settingsAccessor,
        RemoteSearchConfigurationDao remoteSearchConfigurationDao,
        RemoteSearchCacheDao remoteSearchCacheDao,
        RemoteSearchFailureDao remoteSearchFailureDao
    ) {
        this.settingsAccessor = settingsAccessor;
        this.remoteSearchConfigurationDao = remoteSearchConfigurationDao;
        this.remoteSearchCacheDao = remoteSearchCacheDao;
        this.remoteSearchFailureDao = remoteSearchFailureDao;
    }

    @Override
    public String getName() {
        return ACTION_NAME;
    }

    @Override
    public List<Route> routes() {
        return singletonList(new Route(POST, REMOTE_SEARCH_EXECUTE_URL));
    }

    @Override
    protected RestChannelConsumer prepareRequest(RestRequest request, NodeClient client) throws IOException {
        if (!settingsAccessor.isWorkbenchEnabled()) {
            return channel -> channel.sendResponse(new BytesRestResponse(RestStatus.FORBIDDEN, "Search Relevance Workbench is disabled"));
        }

        XContentParser parser = request.contentParser();
        Map<String, Object> source = parser.map();

        String remoteConfigId = asString(source.get("remoteConfigId"));
        String queryText = asString(source.get("queryText"));
        String query = asString(source.getOrDefault("query", "{}"));
        int size = asInt(source.getOrDefault("size", 3));

        if (remoteConfigId == null || remoteConfigId.isBlank()) {
            throw new IllegalArgumentException("remoteConfigId is required");
        }
        final String queryTextFinal = (queryText == null) ? "" : queryText;

        RemoteSearchExecutor executor = new RemoteSearchExecutor(
            remoteSearchConfigurationDao,
            remoteSearchCacheDao,
            remoteSearchFailureDao,
            new RemoteResponseMapper()
        );

        final String experimentId = String.format(Locale.ROOT, "rest_execute_%d", System.currentTimeMillis());

        return channel -> executor.executeRemoteSearch(
            remoteConfigId,
            query,
            queryTextFinal,
            size,
            experimentId,
            new ActionListener<RemoteSearchExecutor.RemoteSearchResponse>() {
                @Override
                public void onResponse(RemoteSearchExecutor.RemoteSearchResponse remoteResponse) {
                    try {
                        // Prefer mapped response if available
                        String json = remoteResponse.getMappedResponse() != null && !remoteResponse.getMappedResponse().isBlank()
                            ? remoteResponse.getMappedResponse()
                            : remoteResponse.getRawResponse();

                        // Use response validation utility to ensure proper format
                        json = ResponseValidationUtils.ensureValidOpenSearchResponse(json);

                        // Validate JSON structure before processing
                        Map<String, Object> data;
                        try (
                            org.opensearch.core.xcontent.XContentParser xParser = org.opensearch.common.xcontent.XContentFactory
                                .jsonBuilder()
                                .contentType()
                                .xContent()
                                .createParser(null, null, json)
                        ) {
                            data = xParser.map();

                            // Additional validation: ensure we have a valid response structure
                            if (data == null || data.isEmpty()) {
                                // Return empty response if parsing resulted in null/empty map
                                XContentBuilder builder = channel.newBuilder();
                                builder.startObject()
                                    .startObject("hits")
                                    .startObject("total")
                                    .field("value", 0)
                                    .field("relation", "eq")
                                    .endObject()
                                    .field("max_score", (String) null)
                                    .startArray("hits")
                                    .endArray()
                                    .endObject()
                                    .field("took", 0)
                                    .field("timed_out", false)
                                    .endObject();
                                channel.sendResponse(new BytesRestResponse(RestStatus.OK, builder));
                                return;
                            }

                            // Ensure hits structure exists for jq compatibility
                            if (!data.containsKey("hits")) {
                                // If no hits structure, wrap the response appropriately
                                Map<String, Object> wrappedData = new HashMap<>();
                                Map<String, Object> hitsContainer = new HashMap<>();
                                Map<String, Object> total = new HashMap<>();
                                total.put("value", 0);
                                total.put("relation", "eq");
                                hitsContainer.put("total", total);
                                hitsContainer.put("max_score", null);
                                hitsContainer.put("hits", new ArrayList<>());
                                wrappedData.put("hits", hitsContainer);
                                wrappedData.put("took", data.getOrDefault("took", 0));
                                wrappedData.put("timed_out", false);
                                data = wrappedData;
                            }
                        } catch (Exception parseException) {
                            // JSON parsing failed - return error response with proper structure
                            XContentBuilder builder = channel.newBuilder();
                            builder.startObject()
                                .startObject("error")
                                .field("type", "remote_response_parse_exception")
                                .field("reason", "Failed to parse remote response: " + parseException.getMessage())
                                .endObject()
                                .endObject();
                            channel.sendResponse(new BytesRestResponse(RestStatus.INTERNAL_SERVER_ERROR, builder));
                            return;
                        }

                        XContentBuilder builder = channel.newBuilder();
                        builder.map(data);
                        channel.sendResponse(new BytesRestResponse(RestStatus.OK, builder));
                    } catch (Exception e) {
                        onFailure(e);
                    }
                }

                @Override
                public void onFailure(Exception e) {
                    try {
                        channel.sendResponse(new BytesRestResponse(channel, ExceptionsHelper.status(e), e));
                    } catch (IOException ex) {
                        try {
                            channel.sendResponse(new BytesRestResponse(channel, ex));
                        } catch (IOException ioEx) {
                            // ignored secondary failure
                        }
                    }
                }
            }
        );
    }

    private static String asString(Object o) {
        return o == null ? null : String.valueOf(o);
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
}
