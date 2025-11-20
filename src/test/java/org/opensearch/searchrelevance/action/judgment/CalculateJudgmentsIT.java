/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */
package org.opensearch.searchrelevance.action.judgment;

import static org.opensearch.searchrelevance.common.PluginConstants.INITIALIZE_URL;
import static org.opensearch.searchrelevance.common.PluginConstants.JUDGMENTS_URL;
import static org.opensearch.searchrelevance.common.PluginConstants.JUDGMENT_INDEX;
import static org.opensearch.searchrelevance.common.PluginConstants.UBI_EVENTS_INDEX;
import static org.opensearch.searchrelevance.common.PluginConstants.UBI_QUERIES_INDEX;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import org.apache.hc.core5.http.HttpHeaders;
import org.apache.hc.core5.http.message.BasicHeader;
import org.opensearch.client.Response;
import org.opensearch.client.ResponseException;
import org.opensearch.cluster.metadata.IndexMetadata;
import org.opensearch.common.settings.Settings;
import org.opensearch.rest.RestRequest;
import org.opensearch.searchrelevance.BaseSearchRelevanceIT;
import org.opensearch.test.OpenSearchIntegTestCase;

import com.carrotsearch.randomizedtesting.annotations.ThreadLeakScope;
import com.google.common.collect.ImmutableList;

import lombok.SneakyThrows;

@ThreadLeakScope(ThreadLeakScope.Scope.NONE)
@OpenSearchIntegTestCase.ClusterScope(scope = OpenSearchIntegTestCase.Scope.SUITE)
public class CalculateJudgmentsIT extends BaseSearchRelevanceIT {
    public void initializeUBIIndices() throws IOException, URISyntaxException {
        if (System.getProperty("ubi.available").equals("true")) {
            makeRequest(
                client(),
                RestRequest.Method.POST.name(),
                INITIALIZE_URL,
                null,
                toHttpEntity(""),
                ImmutableList.of(new BasicHeader(HttpHeaders.USER_AGENT, DEFAULT_USER_AGENT))
            );
        } else {
            String eventsIndexMapping = Files.readString(Path.of(classLoader.getResource("ubi/events-mapping.json").toURI()));
            String queriesIndexMapping = Files.readString(Path.of(classLoader.getResource("ubi/queries-mapping.json").toURI()));
            int eventsIndexMappingSize = eventsIndexMapping.length();
            int queriesIndexMappingSize = queriesIndexMapping.length();
            eventsIndexMapping = eventsIndexMapping.substring(1, eventsIndexMappingSize - 1);
            queriesIndexMapping = queriesIndexMapping.substring(1, queriesIndexMappingSize - 1);
            try {
                final Settings indexSettings = Settings.builder()
                    .put(IndexMetadata.INDEX_NUMBER_OF_SHARDS_SETTING.getKey(), 1)
                    .put(IndexMetadata.INDEX_AUTO_EXPAND_REPLICAS_SETTING.getKey(), "0-2")
                    .put(IndexMetadata.SETTING_PRIORITY, Integer.MAX_VALUE)
                    .build();
                createIndex(UBI_EVENTS_INDEX, indexSettings, eventsIndexMapping);
                createIndex(UBI_QUERIES_INDEX, indexSettings, queriesIndexMapping);
            } catch (Exception ex) {
                // Index may not exist, ignore
                throw new IOException("UBI Indices could not be created manually and are not available.", ex);
            }
        }

        String importDatasetBody = Files.readString(Path.of(classLoader.getResource("sample_ubi_data/SampleUBIEvents.json").toURI()));

        bulkIngest(UBI_EVENTS_INDEX, importDatasetBody);
    }

    @SneakyThrows
    public void testCalculateJudgments() {
        initializeUBIIndices();

        List<String> implicitJudgments = List.of(
            "judgment/ImplicitJudgmentsDates.json",
            "judgment/ImplicitJudgmentsStartDates.json",
            "judgment/ImplicitJudgmentsDatesOutOfBounds.json"
        );
        for (String implicitJudgment : implicitJudgments) {
            String requestBody = Files.readString(Path.of(classLoader.getResource(implicitJudgment).toURI()));
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
            Thread.sleep(DEFAULT_INTERVAL_MS);

            String getJudgmentsByIdUrl = String.join("/", JUDGMENT_INDEX, "_doc", judgmentsId);
            Response getJudgmentsResponse = makeRequest(
                adminClient(),
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
            assertEquals("Implicit Judgements", source.get("name"));
            assertEquals("COMPLETED", source.get("status"));

            // Verify judgments array
            List<Map<String, Object>> judgments = (List<Map<String, Object>>) source.get("judgmentRatings");
            assertNotNull(judgments);
            if (implicitJudgment.equals("judgment/ImplicitJudgmentsDatesOutOfBounds.json")) {
                assertTrue(judgments.isEmpty());
                deleteJudgment(getJudgmentsByIdUrl);
                break;
            }
            assertFalse(judgments.isEmpty());

            // Verify first judgment entry
            Map<String, Object> firstJudgment = judgments.get(0);
            assertNotNull(firstJudgment.get("query"));
            List<Map<String, Object>> ratings = (List<Map<String, Object>>) firstJudgment.get("ratings");
            assertNotNull(ratings);
            if (implicitJudgment.equals("judgment/ImplicitJudgmentsDates.json")) {
                assertEquals(4, ratings.size());
            } else {
                assertEquals(2, ratings.size());
            }

            for (Map<String, Object> rating : ratings) {
                assertNotNull(rating.get("docId"));
                assertNotNull(rating.get("rating"));
            }

            if (judgments.size() > 1) {
                Map<String, Object> secondJudgment = judgments.get(1);
                assertNotNull(secondJudgment.get("query"));
                List<Map<String, Object>> ratingsSecondJudgment = (List<Map<String, Object>>) secondJudgment.get("ratings");
                assertNotNull(ratingsSecondJudgment);
                if (implicitJudgment.equals("judgment/ImplicitJudgmentsDates.json")) {
                    assertEquals(5, ratingsSecondJudgment.size());
                } else {
                    assertEquals(5, ratingsSecondJudgment.size());
                }

                for (Map<String, Object> rating : ratingsSecondJudgment) {
                    assertNotNull(rating.get("docId"));
                    assertNotNull(rating.get("rating"));
                }
            }

            deleteJudgment(getJudgmentsByIdUrl);
        }

        String malformedRequestUrl = "judgment/MalformedJudgmentsDates.json";
        String requestBody = Files.readString(Path.of(classLoader.getResource(malformedRequestUrl).toURI()));
        expectThrows(
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
    }

    private void deleteJudgment(String getJudgmentsByIdUrl) throws IOException {
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
