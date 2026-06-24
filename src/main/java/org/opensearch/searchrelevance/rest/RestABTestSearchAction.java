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
import static org.opensearch.searchrelevance.common.PluginConstants.AB_TEST_SEARCH_URL;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.opensearch.ExceptionsHelper;
import org.opensearch.core.action.ActionListener;
import org.opensearch.core.rest.RestStatus;
import org.opensearch.rest.BaseRestHandler;
import org.opensearch.rest.BytesRestResponse;
import org.opensearch.rest.RestRequest;
import org.opensearch.searchrelevance.settings.SearchRelevanceSettingsAccessor;
import org.opensearch.searchrelevance.transport.abTest.ABTestSearchAction;
import org.opensearch.searchrelevance.transport.abTest.ABTestSearchRequest;
import org.opensearch.searchrelevance.transport.abTest.ABTestSearchResponse;
import org.opensearch.transport.client.node.NodeClient;

import lombok.AllArgsConstructor;

@AllArgsConstructor
public class RestABTestSearchAction extends BaseRestHandler {
    private static final Logger LOGGER = LogManager.getLogger(RestABTestSearchAction.class);
    private static final String AB_TEST_SEARCH_ACTION = "ab_test_search_action";
    private SearchRelevanceSettingsAccessor settingsAccessor;

    @Override
    public String getName() {
        return AB_TEST_SEARCH_ACTION;
    }

    @Override
    public List<Route> routes() {
        return singletonList(new Route(POST, AB_TEST_SEARCH_URL));
    }

    @Override
    protected RestChannelConsumer prepareRequest(RestRequest request, NodeClient client) throws IOException {
        if (!settingsAccessor.isWorkbenchEnabled()) {
            return channel -> channel.sendResponse(new BytesRestResponse(RestStatus.FORBIDDEN, "Search Relevance Workbench is disabled"));
        }
        String testId = request.param("id");
        if (testId == null || testId.isEmpty()) {
            return channel -> channel.sendResponse(new BytesRestResponse(RestStatus.BAD_REQUEST, "test id is required"));
        }
        Map<String, Object> source = request.contentParser().map();
        @SuppressWarnings("unchecked")
        Map<String, Object> rawParams = (Map<String, Object>) source.get("query_params");
        if (rawParams == null || rawParams.isEmpty()) {
            return channel -> channel.sendResponse(new BytesRestResponse(RestStatus.BAD_REQUEST, "query_params map is required"));
        }
        Map<String, String> params = new HashMap<>();
        for (Map.Entry<String, Object> entry : rawParams.entrySet()) {
            params.put(entry.getKey(), String.valueOf(entry.getValue()));
        }
        ABTestSearchRequest searchRequest = new ABTestSearchRequest(testId, params);
        return channel -> client.execute(ABTestSearchAction.INSTANCE, searchRequest, new ActionListener<ABTestSearchResponse>() {
            @Override
            public void onResponse(ABTestSearchResponse response) {
                try {
                    channel.sendResponse(new BytesRestResponse(RestStatus.OK, response.toXContent(channel.newBuilder(), null)));
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
