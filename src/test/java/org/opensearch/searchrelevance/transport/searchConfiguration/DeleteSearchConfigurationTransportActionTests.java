/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */
package org.opensearch.searchrelevance.transport.searchConfiguration;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.opensearch.searchrelevance.common.PluginConstants.SEARCH_CONFIGURATION_LIST;

import org.apache.lucene.search.TotalHits;
import org.junit.Before;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.opensearch.action.delete.DeleteResponse;
import org.opensearch.action.search.SearchResponse;
import org.opensearch.action.support.ActionFilters;
import org.opensearch.cluster.service.ClusterService;
import org.opensearch.core.action.ActionListener;
import org.opensearch.core.rest.RestStatus;
import org.opensearch.search.SearchHit;
import org.opensearch.search.SearchHits;
import org.opensearch.searchrelevance.dao.ExperimentDao;
import org.opensearch.searchrelevance.dao.SearchConfigurationDao;
import org.opensearch.searchrelevance.exception.SearchRelevanceException;
import org.opensearch.searchrelevance.transport.OpenSearchDocRequest;
import org.opensearch.test.OpenSearchTestCase;
import org.opensearch.transport.TransportService;

public class DeleteSearchConfigurationTransportActionTests extends OpenSearchTestCase {

    @Mock
    private ClusterService clusterService;
    @Mock
    private TransportService transportService;
    @Mock
    private ActionFilters actionFilters;
    @Mock
    private SearchConfigurationDao searchConfigurationDao;
    @Mock
    private ExperimentDao experimentDao;

    private DeleteSearchConfigurationTransportAction transportAction;

    @Before
    public void setup() {
        MockitoAnnotations.openMocks(this);
        transportAction = new DeleteSearchConfigurationTransportAction(
            clusterService,
            transportService,
            actionFilters,
            searchConfigurationDao,
            experimentDao
        );
    }

    public void testSuccessfulDeletion() {
        String searchConfigId = "test-search-config-id";
        OpenSearchDocRequest request = new OpenSearchDocRequest(searchConfigId);

        // Mock no experiments using this search configuration
        SearchResponse mockSearchResponse = createSearchResponseWithHitCount(0);
        doAnswer(invocation -> {
            ActionListener<SearchResponse> listener = invocation.getArgument(3);
            listener.onResponse(mockSearchResponse);
            return null;
        }).when(experimentDao).getExperimentByFieldId(eq(searchConfigId), eq(SEARCH_CONFIGURATION_LIST), eq(3), any(ActionListener.class));

        DeleteResponse mockDeleteResponse = mock(DeleteResponse.class);
        when(mockDeleteResponse.status()).thenReturn(RestStatus.OK);

        doAnswer(invocation -> {
            ActionListener<DeleteResponse> listener = invocation.getArgument(1);
            listener.onResponse(mockDeleteResponse);
            return null;
        }).when(searchConfigurationDao).deleteSearchConfiguration(eq(searchConfigId), any(ActionListener.class));

        ActionListener<DeleteResponse> responseListener = mock(ActionListener.class);
        transportAction.doExecute(null, request, responseListener);

        verify(experimentDao).getExperimentByFieldId(eq(searchConfigId), eq(SEARCH_CONFIGURATION_LIST), eq(3), any(ActionListener.class));
        verify(searchConfigurationDao).deleteSearchConfiguration(eq(searchConfigId), any(ActionListener.class));
    }

    public void testNullSearchConfigurationId() {
        OpenSearchDocRequest request = new OpenSearchDocRequest((String) null);

        ActionListener<DeleteResponse> responseListener = mock(ActionListener.class);
        transportAction.doExecute(null, request, responseListener);

        ArgumentCaptor<Exception> exceptionCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(responseListener).onFailure(exceptionCaptor.capture());

        Exception exception = exceptionCaptor.getValue();
        assertTrue(exception instanceof SearchRelevanceException);
        assertTrue(exception.getMessage().contains("searchConfigurationId cannot be null or empty"));
        assertEquals(RestStatus.BAD_REQUEST, ((SearchRelevanceException) exception).status());

        verify(searchConfigurationDao, never()).deleteSearchConfiguration(any(), any(ActionListener.class));
    }

    public void testSearchConfigurationInUseByExperiment() {
        String searchConfigId = "test-search-config-id";
        OpenSearchDocRequest request = new OpenSearchDocRequest(searchConfigId);

        // Mock experiments using this search configuration
        SearchResponse mockSearchResponse = createSearchResponseWithHitCount(1);
        doAnswer(invocation -> {
            ActionListener<SearchResponse> listener = invocation.getArgument(3);
            listener.onResponse(mockSearchResponse);
            return null;
        }).when(experimentDao).getExperimentByFieldId(eq(searchConfigId), eq(SEARCH_CONFIGURATION_LIST), eq(3), any(ActionListener.class));

        ActionListener<DeleteResponse> responseListener = mock(ActionListener.class);
        transportAction.doExecute(null, request, responseListener);

        ArgumentCaptor<Exception> exceptionCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(responseListener).onFailure(exceptionCaptor.capture());

        Exception exception = exceptionCaptor.getValue();
        assertTrue(exception instanceof SearchRelevanceException);
        assertTrue(
            exception.getMessage().contains("search configuration cannot be deleted as it is currently used by experiments with ids")
        );
        assertEquals(RestStatus.CONFLICT, ((SearchRelevanceException) exception).status());

        verify(searchConfigurationDao, never()).deleteSearchConfiguration(any(), any(ActionListener.class));
    }

    public void testExperimentCheckReturnsNull() {
        String searchConfigId = "test-search-config-id";
        OpenSearchDocRequest request = new OpenSearchDocRequest(searchConfigId);

        // Mock null response from experiment check
        doAnswer(invocation -> {
            ActionListener<SearchResponse> listener = invocation.getArgument(3);
            listener.onResponse(null);
            return null;
        }).when(experimentDao).getExperimentByFieldId(eq(searchConfigId), eq(SEARCH_CONFIGURATION_LIST), eq(3), any(ActionListener.class));

        DeleteResponse mockDeleteResponse = mock(DeleteResponse.class);
        when(mockDeleteResponse.status()).thenReturn(RestStatus.OK);

        doAnswer(invocation -> {
            ActionListener<DeleteResponse> listener = invocation.getArgument(1);
            listener.onResponse(mockDeleteResponse);
            return null;
        }).when(searchConfigurationDao).deleteSearchConfiguration(eq(searchConfigId), any(ActionListener.class));

        ActionListener<DeleteResponse> responseListener = mock(ActionListener.class);
        transportAction.doExecute(null, request, responseListener);

        verify(experimentDao).getExperimentByFieldId(eq(searchConfigId), eq(SEARCH_CONFIGURATION_LIST), eq(3), any(ActionListener.class));
        verify(searchConfigurationDao).deleteSearchConfiguration(eq(searchConfigId), any(ActionListener.class));
    }

    // Helper method to create SearchResponse with specified hit count
    private SearchResponse createSearchResponseWithHitCount(long hitCount) {
        SearchResponse mockSearchResponse = mock(SearchResponse.class);
        SearchHits searchHits = new SearchHits(new SearchHit[0], new TotalHits(hitCount, TotalHits.Relation.EQUAL_TO), 0.0f);
        when(mockSearchResponse.getHits()).thenReturn(searchHits);
        return mockSearchResponse;
    }
}
