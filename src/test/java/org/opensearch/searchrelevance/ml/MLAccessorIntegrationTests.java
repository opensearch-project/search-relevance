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
import org.opensearch.searchrelevance.model.LLMJudgmentRatingType;
import org.opensearch.test.OpenSearchTestCase;

/**
 * Integration tests for MLAccessor covering:
 * - First-attempt success
 * - Failures are reported after a single call (no client-side retry loop); transient-error retries
 *   are delegated to the ml-commons connector
 */
public class MLAccessorIntegrationTests extends OpenSearchTestCase {

    /**
     * Test that MLAccessor works correctly on first attempt when model supports response_format.
     * This simulates GPT-4o model with structured output support.
     */
    public void testFirstAttemptSuccess_WhenModelSupportsResponseFormat() throws Exception {
        MachineLearningNodeClient mlClient = mock(MachineLearningNodeClient.class);
        MLAccessor mlAccessor = new MLAccessor(mlClient);

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
            ActionListener.wrap(chunkResult -> {
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
     * On failure, MLAccessor reports the chunk as failed after exactly one call. It does not retry
     * without response_format and does not enter a retry loop - transient-error retries (throttling,
     * 5xx, timeouts) are the ml-commons connector's responsibility.
     */
    public void testFailure_reportedAfterSingleCall_noRetry() throws Exception {
        MachineLearningNodeClient mlClient = mock(MachineLearningNodeClient.class);
        MLAccessor mlAccessor = new MLAccessor(mlClient);

        AtomicInteger attemptCount = new AtomicInteger(0);
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<ChunkResult> result = new AtomicReference<>();

        doAnswer(invocation -> {
            ActionListener<MLOutput> listener = invocation.getArgument(2);
            attemptCount.incrementAndGet();
            listener.onFailure(new RuntimeException("boom"));
            return null;
        }).when(mlClient).predict(any(), any(MLInput.class), any());

        mlAccessor.predict(
            "gpt-4o-mini",
            4000,
            "test query",
            new HashMap<>(),
            Map.of("doc1", "test content"),
            "Test prompt",
            LLMJudgmentRatingType.SCORE0_1,
            ActionListener.wrap(chunkResult -> {
                result.set(chunkResult);
                latch.countDown();
            }, e -> latch.countDown())
        );

        assertTrue("Should complete", latch.await(10, TimeUnit.SECONDS));
        assertEquals("Exactly one call, no retry loop", 1, attemptCount.get());

        ChunkResult chunkResult = result.get();
        assertNotNull(chunkResult);
        assertEquals(0, chunkResult.getSuccessfulChunksCount());
        assertEquals(1, chunkResult.getFailedChunksCount());
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
