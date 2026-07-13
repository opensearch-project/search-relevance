/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */
package org.opensearch.searchrelevance.action.abTest;

import static org.opensearch.searchrelevance.common.PluginConstants.AB_TESTS_URL;

import java.util.List;
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
 * Integration tests for the AB Test Search API.
 * Tests end-to-end flow: create test → search → verify response.
 */
@ThreadLeakScope(ThreadLeakScope.Scope.NONE)
@OpenSearchIntegTestCase.ClusterScope(scope = OpenSearchIntegTestCase.Scope.SUITE)
public class ABTestSearchIT extends BaseSearchRelevanceIT {

    private static final String TEST_INDEX = "ab-test-search-it-index";

    /** Happy path: enabled test returns interleaved results with UUID attribution per hit */
    @SneakyThrows
    public void testSearchWithEnabledTest() {
        // Setup
        createTestIndex(TEST_INDEX);
        indexTestDocuments();

        String configIdA = createSearchConfiguration("it-config-a", TEST_INDEX, "{\"query\":{\"match\":{\"title\":\"%SearchText%\"}}}");
        String configIdB = createSearchConfiguration("it-config-b", TEST_INDEX, "{\"query\":{\"match_all\":{}}}");
        createABTest("search-it-enabled", configIdA, configIdB);

        // Execute search
        Response response = makeRequest(
            client(),
            RestRequest.Method.POST.name(),
            AB_TESTS_URL + "/search-it-enabled/_search",
            null,
            toHttpEntity("{\"query_params\":{\"SearchText\":\"laptop\"}}"),
            ImmutableList.of(new BasicHeader(HttpHeaders.USER_AGENT, DEFAULT_USER_AGENT))
        );

        // Verify
        Map<String, Object> result = entityAsMap(response);
        assertEquals("search-it-enabled", result.get("test_id"));

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> hits = (List<Map<String, Object>>) result.get("hits");
        assertFalse("Should have interleaved hits", hits.isEmpty());
        for (Map<String, Object> hit : hits) {
            assertNotNull("Each hit should have _search_configuration_id", hit.get("_search_configuration_id"));
        }
    }

    /** Disabled test executes only config A without interleaving */
    @SneakyThrows
    public void testSearchWithDisabledTest() {
        // Setup
        createTestIndex(TEST_INDEX);
        indexTestDocuments();

        String configIdA = createSearchConfiguration(
            "it-config-disabled-a",
            TEST_INDEX,
            "{\"query\":{\"match\":{\"title\":\"%SearchText%\"}}}"
        );
        String configIdB = createSearchConfiguration("it-config-disabled-b", TEST_INDEX, "{\"query\":{\"match_all\":{}}}");
        String testId = "search-it-disabled";
        createABTest(testId, configIdA, configIdB);

        // Disable the test
        makeRequest(
            client(),
            RestRequest.Method.PUT.name(),
            AB_TESTS_URL + "/" + testId + "/_update",
            null,
            toHttpEntity("{\"enabled\":false}"),
            ImmutableList.of(new BasicHeader(HttpHeaders.USER_AGENT, DEFAULT_USER_AGENT))
        );

        // Execute search
        Response response = makeRequest(
            client(),
            RestRequest.Method.POST.name(),
            AB_TESTS_URL + "/" + testId + "/_search",
            null,
            toHttpEntity("{\"query_params\":{\"SearchText\":\"laptop\"}}"),
            ImmutableList.of(new BasicHeader(HttpHeaders.USER_AGENT, DEFAULT_USER_AGENT))
        );

        // Verify
        Map<String, Object> result = entityAsMap(response);
        assertEquals(testId, result.get("test_id"));
    }

    /** Non-existent test ID returns 404 */
    @SneakyThrows
    public void testSearchWithNonExistentTest() {
        try {
            makeRequest(
                client(),
                RestRequest.Method.POST.name(),
                AB_TESTS_URL + "/non-existent-test/_search",
                null,
                toHttpEntity("{\"query_params\":{\"SearchText\":\"laptop\"}}"),
                ImmutableList.of(new BasicHeader(HttpHeaders.USER_AGENT, DEFAULT_USER_AGENT))
            );
            fail("Expected 404 for non-existent test");
        } catch (ResponseException e) {
            assertEquals(404, e.getResponse().getStatusLine().getStatusCode());
        }
    }

    /** Empty query_params map returns 400 */
    @SneakyThrows
    public void testSearchWithMissingQueryParams() {
        // Setup
        createTestIndex(TEST_INDEX);
        indexTestDocuments();

        String configIdA = createSearchConfiguration(
            "it-config-missing-a",
            TEST_INDEX,
            "{\"query\":{\"match\":{\"title\":\"%SearchText%\"}}}"
        );
        String configIdB = createSearchConfiguration("it-config-missing-b", TEST_INDEX, "{\"query\":{\"match_all\":{}}}");
        createABTest("search-it-missing-params", configIdA, configIdB);

        try {
            makeRequest(
                client(),
                RestRequest.Method.POST.name(),
                AB_TESTS_URL + "/search-it-missing-params/_search",
                null,
                toHttpEntity("{\"query_params\":{}}"),
                ImmutableList.of(new BasicHeader(HttpHeaders.USER_AGENT, DEFAULT_USER_AGENT))
            );
            fail("Expected 400 for missing query params");
        } catch (ResponseException e) {
            assertEquals(400, e.getResponse().getStatusLine().getStatusCode());
        }
    }

    /** query_params without SearchText key returns 400 */
    @SneakyThrows
    public void testSearchWithMissingSearchText() {
        // Setup
        createTestIndex(TEST_INDEX);
        indexTestDocuments();

        String configIdA = createSearchConfiguration(
            "it-config-nosearch-a",
            TEST_INDEX,
            "{\"query\":{\"match\":{\"title\":\"%SearchText%\"}}}"
        );
        String configIdB = createSearchConfiguration("it-config-nosearch-b", TEST_INDEX, "{\"query\":{\"match_all\":{}}}");
        createABTest("search-it-no-searchtext", configIdA, configIdB);

        try {
            makeRequest(
                client(),
                RestRequest.Method.POST.name(),
                AB_TESTS_URL + "/search-it-no-searchtext/_search",
                null,
                toHttpEntity("{\"query_params\":{\"SomeOtherParam\":\"value\"}}"),
                ImmutableList.of(new BasicHeader(HttpHeaders.USER_AGENT, DEFAULT_USER_AGENT))
            );
            fail("Expected 400 for missing SearchText");
        } catch (ResponseException e) {
            assertEquals(400, e.getResponse().getStatusLine().getStatusCode());
        }
    }

    /** Configs targeting different indices returns 400 — TDI requires same index */
    @SneakyThrows
    public void testSearchWithDifferentIndices() {
        // Setup: create two configs targeting different indices
        createTestIndex(TEST_INDEX);
        createTestIndex(TEST_INDEX + "-other");
        indexTestDocuments();

        String configIdA = createSearchConfiguration(
            "it-config-diff-idx-a",
            TEST_INDEX,
            "{\"query\":{\"match\":{\"title\":\"%SearchText%\"}}}"
        );
        String configIdB = createSearchConfiguration("it-config-diff-idx-b", TEST_INDEX + "-other", "{\"query\":{\"match_all\":{}}}");
        createABTest("search-it-diff-index", configIdA, configIdB);

        try {
            makeRequest(
                client(),
                RestRequest.Method.POST.name(),
                AB_TESTS_URL + "/search-it-diff-index/_search",
                null,
                toHttpEntity("{\"query_params\":{\"SearchText\":\"laptop\"}}"),
                ImmutableList.of(new BasicHeader(HttpHeaders.USER_AGENT, DEFAULT_USER_AGENT))
            );
            fail("Expected 400 for different target indices");
        } catch (ResponseException e) {
            assertEquals(400, e.getResponse().getStatusLine().getStatusCode());
        }
    }

    // TODO: Size validation test — currently size is embedded inside the query string,
    // not a top-level config field. Size validation will be added when config schema supports it.

    /** SearchText exceeding 1024 characters returns 400 */
    @SneakyThrows
    public void testSearchWithTooLongSearchText() {
        // Setup
        createTestIndex(TEST_INDEX);
        indexTestDocuments();

        String configIdA = createSearchConfiguration(
            "it-config-long-a",
            TEST_INDEX,
            "{\"query\":{\"match\":{\"title\":\"%SearchText%\"}}}"
        );
        String configIdB = createSearchConfiguration("it-config-long-b", TEST_INDEX, "{\"query\":{\"match_all\":{}}}");
        createABTest("search-it-long-text", configIdA, configIdB);

        // Create a string longer than 1024 characters
        String longText = "a".repeat(1025);

        try {
            makeRequest(
                client(),
                RestRequest.Method.POST.name(),
                AB_TESTS_URL + "/search-it-long-text/_search",
                null,
                toHttpEntity("{\"query_params\":{\"SearchText\":\"" + longText + "\"}}"),
                ImmutableList.of(new BasicHeader(HttpHeaders.USER_AGENT, DEFAULT_USER_AGENT))
            );
            fail("Expected 400 for SearchText exceeding max length");
        } catch (ResponseException e) {
            assertEquals(400, e.getResponse().getStatusLine().getStatusCode());
        }
    }

    private void indexTestDocuments() throws Exception {
        String bulkBody = "{ \"index\": { \"_index\": \""
            + TEST_INDEX
            + "\", \"_id\": \"1\" } }\n"
            + "{ \"title\": \"laptop pro\", \"category\": \"electronics\" }\n"
            + "{ \"index\": { \"_index\": \""
            + TEST_INDEX
            + "\", \"_id\": \"2\" } }\n"
            + "{ \"title\": \"wireless mouse\", \"category\": \"electronics\" }\n"
            + "{ \"index\": { \"_index\": \""
            + TEST_INDEX
            + "\", \"_id\": \"3\" } }\n"
            + "{ \"title\": \"laptop stand\", \"category\": \"accessories\" }\n";
        bulkIngest(TEST_INDEX, bulkBody);
    }

    private void createABTest(String testId, String configIdA, String configIdB) throws Exception {
        String abTestBody = String.format("{\"search_configuration_a\":\"%s\",\"search_configuration_b\":\"%s\"}", configIdA, configIdB);
        makeRequest(
            client(),
            RestRequest.Method.PUT.name(),
            AB_TESTS_URL + "/" + testId,
            null,
            toHttpEntity(abTestBody),
            ImmutableList.of(new BasicHeader(HttpHeaders.USER_AGENT, DEFAULT_USER_AGENT))
        );
    }
}
