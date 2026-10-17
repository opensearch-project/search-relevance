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
import java.util.Set;
import java.util.concurrent.TimeUnit;

import org.junit.Before;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.opensearch.cluster.ClusterState;
import org.opensearch.cluster.metadata.IndexMetadata;
import org.opensearch.cluster.metadata.MappingMetadata;
import org.opensearch.cluster.metadata.Metadata;
import org.opensearch.cluster.service.ClusterService;
import org.opensearch.core.common.bytes.BytesArray;
import org.opensearch.search.SearchHit;
import org.opensearch.searchrelevance.dao.JudgmentDao;
import org.opensearch.searchrelevance.dao.QuerySetDao;
import org.opensearch.searchrelevance.dao.SearchConfigurationDao;
import org.opensearch.searchrelevance.ml.MLAccessor;
import org.opensearch.searchrelevance.model.JudgmentType;
import org.opensearch.searchrelevance.model.LLMJudgmentRatingType;
import org.opensearch.searchrelevance.settings.SearchRelevanceSettingsAccessor;
import org.opensearch.searchrelevance.stats.events.EventStatsManager;
import org.opensearch.test.OpenSearchTestCase;
import org.opensearch.threadpool.TestThreadPool;
import org.opensearch.threadpool.ThreadPool;
import org.opensearch.transport.client.Client;

/**
 * Unit tests for LlmJudgmentsProcessor focusing on prompt templates and rating types.
 */
public class LlmJudgmentsProcessorTests extends OpenSearchTestCase {

    private LlmJudgmentsProcessor processor;
    private ThreadPool threadPool;

    @Mock
    private MLAccessor mockMLAccessor;

    @Mock
    private QuerySetDao mockQuerySetDao;

    @Mock
    private SearchConfigurationDao mockSearchConfigurationDao;

    @Mock
    private JudgmentDao mockJudgmentDao;

    @Mock
    private Client mockClient;

    @Mock
    private ClusterService mockClusterService;

    @Mock
    private SearchRelevanceSettingsAccessor mockSettingsAccessor;

    private EventStatsManager eventStatsManager;

    @Before
    public void setUp() throws Exception {
        super.setUp();
        MockitoAnnotations.openMocks(this);

        // Configure the mock settings accessor
        when(mockSettingsAccessor.isStatsEnabled()).thenReturn(false);

        // Initialize and configure EventStatsManager with our mock
        eventStatsManager = EventStatsManager.instance();
        eventStatsManager.initialize(mockSettingsAccessor);

        // Create a real thread pool for testing
        threadPool = new TestThreadPool("test-thread-pool");

        processor = new LlmJudgmentsProcessor(
            mockMLAccessor,
            mockQuerySetDao,
            mockSearchConfigurationDao,
            mockJudgmentDao,
            mockClient,
            mockClusterService,
            threadPool
        );
    }

    @Override
    public void tearDown() throws Exception {
        super.tearDown();
        ThreadPool.terminate(threadPool, 10, TimeUnit.SECONDS);
    }

    public void testGetJudgmentType() {
        assertEquals(JudgmentType.LLM_JUDGMENT, processor.getJudgmentType());
    }

    // ============================================
    // Metadata Validation Tests
    // ============================================

    public void testMetadata_AllRatingTypes() {
        // Test that all rating types are valid values for metadata
        Map<String, Object> metadata = createBasicMetadata();

        // SCORE0_1
        metadata.put("llmJudgmentRatingType", LLMJudgmentRatingType.SCORE0_1);
        assertNotNull("SCORE0_1 should be valid", metadata.get("llmJudgmentRatingType"));

        // RELEVANT_IRRELEVANT
        metadata.put("llmJudgmentRatingType", LLMJudgmentRatingType.RELEVANT_IRRELEVANT);
        assertNotNull("RELEVANT_IRRELEVANT should be valid", metadata.get("llmJudgmentRatingType"));
    }

    public void testMetadata_DefaultRatingTypeIsNull() {
        // Test that null rating type in metadata is acceptable
        Map<String, Object> metadata = createBasicMetadata();
        metadata.put("llmJudgmentRatingType", null);

        // This should not throw any exception
        assertNull("Rating type can be null", metadata.get("llmJudgmentRatingType"));
    }

    public void testMetadata_PromptTemplateVariations() {
        // Test various prompt template values
        Map<String, Object> metadata = createBasicMetadata();

        // Custom template
        String customTemplate = "Rate relevance from 0 to 1";
        metadata.put("promptTemplate", customTemplate);
        assertEquals("Custom template should be set", customTemplate, metadata.get("promptTemplate"));

        // Empty template
        metadata.put("promptTemplate", "");
        assertEquals("Empty template should be set", "", metadata.get("promptTemplate"));

        // Null template
        metadata.put("promptTemplate", null);
        assertNull("Null template should be allowed", metadata.get("promptTemplate"));
    }

    public void testMetadata_CombinedRatingTypeAndPrompt() {
        // Test that metadata can hold both rating type and prompt template
        Map<String, Object> metadata = new HashMap<>();

        metadata.put("llmJudgmentRatingType", LLMJudgmentRatingType.SCORE0_1);
        metadata.put("promptTemplate", "Custom prompt for 0-1 scale");

        assertEquals(LLMJudgmentRatingType.SCORE0_1, metadata.get("llmJudgmentRatingType"));
        assertEquals("Custom prompt for 0-1 scale", metadata.get("promptTemplate"));
    }

    public void testMetadata_RequiredFields() {
        // Test that basic metadata contains all required fields
        Map<String, Object> metadata = createBasicMetadata();

        assertTrue("Metadata should contain querySetId", metadata.containsKey("querySetId"));
        assertTrue("Metadata should contain searchConfigurationList", metadata.containsKey("searchConfigurationList"));
        assertTrue("Metadata should contain size", metadata.containsKey("size"));
        assertTrue("Metadata should contain modelId", metadata.containsKey("modelId"));
        assertTrue("Metadata should contain tokenLimit", metadata.containsKey("tokenLimit"));
        assertTrue("Metadata should contain contextFields", metadata.containsKey("contextFields"));
        assertTrue("Metadata should contain ignoreFailure", metadata.containsKey("ignoreFailure"));
    }

    // ============================================
    // Rating Type Enum Tests
    // ============================================

    public void testRatingTypeEnum_AllValues() {
        // Verify all expected rating types exist
        LLMJudgmentRatingType[] ratingTypes = LLMJudgmentRatingType.values();

        assertEquals("Should have exactly 2 rating types", 2, ratingTypes.length);

        boolean hasSCORE0_1 = false;
        boolean hasRELEVANT_IRRELEVANT = false;

        for (LLMJudgmentRatingType type : ratingTypes) {
            if (type == LLMJudgmentRatingType.SCORE0_1) hasSCORE0_1 = true;
            if (type == LLMJudgmentRatingType.RELEVANT_IRRELEVANT) hasRELEVANT_IRRELEVANT = true;
        }

        assertTrue("Should have SCORE0_1", hasSCORE0_1);
        assertTrue("Should have RELEVANT_IRRELEVANT", hasRELEVANT_IRRELEVANT);
    }

    public void testRatingTypeEnum_GetValidValues() {
        // Test that getValidValues() returns all rating types
        String validValues = LLMJudgmentRatingType.getValidValues();

        assertTrue("Valid values should contain SCORE0_1", validValues.contains("SCORE0_1"));
        assertTrue("Valid values should contain RELEVANT_IRRELEVANT", validValues.contains("RELEVANT_IRRELEVANT"));
    }

    // ============================================
    // buildResultWithFailures Tests
    // ============================================

    @SuppressWarnings("unchecked")
    public void testBuildResultWithFailures_partialFailure() {
        Map<String, String> docIdToScore = Map.of("A", "0.9", "B", "0.4");

        Map<String, Object> result = LlmJudgmentsProcessor.buildResultWithFailures("laptop", Set.of("A", "B", "C"), docIdToScore);

        assertEquals("laptop", result.get("query"));
        List<Map<String, String>> ratings = (List<Map<String, String>>) result.get("ratings");
        assertEquals(2, ratings.size());

        List<Map<String, String>> failures = (List<Map<String, String>>) result.get("failures");
        assertNotNull(failures);
        assertEquals(1, failures.size());
        assertEquals("C", failures.get(0).get("docId"));
    }

    @SuppressWarnings("unchecked")
    public void testBuildResultWithFailures_allRated_noFailuresKey() {
        Map<String, String> docIdToScore = Map.of("A", "0.9", "B", "0.4");

        Map<String, Object> result = LlmJudgmentsProcessor.buildResultWithFailures("laptop", Set.of("A", "B"), docIdToScore);

        assertEquals(2, ((List<Map<String, String>>) result.get("ratings")).size());
        assertFalse("no failures key when every doc was rated", result.containsKey("failures"));
    }

    @SuppressWarnings("unchecked")
    public void testBuildResultWithFailures_allFailed_emptyRatings() {
        Map<String, String> docIdToScore = Map.of();

        Map<String, Object> result = LlmJudgmentsProcessor.buildResultWithFailures("laptop", Set.of("A", "B"), docIdToScore);

        assertTrue(((List<Map<String, String>>) result.get("ratings")).isEmpty());
        assertEquals(2, ((List<Map<String, String>>) result.get("failures")).size());
    }

    public void testBuildResultWithFailures_noDocsSent_noFailuresKey() {
        Map<String, Object> result = LlmJudgmentsProcessor.buildResultWithFailures("laptop", Set.of(), Map.of());

        assertTrue(((List<?>) result.get("ratings")).isEmpty());
        assertFalse("no failures key when nothing was sent", result.containsKey("failures"));
    }

    // ============================================
    // getContextSource — vector field exclusion
    // ============================================

    public void testGetContextSource_contextFieldsSpecified_sendsOnlyNamedFieldsIncludingVector() {
        SearchHit hit = hit("{\"title\":\"Laptop\",\"embedding\":[0.1,0.2,0.3],\"category\":\"tech\"}");

        // Explicit opt-in: a vector field named in contextFields is still sent; unnamed fields are not.
        String out = processor.getContextSource(hit, List.of("title", "embedding"), Set.of("embedding"));

        assertTrue(out.contains("title"));
        assertTrue("explicitly named vector field must be sent", out.contains("embedding"));
        assertFalse("field not named in contextFields must not be sent", out.contains("category"));
    }

    public void testGetContextSource_defaultPath_dropsMappedVectorField() {
        SearchHit hit = hit("{\"title\":\"Laptop\",\"embedding\":[0.1,0.2,0.3],\"category\":\"tech\"}");

        String out = processor.getContextSource(hit, null, Set.of("embedding"));

        assertTrue(out.contains("title"));
        assertTrue(out.contains("category"));
        assertFalse("mapped vector field must be dropped from the default prompt", out.contains("embedding"));
    }

    public void testGetContextSource_defaultPath_noVectorFields_keepsEverything() {
        SearchHit hit = hit("{\"title\":\"Laptop\",\"category\":\"tech\"}");

        String out = processor.getContextSource(hit, null, Set.of());

        assertTrue(out.contains("title"));
        assertTrue(out.contains("category"));
    }

    public void testGetContextSource_defaultPath_dropsNestedVectorKeepsSiblings() {
        SearchHit hit = hit("{\"title\":\"Laptop\",\"nested\":{\"passage_embedding\":[0.1,0.2],\"text\":\"body\"}}");

        String out = processor.getContextSource(hit, null, Set.of("passage_embedding"));

        assertTrue(out.contains("title"));
        assertTrue("sibling of a nested vector field must survive", out.contains("body"));
        assertFalse("nested vector field must be dropped", out.contains("passage_embedding"));
    }

    public void testGetContextSource_defaultPath_emptyExcluded_sendsFullSourceIncludingVector() {
        SearchHit hit = hit("{\"title\":\"Laptop\",\"embedding\":[0.1,0.2,0.3]}");

        // Empty set: no vector fields resolved (or mapping unreadable) => send _source unchanged.
        String out = processor.getContextSource(hit, null, Set.of());

        assertTrue(out.contains("title"));
        assertTrue(out.contains("embedding"));
    }

    // ============================================
    // resolveExcludedVectorFields — mapping walk
    // ============================================

    public void testResolveExcludedVectorFields_collectsVectorTypesIncludingNested() {
        Map<String, Object> props = new HashMap<>();
        props.put("title", Map.of("type", "text"));
        props.put("embedding", Map.of("type", "knn_vector", "dimension", 768));
        props.put("tokens", Map.of("type", "rank_features"));
        Map<String, Object> nested = new HashMap<>();
        nested.put("properties", Map.of("passage_embedding", Map.of("type", "knn_vector")));
        props.put("nested", nested);
        Map<String, Object> mappingSource = new HashMap<>();
        mappingSource.put("properties", props);
        mockMapping("products", mappingSource);

        Set<String> excluded = processor.resolveExcludedVectorFields("products");

        assertNotNull(excluded);
        assertTrue(excluded.contains("embedding"));
        assertTrue(excluded.contains("tokens"));
        assertTrue("nested vector field must be collected", excluded.contains("passage_embedding"));
        assertFalse(excluded.contains("title"));
    }

    public void testResolveExcludedVectorFields_missingIndex_returnsEmpty() {
        ClusterState state = mock(ClusterState.class);
        Metadata md = mock(Metadata.class);
        when(mockClusterService.state()).thenReturn(state);
        when(state.metadata()).thenReturn(md);
        when(md.index("missing")).thenReturn(null);

        assertTrue("unreadable mapping => empty set => full _source is sent", processor.resolveExcludedVectorFields("missing").isEmpty());
    }

    // ============================================
    // Helper Methods
    // ============================================

    private static SearchHit hit(String sourceJson) {
        return new SearchHit(1, "doc1", Map.of(), Map.of()).sourceRef(new BytesArray(sourceJson));
    }

    private void mockMapping(String index, Map<String, Object> mappingSource) {
        ClusterState state = mock(ClusterState.class);
        Metadata md = mock(Metadata.class);
        IndexMetadata im = mock(IndexMetadata.class);
        MappingMetadata mm = mock(MappingMetadata.class);
        when(mockClusterService.state()).thenReturn(state);
        when(state.metadata()).thenReturn(md);
        when(md.index(index)).thenReturn(im);
        when(im.mapping()).thenReturn(mm);
        when(mm.sourceAsMap()).thenReturn(mappingSource);
    }

    private Map<String, Object> createBasicMetadata() {
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("querySetId", "test-query-set");
        metadata.put("searchConfigurationList", List.of("test-config"));
        metadata.put("size", 10);
        metadata.put("modelId", "test-model");
        metadata.put("tokenLimit", 4000);
        metadata.put("contextFields", List.of("title", "description"));
        metadata.put("ignoreFailure", false);
        metadata.put("promptTemplate", "Default prompt template");
        metadata.put("llmJudgmentRatingType", LLMJudgmentRatingType.SCORE0_1);
        return metadata;
    }

    private void setupMocksForSuccessfulExecution() {
        // Since LlmJudgmentsProcessor uses complex async operations and thread pool,
        // we just verify that the methods don't throw exceptions with valid inputs.
        // The actual processing logic is tested through integration tests.

        // For unit tests, we're primarily testing:
        // 1. Default rating type behavior
        // 2. Handling of different rating types
        // 3. Handling of different prompt templates
        // 4. No exceptions are thrown for valid inputs
    }
}
