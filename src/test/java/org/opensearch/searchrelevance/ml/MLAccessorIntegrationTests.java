/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */
package org.opensearch.searchrelevance.ml;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.opensearch.core.action.ActionListener;
import org.opensearch.ml.client.MachineLearningNodeClient;
import org.opensearch.ml.common.dataset.remote.RemoteInferenceInputDataSet;
import org.opensearch.ml.common.input.MLInput;
import org.opensearch.ml.common.output.MLOutput;
import org.opensearch.ml.common.output.model.ModelTensor;
import org.opensearch.ml.common.output.model.ModelTensorOutput;
import org.opensearch.ml.common.output.model.ModelTensors;
import org.opensearch.searchrelevance.ml.connector.ConnectorType;
import org.opensearch.searchrelevance.model.LLMJudgmentRatingType;
import org.opensearch.test.OpenSearchTestCase;

/**
 * Integration tests for MLAccessor focusing on:
 * - First attempt success with response_format (GPT-4o scenario)
 * - Response processing with structured outputs
 *
 * Note: Tests for retry logic and fallback behavior (GPT-3.5 compatibility) are documented
 * in TESTING_GPT35_FALLBACK.md as manual tests because they require delayed retries which
 * create thread leaks in the OpenSearch test framework. The retry mechanism uses
 * CompletableFuture.delayedExecutor which creates daemon threads that cannot be properly
 * cleaned up within test execution.
 *
 * Covered by unit tests:
 * - MLInputOutputTransformerTests: Verifies response_format parameter is correctly included/excluded
 * - RatingOutputProcessorTests: Verifies both structured and unstructured response parsing
 */
public class MLAccessorIntegrationTests extends OpenSearchTestCase {

    private MLAccessor mlAccessor;

    @Override
    public void tearDown() throws Exception {
        if (mlAccessor != null) {
            mlAccessor.shutdown();
        }
        super.tearDown();
    }

    /**
     * Note: GPT-3.5 fallback testing is documented in TESTING_GPT35_FALLBACK.md as "Scenario 2"
     * This scenario requires triggering scheduleRetry which creates CompletableFuture threads that leak.
     * Coverage is provided by:
     * - Unit tests: MLInputOutputTransformerTests verifies response_format parameter handling
     * - Manual tests: Real OpenAI GPT-3.5 API integration testing
     */

    /**
     * Test that MLAccessor works correctly on first attempt when model supports response_format.
     * This simulates GPT-4o model with structured output support.
     */
    public void testFirstAttemptSuccess_WhenModelSupportsResponseFormat() throws Exception {
        MachineLearningNodeClient mlClient = mock(MachineLearningNodeClient.class);
        mlAccessor = new MLAccessor(mlClient);

        AtomicInteger attemptCount = new AtomicInteger(0);
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<ChunkResult> result = new AtomicReference<>();

        // Mock ML client - succeeds on first attempt with response_format
        doAnswer(invocation -> {
            MLInput mlInput = invocation.getArgument(1);
            ActionListener<MLOutput> listener = invocation.getArgument(2);

            attemptCount.incrementAndGet();

            RemoteInferenceInputDataSet dataset = (RemoteInferenceInputDataSet) mlInput.getInputDataset();
            Map<String, String> params = dataset.getParameters();

            // Verify response_format is present
            assertTrue("Should have response_format", params.containsKey("response_format"));

            // Return structured output
            String structuredResponse = "{\"ratings\":[{\"id\":\"doc1\",\"rating_score\":0.9}]}";
            MLOutput mockOutput = createMockMLOutput(structuredResponse);
            listener.onResponse(mockOutput);

            return null;
        }).when(mlClient).predict(any(), any(MLInput.class), any());

        // Execute prediction
        Map<String, String> hits = Map.of("doc1", "test content");
        mlAccessor.predict(
            "gpt-4o-mini",
            4000,
            "test query",
            new HashMap<>(),
            hits,
            "Test prompt",
            LLMJudgmentRatingType.SCORE0_1,
            ConnectorType.OPENAI,
            1000L,
            ActionListener.wrap((ChunkResult chunkResult) -> {
                result.set(chunkResult);
                latch.countDown();
            }, e -> latch.countDown())
        );

        assertTrue("Should complete", latch.await(10, TimeUnit.SECONDS));

        // Verify only one attempt was made
        assertEquals("Should only need one attempt", 1, attemptCount.get());

        // Verify successful result
        ChunkResult chunkResult = result.get();
        assertNotNull(chunkResult);
        assertEquals(1, chunkResult.getSuccessfulChunksCount());
        assertEquals(0, chunkResult.getFailedChunksCount());
    }

    /**
     * Note: Binary rating (RELEVANT/IRRELEVANT) fallback testing is documented in
     * TESTING_GPT35_FALLBACK.md as "Scenario 3". This test would trigger scheduleRetry
     * creating thread leaks. Coverage is provided by:
     * - Unit tests: MLInputOutputTransformerTests.testCreateMLInput_BinaryRatingWithoutResponseFormat
     * - Unit tests: RatingOutputProcessorTests verifies RELEVANT→1.0, IRRELEVANT→0.0 conversion
     * - Manual tests: Real OpenAI API integration testing
     */

    /**
     * Note: Testing retry exhaustion (all attempts fail) is documented in TESTING_GPT35_FALLBACK.md
     * as a manual test scenario because it requires delayed retries which create thread leaks in tests.
     * The retry logic with exponential backoff uses CompletableFuture.delayedExecutor which creates
     * daemon threads that cannot be properly cleaned up in the OpenSearch test framework.
     */

    /**
     * Test that non-OpenAI connectors (Claude, Cohere, DeepSeek) don't trigger response_format retry logic.
     * This verifies the fix for the issue where all connector types were attempting response_format fallback.
     */
    public void testNonOpenAIConnectors_DoNotUseResponseFormatRetry() throws Exception {
        // Test each non-OpenAI connector type
        ConnectorType[] nonOpenAIConnectors = { ConnectorType.CLAUDE, ConnectorType.COHERE, ConnectorType.DEEPSEEK };

        for (ConnectorType connectorType : nonOpenAIConnectors) {
            MachineLearningNodeClient mlClient = mock(MachineLearningNodeClient.class);
            mlAccessor = new MLAccessor(mlClient);

            AtomicInteger attemptCount = new AtomicInteger(0);
            CountDownLatch latch = new CountDownLatch(1);
            AtomicReference<Exception> error = new AtomicReference<>();

            // Mock ML client to always fail - this should NOT trigger response_format retry for non-OpenAI
            doAnswer(invocation -> {
                ActionListener<MLOutput> listener = invocation.getArgument(2);
                attemptCount.incrementAndGet();

                // Fail immediately - non-OpenAI connectors should not retry with response_format removal
                listener.onFailure(new RuntimeException("Simulated failure for " + connectorType));
                return null;
            }).when(mlClient).predict(any(), any(MLInput.class), any());

            // Execute prediction
            Map<String, String> hits = Map.of("doc1", "test content");
            mlAccessor.predict(
                "test-model",
                4000,
                "test query",
                new HashMap<>(),
                hits,
                "Test prompt",
                LLMJudgmentRatingType.SCORE0_1,
                connectorType,
                1000L,
                ActionListener.wrap((ChunkResult chunkResult) -> {
                    // Should not succeed
                    latch.countDown();
                }, e -> {
                    error.set(e);
                    latch.countDown();
                })
            );

            assertTrue("Should complete for " + connectorType, latch.await(15, TimeUnit.SECONDS));

            // Verify failure occurred (as expected)
            assertNotNull("Should have failed for " + connectorType, error.get());

            // Key assertion: For non-OpenAI connectors, should attempt regular retries but not response_format retry
            // The fix ensures that only OpenAI connectors attempt the response_format fallback
            assertTrue("Should attempt at least once for " + connectorType, attemptCount.get() >= 1);
            // With regular retry logic (3 attempts), we expect exactly 4 attempts (1 initial + 3 retries)
            assertEquals("Should attempt exactly 4 times (1 initial + 3 retries) for " + connectorType, 4, attemptCount.get());

            mlAccessor.shutdown();
        }
    }

    // ============================================
    // Helper Methods
    // ============================================

    /**
     * Creates a mock MLOutput with the given JSON response.
     */
    private MLOutput createMockMLOutput(String jsonResponse) {
        Map<String, Object> dataMap = new HashMap<>();
        List<Map<String, Object>> choices = new ArrayList<>();
        Map<String, Object> choice = new HashMap<>();
        Map<String, Object> message = new HashMap<>();
        message.put("content", jsonResponse);
        choice.put("message", message);
        choices.add(choice);
        dataMap.put("choices", choices);

        ModelTensor tensor = ModelTensor.builder().dataAsMap(dataMap).build();
        ModelTensors tensors = ModelTensors.builder().mlModelTensors(List.of(tensor)).build();
        return ModelTensorOutput.builder().mlModelOutputs(List.of(tensors)).build();
    }
}
