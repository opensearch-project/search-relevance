/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */
package org.opensearch.searchrelevance.experiment;

import java.util.Map;
import java.util.Set;

import org.opensearch.test.OpenSearchTestCase;

public class ExperimentOptionsFactoryTests extends OpenSearchTestCase {

    private static final float DELTA_FOR_FLOAT_ASSERTION = 0.0001f;

    public void testCreateExperimentOptions_whenValidHybridSearchOptions_thenSuccessful() {
        // When
        ExperimentOptions experimentOptions = ExperimentOptionsFactory.createExperimentOptions(
            ExperimentOptionsFactory.HYBRID_SEARCH_EXPERIMENT_OPTIONS,
            ExperimentOptionsFactory.createDefaultExperimentParametersForHybridSearch()
        );

        // Then
        assertNotNull(experimentOptions);
        assertTrue(experimentOptions instanceof ExperimentOptionsForHybridSearch);
        ExperimentOptionsForHybridSearch result = (ExperimentOptionsForHybridSearch) experimentOptions;
        assertNotNull(result.getWeightsRange());
        ExperimentOptionsForHybridSearch.WeightsRange resultWeightsRange = result.getWeightsRange();
        assertEquals(0.0f, resultWeightsRange.getRangeMin(), DELTA_FOR_FLOAT_ASSERTION);
        assertEquals(1.0f, resultWeightsRange.getRangeMax(), DELTA_FOR_FLOAT_ASSERTION);
        assertEquals(0.1f, resultWeightsRange.getIncrement(), DELTA_FOR_FLOAT_ASSERTION);
    }

    public void testCreateExperimentOptions_whenInvalidHybridSearchOptions_thenFail() {
        // Given
        Map<String, Object> invalidOptions = Map.of(
            "normalizationTechniques",
            Set.of("min_max", "l2"),
            "combinationTechniques",
            Set.of("arithmetic_mean", "geometric_mean", "harmonic_mean"),
            "weightsRange",
            Map.of("rangeMin", 1.0, "rangeMax", 0.0, "increment", 0.1)
        );

        // When & Then
        IllegalArgumentException exception = assertThrows(
            IllegalArgumentException.class,
            () -> ExperimentOptionsFactory.createExperimentOptions("random_experiment_name", invalidOptions)
        );

        assertEquals("provided experiment name is not supported", exception.getMessage());
    }
}
