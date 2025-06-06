/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */
package org.opensearch.searchrelevance.action.searchConfiguration;

import static org.opensearch.searchrelevance.common.PluginConstants.SEARCH_CONFIGURATIONS_URL;
import static org.opensearch.searchrelevance.common.PluginConstants.SEARCH_CONFIGURATION_INDEX;

import java.nio.file.Files;
import java.nio.file.Path;
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

@ThreadLeakScope(ThreadLeakScope.Scope.NONE)
@OpenSearchIntegTestCase.ClusterScope(scope = OpenSearchIntegTestCase.Scope.SUITE)
public class SearchConfigurationIT extends BaseSearchRelevanceIT {

    @SneakyThrows
    public void testMainActions_whenCreateReadDeleteSearchConfig_thenSuccessful() {
        String requestBody = Files.readString(Path.of(classLoader.getResource("searchconfig/CreateSearchConfiguration.json").toURI()));
        Response uploadResponse = makeRequest(
            client(),
            RestRequest.Method.PUT.name(),
            SEARCH_CONFIGURATIONS_URL,
            null,
            toHttpEntity(requestBody),
            ImmutableList.of(new BasicHeader(HttpHeaders.USER_AGENT, DEFAULT_USER_AGENT))
        );
        Map<String, Object> putResultJson = entityAsMap(uploadResponse);
        assertNotNull(putResultJson);
        String configId = putResultJson.get("search_configuration_id").toString();
        assertNotNull(configId);
        assertEquals("CREATED", putResultJson.get("search_configuration_result").toString());

        String getSearchConfigByIdUrl = String.join("/", SEARCH_CONFIGURATION_INDEX, "_doc", configId);
        Response getSearchConfigResponse = makeRequest(
            client(),
            RestRequest.Method.GET.name(),
            getSearchConfigByIdUrl,
            null,
            null,
            ImmutableList.of(new BasicHeader(HttpHeaders.USER_AGENT, DEFAULT_USER_AGENT))
        );
        Map<String, Object> getSearchConfigResultJson = entityAsMap(getSearchConfigResponse);
        assertNotNull(getSearchConfigResultJson);
        assertEquals(configId, getSearchConfigResultJson.get("_id").toString());
        Map<String, Object> source = (Map<String, Object>) getSearchConfigResultJson.get("_source");
        assertNotNull(source);
        assertNotNull(source.get("id"));
        assertNotNull(source.get("timestamp"));
        assertEquals("test_search_config", source.get("name"));
        assertEquals("test_index", source.get("index"));
        assertEquals("{\"query\": {\"match_all\": {}}}", source.get("query"));
        assertEquals("test_pipeline", source.get("searchPipeline"));

        Response deleteSearchConfigResponse = makeRequest(
            client(),
            RestRequest.Method.DELETE.name(),
            getSearchConfigByIdUrl,
            null,
            null,
            ImmutableList.of(new BasicHeader(HttpHeaders.USER_AGENT, DEFAULT_USER_AGENT))
        );
        Map<String, Object> deleteSearchConfigResultJson = entityAsMap(deleteSearchConfigResponse);
        assertNotNull(deleteSearchConfigResultJson);
        assertEquals("deleted", deleteSearchConfigResultJson.get("result").toString());

        expectThrows(
            ResponseException.class,
            () -> makeRequest(
                client(),
                RestRequest.Method.GET.name(),
                getSearchConfigByIdUrl,
                null,
                null,
                ImmutableList.of(new BasicHeader(HttpHeaders.USER_AGENT, DEFAULT_USER_AGENT))
            )
        );
    }
}
