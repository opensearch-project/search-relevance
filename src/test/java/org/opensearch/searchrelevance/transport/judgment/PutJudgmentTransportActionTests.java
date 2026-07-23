/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */
package org.opensearch.searchrelevance.transport.judgment;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.apache.lucene.search.TotalHits;
import org.junit.Before;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.opensearch.Version;
import org.opensearch.action.index.IndexResponse;
import org.opensearch.action.search.SearchResponse;
import org.opensearch.action.support.ActionFilters;
import org.opensearch.cluster.ClusterState;
import org.opensearch.cluster.node.DiscoveryNodes;
import org.opensearch.cluster.service.ClusterService;
import org.opensearch.core.action.ActionListener;
import org.opensearch.search.SearchHit;
import org.opensearch.search.SearchHits;
import org.opensearch.searchrelevance.dao.JudgmentDao;
import org.opensearch.searchrelevance.dao.QuerySetDao;
import org.opensearch.searchrelevance.dao.SearchConfigurationDao;
import org.opensearch.searchrelevance.judgments.JudgmentsProcessorFactory;
import org.opensearch.searchrelevance.model.JudgmentType;
import org.opensearch.test.OpenSearchTestCase;
import org.opensearch.transport.TransportService;

public class PutJudgmentTransportActionTests extends OpenSearchTestCase {

    @Mock
    private ClusterService clusterService;
    @Mock
    private TransportService transportService;
    @Mock
    private ActionFilters actionFilters;
    @Mock
    private JudgmentDao judgmentDao;
    @Mock
    private QuerySetDao querySetDao;
    @Mock
    private SearchConfigurationDao searchConfigurationDao;
    @Mock
    private JudgmentsProcessorFactory judgmentsProcessorFactory;

    private PutJudgmentTransportAction action;

    @Before
    public void setup() {
        MockitoAnnotations.openMocks(this);

        // The transport action checks the cluster's minimum node version before allowing the
        // existingJudgments field (a rolling-upgrade guard). Stub the chain to report a fully
        // upgraded cluster so the guard passes and the rest of the validation is exercised.
        ClusterState clusterState = mock(ClusterState.class);
        DiscoveryNodes discoveryNodes = mock(DiscoveryNodes.class);
        when(clusterService.state()).thenReturn(clusterState);
        when(clusterState.getNodes()).thenReturn(discoveryNodes);
        when(discoveryNodes.getMinNodeVersion()).thenReturn(Version.CURRENT);

        action = new PutJudgmentTransportAction(
            clusterService,
            transportService,
            actionFilters,
            judgmentDao,
            querySetDao,
            searchConfigurationDao,
            judgmentsProcessorFactory
        );
    }

    public void testValidation_LlmJudgment_QuerySetNotFound() {
        PutLlmJudgmentRequest request = new PutLlmJudgmentRequest(
            JudgmentType.LLM_JUDGMENT,
            "test-judgment",
            "test description",
            "test-model-id",
            "missing-queryset-id",
            List.of(),
            10,
            1000,
            null, // contextFields
            false, // ignoreFailure
            null, // promptTemplate
            null, // llmJudgmentRatingType
            null // existingJudgments
        );

        // Mock QuerySet DAO to return 0 hits (entity not found)
        SearchResponse mockResponse = mock(SearchResponse.class);
        SearchHits searchHits = new SearchHits(new SearchHit[0], new TotalHits(0, TotalHits.Relation.EQUAL_TO), 0.0f);
        when(mockResponse.getHits()).thenReturn(searchHits);

        doAnswer(invocation -> {
            ActionListener<SearchResponse> listener = invocation.getArgument(1);
            listener.onResponse(mockResponse);
            return null;
        }).when(querySetDao).checkQuerySetExists(eq("missing-queryset-id"), any(ActionListener.class));

        ActionListener<IndexResponse> responseListener = mock(ActionListener.class);
        action.doExecute(null, request, responseListener);

        ArgumentCaptor<Exception> exceptionCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(responseListener).onFailure(exceptionCaptor.capture());

        Exception exception = exceptionCaptor.getValue();
        assertTrue(exception.getMessage().contains("QuerySet [missing-queryset-id] does not exist"));
    }

    public void testValidation_LlmJudgment_TooManyExistingJudgments() {
        // Reference 6 existing judgments — one more than the allowed maximum of 5.
        PutLlmJudgmentRequest request = new PutLlmJudgmentRequest(
            JudgmentType.LLM_JUDGMENT,
            "test-judgment",
            "test description",
            "test-model-id",
            "valid-queryset-id",
            List.of(),
            10,
            1000,
            null, // contextFields
            false, // ignoreFailure
            null, // promptTemplate
            null, // llmJudgmentRatingType
            List.of("j1", "j2", "j3", "j4", "j5", "j6") // existingJudgments — over the limit
        );

        ActionListener<IndexResponse> responseListener = mock(ActionListener.class);
        action.doExecute(null, request, responseListener);

        ArgumentCaptor<Exception> exceptionCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(responseListener).onFailure(exceptionCaptor.capture());

        Exception exception = exceptionCaptor.getValue();
        assertTrue(exception.getMessage().contains("Too many existing judgments referenced"));
    }

    public void testValidation_LlmJudgment_SearchConfigNotFound() {
        PutLlmJudgmentRequest request = new PutLlmJudgmentRequest(
            JudgmentType.LLM_JUDGMENT,
            "test-judgment",
            "test description",
            "test-model-id",
            "valid-queryset-id",
            List.of("missing-config-id"),
            10,
            1000,
            null, // contextFields
            false, // ignoreFailure
            null, // promptTemplate
            null, // llmJudgmentRatingType
            null // existingJudgments
        );

        // Mock QuerySet exists
        SearchResponse mockQuerySetResponse = mock(SearchResponse.class);
        SearchHits querySetHits = new SearchHits(new SearchHit[0], new TotalHits(1, TotalHits.Relation.EQUAL_TO), 0.0f);
        when(mockQuerySetResponse.getHits()).thenReturn(querySetHits);

        doAnswer(invocation -> {
            ActionListener<SearchResponse> listener = invocation.getArgument(1);
            listener.onResponse(mockQuerySetResponse);
            return null;
        }).when(querySetDao).checkQuerySetExists(eq("valid-queryset-id"), any(ActionListener.class));

        // Mock SearchConfiguration DAO to return 0 hits
        SearchResponse mockResponse = mock(SearchResponse.class);
        SearchHits searchHits = new SearchHits(new SearchHit[0], new TotalHits(0, TotalHits.Relation.EQUAL_TO), 0.0f);
        when(mockResponse.getHits()).thenReturn(searchHits);

        doAnswer(invocation -> {
            ActionListener<SearchResponse> listener = invocation.getArgument(1);
            listener.onResponse(mockResponse);
            return null;
        }).when(searchConfigurationDao).checkSearchConfigurationExists(eq("missing-config-id"), any(ActionListener.class));

        ActionListener<IndexResponse> responseListener = mock(ActionListener.class);
        action.doExecute(null, request, responseListener);

        ArgumentCaptor<Exception> exceptionCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(responseListener).onFailure(exceptionCaptor.capture());

        Exception exception = exceptionCaptor.getValue();
        assertTrue(exception.getMessage().contains("SearchConfiguration [missing-config-id] does not exist"));
    }

    public void testValidation_LlmJudgment_ExistingJudgmentsIgnoredOnMixedVersionCluster() {
        // Simulate a rolling upgrade: at least one node is still on an older version. The
        // existingJudgments field cannot be serialized to such a node, so it is ignored (cleared)
        // and the request falls back to the previous behavior rather than being rejected.
        when(clusterService.state().getNodes().getMinNodeVersion()).thenReturn(Version.V_3_7_0);

        PutLlmJudgmentRequest request = new PutLlmJudgmentRequest(
            JudgmentType.LLM_JUDGMENT,
            "test-judgment",
            "test description",
            "test-model-id",
            "valid-queryset-id",
            List.of(),
            10,
            1000,
            null, // contextFields
            false, // ignoreFailure
            null, // promptTemplate
            null, // llmJudgmentRatingType
            List.of("j1") // existingJudgments — present, but cluster is not fully upgraded
        );

        // QuerySet exists so validation proceeds past the version check.
        SearchResponse mockQuerySetResponse = mock(SearchResponse.class);
        SearchHits querySetHits = new SearchHits(new SearchHit[0], new TotalHits(1, TotalHits.Relation.EQUAL_TO), 0.0f);
        when(mockQuerySetResponse.getHits()).thenReturn(querySetHits);
        doAnswer(invocation -> {
            ActionListener<SearchResponse> listener = invocation.getArgument(1);
            listener.onResponse(mockQuerySetResponse);
            return null;
        }).when(querySetDao).checkQuerySetExists(eq("valid-queryset-id"), any(ActionListener.class));

        ActionListener<IndexResponse> responseListener = mock(ActionListener.class);
        action.doExecute(null, request, responseListener);

        // The field is ignored (cleared), not rejected: no version-related failure is raised and
        // the request no longer carries existingJudgments.
        assertNull("existingJudgments should be cleared on a mixed-version cluster", request.getExistingJudgments());
    }
}
