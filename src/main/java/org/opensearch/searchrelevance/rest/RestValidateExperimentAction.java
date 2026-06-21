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
import static org.opensearch.searchrelevance.common.PluginConstants.EXPERIMENTS_URI;

import java.io.IOException;
import java.util.List;
import java.util.Locale;

import org.opensearch.core.action.ActionListener;
import org.opensearch.core.rest.RestStatus;
import org.opensearch.core.xcontent.ToXContent;
import org.opensearch.core.xcontent.XContentBuilder;
import org.opensearch.rest.BaseRestHandler;
import org.opensearch.rest.BytesRestResponse;
import org.opensearch.rest.RestRequest;
import org.opensearch.searchrelevance.settings.SearchRelevanceSettingsAccessor;
import org.opensearch.searchrelevance.transport.OpenSearchDocRequest;
import org.opensearch.searchrelevance.transport.experiment.ValidateExperimentAction;
import org.opensearch.searchrelevance.transport.experiment.ValidateExperimentResponse;
import org.opensearch.transport.client.node.NodeClient;

import lombok.AllArgsConstructor;

/**
 * REST handler for {@code GET /_plugins/_search_relevance/experiments/{id}/validate}.
 */
@AllArgsConstructor
public class RestValidateExperimentAction extends BaseRestHandler {

    private static final String ACTION_NAME = "validate_experiment_action";
    private final SearchRelevanceSettingsAccessor settingsAccessor;

    @Override
    public String getName() {
        return ACTION_NAME;
    }

    @Override
    public List<Route> routes() {
        return List.of(new Route(GET, String.format(Locale.ROOT, "%s/{%s}/validate", EXPERIMENTS_URI, DOCUMENT_ID)));
    }

    @Override
    protected RestChannelConsumer prepareRequest(RestRequest request, NodeClient client) throws IOException {
        if (!settingsAccessor.isWorkbenchEnabled()) {
            return channel -> channel.sendResponse(new BytesRestResponse(RestStatus.FORBIDDEN, "Search Relevance Workbench is disabled"));
        }
        final String experimentId = request.param(DOCUMENT_ID);
        if (experimentId == null || experimentId.isBlank()) {
            return channel -> channel.sendResponse(new BytesRestResponse(RestStatus.BAD_REQUEST, "Experiment id is required"));
        }
        OpenSearchDocRequest validateRequest = new OpenSearchDocRequest(experimentId);
        return channel -> client.execute(
            ValidateExperimentAction.INSTANCE,
            validateRequest,
            new ActionListener<ValidateExperimentResponse>() {
                @Override
                public void onResponse(ValidateExperimentResponse response) {
                    try {
                        XContentBuilder builder = channel.newBuilder();
                        response.toXContent(builder, ToXContent.EMPTY_PARAMS);
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
                        logger.error("Failed to send error response", ex);
                    }
                }
            }
        );
    }
}
