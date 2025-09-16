/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */
package org.opensearch.searchrelevance.experiment;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.Before;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
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
        ActionListener<Map<String, Object>> listener = new ActionListener<Map<String, Object>>() {
            @Override
            public void onResponse(Map<String, Object> response) {
                fail("Should not have succeded");
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
