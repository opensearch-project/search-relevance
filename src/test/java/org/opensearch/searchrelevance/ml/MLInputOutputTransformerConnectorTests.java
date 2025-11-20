/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */
package org.opensearch.searchrelevance.ml;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;

import org.mockito.stubbing.Answer;
import org.opensearch.ml.common.output.MLOutput;
import org.opensearch.ml.common.output.model.ModelTensor;
import org.opensearch.ml.common.output.model.ModelTensorOutput;
import org.opensearch.ml.common.output.model.ModelTensors;
import org.opensearch.searchrelevance.ml.connector.ClaudeConnector;
import org.opensearch.searchrelevance.ml.connector.CohereConnector;
import org.opensearch.searchrelevance.ml.connector.DeepSeekConnector;
import org.opensearch.searchrelevance.ml.connector.LLMConnector;
import org.opensearch.searchrelevance.ml.connector.OpenAIConnector;
import org.opensearch.searchrelevance.model.LLMJudgmentRatingType;
import org.opensearch.test.OpenSearchTestCase;

public class MLInputOutputTransformerConnectorTests extends OpenSearchTestCase {

    public void testDefaultConstructorUsesOpenAI() {
        MLInputOutputTransformer transformer = new MLInputOutputTransformer();
        assertNotNull(transformer);
        // Default behavior should work (uses OpenAI connector internally)
    }

    public void testConnectorConstructor() {
        LLMConnector claudeConnector = new ClaudeConnector();
        MLInputOutputTransformer transformer = new MLInputOutputTransformer(claudeConnector);
        assertNotNull(transformer);
    }

    public void testExtractResponseContentWithDifferentConnectors() {
        // Test OpenAI response extraction
        testResponseExtraction(new OpenAIConnector(), createOpenAIResponse(), "OpenAI response");

        // Test Claude response extraction
        testResponseExtraction(new ClaudeConnector(), createClaudeResponse(), "Claude response");

        // Test Cohere response extraction
        testResponseExtraction(new CohereConnector(), createCohereResponse(), "Cohere response");

        // Test DeepSeek response extraction (uses OpenAI format but returns DEEPSEEK type)
        testResponseExtraction(new DeepSeekConnector(), createDeepSeekResponse(), "DeepSeek response");
    }

    private void testResponseExtraction(LLMConnector connector, MLOutput mlOutput, String expectedResponse) {
        MLInputOutputTransformer transformer = new MLInputOutputTransformer(connector);
        String result = transformer.extractResponseContent(mlOutput);
        assertEquals(expectedResponse, result);
    }

    private MLOutput createOpenAIResponse() {
        return createMockMLOutput(Map.of("choices", List.of(Map.of("message", Map.of("content", "OpenAI response")))));
    }

    private MLOutput createClaudeResponse() {
        return createMockMLOutput(Map.of("content", List.of(Map.of("text", "Claude response"))));
    }

    private MLOutput createCohereResponse() {
        return createMockMLOutput(Map.of("text", "Cohere response"));
    }

    private MLOutput createDeepSeekResponse() {
        return createMockMLOutput(Map.of("choices", List.of(Map.of("message", Map.of("content", "DeepSeek response")))));
    }

    @SuppressWarnings("unchecked")
    private MLOutput createMockMLOutput(Map<String, Object> dataMap) {
        ModelTensor tensor = mock(ModelTensor.class);
        when(tensor.getDataAsMap()).thenAnswer((Answer<Map<String, ?>>) invocation -> dataMap);

        ModelTensors modelTensors = mock(ModelTensors.class);
        when(modelTensors.getMlModelTensors()).thenReturn(List.of(tensor));

        ModelTensorOutput output = mock(ModelTensorOutput.class);
        when(output.getMlModelOutputs()).thenReturn(List.of(modelTensors));

        return output;
    }

    public void testCreateMLInputWithDifferentConnectors() {
        String searchText = "test query";
        Map<String, String> referenceData = Map.of("ref", "reference");
        Map<String, String> hits = Map.of("hit1", "result1");
        String promptTemplate = "{{searchText}} {{hits}}";
        LLMJudgmentRatingType ratingType = LLMJudgmentRatingType.RELEVANT_IRRELEVANT;

        // Test with different connectors
        testMLInputCreation(new OpenAIConnector(), searchText, referenceData, hits, promptTemplate, ratingType);
        testMLInputCreation(new ClaudeConnector(), searchText, referenceData, hits, promptTemplate, ratingType);
        testMLInputCreation(new CohereConnector(), searchText, referenceData, hits, promptTemplate, ratingType);
        testMLInputCreation(new DeepSeekConnector(), searchText, referenceData, hits, promptTemplate, ratingType);
    }

    private void testMLInputCreation(
        LLMConnector connector,
        String searchText,
        Map<String, String> referenceData,
        Map<String, String> hits,
        String promptTemplate,
        LLMJudgmentRatingType ratingType
    ) {
        MLInputOutputTransformer transformer = new MLInputOutputTransformer(connector);

        // This should not throw an exception
        var mlInput = transformer.createMLInput(searchText, referenceData, hits, promptTemplate, ratingType);
        assertNotNull(mlInput);
    }
}
