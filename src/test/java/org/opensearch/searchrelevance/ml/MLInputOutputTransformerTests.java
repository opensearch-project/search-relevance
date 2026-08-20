/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */
package org.opensearch.searchrelevance.ml;

import static org.mockito.Mockito.mock;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.opensearch.ml.common.dataset.remote.RemoteInferenceInputDataSet;
import org.opensearch.ml.common.input.MLInput;
import org.opensearch.ml.common.output.MLOutput;
import org.opensearch.ml.common.output.model.ModelTensor;
import org.opensearch.ml.common.output.model.ModelTensorOutput;
import org.opensearch.ml.common.output.model.ModelTensors;
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

        MLInput mlInput = transformer.createMLInput(searchText, referenceData, hits, promptTemplate, ratingType);

        assertNotNull(mlInput);
        RemoteInferenceInputDataSet dataset = (RemoteInferenceInputDataSet) mlInput.getInputDataset();
        Map<String, String> parameters = dataset.getParameters();

        // Should include response_format parameter
        assertTrue("response_format parameter should be present", parameters.containsKey("response_format"));
        assertNotNull("response_format should not be null", parameters.get("response_format"));
        assertTrue("response_format should contain json_schema", parameters.get("response_format").contains("json_schema"));
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

        MLInput mlInput = transformer.createMLInput(searchText, referenceData, hits, promptTemplate, ratingType);

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

    public void testCreateMLInput_NumericRatingWithResponseFormat() {
        String searchText = "test query";
        Map<String, String> referenceData = new HashMap<>();
        Map<String, String> hits = new HashMap<>();
        hits.put("doc1", "test content");
        String promptTemplate = "Test prompt";
        LLMJudgmentRatingType ratingType = LLMJudgmentRatingType.SCORE0_1;

        MLInput mlInput = transformer.createMLInput(searchText, referenceData, hits, promptTemplate, ratingType);

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

        MLInput mlInput = transformer.createMLInput(searchText, referenceData, hits, promptTemplate, ratingType);

        assertNotNull(mlInput);
        RemoteInferenceInputDataSet dataset = (RemoteInferenceInputDataSet) mlInput.getInputDataset();
        Map<String, String> parameters = dataset.getParameters();

        assertTrue("response_format should be present even with multiple hits", parameters.containsKey("response_format"));
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

        MLInput mlInput = transformer.createMLInput(searchText, referenceData, hits, promptTemplate, ratingType);

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

        MLInput mlInput = transformer.createMLInput(searchText, referenceData, hits, promptTemplate, ratingType);

        assertNotNull(mlInput);
        RemoteInferenceInputDataSet dataset = (RemoteInferenceInputDataSet) mlInput.getInputDataset();
        Map<String, String> parameters = dataset.getParameters();

        assertTrue("response_format should be present", parameters.containsKey("response_format"));
        assertNotNull("messages parameter should not be null", parameters.get("messages"));
        assertFalse("messages parameter should not be empty", parameters.get("messages").isEmpty());
    }

    // ============================================
    // Provider-neutral parameter Tests
    // ============================================

    public void testCreateMLInput_emitsNeutralSystemAndUserPrompt() {
        Map<String, String> hits = new HashMap<>();
        hits.put("doc1", "leather running shoes");

        MLInput mlInput = transformer.createMLInput(
            "red shoes",
            new HashMap<>(),
            hits,
            "{{searchText}} {{hits}}",
            LLMJudgmentRatingType.SCORE0_1
        );
        Map<String, String> parameters = ((RemoteInferenceInputDataSet) mlInput.getInputDataset()).getParameters();

        // Neutral fields a non-OpenAI blueprint references directly.
        assertTrue("system_prompt should be present", parameters.containsKey("system_prompt"));
        assertTrue("user_prompt should be present", parameters.containsKey("user_prompt"));
        assertFalse("system_prompt should not be empty", parameters.get("system_prompt").isEmpty());
        assertTrue("user_prompt should carry the search text", parameters.get("user_prompt").contains("red shoes"));
        assertTrue("user_prompt should carry the hit content", parameters.get("user_prompt").contains("leather running shoes"));
    }

    public void testCreateMLInput_neutralAndLegacyParamsCoexist() {
        Map<String, String> hits = new HashMap<>();
        hits.put("doc1", "content");

        MLInput mlInput = transformer.createMLInput("q", new HashMap<>(), hits, "Test prompt", LLMJudgmentRatingType.SCORE0_1);
        Map<String, String> parameters = ((RemoteInferenceInputDataSet) mlInput.getInputDataset()).getParameters();

        // Both the neutral params and the legacy OpenAI-shaped messages are emitted on every call.
        assertTrue(parameters.containsKey("system_prompt"));
        assertTrue(parameters.containsKey("user_prompt"));
        assertTrue(parameters.containsKey("messages"));
    }

    public void testCreateMLInput_systemPromptMatchesRatingType() {
        Map<String, String> hits = new HashMap<>();
        hits.put("doc1", "content");

        MLInput numeric = transformer.createMLInput("q", new HashMap<>(), hits, "p", LLMJudgmentRatingType.SCORE0_1);
        MLInput binary = transformer.createMLInput("q", new HashMap<>(), hits, "p", LLMJudgmentRatingType.RELEVANT_IRRELEVANT);

        String numericSystem = ((RemoteInferenceInputDataSet) numeric.getInputDataset()).getParameters().get("system_prompt");
        String binarySystem = ((RemoteInferenceInputDataSet) binary.getInputDataset()).getParameters().get("system_prompt");

        assertTrue("numeric system prompt should describe the 0-1 scale", numericSystem.contains("Score 1.0"));
        assertTrue("binary system prompt should describe RELEVANT", binarySystem.contains("RELEVANT"));
    }

    // ============================================
    // Response extraction Tests
    // ============================================

    public void testExtractResponseContent_readsNeutralResponseField() {
        MLOutput output = outputWithDataMap(Map.of("response", "rated text"));
        assertEquals("rated text", transformer.extractResponseContent(output));
    }

    public void testExtractResponseContent_fallsBackToOpenAIChoices() {
        Map<String, Object> message = Map.of("content", "legacy text");
        Map<String, Object> choice = Map.of("message", message);
        MLOutput output = outputWithDataMap(Map.of("choices", List.of(choice)));
        assertEquals("legacy text", transformer.extractResponseContent(output));
    }

    public void testExtractResponseContent_prefersResponseFieldOverChoices() {
        Map<String, Object> message = Map.of("content", "legacy text");
        Map<String, Object> choice = Map.of("message", message);
        MLOutput output = outputWithDataMap(Map.of("response", "neutral text", "choices", List.of(choice)));
        assertEquals("neutral text", transformer.extractResponseContent(output));
    }

    public void testExtractResponseContent_throwsOnUnrecognisedShape() {
        MLOutput output = outputWithDataMap(Map.of("unexpected_key", "value"));
        IllegalStateException ex = expectThrows(IllegalStateException.class, () -> transformer.extractResponseContent(output));
        assertTrue(ex.getMessage().contains("Unrecognised model response shape"));
    }

    public void testExtractResponseContent_throwsOnEmptyChoices() {
        MLOutput output = outputWithDataMap(Map.of("choices", List.of()));
        expectThrows(IllegalStateException.class, () -> transformer.extractResponseContent(output));
    }

    public void testExtractResponseContent_throwsOnWrongOutputType() {
        expectThrows(IllegalArgumentException.class, () -> transformer.extractResponseContent(mock(MLOutput.class)));
    }

    public void testExtractResponseContent_throwsOnEmptyTensors() {
        MLOutput output = ModelTensorOutput.builder().mlModelOutputs(List.of()).build();
        expectThrows(IllegalStateException.class, () -> transformer.extractResponseContent(output));
    }

    // ============================================
    // Chunking Tests
    // ============================================

    public void testCreateMLInputs_splitsIntoMultipleChunksWhenTokenLimitExceeded() {
        Map<String, String> hits = new HashMap<>();
        for (int i = 0; i < 5; i++) {
            hits.put("doc" + i, "content-" + i);
        }
        List<MLInput> inputs = transformer.createMLInputs(
            50,
            "q",
            new HashMap<>(),
            hits,
            "{{searchText}} {{hits}}",
            LLMJudgmentRatingType.SCORE0_1
        );
        assertTrue("expected multiple chunks, got " + inputs.size(), inputs.size() > 1);
    }

    public void testCreateMLInputs_singleChunkWhenUnderTokenLimit() {
        Map<String, String> hits = new HashMap<>();
        hits.put("doc1", "short");
        hits.put("doc2", "short");
        List<MLInput> inputs = transformer.createMLInputs(
            10000,
            "q",
            new HashMap<>(),
            hits,
            "{{searchText}} {{hits}}",
            LLMJudgmentRatingType.SCORE0_1
        );
        assertEquals(1, inputs.size());
    }

    public void testCreateMLInputs_truncatesSingleOversizedHit() {
        StringBuilder big = new StringBuilder();
        for (int i = 0; i < 1000; i++) {
            big.append("very-long-token-text ");
        }
        Map<String, String> hits = new HashMap<>();
        hits.put("doc1", big.toString());

        List<MLInput> inputs = transformer.createMLInputs(
            30,
            "q",
            new HashMap<>(),
            hits,
            "{{searchText}} {{hits}}",
            LLMJudgmentRatingType.SCORE0_1
        );
        assertEquals(1, inputs.size());
        assertNotNull(((RemoteInferenceInputDataSet) inputs.get(0).getInputDataset()).getParameters().get("user_prompt"));
    }

    public void testCreateMLInputs_emptyHitsReturnsEmptyList() {
        List<MLInput> inputs = transformer.createMLInputs(
            1000,
            "q",
            new HashMap<>(),
            new HashMap<>(),
            "{{searchText}} {{hits}}",
            LLMJudgmentRatingType.SCORE0_1
        );
        assertTrue(inputs.isEmpty());
    }

    // ============================================
    // Boundary-aware truncation tests
    // ============================================

    public void testCreateMLInputs_oversizedHitIsMarkedTruncated() {
        int limit = overheadTokens() + 40;
        Map<String, String> hits = new HashMap<>();
        hits.put("doc1", longContent());

        List<MLInput> inputs = transformer.createMLInputs(
            limit,
            "q",
            new HashMap<>(),
            hits,
            "{{searchText}} {{hits}}",
            LLMJudgmentRatingType.SCORE0_1
        );

        assertEquals(1, inputs.size());
        assertTrue("truncated hit should carry the truncation marker", userPromptOf(inputs.get(0)).contains("[content truncated]"));
    }

    public void testCreateMLInputs_oversizedHitStaysWithinTokenLimitAfterWrapping() {
        int limit = overheadTokens() + 40;
        Map<String, String> hits = new HashMap<>();
        hits.put("doc1", longContent());

        List<MLInput> inputs = transformer.createMLInputs(
            limit,
            "q",
            new HashMap<>(),
            hits,
            "{{searchText}} {{hits}}",
            LLMJudgmentRatingType.SCORE0_1
        );

        assertEquals(1, inputs.size());
        int actual = TokenizerUtil.countTokens(messagesOf(inputs.get(0)));
        assertTrue("wrapped payload (" + actual + ") must not exceed the token limit (" + limit + ")", actual <= limit);
    }

    public void testCreateMLInputs_oversizedHitAfterNormalHitIsStillTruncated() {
        int limit = overheadTokens() + 40;
        // LinkedHashMap forces the oversized hit to be processed after a normal one
        Map<String, String> hits = new LinkedHashMap<>();
        hits.put("normal", "short content");
        hits.put("oversized", longContent());

        List<MLInput> inputs = transformer.createMLInputs(
            limit,
            "q",
            new HashMap<>(),
            hits,
            "{{searchText}} {{hits}}",
            LLMJudgmentRatingType.SCORE0_1
        );

        for (MLInput input : inputs) {
            int actual = TokenizerUtil.countTokens(messagesOf(input));
            assertTrue("every chunk must stay within the limit, but one was " + actual, actual <= limit);
        }
        boolean marked = inputs.stream().anyMatch(input -> userPromptOf(input).contains("[content truncated]"));
        assertTrue("oversized hit following a normal hit must still be truncated and marked", marked);
    }

    public void testCreateMLInputs_normalSizedHitIsNotMarked() {
        Map<String, String> hits = new HashMap<>();
        hits.put("doc1", "a perfectly reasonable short description");

        List<MLInput> inputs = transformer.createMLInputs(
            10000,
            "q",
            new HashMap<>(),
            hits,
            "{{searchText}} {{hits}}",
            LLMJudgmentRatingType.SCORE0_1
        );

        assertEquals(1, inputs.size());
        assertFalse("content that fits must not be marked truncated", userPromptOf(inputs.get(0)).contains("[content truncated]"));
    }

    public void testCreateMLInputs_tinyLimitStillProducesMarkedBestEffortChunk() {
        Map<String, String> hits = new HashMap<>();
        hits.put("doc1", longContent());
        // limit far below the fixed wrapping overhead: cannot truly fit, but must still return a
        // single best-effort chunk (marked), without looping forever or throwing
        List<MLInput> inputs = transformer.createMLInputs(
            5,
            "q",
            new HashMap<>(),
            hits,
            "{{searchText}} {{hits}}",
            LLMJudgmentRatingType.SCORE0_1
        );

        assertEquals(1, inputs.size());
        assertTrue("best-effort truncated chunk should still be marked", userPromptOf(inputs.get(0)).contains("[content truncated]"));
    }

    // fixed wrapping overhead (system prompt + JSON shell + template) measured on a tiny hit
    private int overheadTokens() {
        MLInput tiny = transformer.createMLInput(
            "q",
            new HashMap<>(),
            Map.of("d", "x"),
            "{{searchText}} {{hits}}",
            LLMJudgmentRatingType.SCORE0_1
        );
        return TokenizerUtil.countTokens(messagesOf(tiny));
    }

    private static String longContent() {
        StringBuilder big = new StringBuilder();
        for (int i = 0; i < 1000; i++) {
            big.append("very long descriptive token text ");
        }
        return big.toString();
    }

    private static String messagesOf(MLInput input) {
        return ((RemoteInferenceInputDataSet) input.getInputDataset()).getParameters().get("messages");
    }

    private static String userPromptOf(MLInput input) {
        return ((RemoteInferenceInputDataSet) input.getInputDataset()).getParameters().get("user_prompt");
    }

    private static MLOutput outputWithDataMap(Map<String, ?> dataMap) {
        ModelTensor tensor = ModelTensor.builder().dataAsMap(dataMap).build();
        ModelTensors tensors = ModelTensors.builder().mlModelTensors(List.of(tensor)).build();
        return ModelTensorOutput.builder().mlModelOutputs(List.of(tensors)).build();
    }
}
