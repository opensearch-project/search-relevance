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
import static org.opensearch.searchrelevance.common.PluginConstants.DOCUMENT_ID;
import static org.opensearch.searchrelevance.common.PluginConstants.JUDGMENTS_URL;

import java.io.IOException;
import java.util.List;
import java.util.Locale;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.opensearch.action.index.IndexResponse;
import org.opensearch.core.action.ActionListener;
import org.opensearch.core.rest.RestStatus;
import org.opensearch.core.xcontent.XContentBuilder;
import org.opensearch.rest.BaseRestHandler;
import org.opensearch.rest.BytesRestResponse;
import org.opensearch.rest.RestRequest;
import org.opensearch.searchrelevance.model.AsyncStatus;
import org.opensearch.searchrelevance.settings.SearchRelevanceSettingsAccessor;
import org.opensearch.searchrelevance.transport.judgment.RetryFailedJudgmentAction;
import org.opensearch.searchrelevance.transport.judgment.RetryFailedJudgmentRequest;
import org.opensearch.transport.client.node.NodeClient;

import lombok.AllArgsConstructor;

/**
 * Rest Action to facilitate requests to retry failed documents in an existing LLM judgment.
 */
@AllArgsConstructor
public class RestRetryFailedJudgmentAction extends BaseRestHandler {
    private static final Logger LOGGER = LogManager.getLogger(RestRetryFailedJudgmentAction.class);
    private static final String RETRY_JUDGMENT_ACTION = "retry_judgment_action";
    private SearchRelevanceSettingsAccessor settingsAccessor;

    @Override
    public String getName() {
        return RETRY_JUDGMENT_ACTION;
    }

    @Override
    public List<Route> routes() {
        return singletonList(new Route(POST, String.format(Locale.ROOT, "%s/{%s}/_retry", JUDGMENTS_URL, DOCUMENT_ID)));
    }

    @Override
    protected RestChannelConsumer prepareRequest(RestRequest request, NodeClient client) throws IOException {
        if (!settingsAccessor.isWorkbenchEnabled()) {
            return channel -> channel.sendResponse(new BytesRestResponse(RestStatus.FORBIDDEN, "Search Relevance Workbench is disabled"));
        }

        final String judgmentId = request.param(DOCUMENT_ID);
        if (judgmentId == null || judgmentId.isEmpty()) {
            return channel -> channel.sendResponse(new BytesRestResponse(RestStatus.BAD_REQUEST, "Judgment ID is required"));
        }

        RetryFailedJudgmentRequest retryRequest = new RetryFailedJudgmentRequest(judgmentId);

        return channel -> client.execute(RetryFailedJudgmentAction.INSTANCE, retryRequest, new ActionListener<IndexResponse>() {
            @Override
            public void onResponse(IndexResponse response) {
                try {
                    XContentBuilder builder = channel.newBuilder();
                    builder.startObject();
                    builder.field("judgment_id", judgmentId);
                    builder.field("status", AsyncStatus.RETRYING.name());
                    builder.field("message", "Retrying failed documents");
                    builder.endObject();
                    channel.sendResponse(new BytesRestResponse(RestStatus.OK, builder));
                } catch (IOException e) {
                    onFailure(e);
                }
            }

            @Override
            public void onFailure(Exception e) {
                try {
                    channel.sendResponse(new BytesRestResponse(channel, e));
                } catch (IOException ex) {
                    LOGGER.error("Failed to send error response", ex);
                }
            }
        });
    }
}
