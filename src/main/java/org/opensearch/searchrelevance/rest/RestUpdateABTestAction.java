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
import static org.opensearch.searchrelevance.common.PluginConstants.AB_TEST_UPDATE_URL;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.opensearch.ExceptionsHelper;
import org.opensearch.action.index.IndexResponse;
import org.opensearch.core.action.ActionListener;
import org.opensearch.core.rest.RestStatus;
import org.opensearch.core.xcontent.XContentBuilder;
import org.opensearch.rest.BaseRestHandler;
import org.opensearch.rest.BytesRestResponse;
import org.opensearch.rest.RestRequest;
import org.opensearch.searchrelevance.settings.SearchRelevanceSettingsAccessor;
import org.opensearch.searchrelevance.transport.abTest.UpdateABTestAction;
import org.opensearch.searchrelevance.transport.abTest.UpdateABTestRequest;
import org.opensearch.transport.client.node.NodeClient;

import lombok.AllArgsConstructor;

@AllArgsConstructor
public class RestUpdateABTestAction extends BaseRestHandler {
    private static final Logger LOGGER = LogManager.getLogger(RestUpdateABTestAction.class);
    private static final String UPDATE_AB_TEST_ACTION = "update_ab_test_action";
    private SearchRelevanceSettingsAccessor settingsAccessor;

    @Override
    public String getName() {
        return UPDATE_AB_TEST_ACTION;
    }

    @Override
    public List<Route> routes() {
        return singletonList(new Route(PUT, AB_TEST_UPDATE_URL));
    }

    @Override
    protected RestChannelConsumer prepareRequest(RestRequest request, NodeClient client) throws IOException {
        if (!settingsAccessor.isWorkbenchEnabled()) {
            return channel -> channel.sendResponse(new BytesRestResponse(RestStatus.FORBIDDEN, "Search Relevance Workbench is disabled"));
        }

        String testId = request.param("id");
        if (testId == null || testId.isEmpty()) {
            return channel -> channel.sendResponse(new BytesRestResponse(RestStatus.BAD_REQUEST, "test id is required in URL path"));
        }

        Map<String, Object> source = request.contentParser().map();

        if (source == null || source.isEmpty()) {
            return channel -> channel.sendResponse(new BytesRestResponse(RestStatus.BAD_REQUEST, "Request body must not be empty"));
        }

        Object configAObj = source.get("search_configuration_a");
        Object configBObj = source.get("search_configuration_b");
        Object enabledObj = source.get("enabled");

        if (configAObj != null && !(configAObj instanceof String)) {
            return channel -> channel.sendResponse(
                new BytesRestResponse(RestStatus.BAD_REQUEST, "search_configuration_a must be a string")
            );
        }
        if (configBObj != null && !(configBObj instanceof String)) {
            return channel -> channel.sendResponse(
                new BytesRestResponse(RestStatus.BAD_REQUEST, "search_configuration_b must be a string")
            );
        }
        if (enabledObj != null && !(enabledObj instanceof Boolean)) {
            return channel -> channel.sendResponse(new BytesRestResponse(RestStatus.BAD_REQUEST, "enabled must be a boolean"));
        }

        String searchConfigurationA = (String) configAObj;
        String searchConfigurationB = (String) configBObj;
        Boolean enabled = (Boolean) enabledObj;

        if (searchConfigurationA == null && searchConfigurationB == null && enabled == null) {
            return channel -> channel.sendResponse(
                new BytesRestResponse(RestStatus.BAD_REQUEST, "At least one field must be provided for update")
            );
        }

        UpdateABTestRequest updateRequest = new UpdateABTestRequest(testId, enabled, searchConfigurationA, searchConfigurationB);

        return channel -> client.execute(UpdateABTestAction.INSTANCE, updateRequest, new ActionListener<IndexResponse>() {
            @Override
            public void onResponse(IndexResponse response) {
                try {
                    XContentBuilder builder = channel.newBuilder();
                    builder.startObject();
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
                    LOGGER.error("Failed to send error response", ex);
                }
            }
        });
    }
}
