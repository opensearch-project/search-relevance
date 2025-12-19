/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */
package org.opensearch.searchrelevance.experiment;

import static org.opensearch.searchrelevance.common.PluginConstants.EXPERIMENT_INDEX;
import static org.opensearch.searchrelevance.common.PluginConstants.JUDGMENTS_URL;
import static org.opensearch.searchrelevance.common.PluginConstants.QUERYSETS_URL;
import static org.opensearch.searchrelevance.common.PluginConstants.SEARCH_CONFIGURATIONS_URL;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.apache.hc.core5.http.HttpHeaders;
import org.apache.hc.core5.http.message.BasicHeader;
import org.opensearch.client.Response;
import org.opensearch.common.xcontent.XContentFactory;
import org.opensearch.core.xcontent.XContentBuilder;
import org.opensearch.rest.RestRequest;
import org.opensearch.searchrelevance.BaseSearchRelevanceIT;

import com.google.common.collect.ImmutableList;

import lombok.SneakyThrows;

/**
 * Base class for experiment integration tests containing common logic and setup methods.
 */
public abstract class BaseExperimentIT extends BaseSearchRelevanceIT {

    // Common constants
    public static final List<String> EXPECTED_QUERY_TERMS = List.of(
        "button",
        "keyboard",
        "steel",
        "diamond wheel",
        "phone",
        "metal frame",
        "iphone",
        "giangentic form"
    );

    public static final Map<String, Object> EXPECT_EVALUATION_RESULTS = Map.of(
        "button",
        Map.of(
            "documentIds",
            List.of("B06Y1L1YJD", "B01M3XBRRX", "B07D29PHFY"),
            "metrics",
            Map.of("Coverage@5", 1.0, "Precision@5", 1.0, "MAP@5", 1.0, "NDCG@5", 0.94)
        ),

        "metal frame",
        Map.of(
            "documentIds",
            List.of("B07MBG53JD", "B097Q69V1B", "B00TLYRBMG", "B08G46SS1T", "B07H81Z91C"),
            "metrics",
            Map.of("Coverage@5", 1.0, "Precision@5", 1.0, "MAP@5", 1.0, "NDCG@5", 0.9)
        )
    );

    protected static final String BASE_INDEX_NAME_ESCI = "ecommerce";
    protected static final int MAX_POLL_RETRIES = 120;

    protected String createJudgment() throws IOException, URISyntaxException, InterruptedException {
        String importJudgmentBody = Files.readString(Path.of(classLoader.getResource("data_esci/ImportJudgment.json").toURI()));
        Response importJudgementResponse = makeRequest(
            client(),
            RestRequest.Method.PUT.name(),
            JUDGMENTS_URL,
            null,
            toHttpEntity(importJudgmentBody),
            ImmutableList.of(new BasicHeader(HttpHeaders.USER_AGENT, DEFAULT_USER_AGENT))
        );
        Map<String, Object> importResultJson = entityAsMap(importJudgementResponse);
        String judgmentId = importResultJson.get("judgment_id").toString();
        assertNotNull(judgmentId);

        // wait for completion of import action
        Thread.sleep(DEFAULT_INTERVAL_MS);
        return judgmentId;
    }

    protected String createQuerySet() throws IOException, URISyntaxException {
        String createQuerySetBody = Files.readString(Path.of(classLoader.getResource("queryset/CreateQuerySet.json").toURI()));
        Response createQuerySetResponse = makeRequest(
            client(),
            RestRequest.Method.PUT.name(),
            QUERYSETS_URL,
            null,
            toHttpEntity(createQuerySetBody),
            ImmutableList.of(new BasicHeader(HttpHeaders.USER_AGENT, DEFAULT_USER_AGENT))
        );
        Map<String, Object> createQuerySetResultJson = entityAsMap(createQuerySetResponse);
        String querySetId = createQuerySetResultJson.get("query_set_id").toString();
        assertNotNull(querySetId);
        return querySetId;
    }

    @SneakyThrows
    protected String createSearchConfiguration(String indexName) {
        String createSearchConfigurationRequestBody = Files.readString(
            Path.of(classLoader.getResource("searchconfig/CreateSearchConfigurationQueryWithPlaceholder.json").toURI())
        );
        // Replace the placeholder with the actual index name
        createSearchConfigurationRequestBody = createSearchConfigurationRequestBody.replace("{{index_name}}", indexName);

        Response createSearchConfigurationResponse = makeRequest(
            client(),
            RestRequest.Method.PUT.name(),
            SEARCH_CONFIGURATIONS_URL,
            null,
            toHttpEntity(createSearchConfigurationRequestBody),
            ImmutableList.of(new BasicHeader(HttpHeaders.USER_AGENT, DEFAULT_USER_AGENT))
        );
        Map<String, Object> createSearchConfigurationResultJson = entityAsMap(createSearchConfigurationResponse);
        String searchConfigurationId = createSearchConfigurationResultJson.get("search_configuration_id").toString();
        assertNotNull(searchConfigurationId);
        return searchConfigurationId;
    }

    @SneakyThrows
    protected String createHybridSearchConfiguration(String indexName) {
        String createSearchConfigurationRequestBody = Files.readString(
            Path.of(classLoader.getResource("searchconfig/CreateSearchConfigurationHybridQuery.json").toURI())
        );
        // Replace the placeholder with the actual index name
        createSearchConfigurationRequestBody = createSearchConfigurationRequestBody.replace("{{index_name}}", indexName);

        Response createSearchConfigurationResponse = makeRequest(
            client(),
            RestRequest.Method.PUT.name(),
            SEARCH_CONFIGURATIONS_URL,
            null,
            toHttpEntity(createSearchConfigurationRequestBody),
            ImmutableList.of(new BasicHeader(HttpHeaders.USER_AGENT, DEFAULT_USER_AGENT))
        );
        Map<String, Object> createSearchConfigurationResultJson = entityAsMap(createSearchConfigurationResponse);
        String searchConfigurationId = createSearchConfigurationResultJson.get("search_configuration_id").toString();
        assertNotNull(searchConfigurationId);
        return searchConfigurationId;
    }

    @SneakyThrows
    protected String createSimpleSearchConfiguration(String indexName) {
        String createSearchConfigurationRequestBody = Files.readString(
            Path.of(classLoader.getResource("searchconfig/CreateSearchConfigurationSimpleMatch.json").toURI())
        );
        // Replace the placeholder with the actual index name
        createSearchConfigurationRequestBody = createSearchConfigurationRequestBody.replace("{{index_name}}", indexName);

        Response createSearchConfigurationResponse = makeRequest(
            client(),
            RestRequest.Method.PUT.name(),
            SEARCH_CONFIGURATIONS_URL,
            null,
            toHttpEntity(createSearchConfigurationRequestBody),
            ImmutableList.of(new BasicHeader(HttpHeaders.USER_AGENT, DEFAULT_USER_AGENT))
        );
        Map<String, Object> createSearchConfigurationResultJson = entityAsMap(createSearchConfigurationResponse);
        String searchConfigurationId = createSearchConfigurationResultJson.get("search_configuration_id").toString();
        assertNotNull(searchConfigurationId);
        return searchConfigurationId;
    }

    @SneakyThrows
    protected void initializeIndexIfNotExist(String indexName) {
        if (indexName.startsWith(BASE_INDEX_NAME_ESCI) && !indexExists(indexName)) {
            String indexConfiguration = Files.readString(Path.of(classLoader.getResource("data_esci/CreateIndex.json").toURI()));
            createIndexWithConfiguration(indexName, indexConfiguration);
            String importDatasetBody = Files.readString(Path.of(classLoader.getResource("data_esci/BulkIngestDocuments.json").toURI()));
            // Replace the placeholder with the actual index name
            importDatasetBody = importDatasetBody.replace("{{index_name}}", indexName);
            bulkIngest(indexName, importDatasetBody);
        }
    }

    /**
     * Generate a unique index name for the test class to avoid collisions during parallel execution
     */
    protected static String generateUniqueIndexName(String testClassName) {
        return BASE_INDEX_NAME_ESCI + "_" + testClassName.toLowerCase(Locale.ROOT);
    }

    protected Map<String, Object> pollExperimentUntilCompleted(String experimentId) throws IOException {
        Map<String, Object> source = null;
        String getExperimentByIdUrl = String.join("/", EXPERIMENT_INDEX, "_doc", experimentId);

        int retryCount = 0;
        String status = "PROCESSING";

        while (("PROCESSING".equals(status) || status == null) && retryCount < MAX_POLL_RETRIES) {
            Response getExperimentResponse = makeRequest(
                adminClient(),
                RestRequest.Method.GET.name(),
                getExperimentByIdUrl,
                null,
                null,
                ImmutableList.of(new BasicHeader(HttpHeaders.USER_AGENT, DEFAULT_USER_AGENT))
            );
            Map<String, Object> getExperimentResultJson = entityAsMap(getExperimentResponse);
            assertNotNull(getExperimentResultJson);
            assertEquals(experimentId, getExperimentResultJson.get("_id").toString());

            source = (Map<String, Object>) getExperimentResultJson.get("_source");
            assertNotNull(source);
            status = (String) source.get("status");

            if ("PROCESSING".equals(status) || status == null) {
                retryCount++;
                try {
                    Thread.sleep(DEFAULT_INTERVAL_MS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            } else {
                assertEquals("COMPLETED", status);
            }
        }
        // Assert that we have a valid experiment status
        assertEquals("Experiment status must be COMPLETED", "COMPLETED", status);
        return source;
    }

    protected int findExperimentResultCount(String index, String experimentId) throws IOException {
        String getExperimentVariantCountByIdUrl = String.format(Locale.ROOT, "/%s/_count", index);
        XContentBuilder builder = XContentFactory.jsonBuilder()
            .startObject()
            .startObject("query")
            .startObject("term")
            .field("experimentId", experimentId)
            .endObject()
            .endObject()
            .endObject();
        Response getCountResponse = makeRequest(
            adminClient(),
            RestRequest.Method.GET.name(),
            getExperimentVariantCountByIdUrl,
            null,
            toHttpEntity(builder.toString()),
            ImmutableList.of(new BasicHeader(HttpHeaders.USER_AGENT, DEFAULT_USER_AGENT))
        );

        Map<String, Object> entityMap = entityAsMap(getCountResponse);
        assertNotNull(entityMap);
        return (int) entityMap.get("count");
    }

    protected void assertListsHaveSameElements(List<String> expected, List<String> actual) {
        List<String> sortedExpected = new ArrayList<>(expected);
        List<String> sortedActual = new ArrayList<>(actual);
        Collections.sort(sortedExpected);
        Collections.sort(sortedActual);
        assertArrayEquals(sortedExpected.toArray(new String[0]), sortedActual.toArray(new String[0]));
    }

    protected void assertCommonExperimentFields(
        Map<String, Object> source,
        String judgmentId,
        String searchConfigurationId,
        String querySetId,
        String expectedType
    ) {
        assertNotNull(source.get("id"));
        assertNotNull(source.get("timestamp"));

        List<String> judgmentList = (List<String>) source.get("judgmentList");
        assertNotNull(judgmentList);
        assertEquals(1, judgmentList.size());
        assertEquals(judgmentId, judgmentList.get(0));

        List<String> searchConfigurationList = (List<String>) source.get("searchConfigurationList");
        assertNotNull(searchConfigurationList);
        assertEquals(1, searchConfigurationList.size());
        assertEquals(searchConfigurationId, searchConfigurationList.get(0));

        assertEquals(expectedType, source.get("type"));
        assertEquals(querySetId, source.get("querySetId"));
    }
}
