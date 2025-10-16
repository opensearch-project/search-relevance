/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */
package org.opensearch.searchrelevance.rest;

import static java.util.Collections.singletonList;
import static org.opensearch.rest.RestRequest.Method.DELETE;
import static org.opensearch.searchrelevance.common.PluginConstants.DOCUMENT_ID;
import static org.opensearch.searchrelevance.common.PluginConstants.REMOTE_SEARCH_CONFIGURATIONS_URL;

import java.io.IOException;
import java.util.List;
import java.util.Locale;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.opensearch.action.DocWriteResponse;
import org.opensearch.action.delete.DeleteResponse;
import org.opensearch.core.action.ActionListener;
import org.opensearch.core.rest.RestStatus;
import org.opensearch.core.xcontent.XContentBuilder;
import org.opensearch.rest.BaseRestHandler;
import org.opensearch.rest.BytesRestResponse;
import org.opensearch.rest.RestRequest;
import org.opensearch.searchrelevance.dao.RemoteSearchConfigurationDao;
import org.opensearch.searchrelevance.exception.SearchRelevanceException;
import org.opensearch.searchrelevance.settings.SearchRelevanceSettingsAccessor;
import org.opensearch.transport.client.node.NodeClient;

import lombok.AllArgsConstructor;

/**
 * REST action to delete a RemoteSearchConfiguration by ID.
 * Route: DELETE /_plugins/_search_relevance/remote_search_configurations/{id}
 */
@AllArgsConstructor
public class RestDeleteRemoteSearchConfigurationAction extends BaseRestHandler {
    private static final Logger LOGGER = LogManager.getLogger(RestDeleteRemoteSearchConfigurationAction.class);
    private static final String ACTION_NAME = "delete_remote_search_configuration_action";

    private final SearchRelevanceSettingsAccessor settingsAccessor;
    private final RemoteSearchConfigurationDao remoteSearchConfigurationDao;

    @Override
    public String getName() {
        return ACTION_NAME;
    }

    @Override
    public List<Route> routes() {
        return singletonList(new Route(DELETE, String.format(Locale.ROOT, "%s/{%s}", REMOTE_SEARCH_CONFIGURATIONS_URL, DOCUMENT_ID)));
    }

    @Override
    protected RestChannelConsumer prepareRequest(RestRequest request, NodeClient client) throws IOException {
        if (!settingsAccessor.isWorkbenchEnabled()) {
            return channel -> channel.sendResponse(new BytesRestResponse(RestStatus.FORBIDDEN, "Search Relevance Workbench is disabled"));
        }
        final String id = request.param(DOCUMENT_ID);
        if (id == null || id.isBlank()) {
            throw new SearchRelevanceException("id cannot be null or empty", RestStatus.BAD_REQUEST);
        }

        return channel -> remoteSearchConfigurationDao.deleteRemoteSearchConfiguration(id, new ActionListener<DeleteResponse>() {
            @Override
            public void onResponse(DeleteResponse deleteResponse) {
                try {
                    XContentBuilder builder = channel.newBuilder();
                    deleteResponse.toXContent(builder, request);
                    channel.sendResponse(
                        new BytesRestResponse(
                            deleteResponse.getResult() == DocWriteResponse.Result.NOT_FOUND ? RestStatus.NOT_FOUND : RestStatus.OK,
                            builder
                        )
                    );
                } catch (IOException e) {
                    onFailure(e);
                }
            }

            @Override
            public void onFailure(Exception e) {
                try {
                    LOGGER.error("Failed to delete remote search configuration {}", id, e);
                    channel.sendResponse(new BytesRestResponse(channel, RestStatus.INTERNAL_SERVER_ERROR, e));
                } catch (IOException ex) {
                    LOGGER.error("Failed to send error response", ex);
                }
            }
        });
    }
}
