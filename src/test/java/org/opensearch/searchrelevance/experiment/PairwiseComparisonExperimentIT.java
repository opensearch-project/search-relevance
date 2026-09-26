/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */
package org.opensearch.searchrelevance.experiment;

import static org.opensearch.searchrelevance.common.PluginConstants.EXPERIMENTS_URI;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import org.apache.hc.core5.http.HttpHeaders;
import org.apache.hc.core5.http.message.BasicHeader;
import org.opensearch.client.Response;
import org.opensearch.rest.RestRequest;
import org.opensearch.test.OpenSearchIntegTestCase;

import com.carrotsearch.randomizedtesting.annotations.ThreadLeakScope;
import com.google.common.collect.ImmutableList;

import lombok.SneakyThrows;

/**
 * Integration tests for pairwise comparison experiments, including per-snapshot {@code tookMs}.
 */
@ThreadLeakScope(ThreadLeakScope.Scope.NONE)
@OpenSearchIntegTestCase.ClusterScope(scope = OpenSearchIntegTestCase.Scope.SUITE)
public class PairwiseComparisonExperimentIT extends BaseExperimentIT {

    private static final String INDEX_NAME_ESCI = generateUniqueIndexName("pairwise");

    @SneakyThrows
    public void testPairwiseComparisonExperiment_persistsTookMsOnSnapshots() {
        initializeIndexIfNotExist(INDEX_NAME_ESCI);

        String searchConfigurationIdA = createSimpleSearchConfiguration(INDEX_NAME_ESCI);
        String searchConfigurationIdB = createSearchConfiguration(INDEX_NAME_ESCI);
        String querySetId = createQuerySet();

        String experimentId = createPairwiseExperiment(querySetId, searchConfigurationIdA, searchConfigurationIdB);
        pollExperimentUntilCompleted(experimentId);

        Response getExperimentResponse = makeRequest(
            client(),
            RestRequest.Method.GET.name(),
            EXPERIMENTS_URI + "/" + experimentId,
            null,
            null,
            ImmutableList.of(new BasicHeader(HttpHeaders.USER_AGENT, DEFAULT_USER_AGENT))
        );
        Map<String, Object> getExperimentJson = entityAsMap(getExperimentResponse);
        List<Map<String, Object>> hits = extractHits(getExperimentJson);
        assertFalse("GET experiment should return the stored experiment", hits.isEmpty());

        Map<String, Object> source = (Map<String, Object>) hits.get(0).get("_source");
        assertEquals("PAIRWISE_COMPARISON", source.get("type"));
        List<Map<String, Object>> results = (List<Map<String, Object>>) source.get("results");
        assertNotNull(results);
        assertFalse(results.isEmpty());

        boolean sawTookMs = false;
        for (Map<String, Object> result : results) {
            List<Map<String, Object>> snapshots = (List<Map<String, Object>>) result.get("snapshots");
            assertNotNull("pairwise results should include snapshots", snapshots);
            assertEquals(2, snapshots.size());
            for (Map<String, Object> snapshot : snapshots) {
                assertNotNull(snapshot.get("searchConfigurationId"));
                assertNotNull(snapshot.get("docIds"));
                assertNotNull("snapshot tookMs should round-trip on GET experiment", snapshot.get("tookMs"));
                long tookMs = ((Number) snapshot.get("tookMs")).longValue();
                assertTrue("snapshot tookMs should be >= 0 but was " + tookMs, tookMs >= 0);
                sawTookMs = true;
            }
        }
        assertTrue("expected at least one pairwise snapshot with tookMs", sawTookMs);

        deleteIndex(INDEX_NAME_ESCI);
    }

    @SneakyThrows
    private String createPairwiseExperiment(String querySetId, String searchConfigurationIdA, String searchConfigurationIdB) {
        String createExperimentBody = replacePlaceholders(
            Files.readString(Path.of(classLoader.getResource("experiment/CreateExperimentPairwiseComparison.json").toURI())),
            Map.of(
                "query_set_id",
                querySetId,
                "search_configuration_id_a",
                searchConfigurationIdA,
                "search_configuration_id_b",
                searchConfigurationIdB
            )
        );
        Response createExperimentResponse = makeRequest(
            client(),
            RestRequest.Method.PUT.name(),
            EXPERIMENTS_URI,
            null,
            toHttpEntity(createExperimentBody),
            ImmutableList.of(new BasicHeader(HttpHeaders.USER_AGENT, DEFAULT_USER_AGENT))
        );
        Map<String, Object> createExperimentResultJson = entityAsMap(createExperimentResponse);
        String experimentId = createExperimentResultJson.get("experiment_id").toString();
        assertNotNull(experimentId);
        assertEquals("CREATED", createExperimentResultJson.get("experiment_result").toString());
        return experimentId;
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> extractHits(Map<String, Object> searchResponse) {
        Map<String, Object> hits = (Map<String, Object>) searchResponse.get("hits");
        assertNotNull(hits);
        return (List<Map<String, Object>>) hits.get("hits");
    }
}
