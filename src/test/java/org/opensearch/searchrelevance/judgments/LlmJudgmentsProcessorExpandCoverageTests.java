/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */
package org.opensearch.searchrelevance.judgments;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.lucene.search.TotalHits;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.opensearch.action.search.SearchResponse;
import org.opensearch.action.search.ShardSearchFailure;
import org.opensearch.core.action.ActionListener;
import org.opensearch.core.xcontent.NamedXContentRegistry;
import org.opensearch.search.SearchHit;
import org.opensearch.search.SearchHits;
import org.opensearch.search.internal.InternalSearchResponse;
import org.opensearch.searchrelevance.dao.JudgmentCacheDao;
import org.opensearch.searchrelevance.dao.QuerySetDao;
import org.opensearch.searchrelevance.dao.SearchConfigurationDao;
import org.opensearch.searchrelevance.ml.MLAccessor;
import org.opensearch.searchrelevance.model.SearchConfiguration;
import org.opensearch.searchrelevance.model.builder.SearchRequestBuilder;
import org.opensearch.searchrelevance.settings.SearchRelevanceSettingsAccessor;
import org.opensearch.searchrelevance.stats.events.EventStatsManager;
import org.opensearch.test.OpenSearchTestCase;
import org.opensearch.threadpool.TestThreadPool;
import org.opensearch.threadpool.ThreadPool;
import org.opensearch.transport.client.Client;

import lombok.SneakyThrows;

/**
 * Unit tests for expandCoverage pooling search behavior in LlmJudgmentsProcessor.
 * Verifies that expandCoverage=true triggers N+1 searches (1 equal + N one-hot)
 * and expandCoverage=false triggers exactly 1 search.
 */
public class LlmJudgmentsProcessorExpandCoverageTests extends OpenSearchTestCase {

    @Mock
    private MLAccessor mockMLAccessor;
    @Mock
    private QuerySetDao mockQuerySetDao;
    @Mock
    private SearchConfigurationDao mockSearchConfigurationDao;
    @Mock
    private JudgmentCacheDao mockJudgmentCacheDao;
    @Mock
    private Client mockClient;
    @Mock
    private SearchRelevanceSettingsAccessor mockSettingsAccessor;

    private ThreadPool threadPool;
    private LlmJudgmentsProcessor processor;

    private static final String HYBRID_QUERY_2_SUBQUERIES =
        "{\"query\":{\"hybrid\":{\"queries\":[{\"match\":{\"title\":\"test\"}},{\"neural\":{\"embedding\":{\"query_text\":\"test\",\"model_id\":\"m1\",\"k\":5}}}]}}}";
    private static final String NON_HYBRID_QUERY = "{\"query\":{\"match\":{\"title\":\"test\"}}}";

    @Override
    @SneakyThrows
    public void setUp() {
        super.setUp();
        MockitoAnnotations.openMocks(this);
        threadPool = new TestThreadPool("test");

        when(mockSettingsAccessor.isStatsEnabled()).thenReturn(false);
        EventStatsManager eventStatsManager = EventStatsManager.instance();
        eventStatsManager.initialize(mockSettingsAccessor);

        // Initialize SearchRequestBuilder with EMPTY registry for unit tests.
        // getSubQueryCount() will try typed parsing first (fails with EMPTY), then fall back to map-based.
        SearchRequestBuilder.initialize(NamedXContentRegistry.EMPTY);

        processor = new LlmJudgmentsProcessor(
            mockMLAccessor,
            mockQuerySetDao,
            mockSearchConfigurationDao,
            mockJudgmentCacheDao,
            mockClient,
            threadPool
        );
    }

    @Override
    @SneakyThrows
    public void tearDown() {
        threadPool.shutdown();
        super.tearDown();
    }

    private SearchResponse createMockSearchResponse(String... docIds) {
        SearchHit[] hits = new SearchHit[docIds.length];
        for (int i = 0; i < docIds.length; i++) {
            hits[i] = new SearchHit(i, docIds[i], java.util.Map.of(), java.util.Map.of());
        }
        SearchHits searchHits = new SearchHits(hits, new TotalHits(docIds.length, TotalHits.Relation.EQUAL_TO), 1.0f);
        InternalSearchResponse internalResponse = new InternalSearchResponse(searchHits, null, null, null, false, null, 1);
        return new SearchResponse(internalResponse, null, 1, 1, 0, 0, ShardSearchFailure.EMPTY_ARRAY, SearchResponse.Clusters.EMPTY);
    }

    private SearchConfiguration createMockConfig(String query) {
        // SearchConfiguration fields: id, name, timestamp, index, query, searchPipeline, description
        SearchConfiguration config = new SearchConfiguration(
            "test-config-id",
            "test-config",
            "2026-01-01T00:00:00Z",
            "test-index",
            query,
            null,  // no search pipeline
            "test description"
        );
        return config;
    }

    @SneakyThrows
    public void testExpandCoverage_TwoSubqueries_Triggers3Searches() {
        AtomicInteger searchCount = new AtomicInteger(0);

        doAnswer(invocation -> {
            int count = searchCount.incrementAndGet();
            ActionListener<SearchResponse> listener = invocation.getArgument(1);
            // Return different doc per search to verify unique collection
            listener.onResponse(createMockSearchResponse("doc_from_search_" + count));
            return null;
        }).when(mockClient).search(any(), any());

        SearchConfiguration config = createMockConfig(HYBRID_QUERY_2_SUBQUERIES);
        ConcurrentMap<String, SearchHit> allHits = new ConcurrentHashMap<>();

        processor.processSearchConfigurationsAsync(
            List.of(config),
            "test query",
            10,
            allHits,
            false,
            true  // expandCoverage=true
        );

        // 2 sub-queries → 1 equal-weight + 2 one-hot = 3 searches
        assertEquals("Expected 3 searches for 2-subquery hybrid with expandCoverage", 3, searchCount.get());
        assertEquals("Expected 3 unique docs from 3 searches", 3, allHits.size());
    }

    @SneakyThrows
    public void testNoExpandCoverage_Triggers1Search() {
        AtomicInteger searchCount = new AtomicInteger(0);

        doAnswer(invocation -> {
            searchCount.incrementAndGet();
            ActionListener<SearchResponse> listener = invocation.getArgument(1);
            listener.onResponse(createMockSearchResponse("doc1", "doc2"));
            return null;
        }).when(mockClient).search(any(), any());

        SearchConfiguration config = createMockConfig(NON_HYBRID_QUERY);
        ConcurrentMap<String, SearchHit> allHits = new ConcurrentHashMap<>();

        processor.processSearchConfigurationsAsync(
            List.of(config),
            "test query",
            10,
            allHits,
            false,
            false  // expandCoverage=false
        );

        assertEquals("Expected 1 search without expandCoverage", 1, searchCount.get());
        assertEquals("Expected 2 docs from single search", 2, allHits.size());
    }

    public void testExpandCoverage_NonHybridQuery_ThrowsValidationError() {
        SearchConfiguration config = createMockConfig(NON_HYBRID_QUERY);
        ConcurrentMap<String, SearchHit> allHits = new ConcurrentHashMap<>();

        assertThrows(IllegalArgumentException.class, () -> {
            processor.processSearchConfigurationsAsync(
                List.of(config),
                "test query",
                10,
                allHits,
                false,
                true  // expandCoverage=true with non-hybrid
            );
        });
    }

    @SneakyThrows
    public void testExpandCoverage_DeduplicatesOverlappingDocs() {
        AtomicInteger searchCount = new AtomicInteger(0);

        doAnswer(invocation -> {
            searchCount.incrementAndGet();
            ActionListener<SearchResponse> listener = invocation.getArgument(1);
            // All 3 searches return overlapping doc sets
            listener.onResponse(createMockSearchResponse("doc_common", "doc_unique_" + searchCount.get()));
            return null;
        }).when(mockClient).search(any(), any());

        SearchConfiguration config = createMockConfig(HYBRID_QUERY_2_SUBQUERIES);
        ConcurrentMap<String, SearchHit> allHits = new ConcurrentHashMap<>();

        processor.processSearchConfigurationsAsync(List.of(config), "test query", 10, allHits, false, true);

        assertEquals("Expected 3 searches", 3, searchCount.get());
        // 1 common doc + 3 unique docs = 4 total (putIfAbsent deduplicates)
        assertEquals("Expected 4 unique docs (1 common + 3 unique)", 4, allHits.size());
        assertTrue("Should contain the common doc", allHits.containsKey("doc_common"));
    }
}
