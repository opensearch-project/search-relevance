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

import java.util.List;
import java.util.Map;

import org.opensearch.searchrelevance.model.ExperimentVariant;
import org.opensearch.test.OpenSearchTestCase;

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
}
