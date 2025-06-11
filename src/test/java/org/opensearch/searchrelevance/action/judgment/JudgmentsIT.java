/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */
package org.opensearch.searchrelevance.action.judgment;

import static org.opensearch.searchrelevance.common.PluginConstants.JUDGMENTS_URL;
import static org.opensearch.searchrelevance.common.PluginConstants.JUDGMENT_INDEX;

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
public class JudgmentsIT extends BaseSearchRelevanceIT {

    @SneakyThrows
    public void testMainActions_whenImportReadJudgments_thenSuccessful() {
        String requestBody = Files.readString(Path.of(classLoader.getResource("judgment/ImportJudgments.json").toURI()));
        Response importResponse = makeRequest(
            client(),
            RestRequest.Method.PUT.name(),
            JUDGMENTS_URL,
            null,
            toHttpEntity(requestBody),
            ImmutableList.of(new BasicHeader(HttpHeaders.USER_AGENT, DEFAULT_USER_AGENT))
        );
        Map<String, Object> importResultJson = entityAsMap(importResponse);
        assertNotNull(importResultJson);
        String judgmentsId = importResultJson.get("judgment_id").toString();
        assertNotNull(judgmentsId);

        // wait for completion of import action
        Thread.sleep(1000);

        String getJudgmentsByIdUrl = String.join("/", JUDGMENT_INDEX, "_doc", judgmentsId);
        Response getJudgmentsResponse = makeRequest(
            client(),
            RestRequest.Method.GET.name(),
            getJudgmentsByIdUrl,
            null,
            null,
            ImmutableList.of(new BasicHeader(HttpHeaders.USER_AGENT, DEFAULT_USER_AGENT))
        );
        Map<String, Object> getJudgmentsResultJson = entityAsMap(getJudgmentsResponse);
        assertNotNull(getJudgmentsResultJson);
        assertEquals(judgmentsId, getJudgmentsResultJson.get("_id").toString());

        Map<String, Object> source = (Map<String, Object>) getJudgmentsResultJson.get("_source");
        assertNotNull(source);
        assertNotNull(source.get("id"));
        assertNotNull(source.get("timestamp"));
        assertEquals("ESCI Judgments", source.get("name"));
        assertEquals("COMPLETED", source.get("status"));

        // Verify judgments array
        List<Map<String, Object>> judgments = (List<Map<String, Object>>) source.get("judgmentRatings");
        assertNotNull(judgments);
        assertFalse(judgments.isEmpty());

        // Verify first judgment entry
        Map<String, Object> firstJudgment = judgments.get(0);
        assertNotNull(firstJudgment.get("query"));
        List<Map<String, Object>> ratings = (List<Map<String, Object>>) firstJudgment.get("ratings");
        assertNotNull(ratings);
        assertEquals(10, ratings.size());
        for (Map<String, Object> rating : ratings) {
            assertNotNull(rating.get("docId"));
            assertNotNull(rating.get("rating"));
        }
        Map<String, Object> secondJudgment = judgments.get(1);
        assertNotNull(secondJudgment.get("query"));
        List<Map<String, Object>> ratingsSecondJudgment = (List<Map<String, Object>>) secondJudgment.get("ratings");
        assertNotNull(ratingsSecondJudgment);
        assertEquals(10, ratingsSecondJudgment.size());
        for (Map<String, Object> rating : ratingsSecondJudgment) {
            assertNotNull(rating.get("docId"));
            assertNotNull(rating.get("rating"));
        }

        Response deleteJudgmentsResponse = makeRequest(
            client(),
            RestRequest.Method.DELETE.name(),
            getJudgmentsByIdUrl,
            null,
            null,
            ImmutableList.of(new BasicHeader(HttpHeaders.USER_AGENT, DEFAULT_USER_AGENT))
        );
        Map<String, Object> deleteJudgmentsResultJson = entityAsMap(deleteJudgmentsResponse);
        assertNotNull(deleteJudgmentsResultJson);
        assertEquals("deleted", deleteJudgmentsResultJson.get("result").toString());

        expectThrows(
            ResponseException.class,
            () -> makeRequest(
                client(),
                RestRequest.Method.GET.name(),
                getJudgmentsByIdUrl,
                null,
                null,
                ImmutableList.of(new BasicHeader(HttpHeaders.USER_AGENT, DEFAULT_USER_AGENT))
            )
        );
    }
}
