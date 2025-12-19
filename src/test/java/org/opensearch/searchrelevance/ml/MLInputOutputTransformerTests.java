/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */
package org.opensearch.searchrelevance.ml;

import java.util.HashMap;
import java.util.Map;

import org.opensearch.ml.common.dataset.remote.RemoteInferenceInputDataSet;
import org.opensearch.ml.common.input.MLInput;
import org.opensearch.searchrelevance.model.LLMJudgmentRatingType;
import org.opensearch.test.OpenSearchTestCase;

/**
 * Tests for MLInputOutputTransformer focusing on response_format parameter handling.
 */
public class MLInputOutputTransformerTests extends OpenSearchTestCase {

    private MLInputOutputTransformer transformer;

    @Override
    public void setUp() throws Exception {
        super.setUp();
        transformer = new MLInputOutputTransformer();
    }

    // ============================================
    // Response Format Parameter Tests
    // ============================================

    public void testCreateMLInput_WithResponseFormat() {
        String searchText = "test query";
        Map<String, String> referenceData = new HashMap<>();
        Map<String, String> hits = new HashMap<>();
        hits.put("doc1", "test content");
        String promptTemplate = "Test prompt";
        LLMJudgmentRatingType ratingType = LLMJudgmentRatingType.SCORE0_1;

        MLInput mlInput = transformer.createMLInput(searchText, referenceData, hits, promptTemplate, ratingType, true);

        assertNotNull(mlInput);
        RemoteInferenceInputDataSet dataset = (RemoteInferenceInputDataSet) mlInput.getInputDataset();
        Map<String, String> parameters = dataset.getParameters();

        // Should include response_format parameter
        assertTrue("response_format parameter should be present", parameters.containsKey("response_format"));
        assertNotNull("response_format should not be null", parameters.get("response_format"));
        assertTrue("response_format should contain json_schema", parameters.get("response_format").contains("json_schema"));
    }

    public void testCreateMLInput_WithoutResponseFormat() {
        String searchText = "test query";
        Map<String, String> referenceData = new HashMap<>();
        Map<String, String> hits = new HashMap<>();
        hits.put("doc1", "test content");
        String promptTemplate = "Test prompt";
        LLMJudgmentRatingType ratingType = LLMJudgmentRatingType.SCORE0_1;

        MLInput mlInput = transformer.createMLInput(searchText, referenceData, hits, promptTemplate, ratingType, false);

        assertNotNull(mlInput);
        RemoteInferenceInputDataSet dataset = (RemoteInferenceInputDataSet) mlInput.getInputDataset();
        Map<String, String> parameters = dataset.getParameters();

        // Should NOT include response_format parameter
        assertFalse("response_format parameter should not be present for GPT-3.5 compatibility", parameters.containsKey("response_format"));
        // Messages parameter should still be present
        assertTrue("messages parameter should be present", parameters.containsKey("messages"));
    }

    public void testCreateMLInput_DefaultIncludesResponseFormat() {
        String searchText = "test query";
        Map<String, String> referenceData = new HashMap<>();
        Map<String, String> hits = new HashMap<>();
        hits.put("doc1", "test content");
        String promptTemplate = "Test prompt";
        LLMJudgmentRatingType ratingType = LLMJudgmentRatingType.SCORE0_1;

        // Using the method without includeResponseFormat parameter (default = true)
        MLInput mlInput = transformer.createMLInput(searchText, referenceData, hits, promptTemplate, ratingType);

        assertNotNull(mlInput);
        RemoteInferenceInputDataSet dataset = (RemoteInferenceInputDataSet) mlInput.getInputDataset();
        Map<String, String> parameters = dataset.getParameters();

        // Default should include response_format
        assertTrue("Default behavior should include response_format", parameters.containsKey("response_format"));
    }

    // ============================================
    // Different Rating Types with Response Format
    // ============================================

    public void testCreateMLInput_BinaryRatingWithResponseFormat() {
        String searchText = "test query";
        Map<String, String> referenceData = new HashMap<>();
        Map<String, String> hits = new HashMap<>();
        hits.put("doc1", "test content");
        String promptTemplate = "Test prompt";
        LLMJudgmentRatingType ratingType = LLMJudgmentRatingType.RELEVANT_IRRELEVANT;

        MLInput mlInput = transformer.createMLInput(searchText, referenceData, hits, promptTemplate, ratingType, true);

        assertNotNull(mlInput);
        RemoteInferenceInputDataSet dataset = (RemoteInferenceInputDataSet) mlInput.getInputDataset();
        Map<String, String> parameters = dataset.getParameters();

        assertTrue("response_format parameter should be present", parameters.containsKey("response_format"));
        String responseFormat = parameters.get("response_format");
        // Binary rating should use string enum schema
        assertTrue("Binary rating should use enum schema", responseFormat.contains("enum"));
        assertTrue("Binary rating should include RELEVANT", responseFormat.contains("RELEVANT"));
        assertTrue("Binary rating should include IRRELEVANT", responseFormat.contains("IRRELEVANT"));
    }

    public void testCreateMLInput_BinaryRatingWithoutResponseFormat() {
        String searchText = "test query";
        Map<String, String> referenceData = new HashMap<>();
        Map<String, String> hits = new HashMap<>();
        hits.put("doc1", "test content");
        String promptTemplate = "Test prompt";
        LLMJudgmentRatingType ratingType = LLMJudgmentRatingType.RELEVANT_IRRELEVANT;

        MLInput mlInput = transformer.createMLInput(searchText, referenceData, hits, promptTemplate, ratingType, false);

        assertNotNull(mlInput);
        RemoteInferenceInputDataSet dataset = (RemoteInferenceInputDataSet) mlInput.getInputDataset();
        Map<String, String> parameters = dataset.getParameters();

        // Should NOT include response_format for GPT-3.5 compatibility
        assertFalse("response_format should not be present", parameters.containsKey("response_format"));
    }

    public void testCreateMLInput_NumericRatingWithResponseFormat() {
        String searchText = "test query";
        Map<String, String> referenceData = new HashMap<>();
        Map<String, String> hits = new HashMap<>();
        hits.put("doc1", "test content");
        String promptTemplate = "Test prompt";
        LLMJudgmentRatingType ratingType = LLMJudgmentRatingType.SCORE0_1;

        MLInput mlInput = transformer.createMLInput(searchText, referenceData, hits, promptTemplate, ratingType, true);

        assertNotNull(mlInput);
        RemoteInferenceInputDataSet dataset = (RemoteInferenceInputDataSet) mlInput.getInputDataset();
        Map<String, String> parameters = dataset.getParameters();

        assertTrue("response_format parameter should be present", parameters.containsKey("response_format"));
        String responseFormat = parameters.get("response_format");
        // Numeric rating should use number type
        assertTrue("Numeric rating should use number type", responseFormat.contains("\"type\":\"number\""));
    }

    // ============================================
    // Multiple Hits Scenarios
    // ============================================

    public void testCreateMLInput_MultipleHitsWithResponseFormat() {
        String searchText = "test query";
        Map<String, String> referenceData = new HashMap<>();
        Map<String, String> hits = new HashMap<>();
        hits.put("doc1", "content 1");
        hits.put("doc2", "content 2");
        hits.put("doc3", "content 3");
        String promptTemplate = "Test prompt";
        LLMJudgmentRatingType ratingType = LLMJudgmentRatingType.SCORE0_1;

        MLInput mlInput = transformer.createMLInput(searchText, referenceData, hits, promptTemplate, ratingType, true);

        assertNotNull(mlInput);
        RemoteInferenceInputDataSet dataset = (RemoteInferenceInputDataSet) mlInput.getInputDataset();
        Map<String, String> parameters = dataset.getParameters();

        assertTrue("response_format should be present even with multiple hits", parameters.containsKey("response_format"));
        assertNotNull("messages parameter should not be null", parameters.get("messages"));
        assertFalse("messages parameter should not be empty", parameters.get("messages").isEmpty());
    }

    public void testCreateMLInput_MultipleHitsWithoutResponseFormat() {
        String searchText = "test query";
        Map<String, String> referenceData = new HashMap<>();
        Map<String, String> hits = new HashMap<>();
        hits.put("doc1", "content 1");
        hits.put("doc2", "content 2");
        String promptTemplate = "Test prompt";
        LLMJudgmentRatingType ratingType = LLMJudgmentRatingType.SCORE0_1;

        MLInput mlInput = transformer.createMLInput(searchText, referenceData, hits, promptTemplate, ratingType, false);

        assertNotNull(mlInput);
        RemoteInferenceInputDataSet dataset = (RemoteInferenceInputDataSet) mlInput.getInputDataset();
        Map<String, String> parameters = dataset.getParameters();

        assertFalse("response_format should not be present", parameters.containsKey("response_format"));
        assertNotNull("messages parameter should not be null", parameters.get("messages"));
        assertFalse("messages parameter should not be empty", parameters.get("messages").isEmpty());
    }

    // ============================================
    // Edge Cases
    // ============================================

    public void testCreateMLInput_EmptyHitsWithResponseFormat() {
        String searchText = "test query";
        Map<String, String> referenceData = new HashMap<>();
        Map<String, String> hits = new HashMap<>(); // Empty hits
        String promptTemplate = "Test prompt";
        LLMJudgmentRatingType ratingType = LLMJudgmentRatingType.SCORE0_1;

        MLInput mlInput = transformer.createMLInput(searchText, referenceData, hits, promptTemplate, ratingType, true);

        assertNotNull(mlInput);
        RemoteInferenceInputDataSet dataset = (RemoteInferenceInputDataSet) mlInput.getInputDataset();
        Map<String, String> parameters = dataset.getParameters();

        // Should still have response_format even with empty hits
        assertTrue("response_format should be present even with empty hits", parameters.containsKey("response_format"));
    }

    public void testCreateMLInput_WithReferenceDataAndResponseFormat() {
        String searchText = "test query";
        Map<String, String> referenceData = new HashMap<>();
        referenceData.put("reference", "Expected answer");
        Map<String, String> hits = new HashMap<>();
        hits.put("doc1", "test content");
        String promptTemplate = "Test prompt";
        LLMJudgmentRatingType ratingType = LLMJudgmentRatingType.SCORE0_1;

        MLInput mlInput = transformer.createMLInput(searchText, referenceData, hits, promptTemplate, ratingType, true);

        assertNotNull(mlInput);
        RemoteInferenceInputDataSet dataset = (RemoteInferenceInputDataSet) mlInput.getInputDataset();
        Map<String, String> parameters = dataset.getParameters();

        assertTrue("response_format should be present", parameters.containsKey("response_format"));
        assertNotNull("messages parameter should not be null", parameters.get("messages"));
        assertFalse("messages parameter should not be empty", parameters.get("messages").isEmpty());
    }
}
