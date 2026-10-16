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
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.Before;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.opensearch.core.action.ActionListener;
import org.opensearch.searchrelevance.executors.ExperimentTaskManager;
import org.opensearch.searchrelevance.model.ExperimentType;
import org.opensearch.searchrelevance.model.QuerySetEntry;
import org.opensearch.searchrelevance.model.SearchConfigurationDetails;
import org.opensearch.test.OpenSearchTestCase;

import lombok.SneakyThrows;

/**
 * Tests for PointwiseExperimentProcessor
 */
public class PointwiseExperimentProcessorTests extends OpenSearchTestCase {

    @Mock
    private ExperimentTaskManager taskManager;

    private PointwiseExperimentProcessor processor;

    @Before
    @SneakyThrows
    public void setUp() {
        super.setUp();
        MockitoAnnotations.openMocks(this);
        processor = new PointwiseExperimentProcessor(taskManager);
    }

    @SneakyThrows
    public void testProcessPointwiseExperiment_Success() {
        // Setup test data
        String experimentId = "test-experiment-id";
        String queryText = "test query";
        Map<String, SearchConfigurationDetails> searchConfigurations = new HashMap<>();
        searchConfigurations.put(
            "config1",
            SearchConfigurationDetails.builder().index("test-index").query("test-query").pipeline("test-pipeline").build()
        );
        List<String> judgmentList = Arrays.asList("judgment1");
        Map<String, Map<String, String>> queryTextToDocIdToRatings = Map.of(queryText, Map.of("doc1", "5", "doc2", "3"));
        int size = 10;
        AtomicBoolean hasFailure = new AtomicBoolean(false);

        AtomicBoolean captured = new AtomicBoolean(false);
        doAnswer(invocation -> {
            @SuppressWarnings("unchecked")
            Map<String, String> docIdToScores = invocation.getArgument(9);
            captured.set(true);
            assertEquals(2, docIdToScores.size());
            assertEquals("5", docIdToScores.get("doc1"));
            assertEquals("3", docIdToScores.get("doc2"));
            return CompletableFuture.completedFuture(
                Map.of("evaluationResults", List.of(Map.of("evaluationId", "eval1", "variantId", "var1")))
            );
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
                any()
            );

        // Mock ActionListener with CountDownLatch to wait for completion
        CountDownLatch latch = new CountDownLatch(1);
        ActionListener<Map<String, Object>> listener = new ActionListener<Map<String, Object>>() {
            @Override
            public void onResponse(Map<String, Object> response) {
                assertNotNull(response);
                assertTrue(response.containsKey("results"));
                latch.countDown();
            }

            @Override
            public void onFailure(Exception e) {
                fail("Should not have failed: " + e.getMessage());
                latch.countDown();
            }
        };

        // Execute
        processor.processPointwiseExperiment(
            experimentId,
            new QuerySetEntry(queryText, Map.of()),
            searchConfigurations,
            judgmentList,
            queryTextToDocIdToRatings,
            size,
            hasFailure,
            null,
            null,
            listener
        );

        // Wait for async operation to complete
        assertTrue("Async operation should complete within timeout", latch.await(5, TimeUnit.SECONDS));
        assertTrue("Task manager should have been invoked with ratings", captured.get());
    }

    @SneakyThrows
    public void testProcessPointwiseExperiment_MissingRatingsForQuery() {
        // Setup test data
        String experimentId = "test-experiment-id";
        String queryText = "test query";
        Map<String, SearchConfigurationDetails> searchConfigurations = new HashMap<>();
        searchConfigurations.put(
            "config1",
            SearchConfigurationDetails.builder().index("test-index").query("test-query").pipeline(null).build()
        );
        List<String> judgmentList = Arrays.asList("judgment1");
        Map<String, Map<String, String>> queryTextToDocIdToRatings = Map.of();
        int size = 10;
        AtomicBoolean hasFailure = new AtomicBoolean(false);

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
                any()
            )
        ).thenReturn(
            CompletableFuture.completedFuture(Map.of("evaluationResults", List.of(Map.of("evaluationId", "eval1", "variantId", "var1"))))
        );

        // Mock ActionListener with CountDownLatch to wait for completion
        CountDownLatch latch = new CountDownLatch(1);
        ActionListener<Map<String, Object>> listener = new ActionListener<Map<String, Object>>() {
            @Override
            public void onResponse(Map<String, Object> response) {
                assertNotNull(response);
                latch.countDown();
            }

            @Override
            public void onFailure(Exception e) {
                fail("Should not have failed: " + e.getMessage());
                latch.countDown();
            }
        };

        // Execute
        processor.processPointwiseExperiment(
            experimentId,
            new QuerySetEntry(queryText, Map.of()),
            searchConfigurations,
            judgmentList,
            queryTextToDocIdToRatings,
            size,
            hasFailure,
            null,
            null,
            listener
        );

        // Wait for async operation to complete
        assertTrue("Async operation should complete within timeout", latch.await(5, TimeUnit.SECONDS));
    }

    public void testCreatePointwiseVariants() {
        // Test constructor to ensure processor is properly initialized
        assertNotNull("Processor should be initialized", processor);
        assertNotNull("TaskManager should be injected", taskManager);
    }
}
