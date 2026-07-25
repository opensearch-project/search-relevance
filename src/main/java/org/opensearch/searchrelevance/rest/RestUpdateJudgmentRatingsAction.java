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
import static org.opensearch.searchrelevance.common.PluginConstants.DOCUMENT_ID;
import static org.opensearch.searchrelevance.common.PluginConstants.JUDGMENTS_URL;
import static org.opensearch.searchrelevance.common.PluginConstants.QUERY;

import java.io.IOException;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.opensearch.action.index.IndexResponse;
import org.opensearch.core.action.ActionListener;
import org.opensearch.core.rest.RestStatus;
import org.opensearch.core.xcontent.XContentBuilder;
import org.opensearch.core.xcontent.XContentParser;
import org.opensearch.rest.BaseRestHandler;
import org.opensearch.rest.BytesRestResponse;
import org.opensearch.rest.RestRequest;
import org.opensearch.searchrelevance.settings.SearchRelevanceSettingsAccessor;
import org.opensearch.searchrelevance.transport.judgment.UpdateJudgmentRatingsAction;
import org.opensearch.searchrelevance.transport.judgment.UpdateJudgmentRatingsRequest;
import org.opensearch.transport.client.node.NodeClient;

import lombok.AllArgsConstructor;

/**
 * Rest Action to adjust a single rating on an existing LLM judgment in place (manual edit).
 *
 * <p>Route: {@code PUT /_plugins/_search_relevance/judgments/{id}}. The body carries just the one
 * rating being changed — {@code {"query": ..., "docId": ..., "rating": ...}}. The server updates
 * that entry, recomputes the summary counts, and saves back to the same id. No model call is made.
 */
@AllArgsConstructor
public class RestUpdateJudgmentRatingsAction extends BaseRestHandler {
    private static final Logger LOGGER = LogManager.getLogger(RestUpdateJudgmentRatingsAction.class);
    private static final String UPDATE_JUDGMENT_RATINGS_ACTION = "update_judgment_ratings_action";
    private SearchRelevanceSettingsAccessor settingsAccessor;

    /** @return the unique name of this REST handler */
    @Override
    public String getName() {
        return UPDATE_JUDGMENT_RATINGS_ACTION;
    }

    /** @return the routes handled: {@code PUT /_plugins/_search_relevance/judgments/{id}} */
    @Override
    public List<Route> routes() {
        return singletonList(new Route(PUT, String.format(Locale.ROOT, "%s/{%s}", JUDGMENTS_URL, DOCUMENT_ID)));
    }

    /**
     * Validate the request and dispatch it to {@link UpdateJudgmentRatingsAction}. Returns a
     * 403 if the workbench is disabled, or a 400 for a missing id or a missing/malformed body; on
     * success responds with {@code {judgment_id, result:"updated"}}.
     *
     * @param request - the incoming REST request
     * @param client - node client used to execute the transport action
     * @return a consumer that writes the response to the channel
     * @throws IOException if the request cannot be read
     */
    @Override
    protected RestChannelConsumer prepareRequest(RestRequest request, NodeClient client) throws IOException {
        if (!settingsAccessor.isWorkbenchEnabled()) {
            return channel -> channel.sendResponse(new BytesRestResponse(RestStatus.FORBIDDEN, "Search Relevance Workbench is disabled"));
        }

        final String judgmentId = request.param(DOCUMENT_ID);
        if (judgmentId == null || judgmentId.isEmpty()) {
            return channel -> channel.sendResponse(new BytesRestResponse(RestStatus.BAD_REQUEST, "Judgment ID is required"));
        }

        final Map<String, Object> source;
        try {
            XContentParser parser = request.contentParser();
            source = parser.map();
        } catch (Exception e) {
            LOGGER.warn("Failed to parse update-ratings request body", e);
            return channel -> channel.sendResponse(
                new BytesRestResponse(RestStatus.BAD_REQUEST, "Malformed request body: " + e.getMessage())
            );
        }

        // A single rating adjustment: query, docId and the new rating value are all required.
        final String query = source.get(QUERY) == null ? null : source.get(QUERY).toString();
        final String docId = source.get("docId") == null ? null : source.get("docId").toString();
        final String rating = source.get("rating") == null ? null : source.get("rating").toString();
        if (query == null || query.isEmpty() || docId == null || docId.isEmpty() || rating == null || rating.isEmpty()) {
            return channel -> channel.sendResponse(
                new BytesRestResponse(RestStatus.BAD_REQUEST, "query, docId and rating are all required")
            );
        }

        UpdateJudgmentRatingsRequest updateRequest = new UpdateJudgmentRatingsRequest(judgmentId, query, docId, rating);

        return channel -> client.execute(UpdateJudgmentRatingsAction.INSTANCE, updateRequest, new ActionListener<IndexResponse>() {
            @Override
            public void onResponse(IndexResponse response) {
                try {
                    XContentBuilder builder = channel.newBuilder();
                    builder.startObject();
                    builder.field("judgment_id", judgmentId);
                    builder.field("result", "updated");
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
