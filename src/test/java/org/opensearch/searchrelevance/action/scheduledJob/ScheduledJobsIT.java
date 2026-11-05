/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */
package org.opensearch.searchrelevance.action.scheduledJob;

import static org.opensearch.searchrelevance.common.PluginConstants.EXPERIMENTS_URI;
import static org.opensearch.searchrelevance.common.PluginConstants.EXPERIMENT_INDEX;
import static org.opensearch.searchrelevance.common.PluginConstants.SCHEDULED_EXPERIMENT_HISTORY_INDEX;
import static org.opensearch.searchrelevance.common.PluginConstants.SCHEDULED_EXPERIMENT_URL;
import static org.opensearch.searchrelevance.common.PluginConstants.SCHEDULED_JOBS_INDEX;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.apache.hc.core5.http.HttpHeaders;
import org.apache.hc.core5.http.message.BasicHeader;
import org.opensearch.client.Response;
import org.opensearch.client.ResponseException;
import org.opensearch.common.unit.TimeValue;
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
    public static final int CRON_JOB_POLL_INTERVAL_MS = 5000;
    public static final int SCHEDULED_RESULT_COMPLETION_MS = 10000;
    public static final int SCHEDULED_RESULT_POLL_INTERVAL_MS = 1000;

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

        // Read the experiment that should be updated after putting the schedule.
        String getSingleExperimentByIdUrl = String.join("/", EXPERIMENT_INDEX, "_doc", experimentId);
        // Read the scheduled experiment that was created. The jobs index is a system index, so direct reads use the
        // admin certificate client; for other users a get there is not realtime and misses an unrefreshed job.
        String getScheduledExperimentByIdUrl = String.join("/", SCHEDULED_JOBS_INDEX, "_doc", scheduledExperimentId);
        Response getScheduledExperimentResponse = makeRequest(
            adminClient(),
            RestRequest.Method.GET.name(),
            getScheduledExperimentByIdUrl,
            null,
            null,
            ImmutableList.of(new BasicHeader(HttpHeaders.USER_AGENT, DEFAULT_USER_AGENT))
        );

        Map<String, Object> getScheduledExperimentResultJson = entityAsMap(getScheduledExperimentResponse);
        assertNotNull(getScheduledExperimentResultJson);
        assertEquals(scheduledExperimentId, getScheduledExperimentResultJson.get("_id").toString());
        Map<String, Object> scheduledExperimentSource = (Map<String, Object>) getScheduledExperimentResultJson.get("_source");
        assertNotNull(scheduledExperimentSource);
        assertNotNull(scheduledExperimentSource.get("id"));
        assertNotNull(scheduledExperimentSource.get("schedule"));
        assertEquals(experimentId, scheduledExperimentSource.get("id"));

        String getScheduledExperimentJobRunByIdUrl = String.join("/", SCHEDULED_JOBS_INDEX, "_doc", experimentId);

        // Wait until the scheduled job updates its metadata after the first run.
        assertBusyWithFixedSleepTime(() -> {
            Response getScheduledExperimentJobRunResponse;
            try {
                getScheduledExperimentJobRunResponse = makeRequest(
                    adminClient(),
                    RestRequest.Method.GET.name(),
                    getScheduledExperimentJobRunByIdUrl,
                    null,
                    null,
                    ImmutableList.of(new BasicHeader(HttpHeaders.USER_AGENT, DEFAULT_USER_AGENT))
                );
            } catch (ResponseException e) {
                assertNotEquals(404, e.getResponse().getStatusLine().getStatusCode());
                throw e;
            }
            Map<String, Object> getScheduledExperimentJobRunResponseJson = entityAsMap(getScheduledExperimentJobRunResponse);
            assertNotNull(getScheduledExperimentJobRunResponseJson);
            assertEquals(experimentId, getScheduledExperimentJobRunResponseJson.get("_id").toString());
            Map<String, Object> scheduledExperimentJobRunSource = (Map<String, Object>) getScheduledExperimentJobRunResponseJson.get(
                "_source"
            );
            assertNotNull(scheduledExperimentJobRunSource);
            assertNotEquals(scheduledExperimentJobRunSource.get("enabledTime"), scheduledExperimentJobRunSource.get("lastUpdateTime"));
        }, TimeValue.timeValueMillis(CRON_JOB_COMPLETION_MS), TimeValue.timeValueMillis(CRON_JOB_POLL_INTERVAL_MS));

        String getScheduledExperimentResultsUrl = String.join("/", SCHEDULED_EXPERIMENT_HISTORY_INDEX, "_search");

        // Wait until the scheduled experiment result completes. The history index is a system index too, so the
        // search uses the admin certificate client; for other users it returns no hits.
        assertBusyWithFixedSleepTime(() -> {
            Response getScheduledExperimentResultsResponse;
            try {
                getScheduledExperimentResultsResponse = makeRequest(
                    adminClient(),
                    RestRequest.Method.POST.name(),
                    getScheduledExperimentResultsUrl,
                    null,
                    null,
                    ImmutableList.of(new BasicHeader(HttpHeaders.USER_AGENT, DEFAULT_USER_AGENT))
                );
            } catch (ResponseException e) {
                assertNotEquals(404, e.getResponse().getStatusLine().getStatusCode());
                throw e;
            }
            Map<String, Object> getScheduledExperimentResultListJson = entityAsMap(getScheduledExperimentResultsResponse);

            assertNotNull(getScheduledExperimentResultListJson);
            List<Object> listOfScheduledExperimentResults = (List<Object>) (((Map<String, Object>) (getScheduledExperimentResultListJson
                .get("hits"))).get("hits"));
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
        }, TimeValue.timeValueMillis(SCHEDULED_RESULT_COMPLETION_MS), TimeValue.timeValueMillis(SCHEDULED_RESULT_POLL_INTERVAL_MS));

        // The jobs index is a system index, so the job is deleted through the plugin's API rather than the index.
        String deleteScheduledExperimentUrl = String.join("/", SCHEDULED_EXPERIMENT_URL, scheduledExperimentId);
        Response deleteScheduledExperimentResponse = makeRequest(
            client(),
            RestRequest.Method.DELETE.name(),
            deleteScheduledExperimentUrl,
            null,
            null,
            ImmutableList.of(new BasicHeader(HttpHeaders.USER_AGENT, DEFAULT_USER_AGENT))
        );
        Map<String, Object> deleteScheduledExperimentResultJson = entityAsMap(deleteScheduledExperimentResponse);
        assertNotNull(deleteScheduledExperimentResultJson);
        assertEquals("deleted", deleteScheduledExperimentResultJson.get("result").toString());

        ResponseException deletedJobException = expectThrows(
            ResponseException.class,
            () -> makeRequest(
                adminClient(),
                RestRequest.Method.GET.name(),
                getScheduledExperimentByIdUrl,
                null,
                null,
                ImmutableList.of(new BasicHeader(HttpHeaders.USER_AGENT, DEFAULT_USER_AGENT))
            )
        );
        assertEquals(404, deletedJobException.getResponse().getStatusLine().getStatusCode());

        // Deleting the experiment removes its run history with a delete by query on the history system index. A run
        // that started before the job was deleted can still write history, so wait for every run to finish first.
        assertBusyWithFixedSleepTime(() -> {
            List<Object> statuses = historyStatusesOfExperiment();
            assertFalse("the experiment has run history before it is deleted", statuses.isEmpty());
            assertFalse("no run of the deleted job is still in progress", statuses.contains("PROCESSING"));
        }, TimeValue.timeValueMillis(CRON_JOB_COMPLETION_MS), TimeValue.timeValueMillis(SCHEDULED_RESULT_POLL_INTERVAL_MS));
        Response deleteExperimentResponse = makeRequest(
            client(),
            RestRequest.Method.DELETE.name(),
            String.join("/", EXPERIMENTS_URI, experimentId),
            null,
            null,
            ImmutableList.of(new BasicHeader(HttpHeaders.USER_AGENT, DEFAULT_USER_AGENT))
        );
        assertEquals("deleted", entityAsMap(deleteExperimentResponse).get("result").toString());
        assertBusyWithFixedSleepTime(
            () -> assertEquals("the run history of a deleted experiment is removed", List.of(), historyStatusesOfExperiment()),
            TimeValue.timeValueMillis(SCHEDULED_RESULT_COMPLETION_MS),
            TimeValue.timeValueMillis(SCHEDULED_RESULT_POLL_INTERVAL_MS)
        );
    }

    /** Returns the status of each history document of this test's experiment, read with the admin certificate client. */
    @SneakyThrows
    private List<Object> historyStatusesOfExperiment() {
        String query = "{\"size\": 100, \"query\": {\"term\": {\"experimentId\": \"" + experimentId + "\"}}}";
        Response response = makeRequest(
            adminClient(),
            RestRequest.Method.POST.name(),
            String.join("/", SCHEDULED_EXPERIMENT_HISTORY_INDEX, "_search"),
            null,
            toHttpEntity(query),
            ImmutableList.of(new BasicHeader(HttpHeaders.USER_AGENT, DEFAULT_USER_AGENT))
        );
        List<?> hits = (List<?>) ((Map<?, ?>) entityAsMap(response).get("hits")).get("hits");
        return hits.stream().map(hit -> ((Map<?, ?>) ((Map<?, ?>) hit).get("_source")).get("status")).collect(Collectors.toList());
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
