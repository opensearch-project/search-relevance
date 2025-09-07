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
import static org.opensearch.searchrelevance.common.PluginConstants.SCHEDULED_EXPERIMENT_URL;

import java.io.IOException;
import java.util.List;
import java.util.Locale;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.opensearch.action.DocWriteResponse;
import org.opensearch.action.delete.DeleteResponse;
import org.opensearch.common.annotation.ExperimentalApi;
import org.opensearch.core.action.ActionListener;
import org.opensearch.core.rest.RestStatus;
import org.opensearch.core.xcontent.XContentBuilder;
import org.opensearch.rest.BaseRestHandler;
import org.opensearch.rest.BytesRestResponse;
import org.opensearch.rest.RestRequest;
import org.opensearch.searchrelevance.exception.SearchRelevanceException;
import org.opensearch.searchrelevance.settings.SearchRelevanceSettingsAccessor;
import org.opensearch.searchrelevance.transport.OpenSearchDocRequest;
import org.opensearch.searchrelevance.transport.scheduledJob.DeleteScheduledExperimentAction;
import org.opensearch.transport.client.node.NodeClient;

import lombok.AllArgsConstructor;
import lombok.extern.log4j.Log4j2;

@Log4j2
/**
 * Rest Action to facilitate requests to delete the scheduled running of an experiment```
 */
@ExperimentalApi
@AllArgsConstructor
public class RestDeleteScheduledExperimentAction extends BaseRestHandler {
    private static final Logger LOGGER = LogManager.getLogger(RestDeleteScheduledExperimentAction.class);
    private static final String DELETE_SCHEDULED_EXPERIMENT_ACTION = "delete_scheduled_experiment_action";
    private SearchRelevanceSettingsAccessor settingsAccessor;

    @Override
    public String getName() {
        return DELETE_SCHEDULED_EXPERIMENT_ACTION;
    }

    @Override
    public List<Route> routes() {
        return singletonList(new Route(DELETE, String.format(Locale.ROOT, "%s/{%s}", SCHEDULED_EXPERIMENT_URL, DOCUMENT_ID)));
    }

    @Override
    protected RestChannelConsumer prepareRequest(RestRequest request, NodeClient client) throws IOException {
        if (!settingsAccessor.isWorkbenchEnabled()) {
            return channel -> channel.sendResponse(new BytesRestResponse(RestStatus.FORBIDDEN, "Search Relevance Workbench is disabled"));
        }
        if (!settingsAccessor.isScheduledExperimentsEnabled()) {
            return channel -> channel.sendResponse(new BytesRestResponse(RestStatus.FORBIDDEN, "Scheduled experiments is disabled"));
        }

        // delete job parameter doc from index
        String jobId = request.param(DOCUMENT_ID);
        if (jobId == null) {
            throw new SearchRelevanceException("id cannot be null", RestStatus.BAD_REQUEST);
        }
        OpenSearchDocRequest deleteRequest = new OpenSearchDocRequest(jobId);

        return channel -> client.execute(DeleteScheduledExperimentAction.INSTANCE, deleteRequest, new ActionListener<DeleteResponse>() {
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
