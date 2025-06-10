/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */
package org.opensearch.searchrelevance.transport.stats;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.Before;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.opensearch.action.FailedNodeException;
import org.opensearch.action.support.ActionFilters;
import org.opensearch.cluster.ClusterName;
import org.opensearch.cluster.node.DiscoveryNode;
import org.opensearch.cluster.service.ClusterService;
import org.opensearch.searchrelevance.stats.SearchRelevanceStatsInput;
import org.opensearch.searchrelevance.stats.common.StatSnapshot;
import org.opensearch.searchrelevance.stats.events.EventStatName;
import org.opensearch.searchrelevance.stats.events.EventStatsManager;
import org.opensearch.searchrelevance.stats.events.TimestampedEventStatSnapshot;
import org.opensearch.searchrelevance.stats.info.CountableInfoStatSnapshot;
import org.opensearch.searchrelevance.stats.info.InfoStatName;
import org.opensearch.searchrelevance.stats.info.InfoStatsManager;
import org.opensearch.test.OpenSearchTestCase;
import org.opensearch.threadpool.ThreadPool;
import org.opensearch.transport.TransportService;

public class SearchRelevanceStatsTransportActionTests extends OpenSearchTestCase {

    @Mock
    private ThreadPool threadPool;

    @Mock
    private ClusterService clusterService;

    @Mock
    private TransportService transportService;

    @Mock
    private ActionFilters actionFilters;

    @Mock
    private EventStatsManager eventStatsManager;

    @Mock
    private InfoStatsManager infoStatsManager;

    private SearchRelevanceStatsTransportAction transportAction;
    private ClusterName clusterName;

    private static InfoStatName infoStatName = InfoStatName.CLUSTER_VERSION;
    private static EventStatName eventStatName = EventStatName.LLM_JUDGMENT_RATING_GENERATIONS;

    @Before
    public void setup() {
        MockitoAnnotations.openMocks(this);
        clusterName = new ClusterName("test-cluster");
        when(clusterService.getClusterName()).thenReturn(clusterName);

        transportAction = new SearchRelevanceStatsTransportAction(
            threadPool,
            clusterService,
            transportService,
            actionFilters,
            eventStatsManager,
            infoStatsManager
        );
    }

    public void test_newResponse() {
        // Create inputs
        SearchRelevanceStatsInput input = new SearchRelevanceStatsInput();
        SearchRelevanceStatsRequest request = new SearchRelevanceStatsRequest(new String[] {}, input);
        List<SearchRelevanceStatsNodeResponse> responses = new ArrayList<>();
        List<FailedNodeException> failures = new ArrayList<>();

        // Execute response
        SearchRelevanceStatsResponse response = transportAction.newResponse(request, responses, failures);

        // Validate response
        assertNotNull(response);
        assertEquals(clusterName, response.getClusterName());
        assertTrue(response.getNodes().isEmpty());
    }

    public void test_newResponse_customSearchRelevanceStatsInputParams() {
        // Create inputs
        SearchRelevanceStatsInput input = new SearchRelevanceStatsInput();
        input.setIncludeInfo(false);
        input.setIncludeAllNodes(false);
        input.setIncludeIndividualNodes(false);

        SearchRelevanceStatsRequest request = new SearchRelevanceStatsRequest(new String[] {}, input);
        List<SearchRelevanceStatsNodeResponse> responses = new ArrayList<>();
        List<FailedNodeException> failures = new ArrayList<>();

        // Execute response
        SearchRelevanceStatsResponse response = transportAction.newResponse(request, responses, failures);

        // Validate response
        assertNotNull(response);
        assertEquals(clusterName, response.getClusterName());
        assertFalse(response.isIncludeIndividualNodes());
        assertFalse(response.isIncludeAllNodes());
        assertFalse(response.isIncludeInfo());
    }

    public void test_newResponseMultipleNodesStateAndEventStats() {
        // Create inputs
        EnumSet<EventStatName> eventStats = EnumSet.of(eventStatName);
        EnumSet<InfoStatName> infoStats = EnumSet.of(infoStatName);

        SearchRelevanceStatsInput input = SearchRelevanceStatsInput.builder()
            .eventStatNames(eventStats)
            .infoStatNames(infoStats)
            .includeIndividualNodes(true)
            .includeAllNodes(true)
            .includeInfo(true)
            .build();
        SearchRelevanceStatsRequest request = new SearchRelevanceStatsRequest(new String[] {}, input);

        // Create multiple nodes
        DiscoveryNode node1 = mock(DiscoveryNode.class);
        when(node1.getId()).thenReturn("test-node-1");
        DiscoveryNode node2 = mock(DiscoveryNode.class);
        when(node2.getId()).thenReturn("test-node-2");

        // Create event stats
        TimestampedEventStatSnapshot snapshot1 = TimestampedEventStatSnapshot.builder()
            .statName(eventStatName)
            .value(17)
            .minutesSinceLastEvent(3)
            .trailingIntervalValue(5)
            .build();

        TimestampedEventStatSnapshot snapshot2 = TimestampedEventStatSnapshot.builder()
            .statName(eventStatName)
            .value(33)
            .minutesSinceLastEvent(0)
            .trailingIntervalValue(15)
            .build();

        Map<EventStatName, TimestampedEventStatSnapshot> nodeStats1 = new HashMap<>();
        nodeStats1.put(eventStatName, snapshot1);
        Map<EventStatName, TimestampedEventStatSnapshot> nodeStats2 = new HashMap<>();
        nodeStats2.put(eventStatName, snapshot2);

        List<SearchRelevanceStatsNodeResponse> responses = Arrays.asList(
            new SearchRelevanceStatsNodeResponse(node1, nodeStats1),
            new SearchRelevanceStatsNodeResponse(node2, nodeStats2)
        );

        // Create info stats
        CountableInfoStatSnapshot infoStatSnapshot = new CountableInfoStatSnapshot(infoStatName);
        infoStatSnapshot.incrementBy(2001L);
        Map<InfoStatName, StatSnapshot<?>> mockInfoStats = new HashMap<>();
        mockInfoStats.put(InfoStatName.CLUSTER_VERSION, infoStatSnapshot);
        when(infoStatsManager.getStats(infoStats)).thenReturn(mockInfoStats);

        List<FailedNodeException> failures = new ArrayList<>();

        // Execute
        SearchRelevanceStatsResponse response = transportAction.newResponse(request, responses, failures);

        // Verify node level event stats
        assertNotNull(response);
        assertEquals(2, response.getNodes().size());

        Map<String, Map<String, StatSnapshot<?>>> nodeEventStats = response.getNodeIdToNodeEventStats();

        assertNotNull(nodeEventStats);
        assertTrue(nodeEventStats.containsKey("test-node-1"));
        assertTrue(nodeEventStats.containsKey("test-node-2"));

        StatSnapshot<?> node1Stat = nodeEventStats.get("test-node-1").get(eventStatName.getFullPath());
        assertEquals(17L, node1Stat.getValue());

        StatSnapshot<?> node2Stat = nodeEventStats.get("test-node-2").get(eventStatName.getFullPath());
        assertEquals(33L, node2Stat.getValue());

        Map<String, StatSnapshot<?>> aggregatedNodeStats = response.getAggregatedNodeStats();
        assertNotNull(aggregatedNodeStats);

        // Validate timestamped event stats aggregated correctly
        String aggregatedStatPath = eventStatName.getFullPath();
        TimestampedEventStatSnapshot aggregatedStat = (TimestampedEventStatSnapshot) aggregatedNodeStats.get(aggregatedStatPath);
        assertNotNull(aggregatedStat);

        assertEquals(50L, aggregatedStat.getValue().longValue());
        assertEquals(0L, aggregatedStat.getMinutesSinceLastEvent());
        assertEquals(20L, aggregatedStat.getTrailingIntervalValue());
        assertEquals(eventStatName, aggregatedStat.getStatName());

        // Verify info stats
        Map<String, StatSnapshot<?>> resultStats = response.getInfoStats();
        assertNotNull(resultStats);

        // Verify info stats
        String infoStatPath = infoStatName.getFullPath();
        StatSnapshot<?> resultStateSnapshot = resultStats.get(infoStatPath);
        assertNotNull(resultStateSnapshot);
        assertEquals(2001L, resultStateSnapshot.getValue());
    }

    public void test_newResponseMultipleNodesStateAndEventStats_customParams() {
        // Create inputs
        EnumSet<EventStatName> eventStats = EnumSet.of(eventStatName);
        EnumSet<InfoStatName> infoStats = EnumSet.of(infoStatName);

        SearchRelevanceStatsInput input = SearchRelevanceStatsInput.builder()
            .eventStatNames(eventStats)
            .infoStatNames(infoStats)
            .includeIndividualNodes(true)
            .includeAllNodes(false) // <- exclude
            .includeInfo(false) // <- exclude
            .build();
        SearchRelevanceStatsRequest request = new SearchRelevanceStatsRequest(new String[] {}, input);

        // Create multiple nodes
        DiscoveryNode node1 = mock(DiscoveryNode.class);
        when(node1.getId()).thenReturn("test-node-1");
        DiscoveryNode node2 = mock(DiscoveryNode.class);
        when(node2.getId()).thenReturn("test-node-2");

        // Create event stats
        TimestampedEventStatSnapshot snapshot1 = TimestampedEventStatSnapshot.builder()
            .statName(eventStatName)
            .value(17)
            .minutesSinceLastEvent(3)
            .trailingIntervalValue(5)
            .build();

        TimestampedEventStatSnapshot snapshot2 = TimestampedEventStatSnapshot.builder()
            .statName(eventStatName)
            .value(33)
            .minutesSinceLastEvent(0)
            .trailingIntervalValue(15)
            .build();

        Map<EventStatName, TimestampedEventStatSnapshot> nodeStats1 = new HashMap<>();
        nodeStats1.put(eventStatName, snapshot1);
        Map<EventStatName, TimestampedEventStatSnapshot> nodeStats2 = new HashMap<>();
        nodeStats2.put(eventStatName, snapshot2);

        List<SearchRelevanceStatsNodeResponse> responses = Arrays.asList(
            new SearchRelevanceStatsNodeResponse(node1, nodeStats1),
            new SearchRelevanceStatsNodeResponse(node2, nodeStats2)
        );

        // Create info stats
        CountableInfoStatSnapshot infoStatSnapshot = new CountableInfoStatSnapshot(infoStatName);
        infoStatSnapshot.incrementBy(2001L);
        Map<InfoStatName, StatSnapshot<?>> mockInfoStats = new HashMap<>();
        mockInfoStats.put(infoStatName, infoStatSnapshot);
        when(infoStatsManager.getStats(infoStats)).thenReturn(mockInfoStats);

        List<FailedNodeException> failures = new ArrayList<>();

        // Execute
        SearchRelevanceStatsResponse response = transportAction.newResponse(request, responses, failures);

        // Verify params
        assertTrue(response.isIncludeIndividualNodes());
        assertFalse(response.isIncludeAllNodes());
        assertFalse(response.isIncludeInfo());

        // Verify node level event stats
        assertNotNull(response);
        assertEquals(2, response.getNodes().size());

        Map<String, Map<String, StatSnapshot<?>>> nodeEventStats = response.getNodeIdToNodeEventStats();

        assertNotNull(nodeEventStats);
        assertTrue(nodeEventStats.containsKey("test-node-1"));
        assertTrue(nodeEventStats.containsKey("test-node-2"));

        StatSnapshot<?> node1Stat = nodeEventStats.get("test-node-1").get(eventStatName.getFullPath());
        assertEquals(17L, node1Stat.getValue());

        StatSnapshot<?> node2Stat = nodeEventStats.get("test-node-2").get(eventStatName.getFullPath());
        assertEquals(33L, node2Stat.getValue());

        // Validate all nodes is empty (since is excluded)
        Map<String, StatSnapshot<?>> aggregatedNodeStats = response.getAggregatedNodeStats();
        assertTrue(aggregatedNodeStats.isEmpty());

        // Verify info stats is empty (since is excluded)
        Map<String, StatSnapshot<?>> resultStats = response.getInfoStats();
        assertTrue(resultStats.isEmpty());
    }

    public void test_nodeOperation() {
        EnumSet<EventStatName> eventStats = EnumSet.of(eventStatName);
        SearchRelevanceStatsInput input = SearchRelevanceStatsInput.builder().eventStatNames(eventStats).build();

        SearchRelevanceStatsRequest request = new SearchRelevanceStatsRequest(new String[] {}, input);
        SearchRelevanceStatsNodeRequest nodeRequest = new SearchRelevanceStatsNodeRequest(request);

        DiscoveryNode localNode = mock(DiscoveryNode.class);
        when(clusterService.localNode()).thenReturn(localNode);

        TimestampedEventStatSnapshot snapshot2 = TimestampedEventStatSnapshot.builder()
            .statName(eventStatName)
            .value(33)
            .minutesSinceLastEvent(3)
            .trailingIntervalValue(15)
            .build();

        Map<EventStatName, TimestampedEventStatSnapshot> mockStats = new HashMap<>();
        mockStats.put(eventStatName, snapshot2);
        when(eventStatsManager.getTimestampedEventStatSnapshots(eventStats)).thenReturn(mockStats);

        SearchRelevanceStatsNodeResponse response = transportAction.nodeOperation(nodeRequest);

        assertNotNull(response);
        assertEquals(localNode, response.getNode());

        Map<EventStatName, TimestampedEventStatSnapshot> responseStats = response.getStats();
        assertFalse(responseStats.isEmpty());

        TimestampedEventStatSnapshot stat = responseStats.get(eventStatName);
        assertNotNull(stat);
        assertEquals(33L, stat.getValue().longValue());
        assertEquals(3L, stat.getMinutesSinceLastEvent());
        assertEquals(15L, stat.getTrailingIntervalValue());
        assertEquals(eventStatName, stat.getStatName());

        verify(eventStatsManager).getTimestampedEventStatSnapshots(eventStats);
    }
}
