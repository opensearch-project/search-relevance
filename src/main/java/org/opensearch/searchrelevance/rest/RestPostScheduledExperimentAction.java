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
import static org.opensearch.searchrelevance.common.PluginConstants.CRON_EXPRESSION;
import static org.opensearch.searchrelevance.common.PluginConstants.EXPERIMENT_ID;
import static org.opensearch.searchrelevance.common.PluginConstants.SCHEDULED_EXPERIMENT_URL;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import org.opensearch.ExceptionsHelper;
import org.opensearch.action.index.IndexResponse;
import org.opensearch.common.annotation.ExperimentalApi;
import org.opensearch.core.action.ActionListener;
import org.opensearch.core.rest.RestStatus;
import org.opensearch.core.xcontent.XContentBuilder;
import org.opensearch.core.xcontent.XContentParser;
import org.opensearch.rest.BaseRestHandler;
import org.opensearch.rest.BytesRestResponse;
import org.opensearch.rest.RestRequest;
import org.opensearch.searchrelevance.settings.SearchRelevanceSettingsAccessor;
import org.opensearch.searchrelevance.transport.scheduledJob.PostScheduledExperimentAction;
import org.opensearch.searchrelevance.transport.scheduledJob.PostScheduledExperimentRequest;
import org.opensearch.searchrelevance.utils.CronUtil;
import org.opensearch.searchrelevance.utils.CronUtil.CronValidationResult;
import org.opensearch.transport.client.node.NodeClient;

import lombok.AllArgsConstructor;
import lombok.extern.log4j.Log4j2;

@Log4j2
/**
 * Rest Action to facilitate requests to schedule running an experiment.
 */
@AllArgsConstructor
@ExperimentalApi
public class RestPostScheduledExperimentAction extends BaseRestHandler {
    private static final String POST_SCHEDULED_EXPERIMENT_ACTION = "post_scheduled_experiment_action";
    private SearchRelevanceSettingsAccessor settingsAccessor;
    private CronUtil cronUtil;

    @Override
    public String getName() {
        return POST_SCHEDULED_EXPERIMENT_ACTION;
    }

    @Override
    public List<Route> routes() {
        return singletonList(new Route(POST, SCHEDULED_EXPERIMENT_URL));
    }

    @Override
    protected RestChannelConsumer prepareRequest(RestRequest request, NodeClient client) throws IOException {
        if (!settingsAccessor.isWorkbenchEnabled()) {
            return channel -> channel.sendResponse(new BytesRestResponse(RestStatus.FORBIDDEN, "Search Relevance Workbench is disabled"));
        }
        if (!settingsAccessor.isScheduledExperimentsEnabled()) {
            return channel -> channel.sendResponse(new BytesRestResponse(RestStatus.FORBIDDEN, "Scheduled experiments is disabled"));
        }
        XContentParser parser = request.contentParser();
        Map<String, Object> source = parser.map();

        String experimentId = (String) source.get(EXPERIMENT_ID);
        String cronExpression = (String) source.get(CRON_EXPRESSION);

        if (experimentId == null || experimentId.equals("")) {
            throw new IllegalArgumentException("Invalid or missing experiment Id");
        }

        CronValidationResult validationResult = cronUtil.validateCron(cronExpression);

        if (validationResult.isValid() == false) {
            throw new IllegalArgumentException(validationResult.getErrorMessage());
        }

        PostScheduledExperimentRequest createRequest = new PostScheduledExperimentRequest(experimentId, cronExpression);
        return channel -> client.execute(PostScheduledExperimentAction.INSTANCE, createRequest, new ActionListener<IndexResponse>() {
            @Override
            public void onResponse(IndexResponse response) {
                try {
                    XContentBuilder builder = channel.newBuilder();
                    builder.startObject();
                    builder.field("job_id", response.getId());
                    builder.field("job_result", response.getResult());
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
                    log.error("Failed to send error response", ex);
                }
            }
        });
    }
}
