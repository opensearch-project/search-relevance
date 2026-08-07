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
import static org.opensearch.searchrelevance.common.PluginConstants.UBI_EVENTS_INDEX;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.apache.hc.core5.http.HttpHeaders;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.apache.hc.core5.http.message.BasicHeader;
import org.opensearch.client.Response;
import org.opensearch.client.ResponseException;
import org.opensearch.cluster.metadata.IndexMetadata;
import org.opensearch.common.settings.Settings;
import org.opensearch.core.rest.RestStatus;
import org.opensearch.rest.RestRequest;
import org.opensearch.searchrelevance.BaseSearchRelevanceIT;

import com.google.common.collect.ImmutableList;

import lombok.SneakyThrows;

public class UbiEventsIndexResolutionIT extends BaseSearchRelevanceIT {

    private static final String SAMPLE_UBI_EVENTS_INDEX = "opensearch_dashboards_sample_ubi_events";

    @SneakyThrows
    public void testImplicitJudgmentsResolveUbiEventsAlias() {
        createSampleUbiEventsIndex();
        createAlias(SAMPLE_UBI_EVENTS_INDEX, UBI_EVENTS_INDEX);
        ingestSampleUbiEvents();

        String judgmentId = createJudgment(readResource("judgment/ImplicitJudgmentsDates.json"));
        assertJudgmentCompletedWithRatings(judgmentId);
    }

    @SneakyThrows
    public void testImplicitJudgmentsAcceptExplicitSampleEventsIndex() {
        createSampleUbiEventsIndex();
        ingestSampleUbiEvents(SAMPLE_UBI_EVENTS_INDEX);

        String judgmentId = createJudgment(judgmentRequest(SAMPLE_UBI_EVENTS_INDEX));
        assertJudgmentCompletedWithRatings(judgmentId);
    }

    @SneakyThrows
    public void testImplicitJudgmentsAcceptWildcardEventsIndex() {
        createSampleUbiEventsIndex();
        ingestSampleUbiEvents(SAMPLE_UBI_EVENTS_INDEX);

        String judgmentId = createJudgment(judgmentRequest("*ubi_events*"));
        assertJudgmentCompletedWithRatings(judgmentId);
    }

    @SneakyThrows
    public void testUnknownUbiEventsIndexIsRejectedAsBadRequest() {
        String requestBody = judgmentRequest("no_such_ubi_events");

        ResponseException exception = expectThrows(
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

        assertEquals(RestStatus.BAD_REQUEST.getStatus(), exception.getResponse().getStatusLine().getStatusCode());
        String responseBody = EntityUtils.toString(exception.getResponse().getEntity());
        assertTrue(responseBody, responseBody.contains("UBI events index [no_such_ubi_events] does not exist"));
        assertTrue(responseBody, responseBody.contains("ubiEventsIndex"));
    }

    @SneakyThrows
    private void createSampleUbiEventsIndex() {
        String eventsIndexMapping = readResource("ubi/events-mapping.json");
        eventsIndexMapping = eventsIndexMapping.substring(1, eventsIndexMapping.length() - 1);

        final Settings indexSettings = Settings.builder()
            .put(IndexMetadata.INDEX_NUMBER_OF_SHARDS_SETTING.getKey(), 1)
            .put(IndexMetadata.INDEX_AUTO_EXPAND_REPLICAS_SETTING.getKey(), "0-2")
            .put(IndexMetadata.SETTING_PRIORITY, Integer.MAX_VALUE)
            .build();
        createIndex(SAMPLE_UBI_EVENTS_INDEX, indexSettings, eventsIndexMapping);
    }

    @SneakyThrows
    private void createAlias(String indexName, String alias) {
        Response response = makeRequest(
            client(),
            RestRequest.Method.PUT.name(),
            String.join("/", indexName, "_alias", alias),
            null,
            toHttpEntity("{}"),
            ImmutableList.of(new BasicHeader(HttpHeaders.USER_AGENT, DEFAULT_USER_AGENT))
        );
        assertEquals(RestStatus.OK.getStatus(), response.getStatusLine().getStatusCode());
    }

    @SneakyThrows
    private void ingestSampleUbiEvents() {
        ingestSampleUbiEvents(UBI_EVENTS_INDEX);
    }

    @SneakyThrows
    private void ingestSampleUbiEvents(String targetIndex) {
        String bulkBody = readResource("sample_ubi_data/SampleUBIEvents.json");
        if (!UBI_EVENTS_INDEX.equals(targetIndex)) {
            bulkBody = bulkBody.replace("\"_index\": \"" + UBI_EVENTS_INDEX + "\"", "\"_index\": \"" + targetIndex + "\"");
        }
        bulkIngest(targetIndex, bulkBody);
    }

    private static String judgmentRequest(String ubiEventsIndex) {
        return String.format(Locale.ROOT, """
            {
              "name": "Implicit Judgements",
              "clickModel": "coec",
              "type": "UBI_JUDGMENT",
              "maxRank": 20,
              "startDate": "2024-12-15",
              "endDate": "2024-12-18",
              "ubiEventsIndex": "%s"
            }""", ubiEventsIndex);
    }

    @SneakyThrows
    private String readResource(String resourcePath) {
        return Files.readString(Path.of(classLoader.getResource(resourcePath).toURI()));
    }

    @SneakyThrows
    private String createJudgment(String requestBody) {
        Response response = makeRequest(
            client(),
            RestRequest.Method.PUT.name(),
            JUDGMENTS_URL,
            null,
            toHttpEntity(requestBody),
            ImmutableList.of(new BasicHeader(HttpHeaders.USER_AGENT, DEFAULT_USER_AGENT))
        );
        Map<String, Object> result = entityAsMap(response);
        assertNotNull(result);
        String judgmentId = (String) result.get("judgment_id");
        assertNotNull(judgmentId);
        return judgmentId;
    }

    @SneakyThrows
    private void assertJudgmentCompletedWithRatings(String judgmentId) {
        assertBusy(() -> {
            Response response = makeRequest(
                adminClient(),
                RestRequest.Method.GET.name(),
                String.join("/", JUDGMENT_INDEX, "_doc", judgmentId),
                null,
                null,
                ImmutableList.of(new BasicHeader(HttpHeaders.USER_AGENT, DEFAULT_USER_AGENT))
            );

            Map<String, Object> result = entityAsMap(response);
            assertNotNull(result);
            Map<String, Object> source = (Map<String, Object>) result.get("_source");
            assertNotNull(source);
            assertEquals("COMPLETED", source.get("status"));

            List<Map<String, Object>> judgmentRatings = (List<Map<String, Object>>) source.get("judgmentRatings");
            assertNotNull(judgmentRatings);
            assertFalse(judgmentRatings.isEmpty());
        }, 30, TimeUnit.SECONDS);
    }
}
