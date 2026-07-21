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
import org.opensearch.searchrelevance.exception.SearchRelevanceException;
import org.opensearch.searchrelevance.ml.MLAccessor;
import org.opensearch.searchrelevance.model.LLMJudgmentRatingType;
import org.opensearch.test.OpenSearchTestCase;
import org.opensearch.threadpool.ThreadPool;
import org.opensearch.transport.client.Client;

/**
 * Tests for the existingJudgments deduplication feature in LlmJudgmentsProcessor.
 * Verifies that fetchRatingsForQuery and findRatingForDoc work correctly.
 */
public class ExistingJudgmentsDeduplicationTests extends OpenSearchTestCase {

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

        Map<String, Map<String, String>> result = processor.fetchAllRatings(List.of("nonexistent-id"));
        assertTrue(result.isEmpty());
    }

    public void testFetchRatingsForQuery_QueryTextFound() {
        Map<String, Object> source = buildJudgmentWithRatings(
            "superhero",
            List.of(Map.of("docId", "1", "rating", "0.9"), Map.of("docId", "5", "rating", "0.7"))
        );
        SearchResponse mockResponse = buildMockSearchResponse(source);
        when(judgmentDao.getJudgmentSync("judgment-a")).thenReturn(mockResponse);

        Map<String, String> result = processor.fetchAllRatings(List.of("judgment-a")).get("superhero");

        assertEquals(2, result.size());
        assertEquals("0.9", result.get("1"));
        assertEquals("0.7", result.get("5"));
    }

    public void testFetchRatingsForQuery_QueryTextNotFound() {
        Map<String, Object> source = buildJudgmentWithRatings("comedy", List.of(Map.of("docId", "2", "rating", "0.8")));
        SearchResponse mockResponse = buildMockSearchResponse(source);
        when(judgmentDao.getJudgmentSync("judgment-a")).thenReturn(mockResponse);

        // "superhero" isn't in the judgment, so there's no entry for it in the returned map.
        Map<String, Map<String, String>> result = processor.fetchAllRatings(List.of("judgment-a"));
        assertNull(result.get("superhero"));
    }

    public void testFetchRatingsForQuery_EmptyIds() {
        Map<String, Map<String, String>> result = processor.fetchAllRatings(List.of());
        assertTrue(result.isEmpty());
    }

    public void testFetchRatingsForQuery_MultipleJudgments() {
        Map<String, Object> sourceA = buildJudgmentWithRatings("superhero", List.of(Map.of("docId", "1", "rating", "0.9")));
        SearchResponse mockResponseA = buildMockSearchResponse(sourceA);
        when(judgmentDao.getJudgmentSync("judgment-a")).thenReturn(mockResponseA);

        Map<String, Object> sourceB = buildJudgmentWithRatings("superhero", List.of(Map.of("docId", "5", "rating", "0.7")));
        SearchResponse mockResponseB = buildMockSearchResponse(sourceB);
        when(judgmentDao.getJudgmentSync("judgment-b")).thenReturn(mockResponseB);

        Map<String, String> result = processor.fetchAllRatings(List.of("judgment-a", "judgment-b")).get("superhero");

        // Should have ratings from both judgments
        assertEquals(2, result.size());
        assertEquals("0.9", result.get("1"));
        assertEquals("0.7", result.get("5"));
    }

    public void testFetchRatingsForQuery_FirstJudgmentWinsOnDuplicate() {
        Map<String, Object> sourceA = buildJudgmentWithRatings("superhero", List.of(Map.of("docId", "1", "rating", "0.9")));
        SearchResponse mockResponseA = buildMockSearchResponse(sourceA);
        when(judgmentDao.getJudgmentSync("judgment-a")).thenReturn(mockResponseA);

        Map<String, Object> sourceB = buildJudgmentWithRatings("superhero", List.of(Map.of("docId", "1", "rating", "0.3")));
        SearchResponse mockResponseB = buildMockSearchResponse(sourceB);
        when(judgmentDao.getJudgmentSync("judgment-b")).thenReturn(mockResponseB);

        Map<String, String> result = processor.fetchAllRatings(List.of("judgment-a", "judgment-b")).get("superhero");

        // First judgment (A) wins for the shared docId
        assertEquals(1, result.size());
        assertEquals("0.9", result.get("1"));
    }

    public void testScoringConfig_ParsesAllFields() {
        org.opensearch.searchrelevance.model.SearchConfiguration searchConfig = mock(
            org.opensearch.searchrelevance.model.SearchConfiguration.class
        );
        when(searchConfig.index()).thenReturn("test-index");
        when(searchConfigurationDao.getSearchConfigurationSync("config-1")).thenReturn(searchConfig);

        Map<String, Object> metadata = new HashMap<>();
        metadata.put("modelId", "model-1");
        metadata.put("querySetId", "qs-1");
        metadata.put("searchConfigurationList", List.of("config-1"));
        metadata.put("size", 10);
        metadata.put("tokenLimit", 2000);
        metadata.put("contextFields", List.of("title"));
        metadata.put("ignoreFailure", true);
        metadata.put("promptTemplate", "template");
        metadata.put("existingJudgments", List.of("j1"));

        LlmJudgmentsProcessor.ScoringConfig config = new LlmJudgmentsProcessor.ScoringConfig(metadata, searchConfigurationDao);

        assertEquals("model-1", config.modelId);
        assertEquals("qs-1", config.querySetId);
        assertEquals("test-index", config.index);
        assertEquals(1, config.searchConfigurations.size());
        assertEquals(10, config.size);
        assertEquals(2000, config.tokenLimit);
        assertEquals(List.of("title"), config.contextFields);
        assertTrue(config.ignoreFailure);
        assertEquals("template", config.promptTemplate);
        assertEquals(List.of("j1"), config.existingJudgmentIds);
    }

    public void testScoringConfig_RatingType_FromEnum() {
        Map<String, Object> metadata = baseMetadataWithConfig();
        metadata.put(org.opensearch.searchrelevance.common.MLConstants.LLM_JUDGMENT_RATING_TYPE, LLMJudgmentRatingType.RELEVANT_IRRELEVANT);

        LlmJudgmentsProcessor.ScoringConfig config = new LlmJudgmentsProcessor.ScoringConfig(metadata, searchConfigurationDao);
        assertEquals(LLMJudgmentRatingType.RELEVANT_IRRELEVANT, config.ratingType);
    }

    public void testScoringConfig_RatingType_FromString() {
        Map<String, Object> metadata = baseMetadataWithConfig();
        // Loaded back from the index, ratingType comes through as a String.
        metadata.put(org.opensearch.searchrelevance.common.MLConstants.LLM_JUDGMENT_RATING_TYPE, "RELEVANT_IRRELEVANT");

        LlmJudgmentsProcessor.ScoringConfig config = new LlmJudgmentsProcessor.ScoringConfig(metadata, searchConfigurationDao);
        assertEquals(LLMJudgmentRatingType.RELEVANT_IRRELEVANT, config.ratingType);
    }

    public void testScoringConfig_RatingType_DefaultsWhenMissing() {
        Map<String, Object> metadata = baseMetadataWithConfig();
        // No ratingType provided — should fall back to the shared default.

        LlmJudgmentsProcessor.ScoringConfig config = new LlmJudgmentsProcessor.ScoringConfig(metadata, searchConfigurationDao);
        assertEquals(LLMJudgmentRatingType.DEFAULT, config.ratingType);
    }

    public void testScoringConfig_MissingModelId_Throws() {
        Map<String, Object> metadata = baseMetadataWithConfig();
        metadata.remove("modelId");

        Exception e = expectThrows(
            SearchRelevanceException.class,
            () -> new LlmJudgmentsProcessor.ScoringConfig(metadata, searchConfigurationDao)
        );
        assertTrue(e.getMessage().contains("modelId is missing"));
    }

    public void testScoringConfig_MissingQuerySetId_Throws() {
        Map<String, Object> metadata = baseMetadataWithConfig();
        metadata.remove("querySetId");

        Exception e = expectThrows(
            SearchRelevanceException.class,
            () -> new LlmJudgmentsProcessor.ScoringConfig(metadata, searchConfigurationDao)
        );
        assertTrue(e.getMessage().contains("querySetId is missing"));
    }

    public void testScoringConfig_MissingSearchConfigList_Throws() {
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("modelId", "model-1");
        metadata.put("querySetId", "qs-1");
        // no searchConfigurationList

        Exception e = expectThrows(
            SearchRelevanceException.class,
            () -> new LlmJudgmentsProcessor.ScoringConfig(metadata, searchConfigurationDao)
        );
        assertTrue(e.getMessage().contains("searchConfigurationList is missing"));
    }

    /** Minimal valid metadata (required fields + one mocked search config) for ratingType tests. */
    private Map<String, Object> baseMetadataWithConfig() {
        org.opensearch.searchrelevance.model.SearchConfiguration searchConfig = mock(
            org.opensearch.searchrelevance.model.SearchConfiguration.class
        );
        when(searchConfig.index()).thenReturn("test-index");
        when(searchConfigurationDao.getSearchConfigurationSync("config-1")).thenReturn(searchConfig);

        Map<String, Object> metadata = new HashMap<>();
        metadata.put("modelId", "model-1");
        metadata.put("querySetId", "qs-1");
        metadata.put("searchConfigurationList", List.of("config-1"));
        return metadata;
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
