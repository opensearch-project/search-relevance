/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */
package org.opensearch.searchrelevance.transport.abTest;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import org.junit.Before;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.opensearch.action.StepListener;
import org.opensearch.action.index.IndexResponse;
import org.opensearch.action.search.SearchResponse;
import org.opensearch.action.support.ActionFilters;
import org.opensearch.core.action.ActionListener;
import org.opensearch.core.rest.RestStatus;
import org.opensearch.searchrelevance.dao.ABTestDao;
import org.opensearch.searchrelevance.dao.SearchConfigurationDao;
import org.opensearch.searchrelevance.exception.SearchRelevanceException;
import org.opensearch.searchrelevance.model.ABTest;
import org.opensearch.test.OpenSearchTestCase;
import org.opensearch.transport.TransportService;

public class PutABTestTransportActionTests extends OpenSearchTestCase {

    @Mock
    private TransportService transportService;
    @Mock
    private ActionFilters actionFilters;
    @Mock
    private ABTestDao abTestDao;
    @Mock
    private SearchConfigurationDao searchConfigurationDao;

    private PutABTestTransportAction transportAction;

    @Before
    public void setup() {
        MockitoAnnotations.openMocks(this);
        transportAction = new PutABTestTransportAction(transportService, actionFilters, abTestDao, searchConfigurationDao);
    }

    private void mockConfigExists(String configId) {
        doAnswer(invocation -> {
            ActionListener<SearchResponse> listener = invocation.getArgument(1);
            SearchResponse mockResponse = mock(SearchResponse.class);
            listener.onResponse(mockResponse);
            return null;

        }).when(searchConfigurationDao).getSearchConfiguration(any(String.class), any(ActionListener.class));
    }

    private void mockConfigNotFound(String configId) {
        doAnswer(invocation -> {
            String id = invocation.getArgument(0);
            ActionListener<SearchResponse> listener = invocation.getArgument(1);
            listener.onFailure(new SearchRelevanceException("Document not found: " + id, RestStatus.NOT_FOUND));
            return null;
        }).when(searchConfigurationDao).getSearchConfiguration(any(String.class), any(ActionListener.class));
    }

    private void mockIndexCreation() {
        doAnswer(invocation -> {
            StepListener<Void> stepListener = invocation.getArgument(0);
            stepListener.onResponse(null);
            return null;
        }).when(abTestDao).createIndexIfAbsent(any(StepListener.class));
    }

    private void mockPutABTest() {
        doAnswer(invocation -> {
            ActionListener<IndexResponse> listener = invocation.getArgument(1);
            IndexResponse mockResponse = mock(IndexResponse.class);
            listener.onResponse(mockResponse);
            return null;
        }).when(abTestDao).putABTest(any(ABTest.class), any(ActionListener.class));
    }

    /**
     * Happy path: valid request with both config IDs creates test with enabled=true
     */
    @SuppressWarnings("unchecked")
    public void testHappyPath() {
        mockConfigExists("config-a");
        mockIndexCreation();
        mockPutABTest();

        PutABTestRequest request = new PutABTestRequest("my-test", "config-a", "config-b");
        ActionListener<IndexResponse> listener = mock(ActionListener.class);

        transportAction.doExecute(null, request, listener);

        // Verify the ABTest was stored
        ArgumentCaptor<ABTest> abTestCaptor = ArgumentCaptor.forClass(ABTest.class);
        verify(abTestDao).putABTest(abTestCaptor.capture(), any(ActionListener.class));

        ABTest stored = abTestCaptor.getValue();
        assertEquals("my-test", stored.getTestId());
        assertEquals("config-a", stored.getSearchConfigurationA());
        assertEquals("config-b", stored.getSearchConfigurationB());
        assertTrue(stored.isEnabled());
        assertEquals(0, stored.getVersion());
        assertNotNull(stored.getConfigAUuid());
        assertNotNull(stored.getConfigBUuid());
        assertNotEquals(stored.getConfigAUuid(), stored.getConfigBUuid());
    }

    /**
     * Null request returns failure
     */
    @SuppressWarnings("unchecked")
    public void testNullRequest() {
        ActionListener<IndexResponse> listener = mock(ActionListener.class);

        transportAction.doExecute(null, null, listener);

        ArgumentCaptor<Exception> errorCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(listener).onFailure(errorCaptor.capture());
        assertTrue(errorCaptor.getValue().getMessage().contains("cannot be null"));
    }

    /**
     * Non-existent config_a returns failure with clear message
     */
    @SuppressWarnings("unchecked")
    public void testConfigANotFound() {
        mockConfigNotFound("config-a");

        PutABTestRequest request = new PutABTestRequest("my-test", "config-a", "config-b");
        ActionListener<IndexResponse> listener = mock(ActionListener.class);

        transportAction.doExecute(null, request, listener);

        ArgumentCaptor<Exception> errorCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(listener).onFailure(errorCaptor.capture());
        assertTrue(errorCaptor.getValue().getMessage().contains("search_configuration_a"));
        assertTrue(errorCaptor.getValue().getMessage().contains("not found"));
    }

    /**
     * Non-existent config_b returns failure with clear message
     */
    @SuppressWarnings("unchecked")
    public void testConfigBNotFound() {
        // Config A exists
        doAnswer(invocation -> {
            String id = invocation.getArgument(0);
            ActionListener<SearchResponse> listener = invocation.getArgument(1);
            if (id.equals("config-a")) {
                listener.onResponse(mock(SearchResponse.class));
            } else {
                listener.onFailure(new SearchRelevanceException("Document not found: " + id, RestStatus.NOT_FOUND));
            }
            return null;
        }).when(searchConfigurationDao).getSearchConfiguration(any(String.class), any(ActionListener.class));

        PutABTestRequest request = new PutABTestRequest("my-test", "config-a", "config-b");
        ActionListener<IndexResponse> listener = mock(ActionListener.class);

        transportAction.doExecute(null, request, listener);

        ArgumentCaptor<Exception> errorCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(listener).onFailure(errorCaptor.capture());
        assertTrue(errorCaptor.getValue().getMessage().contains("search_configuration_b"));
        assertTrue(errorCaptor.getValue().getMessage().contains("not found"));
    }

    /**
     * UUID identifiers are unique across calls
     */
    @SuppressWarnings("unchecked")
    public void testUUIDAliasesAreUnique() {
        mockConfigExists("config-a");
        mockIndexCreation();
        mockPutABTest();

        PutABTestRequest request = new PutABTestRequest("my-test", "config-a", "config-b");
        ActionListener<IndexResponse> listener = mock(ActionListener.class);

        transportAction.doExecute(null, request, listener);

        ArgumentCaptor<ABTest> abTestCaptor = ArgumentCaptor.forClass(ABTest.class);
        verify(abTestDao).putABTest(abTestCaptor.capture(), any(ActionListener.class));

        ABTest stored = abTestCaptor.getValue();
        // Aliases should be different from each other
        assertNotEquals(stored.getConfigAUuid(), stored.getConfigBUuid());
        // Aliases should be different from the real config IDs
        assertNotEquals("config-a", stored.getConfigAUuid());
        assertNotEquals("config-b", stored.getConfigBUuid());
    }

    /**
     * Live doc has correct initial values
     */
    @SuppressWarnings("unchecked")
    public void testLiveDocStoredCorrectly() {
        mockConfigExists("config-a");
        mockIndexCreation();
        mockPutABTest();

        PutABTestRequest request = new PutABTestRequest("test-123", "sc-001", "sc-002");
        ActionListener<IndexResponse> listener = mock(ActionListener.class);

        transportAction.doExecute(null, request, listener);

        ArgumentCaptor<ABTest> abTestCaptor = ArgumentCaptor.forClass(ABTest.class);
        verify(abTestDao).putABTest(abTestCaptor.capture(), any(ActionListener.class));

        ABTest stored = abTestCaptor.getValue();
        assertEquals("test-123", stored.getTestId());
        assertEquals("sc-001", stored.getSearchConfigurationA());
        assertEquals("sc-002", stored.getSearchConfigurationB());
        assertTrue(stored.isEnabled());
        assertEquals(0, stored.getVersion());
        assertNotNull(stored.getCreatedAt());
        assertNotNull(stored.getUpdatedAt());
        assertEquals(stored.getCreatedAt(), stored.getUpdatedAt());
    }

    /**
     * Same config for both A and B returns failure
     */
    @SuppressWarnings("unchecked")
    public void testSameConfigForBoth() {
        PutABTestRequest request = new PutABTestRequest("my-test", "same-config-id", "same-config-id");
        ActionListener<IndexResponse> listener = mock(ActionListener.class);

        transportAction.doExecute(null, request, listener);

        ArgumentCaptor<Exception> errorCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(listener).onFailure(errorCaptor.capture());
        assertTrue(errorCaptor.getValue().getMessage().contains("must be different"));
    }
}
