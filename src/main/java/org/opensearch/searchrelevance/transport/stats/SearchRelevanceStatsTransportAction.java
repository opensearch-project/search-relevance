/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */
package org.opensearch.searchrelevance.transport.stats;

import java.io.IOException;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.opensearch.action.FailedNodeException;
import org.opensearch.action.support.ActionFilters;
import org.opensearch.action.support.nodes.TransportNodesAction;
import org.opensearch.cluster.service.ClusterService;
import org.opensearch.common.inject.Inject;
import org.opensearch.core.common.io.stream.StreamInput;
import org.opensearch.searchrelevance.stats.common.StatSnapshot;
import org.opensearch.searchrelevance.stats.events.EventStatName;
import org.opensearch.searchrelevance.stats.events.EventStatsManager;
import org.opensearch.searchrelevance.stats.events.TimestampedEventStatSnapshot;
import org.opensearch.searchrelevance.stats.info.InfoStatName;
import org.opensearch.searchrelevance.stats.info.InfoStatsManager;
import org.opensearch.threadpool.ThreadPool;
import org.opensearch.transport.TransportService;

/**
 *  SearchRelevanceStatsTransportAction contains the logic to extract the stats from the nodes
 */
public class SearchRelevanceStatsTransportAction extends TransportNodesAction<
    SearchRelevanceStatsRequest,
    SearchRelevanceStatsResponse,
    SearchRelevanceStatsNodeRequest,
    SearchRelevanceStatsNodeResponse> {
    private final EventStatsManager eventStatsManager;
    private final InfoStatsManager infoStatsManager;

    /**
     * Constructor
     *
     * @param threadPool ThreadPool to use
     * @param clusterService ClusterService
     * @param transportService TransportService
     * @param actionFilters Action Filters
     */
    @Inject
    public SearchRelevanceStatsTransportAction(
        ThreadPool threadPool,
        ClusterService clusterService,
        TransportService transportService,
        ActionFilters actionFilters,
        EventStatsManager eventStatsManager,
        InfoStatsManager infoStatsManager
    ) {
        super(
            SearchRelevanceStatsAction.NAME,
            threadPool,
            clusterService,
            transportService,
            actionFilters,
            SearchRelevanceStatsRequest::new,
            SearchRelevanceStatsNodeRequest::new,
            ThreadPool.Names.MANAGEMENT,
            SearchRelevanceStatsNodeResponse.class
        );
        this.eventStatsManager = eventStatsManager;
        this.infoStatsManager = infoStatsManager;
    }

    @Override
    protected SearchRelevanceStatsResponse newResponse(
        SearchRelevanceStatsRequest request,
        List<SearchRelevanceStatsNodeResponse> responses,
        List<FailedNodeException> failures
    ) {
        // Convert node level stats to map
        Map<String, Map<String, StatSnapshot<?>>> nodeIdToEventStats = processorNodeEventStatsIntoMap(responses);

        // Sum the map to aggregate
        Map<String, StatSnapshot<?>> aggregatedNodeStats = Collections.emptyMap();
        if (request.getSearchRelevanceStatsInput().isIncludeAllNodes()) {
            aggregatedNodeStats = aggregateNodesResponses(responses, request.getSearchRelevanceStatsInput().getEventStatNames());
        }

        // Get info stats
        Map<String, StatSnapshot<?>> flatInfoStats = Collections.emptyMap();
        if (request.getSearchRelevanceStatsInput().isIncludeInfo()) {
            // Get info stats
            Map<InfoStatName, StatSnapshot<?>> infoStats = infoStatsManager.getStats(
                request.getSearchRelevanceStatsInput().getInfoStatNames()
            );
            // Convert stat name keys into flat path strings
            flatInfoStats = infoStats.entrySet()
                .stream()
                .collect(Collectors.toMap(entry -> entry.getKey().getFullPath(), Map.Entry::getValue));
        }

        return new SearchRelevanceStatsResponse(
            clusterService.getClusterName(),
            responses,
            failures,
            flatInfoStats,
            aggregatedNodeStats,
            nodeIdToEventStats,
            request.getSearchRelevanceStatsInput().isFlatten(),
            request.getSearchRelevanceStatsInput().isIncludeMetadata(),
            request.getSearchRelevanceStatsInput().isIncludeIndividualNodes(),
            request.getSearchRelevanceStatsInput().isIncludeAllNodes(),
            request.getSearchRelevanceStatsInput().isIncludeInfo()
        );
    }

    @Override
    protected SearchRelevanceStatsNodeRequest newNodeRequest(SearchRelevanceStatsRequest request) {
        return new SearchRelevanceStatsNodeRequest(request);
    }

    @Override
    protected SearchRelevanceStatsNodeResponse newNodeResponse(StreamInput in) throws IOException {
        return new SearchRelevanceStatsNodeResponse(in);
    }

    /**
     * Node operation to retrieve stats from node local event stats manager
     * @param request the node level request
     * @return the node level response containing node level event stats
     */
    @Override
    protected SearchRelevanceStatsNodeResponse nodeOperation(SearchRelevanceStatsNodeRequest request) {
        // Reads node level stats on an individual node
        EnumSet<EventStatName> eventStatsToRetrieve = request.getRequest().getSearchRelevanceStatsInput().getEventStatNames();
        Map<EventStatName, TimestampedEventStatSnapshot> eventStatDataMap = eventStatsManager.getTimestampedEventStatSnapshots(
            eventStatsToRetrieve
        );

        return new SearchRelevanceStatsNodeResponse(clusterService.localNode(), eventStatDataMap);
    }

    /**
     * Helper to aggregate node response event stats to give cluster level aggregate info on node-level stats
     * @param responses node stat responses
     * @param statsToRetrieve a list of stats to filter
     * @return A map associating cluster level aggregated stat name strings with their stat snapshot values
     */
    private Map<String, StatSnapshot<?>> aggregateNodesResponses(
        List<SearchRelevanceStatsNodeResponse> responses,
        EnumSet<EventStatName> statsToRetrieve
    ) {
        // Catch empty nodes responses case.
        if (responses == null || responses.isEmpty()) {
            return new HashMap<>();
        }

        // Convert node responses into list of Map<EventStatName, EventStatData>
        List<Map<EventStatName, TimestampedEventStatSnapshot>> nodeEventStatsList = responses.stream()
            .map(SearchRelevanceStatsNodeResponse::getStats)
            .map(map -> map.entrySet().stream().collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue)))
            .toList();

        // Aggregate all events from all responses for all stats to retrieve
        Map<String, StatSnapshot<?>> aggregatedMap = new HashMap<>();
        for (EventStatName eventStatName : statsToRetrieve) {
            Set<TimestampedEventStatSnapshot> timestampedEventStatSnapshotCollection = new HashSet<>();
            for (Map<EventStatName, TimestampedEventStatSnapshot> eventStats : nodeEventStatsList) {
                timestampedEventStatSnapshotCollection.add(eventStats.get(eventStatName));
            }

            TimestampedEventStatSnapshot aggregatedEventSnapshots = TimestampedEventStatSnapshot.aggregateEventStatSnapshots(
                timestampedEventStatSnapshotCollection
            );

            // Skip adding null event stats. This happens when a node id parameter is invalid.
            if (aggregatedEventSnapshots != null) {
                aggregatedMap.put(eventStatName.getFullPath(), aggregatedEventSnapshots);
            }
        }

        return aggregatedMap;
    }

    /**
     * Helper to convert node responses into a map of node id to event stats
     * @param nodeResponses node stat responses
     * @return A map of node id strings to their event stat data
     */
    private Map<String, Map<String, StatSnapshot<?>>> processorNodeEventStatsIntoMap(List<SearchRelevanceStatsNodeResponse> nodeResponses) {
        // Converts list of node responses into Map<NodeId, EventStats>
        Map<String, Map<String, StatSnapshot<?>>> results = new HashMap<>();

        String nodeId;
        for (SearchRelevanceStatsNodeResponse nodesResponse : nodeResponses) {
            nodeId = nodesResponse.getNode().getId();

            // Convert StatNames into paths
            Map<String, StatSnapshot<?>> resultNodeStatsMap = nodesResponse.getStats()
                .entrySet()
                .stream()
                .collect(Collectors.toMap(entry -> entry.getKey().getFullPath(), Map.Entry::getValue));

            // Map each node id to its stats
            results.put(nodeId, resultNodeStatsMap);
        }
        return results;
    }

}
