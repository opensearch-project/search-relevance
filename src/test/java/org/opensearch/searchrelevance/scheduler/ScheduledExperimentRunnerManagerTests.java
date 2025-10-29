/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */
package org.opensearch.searchrelevance.scheduler;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;

import java.util.concurrent.CountDownLatch;

import org.opensearch.action.search.SearchResponse;
import org.opensearch.core.action.ActionListener;
import org.opensearch.searchrelevance.dao.ExperimentDao;
import org.opensearch.searchrelevance.dao.ScheduledExperimentHistoryDao;
import org.opensearch.searchrelevance.dao.ScheduledJobsDao;
import org.opensearch.searchrelevance.executors.ExperimentRunningManager;
import org.opensearch.test.OpenSearchTestCase;

public class ScheduledExperimentRunnerManagerTests extends OpenSearchTestCase {
    private ExperimentDao experimentDao;
    private ScheduledExperimentHistoryDao scheduledExperimentHistoryDao;
    private ExperimentRunningManager experimentRunningManager;
    private ScheduledJobsDao scheduledJobsDao;
    private ScheduledExperimentRunnerManager scheduledExperimentRunnerManager;
    private SearchRelevanceJobParameters jobParameters;
    private String scheduledExperimentId = "scheduled-experiment-id";

    @Override
    public void setUp() throws Exception {
        super.setUp();
        experimentDao = mock(ExperimentDao.class);
        scheduledExperimentHistoryDao = mock(ScheduledExperimentHistoryDao.class);
        experimentRunningManager = mock(ExperimentRunningManager.class);
        scheduledJobsDao = mock(ScheduledJobsDao.class);
        scheduledExperimentRunnerManager = new ScheduledExperimentRunnerManager(
            experimentDao,
            scheduledExperimentHistoryDao,
            experimentRunningManager,
            scheduledJobsDao
        );
        jobParameters = new SearchRelevanceJobParameters();
        jobParameters.setExperimentId("experiment-id");

    }

    public void testRunScheduledExperimentSuccess() {
        CountDownLatch actuallyFinished = new CountDownLatch(1);
        ExperimentCancellationToken cancellationToken = new ExperimentCancellationToken(scheduledExperimentId);
        doAnswer(invocation -> {
            ActionListener<SearchResponse> listener = invocation.getArgument(1);
            actuallyFinished.countDown();
            return null;
        }).when(experimentDao).getExperiment(anyString(), any(ActionListener.class));

        scheduledExperimentRunnerManager.runScheduledExperiment(jobParameters, scheduledExperimentId, cancellationToken, actuallyFinished);

        assertEquals(0, actuallyFinished.getCount());
    }

    public void testRunScheduledExperimentError() {
        CountDownLatch actuallyFinished = new CountDownLatch(1);
        ExperimentCancellationToken cancellationToken = new ExperimentCancellationToken(scheduledExperimentId);
        doAnswer(invocation -> {
            ActionListener<SearchResponse> listener = invocation.getArgument(1);
            listener.onFailure(new Exception("Experiment not found!"));
            return null;
        }).when(experimentDao).getExperiment(anyString(), any(ActionListener.class));

        scheduledExperimentRunnerManager.runScheduledExperiment(jobParameters, scheduledExperimentId, cancellationToken, actuallyFinished);

        assertEquals(0, actuallyFinished.getCount());
    }

    public void testRunScheduledExperimentCancelledBeforeExperimentSearch() {
        CountDownLatch actuallyFinished = new CountDownLatch(1);
        ExperimentCancellationToken cancellationToken = new ExperimentCancellationToken(scheduledExperimentId);
        cancellationToken.cancel();
        SearchResponse searchResponse = mock(SearchResponse.class);
        doAnswer(invocation -> {
            ActionListener<SearchResponse> listener = invocation.getArgument(1);
            listener.onResponse(searchResponse);
            return null;
        }).when(experimentDao).getExperiment(anyString(), any(ActionListener.class));

        scheduledExperimentRunnerManager.runScheduledExperiment(jobParameters, scheduledExperimentId, cancellationToken, actuallyFinished);

        assertEquals(0, actuallyFinished.getCount());
    }
}
