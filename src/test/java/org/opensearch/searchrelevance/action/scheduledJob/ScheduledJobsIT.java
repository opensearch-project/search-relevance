/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */
package org.opensearch.searchrelevance.action.scheduledJob;

import static org.opensearch.searchrelevance.common.PluginConstants.EXPERIMENTS_URI;
import static org.opensearch.searchrelevance.common.PluginConstants.SCHEDULED_EXPERIMENT_HISTORY_INDEX;
import static org.opensearch.searchrelevance.common.PluginConstants.SCHEDULED_EXPERIMENT_URL;
import static org.opensearch.searchrelevance.common.PluginConstants.SCHEDULED_JOBS_INDEX;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.hc.core5.http.HttpHeaders;
import org.apache.hc.core5.http.message.BasicHeader;
import org.opensearch.client.Response;
import org.opensearch.client.ResponseException;
import org.opensearch.rest.RestRequest;
import org.opensearch.searchrelevance.experiment.BaseExperimentIT;
import org.opensearch.test.OpenSearchIntegTestCase;

import com.carrotsearch.randomizedtesting.annotations.ThreadLeakScope;
import com.google.common.collect.ImmutableList;

import lombok.SneakyThrows;

@ThreadLeakScope(ThreadLeakScope.Scope.NONE)
@OpenSearchIntegTestCase.ClusterScope(scope = OpenSearchIntegTestCase.Scope.SUITE)
public class ScheduledJobsIT extends BaseExperimentIT {
    String searchConfigurationId;
    String querySetId;
    String judgmentId;
    String experimentId;

    public static final int CRON_JOB_COMPLETION_MS = 65000;

    public void setUpEnvironment() throws Exception {
        // Arrange
        initializeIndexIfNotExist(BASE_INDEX_NAME_ESCI);

        searchConfigurationId = createSearchConfiguration(BASE_INDEX_NAME_ESCI);
        querySetId = createQuerySet();
        judgmentId = createJudgment();

        // Act
        experimentId = createPointwiseExperiment(querySetId, searchConfigurationId, judgmentId);

        Thread.sleep(DEFAULT_INTERVAL_MS);
    }

    // Create, read, then delete job
    @SneakyThrows
    public void testMainActions_whenCreateReadDeleteScheduledExperiment_thenSuccessful() {
        setUpEnvironment();
        // Create the Scheduled Experiment and check that the results are as expected
        String scheduledExperimentId = createScheduledExperiment();

        // Read the scheduled experiment that was created
        String getScheduledExperimentByIdUrl = String.join("/", SCHEDULED_JOBS_INDEX, "_doc", scheduledExperimentId);
        Response getScheduledExperimentResponse = makeRequest(
            client(),
            RestRequest.Method.GET.name(),
            getScheduledExperimentByIdUrl,
            null,
            null,
            ImmutableList.of(new BasicHeader(HttpHeaders.USER_AGENT, DEFAULT_USER_AGENT))
        );
        Map<String, Object> getScheduledExperimentResultJson = entityAsMap(getScheduledExperimentResponse);
        assertNotNull(getScheduledExperimentResultJson);
        assertEquals(scheduledExperimentId, getScheduledExperimentResultJson.get("_id").toString());
        Map<String, Object> source = (Map<String, Object>) getScheduledExperimentResultJson.get("_source");
        assertNotNull(source);
        assertNotNull(source.get("id"));
        assertNotNull(source.get("schedule"));
        assertEquals(experimentId, source.get("id"));

        // Here we have to wait until at last one
        Thread.sleep(CRON_JOB_COMPLETION_MS);

        // Read the scheduled experiment results that have been run

        String getScheduledExperimentResultsUrl = String.join("/", SCHEDULED_EXPERIMENT_HISTORY_INDEX, "_search");
        Response getScheduledExperimentResultsResponse = makeRequest(
            client(),
            RestRequest.Method.POST.name(),
            getScheduledExperimentResultsUrl,
            null,
            null,
            ImmutableList.of(new BasicHeader(HttpHeaders.USER_AGENT, DEFAULT_USER_AGENT))
        );
        Map<String, Object> getScheduledExperimentResultListJson = entityAsMap(getScheduledExperimentResultsResponse);

        assertNotNull(getScheduledExperimentResultListJson);
        List<Object> listOfScheduledExperimentResults = (List<Object>) (((Map<String, Object>) (getScheduledExperimentResultListJson.get(
            "hits"
        ))).get("hits"));
        assertTrue(listOfScheduledExperimentResults.size() > 0);

        Map<String, Object> getSingleScheduledExperimentResultJson = (Map<String, Object>) (listOfScheduledExperimentResults.get(0));

        Map<String, Object> experimentSource = (Map<String, Object>) (getSingleScheduledExperimentResultJson.get("_source"));

        assertNotNull(experimentSource);
        assertEquals("COMPLETED", experimentSource.get("status"));

        List<Map<String, Object>> results = (List<Map<String, Object>>) experimentSource.get("results");
        assertNotNull(results);

        // convert list of actual results to map of query text and evaluation id
        Map<String, Object> resultsMap = new HashMap<>();
        results.forEach(result -> {
            assertEquals(searchConfigurationId, result.get("searchConfigurationId"));
            resultsMap.put((String) result.get("queryText"), result.get("evaluationId"));
        });
        assertEquals(results.size(), resultsMap.size());

        Response deleteScheduledExperimentResponse = makeRequest(
            client(),
            RestRequest.Method.DELETE.name(),
            getScheduledExperimentByIdUrl,
            null,
            null,
            ImmutableList.of(new BasicHeader(HttpHeaders.USER_AGENT, DEFAULT_USER_AGENT))
        );
        Map<String, Object> deleteScheduledExperimentResultJson = entityAsMap(deleteScheduledExperimentResponse);
        assertNotNull(deleteScheduledExperimentResultJson);
        assertEquals("deleted", deleteScheduledExperimentResultJson.get("result").toString());

        expectThrows(
            ResponseException.class,
            () -> makeRequest(
                client(),
                RestRequest.Method.GET.name(),
                getScheduledExperimentByIdUrl,
                null,
                null,
                ImmutableList.of(new BasicHeader(HttpHeaders.USER_AGENT, DEFAULT_USER_AGENT))
            )
        );
    }

    @SneakyThrows
    private String createPointwiseExperiment(String querySetId, String searchConfigurationId, String judgmentId) {
        String createExperimentBody = replacePlaceholders(
            Files.readString(Path.of(classLoader.getResource("experiment/CreateExperimentPointwiseEvaluation.json").toURI())),
            Map.of("query_set_id", querySetId, "search_configuration_id", searchConfigurationId, "judgment_id", judgmentId)
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

        Thread.sleep(DEFAULT_INTERVAL_MS);
        return experimentId;
    }

    @SneakyThrows
    private String createScheduledExperiment() {
        String createScheduledExperimentBody = replacePlaceholders(
            Files.readString(Path.of(classLoader.getResource("scheduledJob/CreateScheduledJob.json").toURI())),
            Map.of("experiment_id", experimentId)
        );
        Response createScheduledExperimentResponse = makeRequest(
            client(),
            RestRequest.Method.POST.name(),
            SCHEDULED_EXPERIMENT_URL,
            null,
            toHttpEntity(createScheduledExperimentBody),
            ImmutableList.of(new BasicHeader(HttpHeaders.USER_AGENT, DEFAULT_USER_AGENT))
        );
        Map<String, Object> createScheduledExperimentResultJson = entityAsMap(createScheduledExperimentResponse);
        String jobId = createScheduledExperimentResultJson.get("job_id").toString();
        assertNotNull(jobId);
        assertEquals("CREATED", createScheduledExperimentResultJson.get("job_result").toString());

        Thread.sleep(DEFAULT_INTERVAL_MS);
        return jobId;
    }
}
