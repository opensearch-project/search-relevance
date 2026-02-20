/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */
package org.opensearch.searchrelevance.experiment;

import static org.opensearch.searchrelevance.experiment.ExperimentOptionsForHybridSearch.EXPERIMENT_OPTION_COMBINATION_TECHNIQUE;
import static org.opensearch.searchrelevance.experiment.ExperimentOptionsForHybridSearch.EXPERIMENT_OPTION_NORMALIZATION_TECHNIQUE;
import static org.opensearch.searchrelevance.experiment.QuerySourceUtil.POOL_COMBINATION;
import static org.opensearch.searchrelevance.experiment.QuerySourceUtil.POOL_NORMALIZATION;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.opensearch.searchrelevance.model.ExperimentVariant;
import org.opensearch.test.OpenSearchTestCase;

import lombok.SneakyThrows;

public class QuerySourceUtilTests extends OpenSearchTestCase {

    public void testCreateDefinitionOfTemporarySearchPipeline_ValidInput_ReturnsCorrectStructure() {
        // Given
        ExperimentVariant experimentHybridSearchDao = ExperimentVariant.builder()
            .parameters(
                Map.of(EXPERIMENT_OPTION_NORMALIZATION_TECHNIQUE, "min_max", EXPERIMENT_OPTION_COMBINATION_TECHNIQUE, "arithmetic_mean")
            )
            .build();

        // When
        Map<String, Object> result = QuerySourceUtil.createDefinitionOfTemporarySearchPipeline(experimentHybridSearchDao);

        // Then
        assertNotNull(result);
        assertTrue(result.containsKey("phase_results_processors"));

        List<?> processors = (List<?>) result.get("phase_results_processors");
        assertEquals(1, processors.size());

        Map<?, ?> processorObject = (Map<?, ?>) processors.get(0);
        assertTrue(processorObject.containsKey("normalization-processor"));

        Map<?, ?> normalizationProcessor = (Map<?, ?>) processorObject.get("normalization-processor");
        Map<?, ?> normalization = (Map<?, ?>) normalizationProcessor.get("normalization");
        Map<?, ?> combination = (Map<?, ?>) normalizationProcessor.get("combination");

        assertEquals("min_max", normalization.get("technique"));
        assertEquals("arithmetic_mean", combination.get("technique"));
    }

    public void testCreateDefinitionOfTemporarySearchPipeline_NullInput_ThrowsNullPointerException() {
        // When & Then
        assertThrows(NullPointerException.class, () -> QuerySourceUtil.createDefinitionOfTemporarySearchPipeline(null));
    }

    @SneakyThrows
    public void testValidateHybridQuery_ValidQuery() {
        Map<String, Object> hybridQueries = new HashMap<>();
        hybridQueries.put("queries", Arrays.asList(new HashMap<>(), new HashMap<>())); // Two subqueries

        Map<String, Object> hybrid = new HashMap<>();
        hybrid.put("hybrid", hybridQueries);

        Map<String, Object> fullQuery = new HashMap<>();
        fullQuery.put("query", hybrid);

        QuerySourceUtil.validateHybridQuery(fullQuery);
    }

    public void testValidateHybridQuery_MissingQuery() {
        Map<String, Object> fullQuery = new HashMap<>();

        IllegalArgumentException exception = assertThrows(
            IllegalArgumentException.class,
            () -> QuerySourceUtil.validateHybridQuery(fullQuery)
        );
        assertEquals("search configuration must have at least one query", exception.getMessage());
    }

    public void testValidateHybridQuery_InvalidQueryType() {
        Map<String, Object> fullQuery = new HashMap<>();
        fullQuery.put("query", "not_a_map");

        IllegalArgumentException exception = assertThrows(
            IllegalArgumentException.class,
            () -> QuerySourceUtil.validateHybridQuery(fullQuery)
        );
        assertEquals("search configuration must have at least one query", exception.getMessage());
    }

    public void testValidateHybridQuery_MissingHybrid() {
        Map<String, Object> query = new HashMap<>();
        Map<String, Object> fullQuery = new HashMap<>();
        fullQuery.put("query", query);

        IllegalArgumentException exception = assertThrows(
            IllegalArgumentException.class,
            () -> QuerySourceUtil.validateHybridQuery(fullQuery)
        );
        assertEquals("query in search configuration must be of type [hybrid]", exception.getMessage());
    }

    public void testValidateHybridQuery_InvalidHybridType() {
        Map<String, Object> query = new HashMap<>();
        query.put("hybrid", "not_a_map");
        Map<String, Object> fullQuery = new HashMap<>();
        fullQuery.put("query", query);

        IllegalArgumentException exception = assertThrows(
            IllegalArgumentException.class,
            () -> QuerySourceUtil.validateHybridQuery(fullQuery)
        );
        assertEquals("query in search configuration must be of type [hybrid]", exception.getMessage());
    }

    public void testValidateHybridQuery_MissingQueries() {
        Map<String, Object> hybridMap = new HashMap<>();
        Map<String, Object> query = new HashMap<>();
        query.put("hybrid", hybridMap);
        Map<String, Object> fullQuery = new HashMap<>();
        fullQuery.put("query", query);

        IllegalArgumentException exception = assertThrows(
            IllegalArgumentException.class,
            () -> QuerySourceUtil.validateHybridQuery(fullQuery)
        );
        assertEquals("[hybrid] query in search configuration does not have sub-queries", exception.getMessage());
    }

    public void testValidateHybridQuery_InvalidQueriesType() {
        Map<String, Object> hybridMap = new HashMap<>();
        hybridMap.put("queries", "not_a_list");
        Map<String, Object> query = new HashMap<>();
        query.put("hybrid", hybridMap);
        Map<String, Object> fullQuery = new HashMap<>();
        fullQuery.put("query", query);

        IllegalArgumentException exception = assertThrows(
            IllegalArgumentException.class,
            () -> QuerySourceUtil.validateHybridQuery(fullQuery)
        );
        assertEquals("[hybrid] query in search configuration does not have sub-queries", exception.getMessage());
    }

    public void testValidateHybridQuery_whenOneSubquery_thenFail() {
        Map<String, Object> hybridMap = new HashMap<>();
        hybridMap.put("queries", Collections.singletonList(new HashMap<>())); // only one query instead of two
        Map<String, Object> query = new HashMap<>();
        query.put("hybrid", hybridMap);
        Map<String, Object> fullQuery = new HashMap<>();
        fullQuery.put("query", query);

        IllegalArgumentException exception = assertThrows(
            IllegalArgumentException.class,
            () -> QuerySourceUtil.validateHybridQuery(fullQuery)
        );
        assertEquals("invalid hybrid query: expected exactly [2] sub-queries but found [1]", exception.getMessage());
    }

    public void testValidateHybridQuery_whenThreeSubqueries_thenFail() {
        Map<String, Object> hybridMap = new HashMap<>();
        List<Map<?, ?>> queries = Arrays.asList(Map.of(), Map.of(), Map.of());
        hybridMap.put("queries", queries);
        Map<String, Object> query = new HashMap<>();
        query.put("hybrid", hybridMap);
        Map<String, Object> fullQuery = new HashMap<>();
        fullQuery.put("query", query);

        IllegalArgumentException exception = assertThrows(
            IllegalArgumentException.class,
            () -> QuerySourceUtil.validateHybridQuery(fullQuery)
        );
        assertEquals("invalid hybrid query: expected exactly [2] sub-queries but found [3]", exception.getMessage());
    }

    public void testIsHybridQuery_ValidHybrid_ReturnsTrue() {
        Map<String, Object> hybridQueries = new HashMap<>();
        hybridQueries.put("queries", Arrays.asList(new HashMap<>(), new HashMap<>()));
        Map<String, Object> hybrid = new HashMap<>();
        hybrid.put("hybrid", hybridQueries);
        Map<String, Object> fullQuery = new HashMap<>();
        fullQuery.put("query", hybrid);

        assertTrue(QuerySourceUtil.isHybridQuery(fullQuery));
    }

    public void testIsHybridQuery_NonHybridQuery_ReturnsFalse() {
        Map<String, Object> matchQuery = new HashMap<>();
        matchQuery.put("match", Map.of("title", "test"));
        Map<String, Object> fullQuery = new HashMap<>();
        fullQuery.put("query", matchQuery);

        assertFalse(QuerySourceUtil.isHybridQuery(fullQuery));
    }

    public void testIsHybridQuery_EmptyMap_ReturnsFalse() {
        Map<String, Object> emptyMap = new HashMap<>();
        assertFalse(QuerySourceUtil.isHybridQuery(emptyMap));
    }

    public void testIsHybridQuery_NullMap_ReturnsFalse() {
        assertFalse(QuerySourceUtil.isHybridQuery(null));
    }

    public void testIsHybridQuery_WrongSubqueryCount_ReturnsFalse() {
        // isHybridQuery is strict: requires exactly 2 sub-queries
        Map<String, Object> hybridQueries = new HashMap<>();
        hybridQueries.put("queries", Collections.singletonList(new HashMap<>()));
        Map<String, Object> hybrid = new HashMap<>();
        hybrid.put("hybrid", hybridQueries);
        Map<String, Object> fullQuery = new HashMap<>();
        fullQuery.put("query", hybrid);

        assertFalse(QuerySourceUtil.isHybridQuery(fullQuery));
    }

    public void testIsHybridQueryAnySize_1SubQuery_ReturnsTrue() {
        Map<String, Object> fullQuery = buildHybridQuery(1);
        assertTrue(QuerySourceUtil.isHybridQueryAnySize(fullQuery));
    }

    public void testIsHybridQueryAnySize_2SubQueries_ReturnsTrue() {
        Map<String, Object> fullQuery = buildHybridQuery(2);
        assertTrue(QuerySourceUtil.isHybridQueryAnySize(fullQuery));
    }

    public void testIsHybridQueryAnySize_3SubQueries_ReturnsTrue() {
        Map<String, Object> fullQuery = buildHybridQuery(3);
        assertTrue(QuerySourceUtil.isHybridQueryAnySize(fullQuery));
    }

    public void testIsHybridQueryAnySize_5SubQueries_ReturnsTrue() {
        Map<String, Object> fullQuery = buildHybridQuery(5);
        assertTrue(QuerySourceUtil.isHybridQueryAnySize(fullQuery));
    }

    public void testIsHybridQueryAnySize_NonHybrid_ReturnsFalse() {
        Map<String, Object> matchQuery = new HashMap<>();
        matchQuery.put("match", Map.of("title", "test"));
        Map<String, Object> fullQuery = new HashMap<>();
        fullQuery.put("query", matchQuery);

        assertFalse(QuerySourceUtil.isHybridQueryAnySize(fullQuery));
    }

    public void testIsHybridQueryAnySize_Null_ReturnsFalse() {
        assertFalse(QuerySourceUtil.isHybridQueryAnySize(null));
    }

    public void testGetSubQueryCount_2SubQueries() {
        Map<String, Object> fullQuery = buildHybridQuery(2);
        assertEquals(2, QuerySourceUtil.getSubQueryCount(fullQuery));
    }

    public void testGetSubQueryCount_3SubQueries() {
        Map<String, Object> fullQuery = buildHybridQuery(3);
        assertEquals(3, QuerySourceUtil.getSubQueryCount(fullQuery));
    }

    public void testGetSubQueryCount_5SubQueries() {
        Map<String, Object> fullQuery = buildHybridQuery(5);
        assertEquals(5, QuerySourceUtil.getSubQueryCount(fullQuery));
    }

    public void testGetSubQueryCount_NonHybrid_Throws() {
        Map<String, Object> fullQuery = new HashMap<>();
        fullQuery.put("query", Map.of("match", Map.of("title", "test")));
        assertThrows(IllegalArgumentException.class, () -> QuerySourceUtil.getSubQueryCount(fullQuery));
    }

    public void testGetSubQueryCount_Null_Throws() {
        assertThrows(IllegalArgumentException.class, () -> QuerySourceUtil.getSubQueryCount(null));
    }

    public void testGeneratePoolingWeights_1SubQuery() {
        List<List<Float>> weights = QuerySourceUtil.generatePoolingWeights(1);
        assertEquals(2, weights.size()); // 1 equal + 1 one-hot
        // Equal weights: null (omit parameters to trigger default equal-weight behavior)
        assertNull(weights.get(0));
        // One-hot: [1.0]
        assertEquals(1.0f, weights.get(1).get(0), 0.001);
    }

    public void testGeneratePoolingWeights_2SubQueries() {
        List<List<Float>> weights = QuerySourceUtil.generatePoolingWeights(2);
        assertEquals(3, weights.size()); // 1 equal + 2 one-hot

        // Equal weights: null (omit parameters to trigger default equal-weight behavior)
        assertNull(weights.get(0));
        // One-hot: [1, 0]
        assertEquals(1.0f, weights.get(1).get(0), 0.001);
        assertEquals(0.0f, weights.get(1).get(1), 0.001);
        // One-hot: [0, 1]
        assertEquals(0.0f, weights.get(2).get(0), 0.001);
        assertEquals(1.0f, weights.get(2).get(1), 0.001);
    }

    public void testGeneratePoolingWeights_3SubQueries() {
        List<List<Float>> weights = QuerySourceUtil.generatePoolingWeights(3);
        assertEquals(4, weights.size()); // 1 equal + 3 one-hot

        // Equal weights: null (omit parameters to trigger default equal-weight behavior)
        assertNull(weights.get(0));
        // One-hot: [1, 0, 0]
        assertEquals(1.0f, weights.get(1).get(0), 0.001);
        assertEquals(0.0f, weights.get(1).get(1), 0.001);
        assertEquals(0.0f, weights.get(1).get(2), 0.001);
        // One-hot: [0, 1, 0]
        assertEquals(0.0f, weights.get(2).get(0), 0.001);
        assertEquals(1.0f, weights.get(2).get(1), 0.001);
        assertEquals(0.0f, weights.get(2).get(2), 0.001);
        // One-hot: [0, 0, 1]
        assertEquals(0.0f, weights.get(3).get(0), 0.001);
        assertEquals(0.0f, weights.get(3).get(1), 0.001);
        assertEquals(1.0f, weights.get(3).get(2), 0.001);
    }

    public void testGeneratePoolingWeights_5SubQueries() {
        List<List<Float>> weights = QuerySourceUtil.generatePoolingWeights(5);
        assertEquals(6, weights.size()); // 1 equal + 5 one-hot

        // Equal weights: null (omit parameters to trigger default equal-weight behavior)
        assertNull(weights.get(0));
        // Verify each one-hot config
        for (int i = 0; i < 5; i++) {
            for (int j = 0; j < 5; j++) {
                float expected = (i == j) ? 1.0f : 0.0f;
                assertEquals(expected, weights.get(i + 1).get(j), 0.001);
            }
        }
    }

    public void testGeneratePoolingWeights_ZeroThrows() {
        assertThrows(IllegalArgumentException.class, () -> QuerySourceUtil.generatePoolingWeights(0));
    }

    public void testGeneratePoolingWeights_NegativeThrows() {
        assertThrows(IllegalArgumentException.class, () -> QuerySourceUtil.generatePoolingWeights(-1));
    }

    public void testGeneratePoolingWeights_ResultIsUnmodifiable() {
        List<List<Float>> weights = QuerySourceUtil.generatePoolingWeights(2);
        assertThrows(UnsupportedOperationException.class, () -> weights.add(List.of(1.0f)));
        // weights.get(0) is null (equal weights), so test unmodifiable on a one-hot config
        assertThrows(UnsupportedOperationException.class, () -> weights.get(1).add(1.0f));
    }

    public void testCreatePoolingSearchPipeline_ReturnsCorrectStructure() {
        Map<String, Object> result = QuerySourceUtil.createPoolingSearchPipeline(List.of(0.7f, 0.3f), "min_max", "arithmetic_mean");

        assertNotNull(result);
        assertTrue(result.containsKey("phase_results_processors"));

        List<?> processors = (List<?>) result.get("phase_results_processors");
        assertEquals(1, processors.size());

        Map<?, ?> processorObject = (Map<?, ?>) processors.get(0);
        assertTrue(processorObject.containsKey("normalization-processor"));

        Map<?, ?> normalizationProcessor = (Map<?, ?>) processorObject.get("normalization-processor");
        Map<?, ?> normalization = (Map<?, ?>) normalizationProcessor.get("normalization");
        Map<?, ?> combination = (Map<?, ?>) normalizationProcessor.get("combination");

        assertEquals("min_max", normalization.get("technique"));
        assertEquals("arithmetic_mean", combination.get("technique"));

        Map<?, ?> parameters = (Map<?, ?>) combination.get("parameters");
        assertNotNull(parameters);
        List<?> weights = (List<?>) parameters.get("weights");
        assertNotNull(weights);
        assertEquals(2, weights.size());
        assertEquals(0.7, (double) weights.get(0), 0.001);
        assertEquals(0.3, (double) weights.get(1), 0.001);
    }

    public void testCreatePoolingSearchPipeline_3Weights() {
        Map<String, Object> result = QuerySourceUtil.createPoolingSearchPipeline(
            List.of(1.0f, 0.0f, 0.0f),
            POOL_NORMALIZATION,
            POOL_COMBINATION
        );

        List<?> processors = (List<?>) result.get("phase_results_processors");
        Map<?, ?> processorObject = (Map<?, ?>) processors.get(0);
        Map<?, ?> normalizationProcessor = (Map<?, ?>) processorObject.get("normalization-processor");
        Map<?, ?> combination = (Map<?, ?>) normalizationProcessor.get("combination");
        Map<?, ?> parameters = (Map<?, ?>) combination.get("parameters");
        List<?> weights = (List<?>) parameters.get("weights");

        assertEquals(3, weights.size());
        assertEquals(1.0, (double) weights.get(0), 0.001);
        assertEquals(0.0, (double) weights.get(1), 0.001);
        assertEquals(0.0, (double) weights.get(2), 0.001);
    }

    public void testCreatePoolingSearchPipeline_5EqualWeights() {
        Map<String, Object> result = QuerySourceUtil.createPoolingSearchPipeline(
            List.of(0.2f, 0.2f, 0.2f, 0.2f, 0.2f),
            POOL_NORMALIZATION,
            POOL_COMBINATION
        );

        List<?> processors = (List<?>) result.get("phase_results_processors");
        Map<?, ?> processorObject = (Map<?, ?>) processors.get(0);
        Map<?, ?> normalizationProcessor = (Map<?, ?>) processorObject.get("normalization-processor");
        Map<?, ?> combination = (Map<?, ?>) normalizationProcessor.get("combination");
        Map<?, ?> parameters = (Map<?, ?>) combination.get("parameters");
        List<?> weights = (List<?>) parameters.get("weights");

        assertEquals(5, weights.size());
        for (int i = 0; i < 5; i++) {
            assertEquals(0.2, (double) weights.get(i), 0.001);
        }
    }

    private Map<String, Object> buildHybridQuery(int numSubQueries) {
        List<Map<String, Object>> queries = new ArrayList<>();
        for (int i = 0; i < numSubQueries; i++) {
            queries.add(new HashMap<>());
        }
        Map<String, Object> hybridMap = new HashMap<>();
        hybridMap.put("queries", queries);
        Map<String, Object> queryWrapper = new HashMap<>();
        queryWrapper.put("hybrid", hybridMap);
        Map<String, Object> fullQuery = new HashMap<>();
        fullQuery.put("query", queryWrapper);
        return fullQuery;
    }
}
