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
                List.of(
                    Map.of("name", EXPERIMENT_OPTION_NORMALIZATION_TECHNIQUE, "value", "min_max"),
                    Map.of("name", EXPERIMENT_OPTION_COMBINATION_TECHNIQUE, "value", "arithmetic_mean")
                )
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
        assertEquals("query in search configuration does not have query", exception.getMessage());
    }

    public void testValidateHybridQuery_InvalidQueryType() {
        Map<String, Object> fullQuery = new HashMap<>();
        fullQuery.put("query", "not_a_map");

        IllegalArgumentException exception = assertThrows(
            IllegalArgumentException.class,
            () -> QuerySourceUtil.validateHybridQuery(fullQuery)
        );
        assertEquals("query in search configuration does not have query", exception.getMessage());
    }

    public void testValidateHybridQuery_MissingHybrid() {
        Map<String, Object> query = new HashMap<>();
        Map<String, Object> fullQuery = new HashMap<>();
        fullQuery.put("query", query);

        IllegalArgumentException exception = assertThrows(
            IllegalArgumentException.class,
            () -> QuerySourceUtil.validateHybridQuery(fullQuery)
        );
        assertEquals("query in search configuration does must be of type hybrid", exception.getMessage());
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
        assertEquals("query in search configuration does must be of type hybrid", exception.getMessage());
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
        assertEquals("hybrid query in search configuration does not have queries", exception.getMessage());
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
        assertEquals("hybrid query in search configuration does not have queries", exception.getMessage());
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
}
