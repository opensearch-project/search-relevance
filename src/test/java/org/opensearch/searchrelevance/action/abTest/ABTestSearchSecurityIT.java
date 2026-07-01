/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */
package org.opensearch.searchrelevance.action.abTest;

import static org.opensearch.searchrelevance.common.PluginConstants.AB_TESTS_URL;
import static org.opensearch.searchrelevance.common.PluginConstants.SEARCH_CONFIGURATIONS_URL;

import java.util.Map;

import org.apache.hc.core5.http.HttpHeaders;
import org.apache.hc.core5.http.message.BasicHeader;
import org.opensearch.client.Response;
import org.opensearch.client.ResponseException;
import org.opensearch.rest.RestRequest;
import org.opensearch.searchrelevance.BaseSearchRelevanceIT;
import org.opensearch.test.OpenSearchIntegTestCase;

import com.carrotsearch.randomizedtesting.annotations.ThreadLeakScope;
import com.google.common.collect.ImmutableList;

import lombok.SneakyThrows;

/**
 * Security-enabled integration test for AB Test Search API.
 * Verifies that FGAC is enforced when searching target indices — a user without
 * index-level read permissions must not be able to retrieve results via the AB test search endpoint.
 *
 * Run with: ./gradlew integTest -Dsecurity=true -Dhttps=true
 */
@ThreadLeakScope(ThreadLeakScope.Scope.NONE)
@OpenSearchIntegTestCase.ClusterScope(scope = OpenSearchIntegTestCase.Scope.SUITE)
public class ABTestSearchSecurityIT extends BaseSearchRelevanceIT {

    private static final String TEST_INDEX = "ab-test-security-index";
    private static final String TEST_ID = "security-fgac-test";
    private static final String RESTRICTED_USER = "restricted_user";
    private static final String RESTRICTED_PASSWORD = "RestrictedPass@12345!";
    private static final String RESTRICTED_ROLE = "no_target_index_access";

    @Override
    public boolean isUpdateClusterSettings() {
        return isHttps();
    }

    /**
     * Tests that a user without read access to the target index cannot retrieve
     * search results through the AB test search API (FGAC enforcement).
     */
    @SneakyThrows
    public void testABTestSearchDeniedForRestrictedUser() {
        if (!isHttps()) {
            return;
        }

        // Step 1: Create test index as admin
        makeRequest(
            client(),
            RestRequest.Method.PUT.name(),
            TEST_INDEX,
            null,
            toHttpEntity("{\"settings\":{\"number_of_shards\":1,\"number_of_replicas\":0}}"),
            ImmutableList.of(new BasicHeader(HttpHeaders.USER_AGENT, DEFAULT_USER_AGENT))
        );

        // Index a document
        makeRequest(
            client(),
            RestRequest.Method.POST.name(),
            TEST_INDEX + "/_doc/1?refresh=true",
            null,
            toHttpEntity("{\"title\":\"test document\"}"),
            ImmutableList.of(new BasicHeader(HttpHeaders.USER_AGENT, DEFAULT_USER_AGENT))
        );

        // Step 2: Create search configuration A
        String configBodyA = String.format(
            "{\"name\":\"security-test-config-a\",\"index\":\"%s\",\"query\":\"{\\\"query\\\":{\\\"match\\\":{\\\"title\\\":\\\"%%SearchText%%\\\"}}}\"}",
            TEST_INDEX
        );
        Response configResponseA = makeRequest(
            client(),
            RestRequest.Method.PUT.name(),
            SEARCH_CONFIGURATIONS_URL,
            null,
            toHttpEntity(configBodyA),
            ImmutableList.of(new BasicHeader(HttpHeaders.USER_AGENT, DEFAULT_USER_AGENT))
        );
        Map<String, Object> configResultA = entityAsMap(configResponseA);
        String configIdA = (String) configResultA.get("search_configuration_id");

        // Step 2b: Create search configuration B
        String configBodyB = String.format(
            "{\"name\":\"security-test-config-b\",\"index\":\"%s\",\"query\":\"{\\\"query\\\":{\\\"match_all\\\":{}}}\"}",
            TEST_INDEX
        );
        Response configResponseB = makeRequest(
            client(),
            RestRequest.Method.PUT.name(),
            SEARCH_CONFIGURATIONS_URL,
            null,
            toHttpEntity(configBodyB),
            ImmutableList.of(new BasicHeader(HttpHeaders.USER_AGENT, DEFAULT_USER_AGENT))
        );
        Map<String, Object> configResultB = entityAsMap(configResponseB);
        String configIdB = (String) configResultB.get("search_configuration_id");

        // Step 3: Create AB test with two different configurations
        String abTestBody = String.format("{\"search_configuration_a\":\"%s\",\"search_configuration_b\":\"%s\"}", configIdA, configIdB);
        makeRequest(
            client(),
            RestRequest.Method.PUT.name(),
            AB_TESTS_URL + "/" + TEST_ID,
            null,
            toHttpEntity(abTestBody),
            ImmutableList.of(new BasicHeader(HttpHeaders.USER_AGENT, DEFAULT_USER_AGENT))
        );

        // Step 4: Create restricted role (plugin access but no target index access)
        makeRequest(
            client(),
            RestRequest.Method.PUT.name(),
            "_plugins/_security/api/roles/" + RESTRICTED_ROLE,
            null,
            toHttpEntity(
                "{\"cluster_permissions\":[\"cluster:admin/opensearch/search_relevance/*\"],"
                    + "\"index_permissions\":[{\"index_patterns\":[\"no-access-index*\"],\"allowed_actions\":[\"indices:data/read*\"]}]}"
            ),
            ImmutableList.of(new BasicHeader(HttpHeaders.USER_AGENT, DEFAULT_USER_AGENT))
        );

        // Step 5: Create restricted user
        makeRequest(
            client(),
            RestRequest.Method.PUT.name(),
            "_plugins/_security/api/internalusers/" + RESTRICTED_USER,
            null,
            toHttpEntity("{\"password\":\"" + RESTRICTED_PASSWORD + "\",\"backend_roles\":[]}"),
            ImmutableList.of(new BasicHeader(HttpHeaders.USER_AGENT, DEFAULT_USER_AGENT))
        );

        // Step 6: Map role to user
        makeRequest(
            client(),
            RestRequest.Method.PUT.name(),
            "_plugins/_security/api/rolesmapping/" + RESTRICTED_ROLE,
            null,
            toHttpEntity("{\"users\":[\"" + RESTRICTED_USER + "\"]}"),
            ImmutableList.of(new BasicHeader(HttpHeaders.USER_AGENT, DEFAULT_USER_AGENT))
        );

        // Step 7: Call AB test search as restricted user — should fail with 403
        String searchBody = "{\"query_params\":{\"SearchText\":\"test\"}}";
        try {
            makeRequest(
                client(),
                RestRequest.Method.POST.name(),
                AB_TESTS_URL + "/" + TEST_ID + "/_search",
                null,
                toHttpEntity(searchBody),
                ImmutableList.of(
                    new BasicHeader(HttpHeaders.USER_AGENT, DEFAULT_USER_AGENT),
                    new BasicHeader(HttpHeaders.AUTHORIZATION, basicAuthHeaderValue(RESTRICTED_USER, RESTRICTED_PASSWORD))
                )
            );
            fail("Expected security exception for restricted user searching target index");
        } catch (ResponseException e) {
            int statusCode = e.getResponse().getStatusLine().getStatusCode();
            assertEquals("Expected 403 Forbidden for restricted user", 403, statusCode);
        }
    }

    private String basicAuthHeaderValue(String username, String password) {
        String credentials = username + ":" + password;
        return "Basic " + java.util.Base64.getEncoder().encodeToString(credentials.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }
}
