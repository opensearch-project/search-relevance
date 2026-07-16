/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */
package org.opensearch.searchrelevance.judgments;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.lucene.search.TotalHits;
import org.junit.Before;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.opensearch.action.search.SearchResponse;
import org.opensearch.common.xcontent.XContentFactory;
import org.opensearch.core.common.bytes.BytesReference;
import org.opensearch.core.xcontent.XContentBuilder;
import org.opensearch.search.SearchHit;
import org.opensearch.search.SearchHits;
import org.opensearch.searchrelevance.dao.JudgmentDao;
import org.opensearch.searchrelevance.dao.QuerySetDao;
import org.opensearch.searchrelevance.dao.SearchConfigurationDao;
import org.opensearch.searchrelevance.ml.MLAccessor;
import org.opensearch.test.OpenSearchTestCase;
import org.opensearch.threadpool.ThreadPool;
import org.opensearch.transport.client.Client;

/**
 * Tests for the existingJudgements deduplication feature in LlmJudgmentsProcessor.
 * Verifies that fetchRatingsForQuery and findRatingForDoc work correctly.
 */
public class ExistingJudgementsDeduplicationTests extends OpenSearchTestCase {

    @Mock
    private MLAccessor mlAccessor;
    @Mock
    private QuerySetDao querySetDao;
    @Mock
    private SearchConfigurationDao searchConfigurationDao;
    @Mock
    private JudgmentDao judgmentDao;
    @Mock
    private Client client;
    @Mock
    private ThreadPool threadPool;

    private LlmJudgmentsProcessor processor;

    @Before
    public void setup() {
        MockitoAnnotations.openMocks(this);
        java.util.concurrent.ExecutorService directExecutor = org.mockito.Mockito.mock(java.util.concurrent.ExecutorService.class);
        when(threadPool.executor(org.mockito.ArgumentMatchers.any())).thenReturn(directExecutor);
        org.mockito.Mockito.doAnswer(invocation -> {
            ((Runnable) invocation.getArgument(0)).run();
            return null;
        }).when(directExecutor).execute(org.mockito.ArgumentMatchers.any(Runnable.class));
        processor = new LlmJudgmentsProcessor(mlAccessor, querySetDao, searchConfigurationDao, judgmentDao, client, threadPool);
    }

    public void testFetchRatingsForQuery_JudgmentNotFound() {
        SearchResponse mockResponse = mock(SearchResponse.class);
        SearchHits searchHits = new SearchHits(new SearchHit[0], new TotalHits(0, TotalHits.Relation.EQUAL_TO), 0.0f);
        when(mockResponse.getHits()).thenReturn(searchHits);
        when(judgmentDao.getJudgmentSync("nonexistent-id")).thenReturn(mockResponse);

        List<Map<String, String>> result = processor.fetchRatingsForQuery(List.of("nonexistent-id"), "superhero");
        assertTrue(result.isEmpty());
    }

    public void testFetchRatingsForQuery_QueryTextFound() {
        Map<String, Object> source = buildJudgmentWithRatings(
            "superhero",
            List.of(Map.of("docId", "1", "rating", "0.9"), Map.of("docId", "5", "rating", "0.7"))
        );
        SearchResponse mockResponse = buildMockSearchResponse(source);
        when(judgmentDao.getJudgmentSync("judgment-a")).thenReturn(mockResponse);

        List<Map<String, String>> result = processor.fetchRatingsForQuery(List.of("judgment-a"), "superhero");

        assertEquals(2, result.size());
        assertEquals("1", result.get(0).get("docId"));
        assertEquals("0.9", result.get(0).get("rating"));
        assertEquals("5", result.get(1).get("docId"));
        assertEquals("0.7", result.get(1).get("rating"));
    }

    public void testFetchRatingsForQuery_QueryTextNotFound() {
        Map<String, Object> source = buildJudgmentWithRatings("comedy", List.of(Map.of("docId", "2", "rating", "0.8")));
        SearchResponse mockResponse = buildMockSearchResponse(source);
        when(judgmentDao.getJudgmentSync("judgment-a")).thenReturn(mockResponse);

        List<Map<String, String>> result = processor.fetchRatingsForQuery(List.of("judgment-a"), "superhero");
        assertTrue(result.isEmpty());
    }

    public void testFetchRatingsForQuery_EmptyIds() {
        List<Map<String, String>> result = processor.fetchRatingsForQuery(List.of(), "superhero");
        assertTrue(result.isEmpty());
    }

    public void testFindRatingForDoc_Found() {
        List<Map<String, String>> ratings = List.of(
            Map.of("docId", "1", "rating", "0.9"),
            Map.of("docId", "5", "rating", "0.7"),
            Map.of("docId", "13", "rating", "0.6")
        );

        assertEquals("0.9", processor.findRatingForDoc(ratings, "1"));
        assertEquals("0.7", processor.findRatingForDoc(ratings, "5"));
        assertEquals("0.6", processor.findRatingForDoc(ratings, "13"));
    }

    public void testFindRatingForDoc_NotFound() {
        List<Map<String, String>> ratings = List.of(Map.of("docId", "1", "rating", "0.9"), Map.of("docId", "5", "rating", "0.7"));

        assertNull(processor.findRatingForDoc(ratings, "99"));
        assertNull(processor.findRatingForDoc(ratings, "13"));
    }

    public void testFetchRatingsForQuery_MultipleJudgments() {
        Map<String, Object> sourceA = buildJudgmentWithRatings("superhero", List.of(Map.of("docId", "1", "rating", "0.9")));
        SearchResponse mockResponseA = buildMockSearchResponse(sourceA);
        when(judgmentDao.getJudgmentSync("judgment-a")).thenReturn(mockResponseA);

        Map<String, Object> sourceB = buildJudgmentWithRatings("superhero", List.of(Map.of("docId", "5", "rating", "0.7")));
        SearchResponse mockResponseB = buildMockSearchResponse(sourceB);
        when(judgmentDao.getJudgmentSync("judgment-b")).thenReturn(mockResponseB);

        List<Map<String, String>> result = processor.fetchRatingsForQuery(List.of("judgment-a", "judgment-b"), "superhero");

        // Should have ratings from both judgments
        assertEquals(2, result.size());
    }

    private Map<String, Object> buildJudgmentWithRatings(String queryText, List<Map<String, String>> ratings) {
        Map<String, Object> queryEntry = new HashMap<>();
        queryEntry.put("query", queryText);
        queryEntry.put("ratings", ratings);

        Map<String, Object> source = new HashMap<>();
        source.put("judgmentRatings", List.of(queryEntry));
        return source;
    }

    private SearchResponse buildMockSearchResponse(Map<String, Object> source) {
        try {
            XContentBuilder builder = XContentFactory.jsonBuilder();
            builder.map(source);
            SearchHit hit = new SearchHit(1, "test-id", Map.of(), Map.of());
            hit.sourceRef(BytesReference.bytes(builder));
            SearchHits searchHits = new SearchHits(new SearchHit[] { hit }, new TotalHits(1, TotalHits.Relation.EQUAL_TO), 1.0f);
            SearchResponse mockResponse = mock(SearchResponse.class);
            when(mockResponse.getHits()).thenReturn(searchHits);
            return mockResponse;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
