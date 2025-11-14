/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */
package org.opensearch.searchrelevance.experiment;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.Before;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.opensearch.ResourceNotFoundException;
import org.opensearch.action.search.SearchResponse;
import org.opensearch.core.action.ActionListener;
import org.opensearch.searchrelevance.dao.JudgmentDao;
import org.opensearch.searchrelevance.executors.ExperimentTaskManager;
import org.opensearch.searchrelevance.model.SearchConfigurationDetails;
import org.opensearch.searchrelevance.scheduler.ExperimentCancellationToken;
import org.opensearch.test.OpenSearchTestCase;

import lombok.SneakyThrows;

public class HybridOptimizerExperimentProcessorTests extends OpenSearchTestCase {
    @Mock
    private JudgmentDao judgmentDao;

    @Mock
    private ExperimentTaskManager taskManager;

    private HybridOptimizerExperimentProcessor processor;

    @Before
    @SneakyThrows
    public void setUp() {
        super.setUp();
        MockitoAnnotations.openMocks(this);
        processor = new HybridOptimizerExperimentProcessor(judgmentDao, taskManager);
    }

    /**
     * Run experiment after deleting a judgment
     */
    public void testRunExperimentAfterDeletedJudgment_TransitionsToError() throws InterruptedException {
        // Setup test data
        String experimentId = "exp1";
        String queryText = "hello world";
        List<String> judgmentList = List.of("judgment1");
        Map<String, SearchConfigurationDetails> searchConfigs = Map.of(
            "config1",
            SearchConfigurationDetails.builder().index("idx").query("q").pipeline("p").build()
        );

        // Mock deleted judgment
        doAnswer(invocation -> {
            ActionListener<SearchResponse> listener = invocation.getArgument(1);
            listener.onFailure(new ResourceNotFoundException("Document not found"));
            return null;
        }).when(judgmentDao).getJudgment(any(), any());

        AtomicBoolean failureTriggered = new AtomicBoolean(false);
        CountDownLatch latch = new CountDownLatch(1);

        ActionListener<Map<String, Object>> listener = new ActionListener<>() {
            @Override
            public void onResponse(Map<String, Object> response) {
                fail("Experiment should not succeed when judgment is deleted");
            }

            @Override
            public void onFailure(Exception e) {
                failureTriggered.set(true);
                latch.countDown();
            }
        };

        processor.processHybridOptimizerExperiment(
            experimentId,
            queryText,
            searchConfigs,
            judgmentList,
            10,
            "run1",
            new ExperimentCancellationToken(experimentId),
            new ConcurrentHashMap<>(),
            listener
        );

        boolean completed = latch.await(1, TimeUnit.SECONDS);
        assertTrue("Listener should complete within timeout", completed);
        assertTrue("Failure listener should be triggered on deleted judgment", failureTriggered.get());
    }

    /**
     * Concurrent failures → single failure notification
     */
    public void testConcurrentDeletedJudgments_SingleFailureNotification() {
        // Setup test data
        String experimentId = "exp2";
        String queryText = "query";
        List<String> judgmentList = List.of("judgmentA", "judgmentB", "judgmentC");
        Map<String, SearchConfigurationDetails> searchConfigs = Map.of(
            "config1",
            SearchConfigurationDetails.builder().index("i").query("q").pipeline("p").build()
        );

        // Mock deleted judgment
        doAnswer(invocation -> {
            ActionListener<SearchResponse> listener = invocation.getArgument(1);
            listener.onFailure(new ResourceNotFoundException("Document not found"));
            return null;
        }).when(judgmentDao).getJudgment(any(), any());

        AtomicInteger failureCount = new AtomicInteger(0);
        ActionListener<Map<String, Object>> listener = new ActionListener<>() {
            @Override
            public void onResponse(Map<String, Object> response) {
                fail("Experiment should not succeed when judgment is deleted");
            }

            @Override
            public void onFailure(Exception e) {
                failureCount.incrementAndGet();
            }
        };

        processor.processHybridOptimizerExperiment(
            experimentId,
            queryText,
            searchConfigs,
            judgmentList,
            10,
            "run2",
            new ExperimentCancellationToken(experimentId),
            new ConcurrentHashMap<>(),
            listener
        );

        assertEquals("Only one onFailure() should trigger for concurrent failures", 1L, failureCount.get());
    }

    public void testCancelWhenProcessingSearchConfigs() {
        // Setup test data
        String experimentId = "test-experiment-id";
        String queryText = "test query";
        Map<String, SearchConfigurationDetails> searchConfigurations = new HashMap<>();
        searchConfigurations.put(
            "config1",
            SearchConfigurationDetails.builder().index("test-index").query("test-query").pipeline("test-pipeline").build()
        );
        List<String> judgmentList = Arrays.asList("judgment1");
        int size = 10;
        AtomicBoolean hasFailure = new AtomicBoolean(false);
        ActionListener<Map<String, Object>> listener = new ActionListener<>() {
            @Override
            public void onResponse(Map<String, Object> response) {
                fail("Should not have succeeded");
            }

            @Override
            public void onFailure(Exception e) {
                assertTrue(e instanceof TimeoutException);
            }
        };

        ExperimentCancellationToken cancellationToken = new ExperimentCancellationToken(experimentId);
        cancellationToken.cancel();
        processor.processSearchConfigurationsAsync(
            experimentId,
            queryText,
            searchConfigurations,
            judgmentList,
            size,
            null,
            null,
            hasFailure,
            queryText,
            cancellationToken,
            null,
            listener
        );
    }
}
