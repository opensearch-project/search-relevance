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
import static org.opensearch.searchrelevance.common.MLConstants.validateTokenLimit;
import static org.opensearch.searchrelevance.common.MetricsConstants.MODEL_ID;
import static org.opensearch.searchrelevance.common.PluginConstants.CLICK_MODEL;
import static org.opensearch.searchrelevance.common.PluginConstants.CONTEXT_FIELDS;
import static org.opensearch.searchrelevance.common.PluginConstants.DESCRIPTION;
import static org.opensearch.searchrelevance.common.PluginConstants.IGNORE_FAILURE;
import static org.opensearch.searchrelevance.common.PluginConstants.JUDGMENTS_URL;
import static org.opensearch.searchrelevance.common.PluginConstants.JUDGMENT_RATINGS;
import static org.opensearch.searchrelevance.common.PluginConstants.NAME;
import static org.opensearch.searchrelevance.common.PluginConstants.NAX_RANK;
import static org.opensearch.searchrelevance.common.PluginConstants.QUERYSET_ID;
import static org.opensearch.searchrelevance.common.PluginConstants.SEARCH_CONFIGURATION_LIST;
import static org.opensearch.searchrelevance.common.PluginConstants.SIZE;
import static org.opensearch.searchrelevance.common.PluginConstants.TYPE;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Optional;

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
import org.opensearch.searchrelevance.exception.SearchRelevanceException;
import org.opensearch.searchrelevance.model.JudgmentType;
import org.opensearch.searchrelevance.settings.SearchRelevanceSettingsAccessor;
import org.opensearch.searchrelevance.transport.judgment.PutImportJudgmentRequest;
import org.opensearch.searchrelevance.transport.judgment.PutJudgmentAction;
import org.opensearch.searchrelevance.transport.judgment.PutJudgmentRequest;
import org.opensearch.searchrelevance.transport.judgment.PutLlmJudgmentRequest;
import org.opensearch.searchrelevance.transport.judgment.PutUbiJudgmentRequest;
import org.opensearch.searchrelevance.utils.ParserUtils;
import org.opensearch.searchrelevance.utils.TextValidationUtil;
import org.opensearch.transport.client.node.NodeClient;

import lombok.AllArgsConstructor;

/**
 * Rest Action to facilitate requests to create a judgment.
 */
@AllArgsConstructor
public class RestPutJudgmentAction extends BaseRestHandler {
    private static final Logger LOGGER = LogManager.getLogger(RestPutJudgmentAction.class);
    private static final String PUT_JUDGMENT_ACTION = "put_judgment_action";
    private SearchRelevanceSettingsAccessor settingsAccessor;

    @Override
    public String getName() {
        return PUT_JUDGMENT_ACTION;
    }

    @Override
    public List<Route> routes() {
        return singletonList(new Route(PUT, JUDGMENTS_URL));
    }

    @Override
    protected RestChannelConsumer prepareRequest(RestRequest request, NodeClient client) throws IOException {
        if (!settingsAccessor.isWorkbenchEnabled()) {
            return channel -> channel.sendResponse(new BytesRestResponse(RestStatus.FORBIDDEN, "Search Relevance Workbench is disabled"));
        }
        XContentParser parser = request.contentParser();
        Map<String, Object> source = parser.map();

        String name = (String) source.get(NAME);
        TextValidationUtil.ValidationResult nameValidation = TextValidationUtil.validateText(name);
        if (!nameValidation.isValid()) {
            return channel -> channel.sendResponse(
                new BytesRestResponse(RestStatus.BAD_REQUEST, "Invalid name: " + nameValidation.getErrorMessage())
            );
        }
        String description = (String) source.get(DESCRIPTION);
        if (description != null) {
            TextValidationUtil.ValidationResult descriptionValidation = TextValidationUtil.validateText(description);
            if (!descriptionValidation.isValid()) {
                return channel -> channel.sendResponse(
                    new BytesRestResponse(RestStatus.BAD_REQUEST, "Invalid description: " + descriptionValidation.getErrorMessage())
                );
            }
        }

        String typeString = (String) source.get(TYPE);
        JudgmentType type;
        try {
            type = JudgmentType.valueOf(typeString);
        } catch (IllegalArgumentException | NullPointerException e) {
            throw new IllegalArgumentException("Invalid or missing judgment type", e);
        }

        PutJudgmentRequest createRequest;
        switch (type) {
            case LLM_JUDGMENT -> {
                String modelId = (String) source.get(MODEL_ID);
                if (modelId == null) {
                    throw new SearchRelevanceException("modelId is required for LLM_JUDGMENT", RestStatus.BAD_REQUEST);
                }
                String querySetId = (String) source.get(QUERYSET_ID);
                List<String> searchConfigurationList = ParserUtils.convertObjToList(source, SEARCH_CONFIGURATION_LIST);
                int size = (Integer) source.get(SIZE);
                boolean ignoreFailure = Optional.ofNullable((Boolean) source.get(IGNORE_FAILURE)).orElse(Boolean.FALSE);  // default to
                                                                                                                           // false if not
                                                                                                                           // provided

                int tokenLimit = validateTokenLimit(source);
                List<String> contextFields = ParserUtils.convertObjToList(source, CONTEXT_FIELDS);
                createRequest = new PutLlmJudgmentRequest(
                    type,
                    name,
                    description,
                    modelId,
                    querySetId,
                    searchConfigurationList,
                    size,
                    tokenLimit,
                    contextFields,
                    ignoreFailure
                );
            }
            case UBI_JUDGMENT -> {
                String clickModel = (String) source.get(CLICK_MODEL);
                int maxRank = (int) source.get(NAX_RANK);
                createRequest = new PutUbiJudgmentRequest(type, name, description, clickModel, maxRank);
            }
            case IMPORT_JUDGMENT -> {
                List<Map<String, Object>> judgmentRatings = (List<Map<String, Object>>) source.get(JUDGMENT_RATINGS);
                createRequest = new PutImportJudgmentRequest(type, name, description, judgmentRatings);
            }
            default -> {
                throw new SearchRelevanceException("Unsupported experiment type: " + type, RestStatus.BAD_REQUEST);
            }
        }

        return channel -> client.execute(PutJudgmentAction.INSTANCE, createRequest, new ActionListener<IndexResponse>() {
            @Override
            public void onResponse(IndexResponse response) {
                try {
                    XContentBuilder builder = channel.newBuilder();
                    builder.startObject();
                    builder.field("judgment_id", response.getId());
                    builder.endObject();
                    channel.sendResponse(new BytesRestResponse(RestStatus.OK, builder));
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
