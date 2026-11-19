/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */
package org.opensearch.searchrelevance.security;

import static org.opensearch.searchrelevance.common.PluginConstants.EXPERIMENTS_URI;
import static org.opensearch.searchrelevance.common.PluginConstants.JUDGMENTS_URL;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.apache.hc.client5.http.auth.AuthScope;
import org.apache.hc.client5.http.auth.UsernamePasswordCredentials;
import org.apache.hc.client5.http.impl.auth.BasicCredentialsProvider;
import org.apache.hc.client5.http.impl.nio.PoolingAsyncClientConnectionManagerBuilder;
import org.apache.hc.client5.http.ssl.ClientTlsStrategyBuilder;
import org.apache.hc.client5.http.ssl.NoopHostnameVerifier;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.apache.hc.core5.ssl.SSLContextBuilder;
import org.junit.Before;
import org.opensearch.client.Request;
import org.opensearch.client.Response;
import org.opensearch.client.ResponseException;
import org.opensearch.client.RestClient;
import org.opensearch.searchrelevance.BaseSearchRelevanceIT;
import org.opensearch.searchrelevance.indices.SearchRelevanceIndices;

import com.carrotsearch.randomizedtesting.annotations.ThreadLeakScope;

/**
 * Checks, with the Security plugin installed, that a user with only the search relevance actions can use the APIs whose
 * work reads or deletes documents in the plugin's own indices that are not system indices. The plugin does that work
 * as its plugin subject; the user has no permission on those indices.
 */
@ThreadLeakScope(ThreadLeakScope.Scope.NONE)
public class PluginOwnedIndexAccessSecurityIT extends BaseSearchRelevanceIT {

    private static final String USER = "search_relevance_actions_only";
    private static final String PASSWORD = "Search-Relevance-Test-7!";
    private static final String EXPERIMENT_ID = "experiment1";

    @Before
    public void createUser() throws IOException {
        assumeTrue("Security-enabled test cluster required", isHttps());
        Request role = new Request("PUT", "/_plugins/_security/api/roles/" + USER);
        role.setJsonEntity("{\"cluster_permissions\":[\"cluster:admin/opensearch/search_relevance/*\"]}");
        adminClient().performRequest(role);
        Request user = new Request("PUT", "/_plugins/_security/api/internalusers/" + USER);
        user.setJsonEntity("{\"password\":\"" + PASSWORD + "\"}");
        adminClient().performRequest(user);
        Request mapping = new Request("PUT", "/_plugins/_security/api/rolesmapping/" + USER);
        mapping.setJsonEntity("{\"users\":[\"" + USER + "\"]}");
        adminClient().performRequest(mapping);
    }

    public void testDeletingAnExperimentDeletesItsResultsAndVariants() throws Exception {
        for (SearchRelevanceIndices index : List.of(SearchRelevanceIndices.EVALUATION_RESULT, SearchRelevanceIndices.EXPERIMENT_VARIANT)) {
            createIndex(index);
            Request doc = new Request("PUT", "/" + index.getIndexName() + "/_doc/" + index.name().toLowerCase(java.util.Locale.ROOT));
            doc.addParameter("refresh", "true");
            doc.setJsonEntity("{\"experimentId\":\"" + EXPERIMENT_ID + "\"}");
            client().performRequest(doc);
        }

        Response response = asUser("DELETE", EXPERIMENTS_URI + "/" + EXPERIMENT_ID, null);
        assertNotEquals("the user may delete experiments: " + body(response), 403, response.getStatusLine().getStatusCode());

        // The results and variants are deleted after the response, and a failed delete is only logged.
        assertBusy(() -> {
            for (SearchRelevanceIndices index : List.of(
                SearchRelevanceIndices.EVALUATION_RESULT,
                SearchRelevanceIndices.EXPERIMENT_VARIANT
            )) {
                assertEquals("documents of the experiment left in " + index.getIndexName(), 0, count(index));
            }
        }, 30, TimeUnit.SECONDS);
    }

    public void testUpdatingJudgmentRatingsReadsTheJudgment() throws Exception {
        createIndex(SearchRelevanceIndices.JUDGMENT);
        Request doc = new Request("PUT", "/" + SearchRelevanceIndices.JUDGMENT.getIndexName() + "/_doc/judgment1");
        doc.addParameter("refresh", "true");
        doc.setJsonEntity("{\"name\":\"imported\",\"type\":\"IMPORT_JUDGMENT\",\"status\":\"COMPLETED\"}");
        client().performRequest(doc);

        String ratings = "{\"judgmentRatings\":[{\"query\":\"q\",\"ratings\":[{\"docId\":\"d\",\"rating\":\"1\"}]}]}";
        Response response = asUser("PUT", JUDGMENTS_URL + "/judgment1", ratings);

        // The update reads the judgment first. Only an LLM judgment can be updated, so a 400 that says so proves the read worked.
        assertEquals(body(response), 400, response.getStatusLine().getStatusCode());
        assertTrue(body(response), body(response).contains("only supported for LLM_JUDGMENT"));
    }

    private void createIndex(SearchRelevanceIndices index) throws IOException {
        Request create = new Request("PUT", "/" + index.getIndexName());
        create.setJsonEntity("{\"mappings\":" + index.getMapping() + "}");
        client().performRequest(create);
    }

    @SuppressWarnings("unchecked")
    private long count(SearchRelevanceIndices index) throws IOException {
        Response response = client().performRequest(new Request("GET", "/" + index.getIndexName() + "/_count"));
        Map<String, Object> map = entityAsMap(response);
        return ((Number) map.get("count")).longValue();
    }

    private Response asUser(String method, String path, String body) throws IOException {
        Request request = new Request(method, path);
        if (body != null) {
            request.setJsonEntity(body);
        }
        try (RestClient userClient = userClient()) {
            return userClient.performRequest(request);
        } catch (ResponseException e) {
            return e.getResponse();
        }
    }

    private RestClient userClient() {
        HttpHost[] hosts = getClusterHosts().toArray(new HttpHost[0]);
        return RestClient.builder(hosts).setHttpClientConfigCallback(httpClientBuilder -> {
            BasicCredentialsProvider credentials = new BasicCredentialsProvider();
            credentials.setCredentials(new AuthScope(null, -1), new UsernamePasswordCredentials(USER, PASSWORD.toCharArray()));
            try {
                return httpClientBuilder.setDefaultCredentialsProvider(credentials)
                    .setConnectionManager(
                        PoolingAsyncClientConnectionManagerBuilder.create()
                            .setTlsStrategy(
                                ClientTlsStrategyBuilder.create()
                                    .setHostnameVerifier(NoopHostnameVerifier.INSTANCE)
                                    .setSslContext(SSLContextBuilder.create().loadTrustMaterial(null, (chains, authType) -> true).build())
                                    .build()
                            )
                            .build()
                    );
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        }).build();
    }

    private static String body(Response response) throws IOException {
        try {
            return response.getEntity() == null ? "" : EntityUtils.toString(response.getEntity());
        } catch (org.apache.hc.core5.http.ParseException e) {
            throw new IOException(e);
        }
    }
}
