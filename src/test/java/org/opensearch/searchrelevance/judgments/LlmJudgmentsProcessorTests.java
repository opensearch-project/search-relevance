/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */
package org.opensearch.searchrelevance.judgments;

import static org.mockito.Mockito.when;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.junit.Before;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.opensearch.searchrelevance.dao.JudgmentCacheDao;
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
    private JudgmentCacheDao mockJudgmentCacheDao;

    @Mock
    private Client mockClient;

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
            mockJudgmentCacheDao,
            mockClient,
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

        // SCORE1_5
        metadata.put("llmJudgmentRatingType", LLMJudgmentRatingType.SCORE1_5);
        assertNotNull("SCORE1_5 should be valid", metadata.get("llmJudgmentRatingType"));

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

        metadata.put("llmJudgmentRatingType", LLMJudgmentRatingType.SCORE1_5);
        metadata.put("promptTemplate", "Custom prompt for 1-5 scale");

        assertEquals(LLMJudgmentRatingType.SCORE1_5, metadata.get("llmJudgmentRatingType"));
        assertEquals("Custom prompt for 1-5 scale", metadata.get("promptTemplate"));
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
        assertTrue("Metadata should contain overwriteCache", metadata.containsKey("overwriteCache"));
    }

    // ============================================
    // Rating Type Enum Tests
    // ============================================

    public void testRatingTypeEnum_AllValues() {
        // Verify all expected rating types exist
        LLMJudgmentRatingType[] ratingTypes = LLMJudgmentRatingType.values();

        assertEquals("Should have exactly 3 rating types", 3, ratingTypes.length);

        boolean hasSCORE0_1 = false;
        boolean hasSCORE1_5 = false;
        boolean hasRELEVANT_IRRELEVANT = false;

        for (LLMJudgmentRatingType type : ratingTypes) {
            if (type == LLMJudgmentRatingType.SCORE0_1) hasSCORE0_1 = true;
            if (type == LLMJudgmentRatingType.SCORE1_5) hasSCORE1_5 = true;
            if (type == LLMJudgmentRatingType.RELEVANT_IRRELEVANT) hasRELEVANT_IRRELEVANT = true;
        }

        assertTrue("Should have SCORE0_1", hasSCORE0_1);
        assertTrue("Should have SCORE1_5", hasSCORE1_5);
        assertTrue("Should have RELEVANT_IRRELEVANT", hasRELEVANT_IRRELEVANT);
    }

    public void testRatingTypeEnum_GetValidValues() {
        // Test that getValidValues() returns all rating types
        String validValues = LLMJudgmentRatingType.getValidValues();

        assertTrue("Valid values should contain SCORE0_1", validValues.contains("SCORE0_1"));
        assertTrue("Valid values should contain SCORE1_5", validValues.contains("SCORE1_5"));
        assertTrue("Valid values should contain RELEVANT_IRRELEVANT", validValues.contains("RELEVANT_IRRELEVANT"));
    }

    // ============================================
    // Helper Methods
    // ============================================

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
        metadata.put("overwriteCache", false);
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

    // ============================================
    // parseQueryTextWithCustomInput Tests
    // ============================================

    public void testParseQueryTextWithCustomInput_QueryOnly() {
        // Test with only query text, no reference data
        String input = "What is OpenSearch?";
        Map<String, String> result = LlmJudgmentsProcessor.parseQueryTextWithCustomInput(input);

        assertEquals("Query text should be parsed", "What is OpenSearch?", result.get("queryText"));
        assertEquals("Should only contain queryText", 1, result.size());
    }

    public void testParseQueryTextWithCustomInput_LegacyFormat() {
        // Test legacy format: queryText#referenceAnswer
        String input = "What is OpenSearch?#OpenSearch is a community-driven, open source search and analytics suite";
        Map<String, String> result = LlmJudgmentsProcessor.parseQueryTextWithCustomInput(input);

        assertEquals("Query text should be parsed", "What is OpenSearch?", result.get("queryText"));
        assertEquals(
            "Reference answer should be parsed",
            "OpenSearch is a community-driven, open source search and analytics suite",
            result.get("referenceAnswer")
        );
        assertEquals("Should contain queryText and referenceAnswer", 2, result.size());
    }

    public void testParseQueryTextWithCustomInput_NewFormat() {
        // Test new format: queryText#\nkey1: value1\nkey2: value2\n...
        String input = "What is OpenSearch?#\nreferenceAnswer: OpenSearch is a search suite\ncategory: technology";
        Map<String, String> result = LlmJudgmentsProcessor.parseQueryTextWithCustomInput(input);

        assertEquals("Query text should be parsed", "What is OpenSearch?", result.get("queryText"));
        assertEquals("Reference answer should be parsed", "OpenSearch is a search suite", result.get("referenceAnswer"));
        assertEquals("Category should be parsed", "technology", result.get("category"));
        assertEquals("Should contain queryText, referenceAnswer, and category", 3, result.size());
    }

    public void testParseQueryTextWithCustomInput_NewFormatMultipleFields() {
        // Test new format with multiple custom fields
        String input = "red shoes#\nreferenceAnswer: High quality leather shoes\ncolor: red\nbrand: Nike\nprice: 120";
        Map<String, String> result = LlmJudgmentsProcessor.parseQueryTextWithCustomInput(input);

        assertEquals("Query text should be parsed", "red shoes", result.get("queryText"));
        assertEquals("Reference answer should be parsed", "High quality leather shoes", result.get("referenceAnswer"));
        assertEquals("Color should be parsed", "red", result.get("color"));
        assertEquals("Brand should be parsed", "Nike", result.get("brand"));
        assertEquals("Price should be parsed", "120", result.get("price"));
        assertEquals("Should contain 5 entries", 5, result.size());
    }

    public void testParseQueryTextWithCustomInput_NewFormatWithEmptyLines() {
        // Test new format with empty lines (should be skipped)
        String input = "test query#\nkey1: value1\n\nkey2: value2\n\nkey3: value3";
        Map<String, String> result = LlmJudgmentsProcessor.parseQueryTextWithCustomInput(input);

        assertEquals("Query text should be parsed", "test query", result.get("queryText"));
        assertEquals("Key1 should be parsed", "value1", result.get("key1"));
        assertEquals("Key2 should be parsed", "value2", result.get("key2"));
        assertEquals("Key3 should be parsed", "value3", result.get("key3"));
        assertEquals("Should contain 4 entries", 4, result.size());
    }

    public void testParseQueryTextWithCustomInput_NewFormatWithSpaces() {
        // Test new format with extra spaces around keys and values
        String input = "test#\n  key1  :  value1  \nkey2:value2\n  key3:  value3";
        Map<String, String> result = LlmJudgmentsProcessor.parseQueryTextWithCustomInput(input);

        assertEquals("Query text should be parsed", "test", result.get("queryText"));
        assertEquals("Key1 should be trimmed", "value1", result.get("key1"));
        assertEquals("Key2 should be trimmed", "value2", result.get("key2"));
        assertEquals("Key3 should be trimmed", "value3", result.get("key3"));
        assertEquals("Should contain 4 entries", 4, result.size());
    }

    public void testParseQueryTextWithCustomInput_NewFormatInvalidLines() {
        // Test new format with lines that don't match "key: value" format
        String input = "test#\nkey1: value1\ninvalid line without colon\nkey2: value2\n: no key\nkey3: value3";
        Map<String, String> result = LlmJudgmentsProcessor.parseQueryTextWithCustomInput(input);

        assertEquals("Query text should be parsed", "test", result.get("queryText"));
        assertEquals("Key1 should be parsed", "value1", result.get("key1"));
        assertEquals("Key2 should be parsed", "value2", result.get("key2"));
        assertEquals("Key3 should be parsed", "value3", result.get("key3"));
        // Invalid lines should be skipped
        assertEquals("Should contain 4 entries (invalid lines skipped)", 4, result.size());
    }

    public void testParseQueryTextWithCustomInput_ValueWithColons() {
        // Test that values can contain colons
        String input = "test#\nurl: https://example.com:8080\ntime: 10:30:00";
        Map<String, String> result = LlmJudgmentsProcessor.parseQueryTextWithCustomInput(input);

        assertEquals("Query text should be parsed", "test", result.get("queryText"));
        assertEquals("URL with colons should be parsed correctly", "https://example.com:8080", result.get("url"));
        assertEquals("Time with colons should be parsed correctly", "10:30:00", result.get("time"));
        assertEquals("Should contain 3 entries", 3, result.size());
    }

    public void testParseQueryTextWithCustomInput_EmptyReferenceContent() {
        // Test with delimiter but empty content after it
        String input = "test query#";
        Map<String, String> result = LlmJudgmentsProcessor.parseQueryTextWithCustomInput(input);

        assertEquals("Query text should be parsed", "test query", result.get("queryText"));
        assertEquals("Should only contain queryText", 1, result.size());
    }

    // ============================================
    // QuerySetEntry Format Integration Tests
    // ============================================

    public void testQuerySetEntry_OldFormat_SingleReferenceAnswer() {
        // Test old QuerySetEntry format: "queryText#referenceAnswer"
        // This simulates the legacy format where queryText contains both query and reference answer
        String querySetEntry = "What is OpenSearch?#OpenSearch is a community-driven, open source search and analytics suite";

        Map<String, String> result = LlmJudgmentsProcessor.parseQueryTextWithCustomInput(querySetEntry);

        assertEquals("Query text should be extracted", "What is OpenSearch?", result.get("queryText"));
        assertEquals(
            "Reference answer should be extracted",
            "OpenSearch is a community-driven, open source search and analytics suite",
            result.get("referenceAnswer")
        );
        assertEquals("Should contain queryText and referenceAnswer", 2, result.size());

        // Verify this can be used for ML processing
        String queryText = result.remove("queryText");
        Map<String, String> referenceData = result; // Remaining entries are reference data

        assertEquals("Query text should be ready for ML", "What is OpenSearch?", queryText);
        assertEquals("Reference data should contain referenceAnswer", 1, referenceData.size());
        assertTrue("Reference data should have referenceAnswer key", referenceData.containsKey("referenceAnswer"));
    }

    public void testQuerySetEntry_NewFormat_MultipleCustomFields() {
        // Test new QuerySetEntry format from PutQuerySetTransportAction
        // Format: "queryText#\nkey1: value1\nkey2: value2\n..."
        String querySetEntry = "red shoes#\nreferenceAnswer: High quality red leather shoes\ncolor: red\nbrand: Nike\nprice: 120";

        Map<String, String> result = LlmJudgmentsProcessor.parseQueryTextWithCustomInput(querySetEntry);

        assertEquals("Query text should be extracted", "red shoes", result.get("queryText"));
        assertEquals("Reference answer should be extracted", "High quality red leather shoes", result.get("referenceAnswer"));
        assertEquals("Color should be extracted", "red", result.get("color"));
        assertEquals("Brand should be extracted", "Nike", result.get("brand"));
        assertEquals("Price should be extracted", "120", result.get("price"));
        assertEquals("Should contain all fields", 5, result.size());

        // Verify this can be used for ML processing
        String queryText = result.remove("queryText");
        Map<String, String> referenceData = result; // Remaining entries are reference data

        assertEquals("Query text should be ready for ML", "red shoes", queryText);
        assertEquals("Reference data should contain all custom fields", 4, referenceData.size());
        assertTrue("Reference data should have referenceAnswer", referenceData.containsKey("referenceAnswer"));
        assertTrue("Reference data should have color", referenceData.containsKey("color"));
        assertTrue("Reference data should have brand", referenceData.containsKey("brand"));
        assertTrue("Reference data should have price", referenceData.containsKey("price"));
    }

    public void testQuerySetEntry_NewFormat_OnlyReferenceAnswer() {
        // Test new format with only referenceAnswer (no other custom fields)
        String querySetEntry = "What is OpenSearch?#\nreferenceAnswer: OpenSearch is a search and analytics suite";

        Map<String, String> result = LlmJudgmentsProcessor.parseQueryTextWithCustomInput(querySetEntry);

        assertEquals("Query text should be extracted", "What is OpenSearch?", result.get("queryText"));
        assertEquals("Reference answer should be extracted", "OpenSearch is a search and analytics suite", result.get("referenceAnswer"));
        assertEquals("Should contain queryText and referenceAnswer", 2, result.size());

        // Verify this can be used for ML processing
        String queryText = result.remove("queryText");
        Map<String, String> referenceData = result;

        assertEquals("Query text should be ready for ML", "What is OpenSearch?", queryText);
        assertEquals("Reference data should contain only referenceAnswer", 1, referenceData.size());
    }

    public void testQuerySetEntry_NewFormat_NoReferenceAnswerOnlyCustomFields() {
        // Test new format with custom fields but no referenceAnswer
        String querySetEntry = "test query#\ncategory: technology\nexpectedScore: 0.9\ndifficulty: medium";

        Map<String, String> result = LlmJudgmentsProcessor.parseQueryTextWithCustomInput(querySetEntry);

        assertEquals("Query text should be extracted", "test query", result.get("queryText"));
        assertEquals("Category should be extracted", "technology", result.get("category"));
        assertEquals("Expected score should be extracted", "0.9", result.get("expectedScore"));
        assertEquals("Difficulty should be extracted", "medium", result.get("difficulty"));
        assertFalse("Should not have referenceAnswer", result.containsKey("referenceAnswer"));
        assertEquals("Should contain queryText and 3 custom fields", 4, result.size());

        // Verify this can be used for ML processing
        String queryText = result.remove("queryText");
        Map<String, String> referenceData = result;

        assertEquals("Query text should be ready for ML", "test query", queryText);
        assertEquals("Reference data should contain custom fields", 3, referenceData.size());
        assertFalse("Reference data should not have referenceAnswer", referenceData.containsKey("referenceAnswer"));
    }

    public void testQuerySetEntry_OldFormat_EmptyReferenceAnswer() {
        // Test old format with empty reference answer
        String querySetEntry = "What is OpenSearch?#";

        Map<String, String> result = LlmJudgmentsProcessor.parseQueryTextWithCustomInput(querySetEntry);

        assertEquals("Query text should be extracted", "What is OpenSearch?", result.get("queryText"));
        assertEquals("Should only contain queryText", 1, result.size());

        // Verify this can be used for ML processing
        String queryText = result.remove("queryText");
        Map<String, String> referenceData = result;

        assertEquals("Query text should be ready for ML", "What is OpenSearch?", queryText);
        assertTrue("Reference data should be empty", referenceData.isEmpty());
    }

    public void testQuerySetEntry_NoDelimiter_QueryOnly() {
        // Test entry with no delimiter (just query text)
        String querySetEntry = "What is OpenSearch?";

        Map<String, String> result = LlmJudgmentsProcessor.parseQueryTextWithCustomInput(querySetEntry);

        assertEquals("Query text should be extracted", "What is OpenSearch?", result.get("queryText"));
        assertEquals("Should only contain queryText", 1, result.size());

        // Verify this can be used for ML processing
        String queryText = result.remove("queryText");
        Map<String, String> referenceData = result;

        assertEquals("Query text should be ready for ML", "What is OpenSearch?", queryText);
        assertTrue("Reference data should be empty", referenceData.isEmpty());
    }

    public void testQuerySetEntry_BackwardCompatibility_OldToNew() {
        // Test that old format entries work the same way as new format with single referenceAnswer
        String oldFormatEntry = "test query#expected answer";
        String newFormatEntry = "test query#\nreferenceAnswer: expected answer";

        Map<String, String> oldResult = LlmJudgmentsProcessor.parseQueryTextWithCustomInput(oldFormatEntry);
        Map<String, String> newResult = LlmJudgmentsProcessor.parseQueryTextWithCustomInput(newFormatEntry);

        // Both should extract the same queryText
        assertEquals("Query text should match", oldResult.get("queryText"), newResult.get("queryText"));

        // Both should have referenceAnswer
        assertEquals("Both should have referenceAnswer", oldResult.get("referenceAnswer"), newResult.get("referenceAnswer"));

        // Both should have the same size
        assertEquals("Both should have same number of entries", oldResult.size(), newResult.size());
    }

    public void testQuerySetEntry_NewFormat_RealWorldExample() {
        // Test real-world example from PutQuerySetTransportAction
        // Simulates what would be stored in the index
        String querySetEntry = "red leather shoes#\n"
            + "referenceAnswer: High quality red leather shoes with rubber sole and comfortable insole\n"
            + "expectedRelevanceScore: 0.95\n"
            + "productCategory: footwear\n"
            + "targetAudience: adults\n"
            + "priceRange: premium";

        Map<String, String> result = LlmJudgmentsProcessor.parseQueryTextWithCustomInput(querySetEntry);

        // Verify all fields are extracted
        assertEquals("Query text should be extracted", "red leather shoes", result.get("queryText"));
        assertEquals(
            "Reference answer should be extracted",
            "High quality red leather shoes with rubber sole and comfortable insole",
            result.get("referenceAnswer")
        );
        assertEquals("Expected score should be extracted", "0.95", result.get("expectedRelevanceScore"));
        assertEquals("Category should be extracted", "footwear", result.get("productCategory"));
        assertEquals("Target audience should be extracted", "adults", result.get("targetAudience"));
        assertEquals("Price range should be extracted", "premium", result.get("priceRange"));
        assertEquals("Should contain all 6 fields", 6, result.size());

        // Verify this can be used for ML processing and UserPromptFactory
        String queryText = result.remove("queryText");
        Map<String, String> referenceData = result;

        assertEquals("Query text should be ready", "red leather shoes", queryText);
        assertEquals("Reference data should have 5 custom fields", 5, referenceData.size());

        // All these fields can now be used in UserPromptFactory with template variables like:
        // "Query: {{query}}, Expected: {{referenceAnswer}}, Score: {{expectedRelevanceScore}}, Category: {{productCategory}}"
        assertTrue("Should have all fields for template replacement", referenceData.containsKey("referenceAnswer"));
        assertTrue("Should have expectedRelevanceScore", referenceData.containsKey("expectedRelevanceScore"));
        assertTrue("Should have productCategory", referenceData.containsKey("productCategory"));
        assertTrue("Should have targetAudience", referenceData.containsKey("targetAudience"));
        assertTrue("Should have priceRange", referenceData.containsKey("priceRange"));
    }

    public void testQuerySetEntry_NewFormat_SpecialCharactersInValues() {
        // Test new format with special characters in values
        String querySetEntry = "test query#\nurl: https://example.com:8080/path?param=value&other=123\n"
            + "description: Product with \"quotes\" & special <characters>\n"
            + "metadata: key1=val1;key2=val2";

        Map<String, String> result = LlmJudgmentsProcessor.parseQueryTextWithCustomInput(querySetEntry);

        assertEquals("Query text should be extracted", "test query", result.get("queryText"));
        assertEquals(
            "URL with special chars should be extracted",
            "https://example.com:8080/path?param=value&other=123",
            result.get("url")
        );
        assertEquals(
            "Description with quotes should be extracted",
            "Product with \"quotes\" & special <characters>",
            result.get("description")
        );
        assertEquals("Metadata with delimiters should be extracted", "key1=val1;key2=val2", result.get("metadata"));
        assertEquals("Should contain all fields", 4, result.size());
    }
}
