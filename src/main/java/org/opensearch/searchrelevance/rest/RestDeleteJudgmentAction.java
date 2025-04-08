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
import static org.opensearch.searchrelevance.common.PluginConstants.JUDGMENTS_URL;

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
import org.opensearch.searchrelevance.transport.judgment.DeleteJudgmentAction;
import org.opensearch.searchrelevance.transport.OpenSearchDocRequest;
import org.opensearch.transport.client.node.NodeClient;

/**
 * Rest Action to handle requests to delete a judgment.
 */
public class RestDeleteJudgmentAction extends BaseRestHandler {
    private static final Logger LOGGER = LogManager.getLogger(RestDeleteJudgmentAction.class);
    private static final String DELETE_JUDGMENT_ACTION = "delete_judgment_action";

    @Override
    public String getName() {
        return DELETE_JUDGMENT_ACTION;
    }

    @Override
    public List<Route> routes() {
        return singletonList(new Route(DELETE, String.format(Locale.ROOT, "%s/{%s}", JUDGMENTS_URL, DOCUMENT_ID)));
    }

    @Override
    protected RestChannelConsumer prepareRequest(RestRequest request, NodeClient client) throws IOException {
        final String judgmentId = request.param(DOCUMENT_ID);
        if (judgmentId == null) {
            throw new IllegalArgumentException("id cannot be null");
        }
        OpenSearchDocRequest deleteRequest = new OpenSearchDocRequest(judgmentId);
        return channel -> client.execute(DeleteJudgmentAction.INSTANCE, deleteRequest, new ActionListener<DeleteResponse>() {
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
                    channel.sendResponse(new BytesRestResponse(channel, RestStatus.INTERNAL_SERVER_ERROR, e));
                } catch (IOException ex) {
                    LOGGER.error("Failed to send error response", ex);
                }
            }
        });
    }
}
