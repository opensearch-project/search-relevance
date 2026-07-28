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
import static org.opensearch.searchrelevance.common.PluginConstants.JUDGMENT_RATINGS;
import static org.opensearch.searchrelevance.common.PluginConstants.QUERY;

import java.io.IOException;
import java.util.ArrayList;
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
import org.opensearch.searchrelevance.transport.judgment.RatingAdjustment;
import org.opensearch.searchrelevance.transport.judgment.UpdateJudgmentRatingsAction;
import org.opensearch.searchrelevance.transport.judgment.UpdateJudgmentRatingsRequest;
import org.opensearch.transport.client.node.NodeClient;

import lombok.AllArgsConstructor;

/**
 * Rest Action to adjust one or more ratings on an existing LLM judgment in place (manual edit).
 *
 * <p>Route: {@code PUT /_plugins/_search_relevance/judgments/{id}}. The body carries only the
 * ratings being changed, under {@code judgmentRatings} — the same field name and shape the judgment
 * is stored in — so a client can send back the structure it read from a GET:
 * {@code {"judgmentRatings": [{"query": ..., "ratings": [{"docId": ..., "rating": ...}, ...]}, ...]}}.
 * Several queries and several docs per query may be sent at once. This is a partial merge: only the
 * listed (query, docId) pairs are touched and every other rating is left as is. The server updates
 * those entries, recomputes the summary counts, and saves back to the same id under one guarded
 * write. No model call is made.
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

        // The body carries the ratings to change under "judgmentRatings", the same field name and
        // shape the judgment is stored and returned in.
        final List<?> queryEntries;
        try {
            XContentParser parser = request.contentParser();
            Object ratingsToUpdate = parser.map().get(JUDGMENT_RATINGS);
            if (ratingsToUpdate == null) {
                return channel -> channel.sendResponse(new BytesRestResponse(RestStatus.BAD_REQUEST, JUDGMENT_RATINGS + " is required"));
            }
            queryEntries = (List<?>) ratingsToUpdate;
        } catch (Exception e) {
            LOGGER.warn("Failed to parse update-ratings request body", e);
            return channel -> channel.sendResponse(
                new BytesRestResponse(RestStatus.BAD_REQUEST, "Malformed request body: " + e.getMessage())
            );
        }

        // Flatten into one adjustment per (query, docId) pair.
        final List<RatingAdjustment> adjustments = new ArrayList<>();
        try {
            for (Object entry : queryEntries) {
                @SuppressWarnings("unchecked")
                Map<String, Object> queryEntry = (Map<String, Object>) entry;
                String query = queryEntry.get(QUERY) == null ? null : queryEntry.get(QUERY).toString();
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> ratings = (List<Map<String, Object>>) queryEntry.get("ratings");
                if (ratings == null) {
                    return channel -> channel.sendResponse(
                        new BytesRestResponse(RestStatus.BAD_REQUEST, "each entry must have a ratings list")
                    );
                }
                for (Map<String, Object> rating : ratings) {
                    String docId = rating.get("docId") == null ? null : rating.get("docId").toString();
                    String ratingValue = rating.get("rating") == null ? null : rating.get("rating").toString();
                    adjustments.add(new RatingAdjustment(query, docId, ratingValue));
                }
            }
        } catch (ClassCastException e) {
            return channel -> channel.sendResponse(
                new BytesRestResponse(RestStatus.BAD_REQUEST, "expected a list of {query, ratings:[{docId, rating}]} objects")
            );
        }

        if (adjustments.isEmpty()) {
            return channel -> channel.sendResponse(new BytesRestResponse(RestStatus.BAD_REQUEST, "at least one rating is required"));
        }
        for (RatingAdjustment adjustment : adjustments) {
            if (adjustment.isIncomplete()) {
                return channel -> channel.sendResponse(
                    new BytesRestResponse(RestStatus.BAD_REQUEST, "each rating must have a query, docId and rating")
                );
            }
        }

        UpdateJudgmentRatingsRequest updateRequest = new UpdateJudgmentRatingsRequest(judgmentId, adjustments);

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
