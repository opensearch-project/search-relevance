/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */
package org.opensearch.searchrelevance.rest;

import static org.opensearch.searchrelevance.common.PluginConstants.SEARCH_RELEVANCE_BASE_URI;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.opensearch.common.util.set.Sets;
import org.opensearch.core.rest.RestStatus;
import org.opensearch.rest.BaseRestHandler;
import org.opensearch.rest.BytesRestResponse;
import org.opensearch.rest.RestRequest;
import org.opensearch.rest.action.RestActions;
import org.opensearch.searchrelevance.settings.SearchRelevanceSettingsAccessor;
import org.opensearch.searchrelevance.stats.SearchRelevanceStatsInput;
import org.opensearch.searchrelevance.stats.events.EventStatName;
import org.opensearch.searchrelevance.stats.info.InfoStatName;
import org.opensearch.searchrelevance.transport.stats.SearchRelevanceStatsAction;
import org.opensearch.searchrelevance.transport.stats.SearchRelevanceStatsRequest;
import org.opensearch.transport.client.node.NodeClient;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;

import lombok.AllArgsConstructor;
import lombok.extern.log4j.Log4j2;

/**
 * Rest action handler for the neural stats API
 * Calculates info stats and aggregates event stats from nodes and returns them in the response
 */
@Log4j2
@AllArgsConstructor
public class RestSearchRelevanceStatsAction extends BaseRestHandler {
    /**
     * Path parameter name for specified stats
     */
    public static final String STAT_PARAM = "stat";

    /**
     * Path parameter name for specified node ids
     */
    public static final String NODE_ID_PARAM = "nodeId";

    /**
     * Query parameter name to request flattened stat paths as keys
     */
    public static final String FLATTEN_PARAM = "flat_stat_paths";

    /**
     * Query parameter name to include metadata
     */
    public static final String INCLUDE_METADATA_PARAM = "include_metadata";

    /**
     * Regex for valid params, containing only alphanumeric, -, or _
     */
    public static final String PARAM_REGEX = "^[A-Za-z0-9-_]+$";

    /**
     * Max length for an individual query or path param
     */
    public static final int MAX_PARAM_LENGTH = 255;

    private SearchRelevanceSettingsAccessor settingsAccessor;

    private static final String NAME = "search_relevance_stats_action";

    private static final Set<String> EVENT_STAT_NAMES = EnumSet.allOf(EventStatName.class)
        .stream()
        .map(EventStatName::getNameString)
        .map(str -> str.toLowerCase(Locale.ROOT))
        .collect(Collectors.toSet());

    private static final Set<String> STATE_STAT_NAMES = EnumSet.allOf(InfoStatName.class)
        .stream()
        .map(InfoStatName::getNameString)
        .map(str -> str.toLowerCase(Locale.ROOT))
        .collect(Collectors.toSet());

    private static final List<Route> ROUTES = ImmutableList.of(
        new Route(RestRequest.Method.GET, SEARCH_RELEVANCE_BASE_URI + "/{nodeId}/stats/"),
        new Route(RestRequest.Method.GET, SEARCH_RELEVANCE_BASE_URI + "/{nodeId}/stats/{stat}"),
        new Route(RestRequest.Method.GET, SEARCH_RELEVANCE_BASE_URI + "/stats/"),
        new Route(RestRequest.Method.GET, SEARCH_RELEVANCE_BASE_URI + "/stats/{stat}")
    );

    private static final Set<String> RESPONSE_PARAMS = ImmutableSet.of(NODE_ID_PARAM, STAT_PARAM, INCLUDE_METADATA_PARAM, FLATTEN_PARAM);

    /**
     * Validates a param string if its under the max length and matches simple string pattern
     * @param param the string to validate
     * @return whether it's valid
     */
    public static boolean isValidParamString(String param) {
        return param.matches(PARAM_REGEX) && param.length() < MAX_PARAM_LENGTH;
    }

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public List<Route> routes() {
        return ROUTES;
    }

    @Override
    protected Set<String> responseParams() {
        return RESPONSE_PARAMS;
    }

    @Override
    protected RestChannelConsumer prepareRequest(RestRequest request, NodeClient client) {
        if (settingsAccessor.isWorkbenchEnabled() == false) {
            return channel -> channel.sendResponse(new BytesRestResponse(RestStatus.FORBIDDEN, "Search Relevance Workbench is disabled"));
        }

        if (settingsAccessor.isStatsEnabled() == false) {
            return channel -> channel.sendResponse(
                new BytesRestResponse(RestStatus.FORBIDDEN, "Search Relevance Workbench stats is disabled")
            );
        }

        // Read inputs and convert to BaseNodesRequest with correct info configured
        SearchRelevanceStatsRequest searchRelevanceStatsRequest = createStatsRequest(request);

        return channel -> client.execute(
            SearchRelevanceStatsAction.INSTANCE,
            searchRelevanceStatsRequest,
            new RestActions.NodesResponseRestListener<>(channel)
        );
    }

    /**
     * Creates a SearchRelevanceStatsRequest from a RestRequest
     *
     * @param request Rest request
     * @return SearchRelevanceStatsRequest
     */
    private SearchRelevanceStatsRequest createStatsRequest(RestRequest request) {
        SearchRelevanceStatsInput searchRelevanceStatsInput = createNeuralStatsInputFromRequestParams(request);
        String[] nodeIdsArr = searchRelevanceStatsInput.getNodeIds().toArray(new String[0]);

        SearchRelevanceStatsRequest searchRelevanceStatsRequest = new SearchRelevanceStatsRequest(nodeIdsArr, searchRelevanceStatsInput);
        searchRelevanceStatsRequest.timeout(request.param("timeout"));

        return searchRelevanceStatsRequest;
    }

    private SearchRelevanceStatsInput createNeuralStatsInputFromRequestParams(RestRequest request) {
        SearchRelevanceStatsInput searchRelevanceStatsInput = new SearchRelevanceStatsInput();

        // Parse specified nodes
        Optional<String[]> nodeIds = splitCommaSeparatedParam(request, NODE_ID_PARAM);
        if (nodeIds.isPresent()) {
            // Ignore node ids that don't pattern match
            List<String> validFormatNodeIds = Arrays.stream(nodeIds.get()).filter(this::isValidNodeId).toList();
            searchRelevanceStatsInput.getNodeIds().addAll(validFormatNodeIds);
        }

        // Parse query parameters
        boolean flatten = request.paramAsBoolean(FLATTEN_PARAM, false);
        searchRelevanceStatsInput.setFlatten(flatten);

        boolean includeMetadata = request.paramAsBoolean(INCLUDE_METADATA_PARAM, false);
        searchRelevanceStatsInput.setIncludeMetadata(includeMetadata);

        // Process requested stats parameters
        processStatsRequestParameters(request, searchRelevanceStatsInput);

        return searchRelevanceStatsInput;
    }

    private void processStatsRequestParameters(RestRequest request, SearchRelevanceStatsInput searchRelevanceStatsInput) {
        // Determine which stat names to retrieve based on user parameters
        Optional<String[]> optionalStats = splitCommaSeparatedParam(request, STAT_PARAM);

        if (optionalStats.isPresent() == false || optionalStats.get().length == 0) {
            // No specific stats requested, add all stats by default
            addAllStats(searchRelevanceStatsInput);
            return;
        }

        String[] stats = optionalStats.get();
        Set<String> invalidStatNames = new HashSet<>();
        for (String stat : stats) {
            // Validate parameter
            String normalizedStat = stat.toLowerCase(Locale.ROOT);
            if (isValidParamString(normalizedStat) == false) {
                invalidStatNames.add(normalizedStat);
                continue;
            }

            if (EVENT_STAT_NAMES.contains(normalizedStat)) {
                searchRelevanceStatsInput.getEventStatNames().add(EventStatName.from(normalizedStat));
            } else if (STATE_STAT_NAMES.contains(normalizedStat)) {
                searchRelevanceStatsInput.getInfoStatNames().add(InfoStatName.from(normalizedStat));
            } else {
                invalidStatNames.add(normalizedStat);
            }
        }

        // When we reach this block, we must have added at least one stat to the input, or else invalid stats will be
        // non empty. So throwing this exception here without adding all covers the empty input case.
        if (invalidStatNames.isEmpty() == false) {
            throw new IllegalArgumentException(
                unrecognized(request, invalidStatNames, Sets.union(EVENT_STAT_NAMES, STATE_STAT_NAMES), STAT_PARAM)
            );
        }
    }

    private void addAllStats(SearchRelevanceStatsInput searchRelevanceStatsInput) {
        searchRelevanceStatsInput.getEventStatNames().addAll(EnumSet.allOf(EventStatName.class));
        searchRelevanceStatsInput.getInfoStatNames().addAll(EnumSet.allOf(InfoStatName.class));
    }

    private Optional<String[]> splitCommaSeparatedParam(RestRequest request, String paramName) {
        return Optional.ofNullable(request.param(paramName)).map(s -> s.split(","));
    }

    private boolean isValidNodeId(String nodeId) {
        // Validate node id parameter
        return isValidParamString(nodeId) && nodeId.length() == 22;
    }
}
