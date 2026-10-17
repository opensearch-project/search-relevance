/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */
package org.opensearch.searchrelevance.action;

import static org.opensearch.searchrelevance.common.PluginConstants.EXPERIMENTS_URI;
import static org.opensearch.searchrelevance.common.PluginConstants.JUDGMENTS_URL;
import static org.opensearch.searchrelevance.common.PluginConstants.SEARCH_CONFIGURATIONS_URL;

import org.apache.hc.core5.http.HttpHeaders;
import org.apache.hc.core5.http.message.BasicHeader;
import org.opensearch.client.ResponseException;
import org.opensearch.rest.RestRequest;
import org.opensearch.searchrelevance.BaseSearchRelevanceIT;
import org.opensearch.test.OpenSearchIntegTestCase;

import com.carrotsearch.randomizedtesting.annotations.ThreadLeakScope;
import com.google.common.collect.ImmutableList;

import lombok.SneakyThrows;

/**
 * Negative integration tests for referential integrity validation.
 * These tests verify that APIs properly reject requests containing
 * non-existent (fake) entity IDs. They are lightweight and require
 * no data ingestion or index setup.
 */
@ThreadLeakScope(ThreadLeakScope.Scope.NONE)
@OpenSearchIntegTestCase.ClusterScope(scope = OpenSearchIntegTestCase.Scope.SUITE)
public class ReferenceValidationIT extends BaseSearchRelevanceIT {

    // --- Experiment API negative tests ---

    @SneakyThrows
    public void testExperimentWithFakeQuerySetId_thenFails() {
        String requestBody = "{"
            + "\"name\": \"test-experiment\","
            + "\"type\": \"POINTWISE_EVALUATION\","
            + "\"querySetId\": \"non-existent-queryset-id\","
            + "\"searchConfigurationList\": [\"fake-config-id\"],"
            + "\"size\": 10"
            + "}";

        ResponseException ex = expectThrows(
            ResponseException.class,
            () -> makeRequest(
                client(),
                RestRequest.Method.PUT.name(),
                EXPERIMENTS_URI,
                null,
                toHttpEntity(requestBody),
                ImmutableList.of(new BasicHeader(HttpHeaders.USER_AGENT, DEFAULT_USER_AGENT))
            )
        );
        assertTrue(ex.getMessage().contains("does not exist"));
    }

    @SneakyThrows
    public void testExperimentWithFakeSearchConfigId_thenFails() {
        String requestBody = "{"
            + "\"name\": \"test-experiment\","
            + "\"type\": \"POINTWISE_EVALUATION\","
            + "\"querySetId\": \"non-existent-queryset-id\","
            + "\"searchConfigurationList\": [\"non-existent-config-id\"],"
            + "\"size\": 10"
            + "}";

        ResponseException ex = expectThrows(
            ResponseException.class,
            () -> makeRequest(
                client(),
                RestRequest.Method.PUT.name(),
                EXPERIMENTS_URI,
                null,
                toHttpEntity(requestBody),
                ImmutableList.of(new BasicHeader(HttpHeaders.USER_AGENT, DEFAULT_USER_AGENT))
            )
        );
        assertTrue(ex.getMessage().contains("does not exist"));
    }

    @SneakyThrows
    public void testExperimentWithFakeJudgmentId_thenFails() {
        String requestBody = "{"
            + "\"name\": \"test-experiment\","
            + "\"type\": \"POINTWISE_EVALUATION\","
            + "\"querySetId\": \"non-existent-queryset-id\","
            + "\"searchConfigurationList\": [\"non-existent-config\"],"
            + "\"judgmentList\": [\"non-existent-judgment-id\"],"
            + "\"size\": 10"
            + "}";

        ResponseException ex = expectThrows(
            ResponseException.class,
            () -> makeRequest(
                client(),
                RestRequest.Method.PUT.name(),
                EXPERIMENTS_URI,
                null,
                toHttpEntity(requestBody),
                ImmutableList.of(new BasicHeader(HttpHeaders.USER_AGENT, DEFAULT_USER_AGENT))
            )
        );
        assertTrue(ex.getMessage().contains("does not exist"));
    }

    // --- Search Configuration API negative tests ---

    @SneakyThrows
    public void testSearchConfigWithFakeIndex_thenFails() {
        String requestBody = "{"
            + "\"name\": \"test-search-config\","
            + "\"index\": \"non-existent-index\","
            + "\"query\": \"{\\\"query\\\": {\\\"match_all\\\": {}}}\""
            + "}";

        ResponseException ex = expectThrows(
            ResponseException.class,
            () -> makeRequest(
                client(),
                RestRequest.Method.PUT.name(),
                SEARCH_CONFIGURATIONS_URL,
                null,
                toHttpEntity(requestBody),
                ImmutableList.of(new BasicHeader(HttpHeaders.USER_AGENT, DEFAULT_USER_AGENT))
            )
        );
        assertTrue(ex.getMessage().contains("Index [non-existent-index] does not exist"));
    }

    // --- Judgment API negative tests ---

    @SneakyThrows
    public void testJudgmentWithFakeQuerySetId_thenFails() {
        String requestBody = "{"
            + "\"name\": \"test-judgment\","
            + "\"type\": \"LLM_JUDGMENT\","
            + "\"modelId\": \"fake-model-id\","
            + "\"querySetId\": \"non-existent-queryset-id\","
            + "\"searchConfigurationList\": [\"fake-config-id\"],"
            + "\"size\": 10"
            + "}";

        ResponseException ex = expectThrows(
            ResponseException.class,
            () -> makeRequest(
                client(),
                RestRequest.Method.PUT.name(),
                JUDGMENTS_URL,
                null,
                toHttpEntity(requestBody),
                ImmutableList.of(new BasicHeader(HttpHeaders.USER_AGENT, DEFAULT_USER_AGENT))
            )
        );
        assertTrue(ex.getMessage().contains("does not exist"));
    }

    @SneakyThrows
    public void testJudgmentWithFakeSearchConfigId_thenFails() {
        String requestBody = "{"
            + "\"name\": \"test-judgment\","
            + "\"type\": \"LLM_JUDGMENT\","
            + "\"modelId\": \"fake-model-id\","
            + "\"querySetId\": \"non-existent-queryset-id\","
            + "\"searchConfigurationList\": [\"non-existent-config-id\"],"
            + "\"size\": 10"
            + "}";

        ResponseException ex = expectThrows(
            ResponseException.class,
            () -> makeRequest(
                client(),
                RestRequest.Method.PUT.name(),
                JUDGMENTS_URL,
                null,
                toHttpEntity(requestBody),
                ImmutableList.of(new BasicHeader(HttpHeaders.USER_AGENT, DEFAULT_USER_AGENT))
            )
        );
        assertTrue(ex.getMessage().contains("does not exist"));
    }
}
