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
import static org.opensearch.searchrelevance.common.PluginConstants.AB_TESTS_URL;

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
import org.opensearch.rest.RestChannel;
import org.opensearch.rest.RestRequest;
import org.opensearch.searchrelevance.settings.SearchRelevanceSettingsAccessor;
import org.opensearch.searchrelevance.transport.abTest.PutABTestAction;
import org.opensearch.searchrelevance.transport.abTest.PutABTestRequest;
import org.opensearch.searchrelevance.transport.abTest.UpdateABTestAction;
import org.opensearch.searchrelevance.transport.abTest.UpdateABTestRequest;
import org.opensearch.transport.client.node.NodeClient;

import lombok.AllArgsConstructor;

@AllArgsConstructor
public class RestPutABTestAction extends BaseRestHandler {
    private static final Logger LOGGER = LogManager.getLogger(RestPutABTestAction.class);
    private static final String PUT_AB_TEST_ACTION = "put_ab_test_action";
    private SearchRelevanceSettingsAccessor settingsAccessor;

    @Override
    public String getName() {
        return PUT_AB_TEST_ACTION;
    }

    @Override
    public List<Route> routes() {
        return singletonList(new Route(PUT, AB_TESTS_URL + "/{id}"));
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

        String searchConfigurationA = (String) source.get("search_configuration_a");
        String searchConfigurationB = (String) source.get("search_configuration_b");
        Boolean enabled = source.containsKey("enabled") ? (Boolean) source.get("enabled") : null;

        // Validate: empty body
        if (searchConfigurationA == null && searchConfigurationB == null && enabled == null) {
            return channel -> channel.sendResponse(new BytesRestResponse(RestStatus.BAD_REQUEST, "Request body cannot be empty"));
        }

        // Validate: both configs are always mandatory (except for enable/disable toggle)
        if (searchConfigurationA == null || searchConfigurationB == null) {
            // Only exception: enable/disable toggle (no configs needed)
            if (enabled != null && searchConfigurationA == null && searchConfigurationB == null) {
                UpdateABTestRequest updateRequest = new UpdateABTestRequest(testId, enabled, null, null);
                return channel -> client.execute(UpdateABTestAction.INSTANCE, updateRequest, createResponseListener(channel));
            }
            return channel -> channel.sendResponse(
                new BytesRestResponse(RestStatus.BAD_REQUEST, "Both search_configuration_a and search_configuration_b are required")
            );
        }

        // Validate: both configs must be different
        if (searchConfigurationA.equals(searchConfigurationB)) {
            return channel -> channel.sendResponse(
                new BytesRestResponse(RestStatus.BAD_REQUEST, "search_configuration_a and search_configuration_b must be different")
            );
        }

        // Both configs present: try CREATE first, fallback to UPDATE if test already exists
        PutABTestRequest putRequest = new PutABTestRequest(testId, searchConfigurationA, searchConfigurationB);

        return channel -> client.execute(PutABTestAction.INSTANCE, putRequest, new ActionListener<IndexResponse>() {
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
                if (e.getMessage() != null && e.getMessage().contains("version conflict")) {
                    // Test already exists → try UPDATE, return error if nothing changed
                    UpdateABTestRequest updateRequest = new UpdateABTestRequest(
                        testId,
                        enabled,
                        searchConfigurationA,
                        searchConfigurationB
                    );
                    client.execute(UpdateABTestAction.INSTANCE, updateRequest, new ActionListener<IndexResponse>() {
                        @Override
                        public void onResponse(IndexResponse response) {
                            try {
                                if (response == null) {
                                    // Nothing changed → return the version conflict error as-is
                                    channel.sendResponse(new BytesRestResponse(channel, ExceptionsHelper.status(e), e));
                                } else {
                                    // Updated successfully
                                    XContentBuilder builder = channel.newBuilder();
                                    builder.startObject();
                                    builder.endObject();
                                    channel.sendResponse(new BytesRestResponse(RestStatus.OK, builder));
                                }
                            } catch (IOException ex) {
                                LOGGER.error("Failed to send response", ex);
                            }
                        }

                        @Override
                        public void onFailure(Exception ex) {
                            try {
                                channel.sendResponse(new BytesRestResponse(channel, ExceptionsHelper.status(ex), ex));
                            } catch (IOException ioEx) {
                                LOGGER.error("Failed to send error response", ioEx);
                            }
                        }
                    });
                } else {
                    try {
                        channel.sendResponse(new BytesRestResponse(channel, ExceptionsHelper.status(e), e));
                    } catch (IOException ex) {
                        LOGGER.error("Failed to send error response", ex);
                    }
                }
            }
        });
    }

    private ActionListener<IndexResponse> createResponseListener(RestChannel channel) {
        return new ActionListener<IndexResponse>() {
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
        };
    }
}
