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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import org.apache.lucene.search.TotalHits;
import org.junit.Before;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.opensearch.ResourceNotFoundException;
import org.opensearch.action.index.IndexResponse;
import org.opensearch.action.search.SearchResponse;
import org.opensearch.action.support.ActionFilters;
import org.opensearch.core.action.ActionListener;
import org.opensearch.core.rest.RestStatus;
import org.opensearch.search.SearchHit;
import org.opensearch.search.SearchHits;
import org.opensearch.searchrelevance.dao.ABTestDao;
import org.opensearch.searchrelevance.dao.SearchConfigurationDao;
import org.opensearch.searchrelevance.exception.SearchRelevanceException;
import org.opensearch.searchrelevance.model.ABTest;
import org.opensearch.searchrelevance.model.ABTestSnapshot;
import org.opensearch.test.OpenSearchTestCase;
import org.opensearch.transport.TransportService;

public class UpdateABTestTransportActionTests extends OpenSearchTestCase {

    @Mock
    private TransportService transportService;
    @Mock
    private ActionFilters actionFilters;
    @Mock
    private ABTestDao abTestDao;
    @Mock
    private SearchConfigurationDao searchConfigurationDao;

    private UpdateABTestTransportAction transportAction;

    @Before
    public void setup() {
        MockitoAnnotations.openMocks(this);
        transportAction = new UpdateABTestTransportAction(transportService, actionFilters, abTestDao, searchConfigurationDao);
    }

    private SearchResponse createABTestSearchResponse(Map<String, Object> source) {
        SearchHit hit = new SearchHit(1, "test-id", Collections.emptyMap(), Collections.emptyMap());
        hit.sourceRef(org.opensearch.core.common.bytes.BytesReference.bytes(createXContentFromMap(source)));
        hit.setSeqNo(1L);
        hit.setPrimaryTerm(1L);
        SearchHits hits = new SearchHits(new SearchHit[] { hit }, new TotalHits(1, TotalHits.Relation.EQUAL_TO), 1.0f);
        SearchResponse response = mock(SearchResponse.class);
        org.mockito.Mockito.when(response.getHits()).thenReturn(hits);
        return response;
    }

    private org.opensearch.core.xcontent.XContentBuilder createXContentFromMap(Map<String, Object> source) {
        try {
            org.opensearch.core.xcontent.XContentBuilder builder = org.opensearch.common.xcontent.XContentFactory.jsonBuilder();
            builder.map(source);
            return builder;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private Map<String, Object> createTestDoc(boolean enabled, int version) {
        Map<String, Object> source = new HashMap<>();
        source.put(ABTest.TEST_ID, "my-test");
        source.put(ABTest.SEARCH_CONFIGURATION_A, "sc-001");
        source.put(ABTest.SEARCH_CONFIGURATION_B, "sc-002");
        source.put(ABTest.CONFIG_A_UUID, "alias-a");
        source.put(ABTest.CONFIG_B_UUID, "alias-b");
        source.put(ABTest.ENABLED, enabled);
        source.put(ABTest.VERSION, version);
        source.put(ABTest.CREATED_AT, "2026-06-10T00:00:00.000Z");
        source.put(ABTest.UPDATED_AT, "2026-06-10T00:00:00.000Z");
        return source;
    }

    private void mockGetABTest(Map<String, Object> source) {
        doAnswer(invocation -> {
            ActionListener<SearchResponse> listener = invocation.getArgument(1);
            listener.onResponse(createABTestSearchResponse(source));
            return null;
        }).when(abTestDao).getABTestWithSeqNo(any(String.class), any(ActionListener.class));
    }

    private void mockGetABTestNotFound() {
        doAnswer(invocation -> {
            ActionListener<SearchResponse> listener = invocation.getArgument(1);
            listener.onFailure(new ResourceNotFoundException("Document not found: my-test", RestStatus.NOT_FOUND));
            return null;
        }).when(abTestDao).getABTestWithSeqNo(any(String.class), any(ActionListener.class));
    }

    private void mockPutSnapshot() {
        doAnswer(invocation -> {
            ActionListener listener = invocation.getArgument(1);
            IndexResponse mockResponse = mock(IndexResponse.class);
            listener.onResponse(mockResponse);
            return null;
        }).when(abTestDao).putSnapshot(any(ABTestSnapshot.class), any(ActionListener.class));
    }

    private void mockUpdateABTestWithConcurrencyControl() {
        doAnswer(invocation -> {
            ActionListener listener = invocation.getArgument(3);
            IndexResponse mockResponse = mock(IndexResponse.class);
            listener.onResponse(mockResponse);
            return null;
        }).when(abTestDao)
            .updateABTestWithConcurrencyControl(
                any(ABTest.class),
                org.mockito.ArgumentMatchers.anyLong(),
                org.mockito.ArgumentMatchers.anyLong(),
                any(ActionListener.class)
            );
    }

    private void mockConfigExists() {
        doAnswer(invocation -> {
            ActionListener<SearchResponse> listener = invocation.getArgument(1);
            listener.onResponse(mock(SearchResponse.class));
            return null;
        }).when(searchConfigurationDao).getSearchConfiguration(any(String.class), any(ActionListener.class));
    }

    /**
     * Happy path: disable test — saves snapshot, updates live doc
     */
    @SuppressWarnings("unchecked")
    public void testDisableTest() {
        mockGetABTest(createTestDoc(true, 1));
        mockPutSnapshot();
        mockUpdateABTestWithConcurrencyControl();

        UpdateABTestRequest request = new UpdateABTestRequest("my-test", false, null, null);
        ActionListener<IndexResponse> listener = mock(ActionListener.class);

        transportAction.doExecute(null, request, listener);

        // Verify snapshot was saved
        // Verify snapshot was saved
        ArgumentCaptor<ABTestSnapshot> snapshotCaptor = ArgumentCaptor.forClass(ABTestSnapshot.class);
        verify(abTestDao).putSnapshot(snapshotCaptor.capture(), any(ActionListener.class));
        assertEquals("my-test_1", snapshotCaptor.getValue().getDocId());
        assertEquals("my-test", snapshotCaptor.getValue().getTestId());

        // Verify updated test has enabled=false and version=2
        ArgumentCaptor<ABTest> abTestCaptor = ArgumentCaptor.forClass(ABTest.class);
        verify(abTestDao).updateABTestWithConcurrencyControl(
            abTestCaptor.capture(),
            org.mockito.ArgumentMatchers.anyLong(),
            org.mockito.ArgumentMatchers.anyLong(),
            any(ActionListener.class)
        );
        assertFalse(abTestCaptor.getValue().isEnabled());
        assertEquals(2, abTestCaptor.getValue().getVersion());
    }

    /**
     * Happy path: change config B — saves snapshot, updates live doc, uuid stays same
     */
    @SuppressWarnings("unchecked")
    public void testChangeConfigB() {
        mockGetABTest(createTestDoc(true, 0));
        mockPutSnapshot();
        mockUpdateABTestWithConcurrencyControl();
        mockConfigExists();

        UpdateABTestRequest request = new UpdateABTestRequest("my-test", null, "sc-001", "sc-003");
        ActionListener<IndexResponse> listener = mock(ActionListener.class);

        transportAction.doExecute(null, request, listener);

        ArgumentCaptor<ABTest> abTestCaptor = ArgumentCaptor.forClass(ABTest.class);
        verify(abTestDao).updateABTestWithConcurrencyControl(
            abTestCaptor.capture(),
            org.mockito.ArgumentMatchers.anyLong(),
            org.mockito.ArgumentMatchers.anyLong(),
            any(ActionListener.class)
        );

        ABTest updated = abTestCaptor.getValue();
        assertEquals("sc-003", updated.getSearchConfigurationB());
        assertEquals("alias-b", updated.getConfigBUuid()); // uuid stays same
        assertEquals(1, updated.getVersion());
    }

    /**
     * Nothing changed — no snapshot saved, returns null
     */
    @SuppressWarnings("unchecked")
    public void testNothingChanged() {
        mockGetABTest(createTestDoc(true, 1));

        // Same values as current state
        UpdateABTestRequest request = new UpdateABTestRequest("my-test", true, "sc-001", "sc-002");
        ActionListener<IndexResponse> listener = mock(ActionListener.class);

        mockConfigExists();
        transportAction.doExecute(null, request, listener);

        // Verify no snapshot and no update
        verify(abTestDao, never()).putSnapshot(any(ABTestSnapshot.class), any(ActionListener.class));
        verify(abTestDao, never()).updateABTestWithConcurrencyControl(
            any(),
            org.mockito.ArgumentMatchers.anyLong(),
            org.mockito.ArgumentMatchers.anyLong(),
            any()
        );

        // Verify listener received null (nothing changed)
        ArgumentCaptor<IndexResponse> responseCaptor = ArgumentCaptor.forClass(IndexResponse.class);
        verify(listener).onResponse(responseCaptor.capture());
        assertNull(responseCaptor.getValue());
    }

    /**
     * Test not found — returns failure
     */
    @SuppressWarnings("unchecked")
    public void testTestNotFound() {
        mockGetABTestNotFound();

        UpdateABTestRequest request = new UpdateABTestRequest("non-existent", false, null, null);
        ActionListener<IndexResponse> listener = mock(ActionListener.class);

        transportAction.doExecute(null, request, listener);

        ArgumentCaptor<Exception> errorCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(listener).onFailure(errorCaptor.capture());
        assertTrue(errorCaptor.getValue().getMessage().contains("not found"));
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
     * Version increments correctly across updates
     */
    @SuppressWarnings("unchecked")
    public void testVersionIncrements() {
        mockGetABTest(createTestDoc(true, 5));
        mockPutSnapshot();
        mockUpdateABTestWithConcurrencyControl();

        UpdateABTestRequest request = new UpdateABTestRequest("my-test", false, null, null);
        ActionListener<IndexResponse> listener = mock(ActionListener.class);

        transportAction.doExecute(null, request, listener);

        ArgumentCaptor<ABTestSnapshot> snapshotCaptor = ArgumentCaptor.forClass(ABTestSnapshot.class);
        verify(abTestDao).putSnapshot(snapshotCaptor.capture(), any(ActionListener.class));
        assertEquals("my-test_5", snapshotCaptor.getValue().getDocId());

        assertEquals("my-test_5", snapshotCaptor.getValue().getDocId());

    }

    /**
     * Snapshot contains old state before update
     */
    @SuppressWarnings("unchecked")
    public void testSnapshotContainsOldState() {
        mockGetABTest(createTestDoc(true, 0));
        mockPutSnapshot();
        mockUpdateABTestWithConcurrencyControl();

        UpdateABTestRequest request = new UpdateABTestRequest("my-test", false, null, null);
        ActionListener<IndexResponse> listener = mock(ActionListener.class);

        transportAction.doExecute(null, request, listener);

        ArgumentCaptor<ABTestSnapshot> snapCaptor = ArgumentCaptor.forClass(ABTestSnapshot.class);
        verify(abTestDao).putSnapshot(snapCaptor.capture(), any(ActionListener.class));

        Map<String, Object> snapshot = snapCaptor.getValue().getRecord();

        assertEquals("sc-001", snapshot.get(ABTest.SEARCH_CONFIGURATION_A));
        assertEquals("sc-002", snapshot.get(ABTest.SEARCH_CONFIGURATION_B));
        assertEquals("alias-a", snapshot.get(ABTest.CONFIG_A_UUID));
        assertEquals("alias-b", snapshot.get(ABTest.CONFIG_B_UUID));
        assertEquals(true, snapshot.get(ABTest.ENABLED)); // was true before disable
    }

    /**
     * Non-existent config ID in update returns failure
     */
    @SuppressWarnings("unchecked")
    public void testNonExistentConfigInUpdate() {
        doAnswer(invocation -> {
            String id = invocation.getArgument(0);
            ActionListener<SearchResponse> listener = invocation.getArgument(1);
            listener.onFailure(new SearchRelevanceException("Document not found: " + id, RestStatus.NOT_FOUND));
            return null;
        }).when(searchConfigurationDao).getSearchConfiguration(any(String.class), any(ActionListener.class));

        UpdateABTestRequest request = new UpdateABTestRequest("my-test", null, "sc-001", "non-existent");
        ActionListener<IndexResponse> listener = mock(ActionListener.class);

        transportAction.doExecute(null, request, listener);

        ArgumentCaptor<Exception> errorCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(listener).onFailure(errorCaptor.capture());
        assertTrue(errorCaptor.getValue().getMessage().contains("not found"));
    }

    /**
     * Version conflict triggers retry and succeeds on second attempt
     */
    @SuppressWarnings("unchecked")
    public void testVersionConflictRetries() {
        mockGetABTest(createTestDoc(true, 1));
        mockPutSnapshot();

        java.util.concurrent.atomic.AtomicInteger callCount = new java.util.concurrent.atomic.AtomicInteger(0);
        doAnswer(invocation -> {
            ActionListener listener = invocation.getArgument(3);
            if (callCount.getAndIncrement() == 0) {
                listener.onFailure(
                    new org.opensearch.index.engine.VersionConflictEngineException(
                        new org.opensearch.core.index.shard.ShardId("test", "test", 0),
                        "test-id",
                        "version conflict"
                    )
                );
            } else {
                listener.onResponse(mock(IndexResponse.class));
            }
            return null;
        }).when(abTestDao)
            .updateABTestWithConcurrencyControl(
                any(ABTest.class),
                org.mockito.ArgumentMatchers.anyLong(),
                org.mockito.ArgumentMatchers.anyLong(),
                any(ActionListener.class)
            );

        UpdateABTestRequest request = new UpdateABTestRequest("my-test", false, null, null);
        ActionListener<IndexResponse> listener = mock(ActionListener.class);

        transportAction.doExecute(null, request, listener);

        verify(listener).onResponse(any(IndexResponse.class));
        assertEquals(2, callCount.get());
    }
}
