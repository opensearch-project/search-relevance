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
import static org.mockito.Mockito.when;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.Before;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.opensearch.core.action.ActionListener;
import org.opensearch.searchrelevance.executors.ExperimentTaskManager;
import org.opensearch.searchrelevance.model.ExperimentType;
import org.opensearch.searchrelevance.model.QuerySetEntry;
import org.opensearch.searchrelevance.model.SearchConfigurationDetails;
import org.opensearch.searchrelevance.scheduler.ExperimentCancellationToken;
import org.opensearch.test.OpenSearchTestCase;

import lombok.SneakyThrows;

public class HybridOptimizerExperimentProcessorTests extends OpenSearchTestCase {

    @Mock
    private ExperimentTaskManager taskManager;

    private HybridOptimizerExperimentProcessor processor;

    @Before
    @SneakyThrows
    public void setUp() {
        super.setUp();
        MockitoAnnotations.openMocks(this);
        processor = new HybridOptimizerExperimentProcessor(taskManager);
    }

    /**
     * When ratings are missing for the query, the processor continues with an empty map.
     */
    public void testRunExperimentWithNoRatingsForQuery_ContinuesWithEmptyRatings() throws InterruptedException {
        String experimentId = "exp1";
        String queryText = "hello world";
        List<String> judgmentList = List.of("judgment1");
        Map<String, Map<String, String>> queryTextToDocIdToRatings = Map.of();
        Map<String, SearchConfigurationDetails> searchConfigs = Map.of(
            "config1",
            SearchConfigurationDetails.builder().index("idx").query("q").pipeline("p").build()
        );

        when(
            taskManager.scheduleTasksAsync(
                any(ExperimentType.class),
                any(),
                any(),
                any(),
                any(),
                any(QuerySetEntry.class),
                any(Integer.class),
                any(List.class),
                any(List.class),
                any(Map.class),
                any(Map.class),
                any(AtomicBoolean.class),
                any(),
                any(),
                any(ExperimentCancellationToken.class)
            )
        ).thenReturn(CompletableFuture.completedFuture(Map.of("evaluationResults", List.of())));

        CountDownLatch latch = new CountDownLatch(1);
        AtomicBoolean failureTriggered = new AtomicBoolean(false);
        ActionListener<Map<String, Object>> listener = new ActionListener<>() {
            @Override
            public void onResponse(Map<String, Object> response) {
                latch.countDown();
            }

            @Override
            public void onFailure(Exception e) {
                failureTriggered.set(true);
                latch.countDown();
            }
        };

        processor.processHybridOptimizerExperiment(
            experimentId,
            new QuerySetEntry(queryText, Map.of()),
            searchConfigs,
            judgmentList,
            queryTextToDocIdToRatings,
            10,
            "run1",
            new ExperimentCancellationToken(experimentId),
            new ConcurrentHashMap<>(),
            listener
        );

        assertTrue("Listener should complete within timeout", latch.await(1, TimeUnit.SECONDS));
        assertFalse("Failure listener should not be triggered when ratings are missing", failureTriggered.get());
    }

    /**
     * Verify pre-built ratings are passed to the task manager for the current query.
     */
    public void testPreBuiltRatingsArePassedToTaskManager() throws InterruptedException {
        String experimentId = "exp2";
        String queryText = "query";
        String otherQueryText = "other query";
        List<String> judgmentList = List.of("judgmentA");
        Map<String, Map<String, String>> queryTextToDocIdToRatings = Map.of(
            queryText,
            Map.of("doc1", "5", "doc2", "3"),
            otherQueryText,
            Map.of("doc3", "1")
        );
        Map<String, SearchConfigurationDetails> searchConfigs = Map.of(
            "config1",
            SearchConfigurationDetails.builder().index("i").query("q").pipeline("p").build()
        );

        AtomicBoolean captured = new AtomicBoolean(false);
        doAnswer(invocation -> {
            @SuppressWarnings("unchecked")
            Map<String, String> docIdToScores = invocation.getArgument(9);
            captured.set(true);
            assertEquals(2, docIdToScores.size());
            assertEquals("5", docIdToScores.get("doc1"));
            assertEquals("3", docIdToScores.get("doc2"));
            assertNull(docIdToScores.get("doc3"));
            return CompletableFuture.completedFuture(Map.of("evaluationResults", List.of()));
        }).when(taskManager)
            .scheduleTasksAsync(
                any(ExperimentType.class),
                any(),
                any(),
                any(),
                any(),
                any(QuerySetEntry.class),
                any(Integer.class),
                any(List.class),
                any(List.class),
                any(Map.class),
                any(Map.class),
                any(AtomicBoolean.class),
                any(),
                any(),
                any(ExperimentCancellationToken.class)
            );

        CountDownLatch latch = new CountDownLatch(1);
        processor.processHybridOptimizerExperiment(
            experimentId,
            new QuerySetEntry(queryText, Map.of()),
            searchConfigs,
            judgmentList,
            queryTextToDocIdToRatings,
            10,
            "run2",
            new ExperimentCancellationToken(experimentId),
            new ConcurrentHashMap<>(),
            ActionListener.wrap(r -> latch.countDown(), e -> latch.countDown())
        );

        assertTrue("Listener should complete within timeout", latch.await(1, TimeUnit.SECONDS));
        assertTrue("Task manager should have been invoked with ratings", captured.get());
    }

    public void testCancelWhenProcessingSearchConfigs() {
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
            new QuerySetEntry(queryText, Map.of()),
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
