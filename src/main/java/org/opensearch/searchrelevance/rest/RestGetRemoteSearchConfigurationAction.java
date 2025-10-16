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
import static org.opensearch.searchrelevance.common.PluginConstants.REMOTE_SEARCH_CONFIGURATIONS_URL;

import java.io.IOException;
import java.util.List;
import java.util.Locale;

import org.opensearch.ExceptionsHelper;
import org.opensearch.core.action.ActionListener;
import org.opensearch.core.rest.RestStatus;
import org.opensearch.core.xcontent.ToXContent;
import org.opensearch.core.xcontent.XContentBuilder;
import org.opensearch.rest.BaseRestHandler;
import org.opensearch.rest.BytesRestResponse;
import org.opensearch.rest.RestRequest;
import org.opensearch.searchrelevance.dao.RemoteSearchConfigurationDao;
import org.opensearch.searchrelevance.model.RemoteSearchConfiguration;
import org.opensearch.searchrelevance.settings.SearchRelevanceSettingsAccessor;
import org.opensearch.transport.client.node.NodeClient;

/**
 * REST action to get or list remote search configurations.
 * Routes:
 *  - GET /_plugins/_search_relevance/remote_search_configurations/{id}
 *  - GET /_plugins/_search_relevance/remote_search_configurations
 */
public class RestGetRemoteSearchConfigurationAction extends BaseRestHandler {

    private static final String ACTION_NAME = "get_remote_search_configuration_action";

    private final SearchRelevanceSettingsAccessor settingsAccessor;
    private final RemoteSearchConfigurationDao remoteSearchConfigurationDao;

    public RestGetRemoteSearchConfigurationAction(
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
        return List.of(
            new Route(GET, String.format(Locale.ROOT, "%s/{%s}", REMOTE_SEARCH_CONFIGURATIONS_URL, DOCUMENT_ID)),
            new Route(GET, REMOTE_SEARCH_CONFIGURATIONS_URL)
        );
    }

    @Override
    protected RestChannelConsumer prepareRequest(RestRequest request, NodeClient client) throws IOException {
        if (!settingsAccessor.isWorkbenchEnabled()) {
            return channel -> channel.sendResponse(new BytesRestResponse(RestStatus.FORBIDDEN, "Search Relevance Workbench is disabled"));
        }

        final String id = request.param(DOCUMENT_ID);
        if (id != null && !id.isBlank()) {
            // Get single configuration
            return channel -> remoteSearchConfigurationDao.getRemoteSearchConfiguration(
                id,
                new ActionListener<RemoteSearchConfiguration>() {
                    @Override
                    public void onResponse(RemoteSearchConfiguration cfg) {
                        try {
                            if (cfg == null) {
                                channel.sendResponse(new BytesRestResponse(RestStatus.NOT_FOUND, "RemoteSearchConfiguration not found"));
                                return;
                            }
                            XContentBuilder builder = channel.newBuilder();
                            cfg.toXContent(builder, ToXContent.EMPTY_PARAMS);
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

        // List configurations
        return channel -> remoteSearchConfigurationDao.listRemoteSearchConfigurations(
            new ActionListener<List<RemoteSearchConfiguration>>() {
                @Override
                public void onResponse(List<RemoteSearchConfiguration> list) {
                    try {
                        XContentBuilder builder = channel.newBuilder();
                        builder.startArray();
                        for (RemoteSearchConfiguration cfg : list) {
                            cfg.toXContent(builder, ToXContent.EMPTY_PARAMS);
                        }
                        builder.endArray();
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
}
