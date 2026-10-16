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

    public void testCreateExperimentOptions_whenHybridSearchOptions_thenReturnsHybridOptions() {
        // When
        ExperimentOptions options = ExperimentOptionsFactory.createExperimentOptions(
            ExperimentOptionsFactory.HYBRID_SEARCH_EXPERIMENT_OPTIONS,
            Map.of()
        );

        // Then
        assertNotNull(options);
        assertTrue(options instanceof ExperimentOptionsForHybridSearch);
    }

    public void testCreateExperimentOptions_whenEmptyExperimentOptions_thenReturnsEmptyOptions() {
        // When
        ExperimentOptions options = ExperimentOptionsFactory.createExperimentOptions(
            ExperimentOptionsFactory.EMPTY_EXPERIMENT_OPTIONS,
            Map.of()
        );

        // Then
        assertNotNull(options);
        assertTrue(options instanceof EmptyExperimentOptions);
    }

    public void testCreateExperimentOptions_whenUnknownName_thenThrows() {
        // Given
        Map<String, Object> ignoredOptions = Map.of(
            "normalizationTechniques",
            Set.of("min_max", "l2"),
            "combinationTechniques",
            Set.of("arithmetic_mean", "geometric_mean", "harmonic_mean")
        );

        // When & Then
        IllegalArgumentException exception = assertThrows(
            IllegalArgumentException.class,
            () -> ExperimentOptionsFactory.createExperimentOptions("random_experiment_name", ignoredOptions)
        );

        assertEquals("provided experiment name is not supported", exception.getMessage());
    }
}
