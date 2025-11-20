/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */
package org.opensearch.searchrelevance.experiment;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
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
import org.opensearch.action.search.SearchResponse;
import org.opensearch.core.action.ActionListener;
import org.opensearch.searchrelevance.dao.JudgmentDao;
import org.opensearch.searchrelevance.executors.ExperimentTaskManager;
import org.opensearch.searchrelevance.model.ExperimentType;
import org.opensearch.searchrelevance.model.SearchConfigurationDetails;
import org.opensearch.test.OpenSearchTestCase;

import lombok.SneakyThrows;

/**
 * Tests for PointwiseExperimentProcessor
 */
public class PointwiseExperimentProcessorTests extends OpenSearchTestCase {

    @Mock
    private JudgmentDao judgmentDao;

    @Mock
    private ExperimentTaskManager taskManager;

    private PointwiseExperimentProcessor processor;

    @Before
    @SneakyThrows
    public void setUp() {
        super.setUp();
        MockitoAnnotations.openMocks(this);
        processor = new PointwiseExperimentProcessor(judgmentDao, taskManager);
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
        int size = 10;
        AtomicBoolean hasFailure = new AtomicBoolean(false);

        // Mock successful judgment response with actual judgment data
        doAnswer(invocation -> {
            ActionListener<SearchResponse> listener = invocation.getArgument(1);
            SearchResponse mockResponse = mock(SearchResponse.class);
            when(mockResponse.getHits()).thenReturn(null);
            listener.onResponse(mockResponse);
            return null;
        }).when(judgmentDao).getJudgment(anyString(), any(ActionListener.class));

        // Mock task manager response
        CompletableFuture<Map<String, Object>> mockFuture = CompletableFuture.completedFuture(
            Map.of("evaluationResults", List.of(Map.of("evaluationId", "eval1", "variantId", "var1")))
        );
        when(
            taskManager.scheduleTasksAsync(
                any(ExperimentType.class),
                anyString(),
                anyString(),
                anyString(),
                anyString(),
                anyString(),
                any(Integer.class),
                any(List.class),
                any(List.class),
                any(Map.class),
                any(Map.class),
                any(AtomicBoolean.class),
                isNull(),
                isNull(),
                isNull()
            )
        ).thenReturn(mockFuture);

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
            queryText,
            searchConfigurations,
            judgmentList,
            size,
            hasFailure,
            null,
            null,
            listener
        );

        // Wait for async operation to complete
        assertTrue("Async operation should complete within timeout", latch.await(5, TimeUnit.SECONDS));

        // Verify interactions
        verify(judgmentDao).getJudgment(anyString(), any(ActionListener.class));
    }

    @SneakyThrows
    public void testProcessPointwiseExperiment_JudgmentFailure() {
        // Setup test data
        String experimentId = "test-experiment-id";
        String queryText = "test query";
        Map<String, SearchConfigurationDetails> searchConfigurations = new HashMap<>();
        searchConfigurations.put(
            "config1",
            SearchConfigurationDetails.builder().index("test-index").query("test-query").pipeline(null).build()
        );
        List<String> judgmentList = Arrays.asList("judgment1");
        int size = 10;
        AtomicBoolean hasFailure = new AtomicBoolean(false);

        // Mock judgment failure
        doAnswer(invocation -> {
            ActionListener<SearchResponse> listener = invocation.getArgument(1);
            listener.onFailure(new RuntimeException("Judgment fetch failed"));
            return null;
        }).when(judgmentDao).getJudgment(anyString(), any(ActionListener.class));

        // Mock ActionListener with CountDownLatch to wait for completion
        CountDownLatch latch = new CountDownLatch(1);
        ActionListener<Map<String, Object>> listener = new ActionListener<Map<String, Object>>() {
            @Override
            public void onResponse(Map<String, Object> response) {
                fail("Should have failed, but got response");
                latch.countDown();
            }

            @Override
            public void onFailure(Exception e) {
                assertNotNull(e);
                latch.countDown();
            }
        };

        // Execute
        processor.processPointwiseExperiment(
            experimentId,
            queryText,
            searchConfigurations,
            judgmentList,
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

        // Test that the processor has proper dependencies
        assertNotNull("JudgmentDao should be injected", judgmentDao);
        assertNotNull("TaskManager should be injected", taskManager);
    }
}
