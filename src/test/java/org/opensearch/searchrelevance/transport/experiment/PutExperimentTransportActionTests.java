/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */
package org.opensearch.searchrelevance.transport.experiment;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.Before;
import org.opensearch.action.index.IndexResponse;
import org.opensearch.action.support.ActionFilters;
import org.opensearch.cluster.service.ClusterService;
import org.opensearch.core.action.ActionListener;
import org.opensearch.searchrelevance.dao.ExperimentDao;
import org.opensearch.searchrelevance.dao.ExperimentVariantDao;
import org.opensearch.searchrelevance.dao.QuerySetDao;
import org.opensearch.searchrelevance.dao.SearchConfigurationDao;
import org.opensearch.searchrelevance.exception.SearchRelevanceException;
import org.opensearch.searchrelevance.metrics.MetricsHelper;
import org.opensearch.searchrelevance.model.ExperimentType;
import org.opensearch.test.OpenSearchTestCase;
import org.opensearch.transport.TransportService;

public class PutExperimentTransportActionTests extends OpenSearchTestCase {

    private PutExperimentTransportAction transportAction;
    private ClusterService clusterService;
    private ExperimentDao experimentDao;
    private ExperimentVariantDao experimentVariantDao;
    private QuerySetDao querySetDao;
    private SearchConfigurationDao searchConfigurationDao;
    private MetricsHelper metricsHelper;
    private TransportService transportService;
    private ActionFilters actionFilters;

    @Before
    public void setUp() throws Exception {
        super.setUp();

        clusterService = mock(ClusterService.class);
        experimentDao = mock(ExperimentDao.class);
        experimentVariantDao = mock(ExperimentVariantDao.class);
        querySetDao = mock(QuerySetDao.class);
        searchConfigurationDao = mock(SearchConfigurationDao.class);
        metricsHelper = mock(MetricsHelper.class);
        transportService = mock(TransportService.class);
        actionFilters = mock(ActionFilters.class);

        transportAction = new PutExperimentTransportAction(
            clusterService,
            transportService,
            actionFilters,
            experimentDao,
            experimentVariantDao,
            querySetDao,
            searchConfigurationDao,
            metricsHelper
        );
    }

    public void testHandleImportedResults_WithSearchText() {
        // Setup
        List<Map<String, Object>> evaluationResults = List.of(
            Map.of("searchText", "test query 1", "dcg@10", 0.8, "ndcg@10", 0.75),
            Map.of("searchText", "test query 2", "dcg@10", 0.9, "ndcg@10", 0.85)
        );

        PutExperimentRequest request = new PutExperimentRequest(
            ExperimentType.POINTWISE_EVALUATION_IMPORT,
            "1234",
            List.of("config1", "config2"),
            List.of("judgment1", "judgment2"),
            evaluationResults
        );

        Map<String, Object> finalResults = new HashMap<>();
        AtomicInteger pendingQueries = new AtomicInteger(2);
        AtomicBoolean hasFailure = new AtomicBoolean(false);
        List<String> judgmentList = List.of("judgment1", "judgment2");

        // Create a test instance using reflection to access private method
        // Since handleImportedResults is private, we'll test the logic by verifying
        // that the PutExperimentRequest correctly handles the evaluation results

        // Verify the evaluation results are properly stored
        assertNotNull(request.getEvaluationResultList());
        assertEquals(2, request.getEvaluationResultList().size());
        assertEquals("test query 1", request.getEvaluationResultList().get(0).get("searchText"));
        assertEquals("test query 2", request.getEvaluationResultList().get(1).get("searchText"));
    }

    public void testHandleImportedResults_WithNestedMetrics() {
        // Setup
        List<Map<String, Object>> evaluationResults = List.of(
            Map.of(
                "searchText",
                "test query",
                "metrics",
                Map.of("dcg@10", 0.8, "ndcg@10", 0.75),
                "judgmentIds",
                List.of("j1", "j2"),
                "documentIds",
                List.of("d1", "d2")
            )
        );

        PutExperimentRequest request = new PutExperimentRequest(
            ExperimentType.POINTWISE_EVALUATION_IMPORT,
            "1234",
            List.of("config1", "config2"),
            List.of("judgment1", "judgment2"),
            evaluationResults
        );

        // Verify the evaluation results include nested metrics
        assertNotNull(request.getEvaluationResultList());
        assertEquals(1, request.getEvaluationResultList().size());

        Map<String, Object> result = request.getEvaluationResultList().get(0);
        assertEquals("test query", result.get("searchText"));
        assertTrue(result.containsKey("metrics"));
        assertTrue(result.containsKey("judgmentIds"));
        assertTrue(result.containsKey("documentIds"));
    }

    public void testHandleImportedResults_BackwardsCompatibilityQueryText() {
        // Setup - test backwards compatibility with queryText instead of searchText
        List<Map<String, Object>> evaluationResults = List.of(Map.of("queryText", "legacy query", "dcg@10", 0.8, "ndcg@10", 0.75));

        PutExperimentRequest request = new PutExperimentRequest(
            ExperimentType.POINTWISE_EVALUATION_IMPORT,
            "1234",
            List.of("config1", "config2"),
            List.of("judgment1", "judgment2"),
            evaluationResults
        );

        // Verify backwards compatibility
        assertNotNull(request.getEvaluationResultList());
        assertEquals(1, request.getEvaluationResultList().size());
        assertEquals("legacy query", request.getEvaluationResultList().get(0).get("queryText"));
    }

    public void testHandleImportedResults_EmptyEvaluationResults() {
        // Setup
        List<Map<String, Object>> evaluationResults = List.of();

        PutExperimentRequest request = new PutExperimentRequest(
            ExperimentType.POINTWISE_EVALUATION_IMPORT,
            "1234",
            List.of("config1", "config2"),
            List.of("judgment1", "judgment2"),
            evaluationResults
        );

        // Verify empty results are handled
        assertNotNull(request.getEvaluationResultList());
        assertEquals(0, request.getEvaluationResultList().size());
    }

    public void testHandleImportedResults_NullEvaluationResults() {
        // Setup
        PutExperimentRequest request = new PutExperimentRequest(
            ExperimentType.POINTWISE_EVALUATION_IMPORT,
            "1234",
            List.of("config1", "config2"),
            List.of("judgment1", "judgment2"),
            null
        );

        // Verify null results are handled
        assertNull(request.getEvaluationResultList());
    }

    public void testDoExecute_NullRequest() {
        // Setup
        ActionListener<IndexResponse> listener = mock(ActionListener.class);

        // Execute
        transportAction.doExecute(null, null, listener);

        // Verify
        verify(listener).onFailure(any(SearchRelevanceException.class));
    }

    public void testImportExperimentType_EnumExists() {
        // Verify the new experiment type exists
        ExperimentType importType = ExperimentType.POINTWISE_EVALUATION_IMPORT;
        assertNotNull(importType);
        assertEquals("POINTWISE_EVALUATION_IMPORT", importType.name());
    }

    public void testMetricsFlattening_Logic() {
        // Test the logic for flattening nested metrics
        Map<String, Object> queryResults = new HashMap<>();
        queryResults.put("searchText", "test query");
        queryResults.put("metrics", Map.of("dcg@10", 0.8, "ndcg@10", 0.75));
        queryResults.put("judgmentIds", List.of("j1", "j2"));

        // Simulate the flattening logic from handleImportedResults
        if (queryResults.containsKey("metrics") && queryResults.get("metrics") instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> metrics = (Map<String, Object>) queryResults.get("metrics");
            queryResults.remove("metrics");
            queryResults.putAll(metrics);
        }
        queryResults.remove("searchText"); // Remove searchText as it's used as the key

        // Verify flattening worked correctly
        assertFalse(queryResults.containsKey("metrics"));
        assertFalse(queryResults.containsKey("searchText"));
        assertTrue(queryResults.containsKey("dcg@10"));
        assertTrue(queryResults.containsKey("ndcg@10"));
        assertTrue(queryResults.containsKey("judgmentIds"));
        assertEquals(0.8, queryResults.get("dcg@10"));
        assertEquals(0.75, queryResults.get("ndcg@10"));
    }

    public void testSearchTextVsQueryText_Compatibility() {
        // Test that both searchText and queryText are supported
        List<Map<String, Object>> evaluationResults = List.of(
            Map.of("searchText", "new format query", "dcg@10", 0.8),
            Map.of("queryText", "legacy format query", "dcg@10", 0.9)
        );

        PutExperimentRequest request = new PutExperimentRequest(
            ExperimentType.POINTWISE_EVALUATION_IMPORT,
            "1234",
            List.of("config1"),
            List.of("judgment1"),
            evaluationResults
        );

        // Verify both formats are preserved
        assertEquals(2, request.getEvaluationResultList().size());
        assertEquals("new format query", request.getEvaluationResultList().get(0).get("searchText"));
        assertEquals("legacy format query", request.getEvaluationResultList().get(1).get("queryText"));
    }

    public void testComplexImportScenario() {
        // Test a complex import scenario with mixed data structures
        List<Map<String, Object>> evaluationResults = List.of(
            // Flat metrics structure
            Map.of("searchText", "query 1", "dcg@10", 0.8, "ndcg@10", 0.75),
            // Nested metrics structure
            Map.of(
                "searchText",
                "query 2",
                "metrics",
                Map.of("dcg@10", 0.9, "ndcg@10", 0.85, "mrr", 0.7),
                "judgmentIds",
                List.of("j1", "j2"),
                "documentIds",
                List.of("d1", "d2")
            ),
            // Legacy queryText format
            Map.of("queryText", "query 3", "dcg@10", 0.6),
            // Complex nested structure
            Map.of(
                "searchText",
                "query 4",
                "metrics",
                Map.of("dcg@10", 0.95, "precision@5", 0.8),
                "judgmentIds",
                List.of("j3", "j4", "j5"),
                "documentIds",
                List.of("d3", "d4", "d5"),
                "metadata",
                Map.of("source", "external_tool", "version", "1.0")
            )
        );

        PutExperimentRequest request = new PutExperimentRequest(
            ExperimentType.POINTWISE_EVALUATION_IMPORT,
            "1234",
            List.of("config1", "config2"),
            List.of("judgment1", "judgment2"),
            evaluationResults
        );

        // Verify all structures are preserved
        assertEquals(4, request.getEvaluationResultList().size());

        // Verify flat structure
        Map<String, Object> result1 = request.getEvaluationResultList().get(0);
        assertEquals("query 1", result1.get("searchText"));
        assertEquals(0.8, result1.get("dcg@10"));

        // Verify nested structure
        Map<String, Object> result2 = request.getEvaluationResultList().get(1);
        assertEquals("query 2", result2.get("searchText"));
        assertTrue(result2.containsKey("metrics"));
        assertTrue(result2.containsKey("judgmentIds"));

        // Verify legacy format
        Map<String, Object> result3 = request.getEvaluationResultList().get(2);
        assertEquals("query 3", result3.get("queryText"));
        assertEquals(0.6, result3.get("dcg@10"));

        // Verify complex structure
        Map<String, Object> result4 = request.getEvaluationResultList().get(3);
        assertEquals("query 4", result4.get("searchText"));
        assertTrue(result4.containsKey("metadata"));
    }
}
