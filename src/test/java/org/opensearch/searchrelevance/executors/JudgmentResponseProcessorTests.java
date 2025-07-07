/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */
package org.opensearch.searchrelevance.executors;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.apache.lucene.search.TotalHits;
import org.opensearch.action.search.SearchResponse;
import org.opensearch.core.action.ActionListener;
import org.opensearch.core.common.bytes.BytesArray;
import org.opensearch.search.SearchHit;
import org.opensearch.search.SearchHits;
import org.opensearch.searchrelevance.dao.JudgmentCacheDao;
import org.opensearch.test.OpenSearchTestCase;

public class JudgmentResponseProcessorTests extends OpenSearchTestCase {

    private JudgmentCacheDao judgmentCacheDao;
    private JudgmentResponseProcessor processor;
    private JudgmentTaskContext context;

    @Override
    public void setUp() throws Exception {
        super.setUp();
        judgmentCacheDao = mock(JudgmentCacheDao.class);
        processor = new JudgmentResponseProcessor(judgmentCacheDao);
        context = createMockContext();
    }

    public void testProcessSearchResponseSuccess() {
        // Arrange
        SearchResponse response = createSearchResponse(2);
        ConcurrentMap<String, SearchHit> allHits = new ConcurrentHashMap<>();
        String configIndex = "test-index";

        // Act
        processor.processSearchResponse(response, configIndex, allHits, context);

        // Assert
        assertEquals(2, allHits.size());
        verify(context).completeSearchTask(true);
    }

    public void testProcessSearchResponseNoHits() {
        // Arrange
        SearchResponse response = createSearchResponse(0);
        ConcurrentMap<String, SearchHit> allHits = new ConcurrentHashMap<>();
        String configIndex = "test-index";

        // Act
        processor.processSearchResponse(response, configIndex, allHits, context);

        // Assert
        assertEquals(0, allHits.size());
        verify(context).completeSearchTask(true);
    }

    public void testProcessSearchResponseException() {
        // Arrange
        SearchResponse response = mock(SearchResponse.class);
        when(response.getHits()).thenThrow(new RuntimeException("Test exception"));
        ConcurrentMap<String, SearchHit> allHits = new ConcurrentHashMap<>();
        String configIndex = "test-index";

        // Act
        processor.processSearchResponse(response, configIndex, allHits, context);

        // Assert
        verify(context).completeSearchTask(false);
    }

    public void testProcessCacheResponseFound() {
        // Arrange
        SearchResponse response = createCacheResponse("0.8", "field1,field2");
        String docId = "doc1";
        ConcurrentMap<String, Boolean> processedDocs = new ConcurrentHashMap<>();

        // Act
        processor.processCacheResponse(response, docId, processedDocs, context);

        // Assert
        verify(context).getDocIdToScore();
        verify(context).completeCacheTask(true);
        assertTrue(processedDocs.get(docId));
    }

    public void testProcessCacheResponseNotFound() {
        // Arrange
        SearchResponse response = createSearchResponse(0);
        String docId = "doc1";
        ConcurrentMap<String, Boolean> processedDocs = new ConcurrentHashMap<>();

        // Act
        processor.processCacheResponse(response, docId, processedDocs, context);

        // Assert
        verify(context).completeCacheTask(true);
        assertNull(processedDocs.get(docId));
    }

    public void testHandleSearchFailure() {
        // Arrange
        Exception error = new RuntimeException("Search failed");
        String configIndex = "test-index";

        // Act
        processor.handleSearchFailure(error, configIndex, context);

        // Assert
        verify(context).completeSearchTask(false);
    }

    public void testHandleSearchFailureWithIgnoreFailureFalse() {
        // Arrange
        when(context.isIgnoreFailure()).thenReturn(false);
        Exception error = new RuntimeException("Search failed");
        String configIndex = "test-index";

        // Act
        processor.handleSearchFailure(error, configIndex, context);

        // Assert
        verify(context).completeSearchTask(false);
        verify(context).failJudgment(error);
    }

    public void testHandleCacheFailure() {
        // Arrange
        Exception error = new RuntimeException("Cache failed");
        String docId = "doc1";

        // Act
        processor.handleCacheFailure(error, docId, context);

        // Assert
        verify(context).completeCacheTask(false);
    }

    public void testHandleLLMFailureWithIgnoreFailureFalse() {
        // Arrange
        Exception error = new RuntimeException("LLM failed");
        String queryText = "test query";
        ActionListener<Map<String, String>> listener = mock(ActionListener.class);

        // Act
        processor.handleLLMFailure(error, queryText, false, listener);

        // Assert
        verify(listener).onFailure(error);
        verify(listener, never()).onResponse(any());
    }

    public void testHandleLLMFailureWithIgnoreFailureTrue() {
        // Arrange
        Exception error = new RuntimeException("LLM failed");
        String queryText = "test query";
        ActionListener<Map<String, String>> listener = mock(ActionListener.class);

        // Act
        processor.handleLLMFailure(error, queryText, true, listener);

        // Assert
        verify(listener).onResponse(Map.of());
        verify(listener, never()).onFailure(any());
    }

    private JudgmentTaskContext createMockContext() {
        JudgmentTaskContext context = mock(JudgmentTaskContext.class);
        when(context.getDocIdToScore()).thenReturn(new ConcurrentHashMap<>());
        when(context.isIgnoreFailure()).thenReturn(true);
        return context;
    }

    private SearchResponse createSearchResponse(int hitCount) {
        SearchResponse response = mock(SearchResponse.class);

        SearchHit[] hitArray = new SearchHit[hitCount];
        for (int i = 0; i < hitCount; i++) {
            hitArray[i] = new SearchHit(i, "doc" + i, null, null);
        }

        SearchHits hits = new SearchHits(hitArray, new TotalHits(hitCount, TotalHits.Relation.EQUAL_TO), 0);
        when(response.getHits()).thenReturn(hits);

        return response;
    }

    private SearchResponse createCacheResponse(String rating, String contextFields) {
        SearchResponse response = mock(SearchResponse.class);

        String jsonSource = "{\"rating\":\"" + rating + "\",\"contextFieldsStr\":\"" + contextFields + "\"}";
        SearchHit hit = new SearchHit(0, "doc1", null, null);
        hit.sourceRef(new BytesArray(jsonSource));

        SearchHits hits = new SearchHits(new SearchHit[] { hit }, new TotalHits(1, TotalHits.Relation.EQUAL_TO), 0);
        when(response.getHits()).thenReturn(hits);

        return response;
    }
}
