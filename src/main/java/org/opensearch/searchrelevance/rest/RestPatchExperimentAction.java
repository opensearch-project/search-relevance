/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */
package org.opensearch.searchrelevance.rest;

import static java.util.Collections.singletonList;
import static org.opensearch.rest.RestRequest.Method.PATCH;
import static org.opensearch.searchrelevance.common.PluginConstants.DESCRIPTION;
import static org.opensearch.searchrelevance.common.PluginConstants.DOCUMENT_ID;
import static org.opensearch.searchrelevance.common.PluginConstants.EXPERIMENTS_URI;
import static org.opensearch.searchrelevance.common.PluginConstants.NAME;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.opensearch.ExceptionsHelper;
import org.opensearch.action.update.UpdateResponse;
import org.opensearch.core.action.ActionListener;
import org.opensearch.core.rest.RestStatus;
import org.opensearch.core.xcontent.XContentBuilder;
import org.opensearch.core.xcontent.XContentParser;
import org.opensearch.rest.BaseRestHandler;
import org.opensearch.rest.BytesRestResponse;
import org.opensearch.rest.RestRequest;
import org.opensearch.searchrelevance.settings.SearchRelevanceSettingsAccessor;
import org.opensearch.searchrelevance.transport.experiment.PatchExperimentAction;
import org.opensearch.searchrelevance.transport.experiment.PatchExperimentRequest;
import org.opensearch.searchrelevance.utils.TextValidationUtil;
import org.opensearch.transport.client.node.NodeClient;

import lombok.AllArgsConstructor;
import lombok.extern.log4j.Log4j2;

/**
 * Rest Action to facilitate requests to patch (partially update) an experiment.
 * Supports updating name and description fields.
 */
@Log4j2
@AllArgsConstructor
public class RestPatchExperimentAction extends BaseRestHandler {
    private static final String PATCH_EXPERIMENT_ACTION = "patch_experiment_action";
    private SearchRelevanceSettingsAccessor settingsAccessor;

    @Override
    public String getName() {
        return PATCH_EXPERIMENT_ACTION;
    }

    @Override
    public List<Route> routes() {
        return singletonList(new Route(PATCH, EXPERIMENTS_URI + "/{" + DOCUMENT_ID + "}"));
    }

    @Override
    protected Set<String> responseParams() {
        return Set.of(DOCUMENT_ID);
    }

    @Override
    protected RestChannelConsumer prepareRequest(RestRequest request, NodeClient client) throws IOException {
        if (!settingsAccessor.isWorkbenchEnabled()) {
            return channel -> channel.sendResponse(new BytesRestResponse(RestStatus.FORBIDDEN, "Search Relevance Workbench is disabled"));
        }

        String experimentId = request.param(DOCUMENT_ID);
        if (experimentId == null || experimentId.trim().isEmpty()) {
            return channel -> channel.sendResponse(new BytesRestResponse(RestStatus.BAD_REQUEST, "Experiment ID is required"));
        }

        XContentParser parser = request.contentParser();
        Map<String, Object> source = parser.map();

        // Parse and validate optional name field
        TextValidationUtil.ParsedField parsedName = TextValidationUtil.parseOptionalExperimentName(source, NAME);
        if (parsedName.hasError()) {
            return channel -> channel.sendResponse(
                new BytesRestResponse(RestStatus.BAD_REQUEST, "Invalid name: " + parsedName.getErrorMessage())
            );
        }
        String name = parsedName.getValue();

        // Parse and validate optional description field
        TextValidationUtil.ParsedField parsedDescription = TextValidationUtil.parseOptionalDescription(source, DESCRIPTION);
        if (parsedDescription.hasError()) {
            return channel -> channel.sendResponse(
                new BytesRestResponse(RestStatus.BAD_REQUEST, "Invalid description: " + parsedDescription.getErrorMessage())
            );
        }
        String description = parsedDescription.getValue();

        // Fail fast if neither name nor description is provided
        if (name == null && description == null) {
            return channel -> channel.sendResponse(
                new BytesRestResponse(RestStatus.BAD_REQUEST, "At least one of 'name' or 'description' must be provided for update")
            );
        }

        PatchExperimentRequest patchRequest = new PatchExperimentRequest(experimentId, name, description);

        return channel -> client.execute(PatchExperimentAction.INSTANCE, patchRequest, new ActionListener<UpdateResponse>() {
            @Override
            public void onResponse(UpdateResponse response) {
                try {
                    XContentBuilder builder = channel.newBuilder();
                    builder.startObject();
                    builder.field("experiment_id", response.getId());
                    builder.field("experiment_result", response.getResult().getLowercase());
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
