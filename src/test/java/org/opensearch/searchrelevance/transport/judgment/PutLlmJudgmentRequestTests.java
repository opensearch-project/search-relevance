/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */
package org.opensearch.searchrelevance.transport.judgment;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import java.util.List;

import org.opensearch.searchrelevance.ml.connector.ConnectorType;
import org.opensearch.searchrelevance.model.JudgmentType;
import org.opensearch.searchrelevance.model.LLMJudgmentRatingType;
import org.opensearch.test.OpenSearchTestCase;

public class PutLlmJudgmentRequestTests extends OpenSearchTestCase {

    public void testConnectorTypeDefaultsToOpenAI() {
        PutLlmJudgmentRequest request = new PutLlmJudgmentRequest(
            JudgmentType.LLM_JUDGMENT,
            "test-judgment",
            "Test description",
            "test-model-id",
            "test-queryset-id",
            List.of("test-config"),
            10,
            1000,
            List.of("field1"),
            false,
            "{{searchText}} {{hits}}",
            LLMJudgmentRatingType.SCORE0_1,
            false,
            null, // connectorType is null
            1000L
        );

        assertEquals(ConnectorType.OPENAI, request.getConnectorType());
    }

    public void testConnectorTypeIsPreserved() {
        PutLlmJudgmentRequest request = new PutLlmJudgmentRequest(
            JudgmentType.LLM_JUDGMENT,
            "test-judgment",
            "Test description",
            "test-model-id",
            "test-queryset-id",
            List.of("test-config"),
            10,
            1000,
            List.of("field1"),
            false,
            "{{searchText}} {{hits}}",
            LLMJudgmentRatingType.SCORE0_1,
            false,
            ConnectorType.CLAUDE,
            1000L
        );

        assertEquals(ConnectorType.CLAUDE, request.getConnectorType());
    }

    public void testAllConnectorTypes() {
        for (ConnectorType type : ConnectorType.values()) {
            PutLlmJudgmentRequest request = new PutLlmJudgmentRequest(
                JudgmentType.LLM_JUDGMENT,
                "test-judgment",
                "Test description",
                "test-model-id",
                "test-queryset-id",
                List.of("test-config"),
                10,
                1000,
                List.of("field1"),
                false,
                "{{searchText}} {{hits}}",
                LLMJudgmentRatingType.SCORE0_1,
                false,
                type,
                1000L
            );

            assertEquals(type, request.getConnectorType());
            assertNotNull(request.getConnectorType());
        }
    }

    public void testRateLimitDefaultsToZero() {
        PutLlmJudgmentRequest request = new PutLlmJudgmentRequest(
            JudgmentType.LLM_JUDGMENT,
            "test-judgment",
            "Test description",
            "test-model-id",
            "test-queryset-id",
            List.of("test-config"),
            10,
            1000,
            List.of("field1"),
            false,
            "{{searchText}} {{hits}}",
            LLMJudgmentRatingType.SCORE0_1,
            false,
            ConnectorType.OPENAI,
            0L // explicitly set rateLimit to 0
        );

        assertEquals(0L, request.getRateLimit());
    }

    public void testNegativeRateLimitBecomesZero() {
        PutLlmJudgmentRequest request = new PutLlmJudgmentRequest(
            JudgmentType.LLM_JUDGMENT,
            "test-judgment",
            "Test description",
            "test-model-id",
            "test-queryset-id",
            List.of("test-config"),
            10,
            1000,
            List.of("field1"),
            false,
            "{{searchText}} {{hits}}",
            LLMJudgmentRatingType.SCORE0_1,
            false,
            ConnectorType.OPENAI,
            -100L // negative rateLimit
        );

        assertEquals(0L, request.getRateLimit()); // should be clamped to 0
    }
}
