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
import static org.opensearch.searchrelevance.common.PluginConstants.SCHEDULED_EXPERIMENT_URL_PREFIX;
import static org.opensearch.searchrelevance.common.PluginConstants.SCHEDULED_EXPERIMENT_URL_SUFFIX;

import java.io.IOException;
import java.util.List;
import java.util.Locale;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.opensearch.action.search.SearchResponse;
import org.opensearch.core.action.ActionListener;
import org.opensearch.core.rest.RestStatus;
import org.opensearch.core.xcontent.ToXContent;
import org.opensearch.core.xcontent.XContentBuilder;
import org.opensearch.index.query.QueryBuilders;
import org.opensearch.rest.BaseRestHandler;
import org.opensearch.rest.BytesRestResponse;
import org.opensearch.rest.RestRequest;
import org.opensearch.search.builder.SearchSourceBuilder;
import org.opensearch.searchrelevance.model.SearchParams;
import org.opensearch.searchrelevance.settings.SearchRelevanceSettingsAccessor;
import org.opensearch.searchrelevance.transport.OpenSearchDocRequest;
import org.opensearch.searchrelevance.transport.scheduledJob.GetScheduledExperimentAction;
import org.opensearch.searchrelevance.utils.ParserUtils;
import org.opensearch.transport.client.node.NodeClient;

import lombok.AllArgsConstructor;

/**
 * Rest Action to facilitate requests to get/list scheduled job.
 */
@AllArgsConstructor
public class RestGetScheduledExperimentAction extends BaseRestHandler {
    private static final Logger LOGGER = LogManager.getLogger(RestGetScheduledExperimentAction.class);
    private static final String GET_SCHEDULED_EXPERIMENT_ACTION = "get_scheduled_experiment_action";
    private SearchRelevanceSettingsAccessor settingsAccessor;

    @Override
    public String getName() {
        return GET_SCHEDULED_EXPERIMENT_ACTION;
    }

    @Override
    public List<Route> routes() {
        return List.of(
            new Route(
                GET,
                String.format(Locale.ROOT, "%s/{%s}%s", SCHEDULED_EXPERIMENT_URL_PREFIX, DOCUMENT_ID, SCHEDULED_EXPERIMENT_URL_SUFFIX)
            ),
            new Route(GET, SCHEDULED_EXPERIMENT_URL_PREFIX + SCHEDULED_EXPERIMENT_URL_SUFFIX)
        );
    }

    @Override
    protected RestChannelConsumer prepareRequest(RestRequest request, NodeClient client) throws IOException {
        if (!settingsAccessor.isWorkbenchEnabled()) {
            return channel -> channel.sendResponse(new BytesRestResponse(RestStatus.FORBIDDEN, "Search Relevance Workbench is disabled"));
        }
        final String jobId = request.param(DOCUMENT_ID);
        // If id is provided, get specific scheudled experiment
        if (jobId != null && !jobId.isEmpty()) {
            OpenSearchDocRequest getRequest = new OpenSearchDocRequest(jobId);
            return executeGetRequest(client, getRequest);
        }

        // Otherwise, handle list request
        SearchParams searchParams = ParserUtils.parseSearchParams(request);
        SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder().query(QueryBuilders.matchAllQuery())
            .size(searchParams.getSize())
            .sort(searchParams.getSortField(), searchParams.getSortOrder());

        OpenSearchDocRequest getRequest = new OpenSearchDocRequest(searchSourceBuilder);
        return executeGetRequest(client, getRequest);
    }

    private RestChannelConsumer executeGetRequest(NodeClient client, OpenSearchDocRequest request) {
        return channel -> client.execute(GetScheduledExperimentAction.INSTANCE, request, new ActionListener<SearchResponse>() {
            @Override
            public void onResponse(SearchResponse response) {
                try {
                    XContentBuilder builder = channel.newBuilder();
                    response.toXContent(builder, ToXContent.EMPTY_PARAMS);
                    RestStatus status = response.status();
                    channel.sendResponse(new BytesRestResponse(status, builder));
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
