/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */
package org.opensearch.searchrelevance.scheduler;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.opensearch.common.unit.TimeValue;
import org.opensearch.core.action.ActionListener;
import org.opensearch.jobscheduler.spi.JobExecutionContext;
import org.opensearch.jobscheduler.spi.LockModel;
import org.opensearch.jobscheduler.spi.ScheduledJobParameter;
import org.opensearch.jobscheduler.spi.utils.LockService;
import org.opensearch.searchrelevance.settings.SearchRelevanceSettingsAccessor;
import org.opensearch.test.OpenSearchTestCase;
import org.opensearch.threadpool.ThreadPool;
import org.opensearch.transport.client.Client;

/**
 * Mocks and sets up unit tests for {@link SearchRelevanceJobRunner}.
 *
 * <p>
 * A lot of these tests will be related to scheduling experiment tasks and testing
 * whether cleanup runs properly depending on different errors within
 * </p>
 */
public class SearchRelevanceJobRunnerTests extends OpenSearchTestCase {
    private SearchRelevanceJobRunner searchRelevanceJobRunner;
    private String experimentId = "experiment";
    private ThreadPool threadPool;
    private Client client;
    private SearchRelevanceSettingsAccessor settingsAccessor;
    private ScheduledExperimentRunnerManager manager;
    private ExecutorService immediateExecutor;
    private ScheduledExecutorService scheduledExecutor;
    private SearchRelevanceJobParameters jobParameters;
    private JobExecutionContext jobExecutionContext;
    private LockService lockService;

    @Override
    public void setUp() throws Exception {
        super.setUp();
        searchRelevanceJobRunner = SearchRelevanceJobRunner.INSTANCE;
        threadPool = mock(ThreadPool.class);
        client = mock(Client.class);
        settingsAccessor = mock(SearchRelevanceSettingsAccessor.class);
        manager = mock(ScheduledExperimentRunnerManager.class);
        searchRelevanceJobRunner.setThreadPool(threadPool);
        searchRelevanceJobRunner.setClient(client);
        searchRelevanceJobRunner.setSettingsAccessor(settingsAccessor);
        searchRelevanceJobRunner.setManager(manager);

        // Set default timeout for experiment
        when(settingsAccessor.getScheduledExperimentsTimeout()).thenReturn(TimeValue.timeValueMinutes(60));

        // Create an immediate executor
        immediateExecutor = mock(ExecutorService.class);
        doAnswer(invocation -> {
            Runnable command = invocation.getArgument(0);
            command.run();
            return null;
        }).when(immediateExecutor).execute(any(Runnable.class));
        doAnswer(invocation -> {
            // Here we run the command that is supposed to hold the experiment run without the timeout.
            // We also synchronously run the command for that experiment and mark it as complete.
            Runnable command = invocation.getArgument(0);
            command.run();
            CompletableFuture<?> future = invocation.getArgument(1);
            future.complete(null);
            return future;
        }).when(immediateExecutor).submit(any(Runnable.class), any(CompletableFuture.class));

        doAnswer(invocation -> {
            // Here we run the command that is supposed to hold the experiment run without the timeout.
            // We also synchronously run the command for that experiment and mark it as complete.
            Runnable command = invocation.getArgument(0);
            command.run();
            CompletableFuture<?> future = new CompletableFuture<>();
            future.complete(null);
            return future;
        }).when(immediateExecutor).submit(any(Runnable.class));

        // Create a scheduled executor
        scheduledExecutor = mock(ScheduledExecutorService.class);

        // Setup thread pool mocks
        when(threadPool.generic()).thenReturn(immediateExecutor);
        when(threadPool.scheduler()).thenReturn(scheduledExecutor);
        doAnswer(invocation -> {
            // We do not have to worry about cancellation being scheduled for this general case or timeout.
            return null;
        }).when(scheduledExecutor).schedule(any(Runnable.class), anyLong(), any(TimeUnit.class));

        jobParameters = new SearchRelevanceJobParameters();
        jobParameters.setExperimentId("experiment-id");
        jobParameters.setLockDurationSeconds(20L);

        jobExecutionContext = mock(JobExecutionContext.class);

        lockService = mock(LockService.class);
        when(jobExecutionContext.getLockService()).thenReturn(lockService);
    }

    public void testCleanupReachedForSuccessfulExperiment() {
        doAnswer(invocation -> {
            CountDownLatch actuallyFinished = invocation.getArgument(3);
            actuallyFinished.countDown();
            return null;
        }).when(manager)
            .runScheduledExperiment(
                any(SearchRelevanceJobParameters.class),
                any(String.class),
                any(ExperimentCancellationToken.class),
                any(CountDownLatch.class)
            );
        doAnswer(invocation -> {
            ActionListener<LockModel> actionListener = invocation.getArgument(2);
            LockModel model = new LockModel("experiment-run", "DEFAULT_LINE_DOCS_FILE", Instant.now(), 30L, false);
            actionListener.onResponse(model);
            return null;
        }).when(lockService).acquireLock(any(ScheduledJobParameter.class), any(JobExecutionContext.class), any(ActionListener.class));

        searchRelevanceJobRunner.runJob(jobParameters, jobExecutionContext);

        // Verify that the ScheduledExperimentRunnerManager actually ran the experiment.
        verify(manager, times(1)).runScheduledExperiment(
            any(SearchRelevanceJobParameters.class),
            any(String.class),
            any(ExperimentCancellationToken.class),
            any(CountDownLatch.class)
        );

        // Verify that the cleanup stage was actually reached.
        verify(manager, times(1)).cleanupResources(any(String.class), any(String.class), any(ExperimentCancellationToken.class));
        verify(lockService, times(1)).release(any(), any());
    }

    public void testCleanupReachedForFailedExperimentRunAttempt() {
        doThrow(new IllegalStateException()).when(manager)
            .runScheduledExperiment(
                any(SearchRelevanceJobParameters.class),
                any(String.class),
                any(ExperimentCancellationToken.class),
                any(CountDownLatch.class)
            );
        doAnswer(invocation -> {
            ActionListener<LockModel> actionListener = invocation.getArgument(2);
            LockModel model = new LockModel("experiment-run", "experiment-id", Instant.now(), 30L, false);
            actionListener.onResponse(model);
            return null;
        }).when(lockService).acquireLock(any(ScheduledJobParameter.class), any(JobExecutionContext.class), any(ActionListener.class));

        searchRelevanceJobRunner.runJob(jobParameters, jobExecutionContext);

        // Verify that the ScheduledExperimentRunnerManager actually ran the experiment.
        verify(manager, times(1)).runScheduledExperiment(
            any(SearchRelevanceJobParameters.class),
            any(String.class),
            any(ExperimentCancellationToken.class),
            any(CountDownLatch.class)
        );

        // Verify that the cleanup stage was actually reached.
        verify(manager, times(1)).cleanupResources(any(String.class), any(String.class), any(ExperimentCancellationToken.class));
        verify(lockService, times(1)).release(any(), any());
    }

    public void testCleanupReachedForFailedLockAcquisitionRunAttempt() {
        doAnswer(invocation -> {
            CountDownLatch actuallyFinished = invocation.getArgument(3);
            actuallyFinished.countDown();
            return null;
        }).when(manager)
            .runScheduledExperiment(
                any(SearchRelevanceJobParameters.class),
                any(String.class),
                any(ExperimentCancellationToken.class),
                any(CountDownLatch.class)
            );
        doAnswer(invocation -> {
            ActionListener<LockModel> actionListener = invocation.getArgument(2);
            actionListener.onFailure(new IllegalAccessException());
            return null;
        }).when(lockService).acquireLock(any(ScheduledJobParameter.class), any(JobExecutionContext.class), any(ActionListener.class));

        searchRelevanceJobRunner.runJob(jobParameters, jobExecutionContext);

        // Verify that the cleanup stage was actually reached.
        verify(manager, times(1)).cleanupResources(any(String.class), any(String.class), any(ExperimentCancellationToken.class));
    }
}
