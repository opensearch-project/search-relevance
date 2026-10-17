/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */
package org.opensearch.searchrelevance.action.queryset;

import static org.opensearch.searchrelevance.common.PluginConstants.QUERYSETS_URL;
import static org.opensearch.searchrelevance.common.PluginConstants.QUERY_SET_INDEX;

import java.nio.file.Files;
import java.nio.file.Path;
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

@ThreadLeakScope(ThreadLeakScope.Scope.NONE)
@OpenSearchIntegTestCase.ClusterScope(scope = OpenSearchIntegTestCase.Scope.SUITE)
public class QuerySetIT extends BaseSearchRelevanceIT {

    @SneakyThrows
    public void testMainActions_whenCreateReadDeleteQuerySet_thenSuccessful() {
        String requestBody = Files.readString(Path.of(classLoader.getResource("queryset/CreateQuerySet.json").toURI()));
        Response uploadResponse = makeRequest(
            client(),
            RestRequest.Method.PUT.name(),
            QUERYSETS_URL,
            null,
            toHttpEntity(requestBody),
            ImmutableList.of(new BasicHeader(HttpHeaders.USER_AGENT, DEFAULT_USER_AGENT))
        );
        Map<String, Object> putResultJson = entityAsMap(uploadResponse);
        assertNotNull(putResultJson);
        String querySetId = putResultJson.get("query_set_id").toString();
        assertNotNull(querySetId);
        assertEquals("CREATED", putResultJson.get("query_set_result").toString());

        String getQuerySetByIdUrl = String.join("/", QUERY_SET_INDEX, "_doc", querySetId);
        Response getQuerySetResponse = makeRequest(
            client(),
            RestRequest.Method.GET.name(),
            getQuerySetByIdUrl,
            null,
            null,
            ImmutableList.of(new BasicHeader(HttpHeaders.USER_AGENT, DEFAULT_USER_AGENT))
        );
        Map<String, Object> getQuerySetResultJson = entityAsMap(getQuerySetResponse);
        assertNotNull(getQuerySetResultJson);
        assertEquals(querySetId, getQuerySetResultJson.get("_id").toString());
        Map<String, Object> source = (Map<String, Object>) getQuerySetResultJson.get("_source");
        assertNotNull(source);
        assertNotNull(source.get("id"));
        assertEquals("query_set", source.get("name"));
        assertEquals("Test query set", source.get("description"));

        String searchBody = "{" + "\"query\": {" + "\"term\": {" + "\"id\": \"" + querySetId + "\"" + "}" + "}" + "}";
        Response searchResponse = makeRequest(
            client(),
            RestRequest.Method.POST.name(),
            QUERYSETS_URL + "/_search",
            null,
            toHttpEntity(searchBody),
            ImmutableList.of(new BasicHeader(HttpHeaders.USER_AGENT, DEFAULT_USER_AGENT))
        );
        Map<String, Object> searchResultJson = entityAsMap(searchResponse);
        Map<String, Object> hits = (Map<String, Object>) searchResultJson.get("hits");
        List<Map<String, Object>> hitList = (List<Map<String, Object>>) hits.get("hits");
        assertFalse(hitList.isEmpty());
        Map<String, Object> firstHit = hitList.get(0);
        assertEquals(querySetId, firstHit.get("_id"));
        Map<String, Object> firstHitSource = (Map<String, Object>) firstHit.get("_source");
        assertEquals("query_set", firstHitSource.get("name"));

        Response deleteQuerySetResponse = makeRequest(
            client(),
            RestRequest.Method.DELETE.name(),
            getQuerySetByIdUrl,
            null,
            null,
            ImmutableList.of(new BasicHeader(HttpHeaders.USER_AGENT, DEFAULT_USER_AGENT))
        );
        Map<String, Object> deleteQuerySetResultJson = entityAsMap(deleteQuerySetResponse);
        assertNotNull(deleteQuerySetResultJson);
        assertEquals("deleted", deleteQuerySetResultJson.get("result").toString());

        expectThrows(
            ResponseException.class,
            () -> makeRequest(
                client(),
                RestRequest.Method.GET.name(),
                getQuerySetByIdUrl,
                null,
                null,
                ImmutableList.of(new BasicHeader(HttpHeaders.USER_AGENT, DEFAULT_USER_AGENT))
            )
        );
    }
}
