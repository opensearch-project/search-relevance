/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */
package org.opensearch.searchrelevance.experiment;

import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

/**
 * Factory class for creating ExperimentOptions based on the experiment name and parameters
 */
public class ExperimentOptionsFactory {

    public static final String EMPTY_EXPERIMENT_OPTIONS = "EMPTY_EXPERIMENT_OPTIONS";
    public static final String HYBRID_SEARCH_EXPERIMENT_OPTIONS = "HYBRID_SEARCH_EXPERIMENT_OPTIONS";

    private static final Map<String, Function<Map<String, Object>, ExperimentOptions>> OPTIONS_BY_EXPERIMENT_NAME = Map.of(
        EMPTY_EXPERIMENT_OPTIONS,
        params -> new EmptyExperimentOptions(),
        HYBRID_SEARCH_EXPERIMENT_OPTIONS,
        params -> new ExperimentOptionsForHybridSearch()
    );

    /**
     * Creates an ExperimentOptions object based on the provided experiment name and parameters.
     *
     * @param experimentName The name of the experiment.
     * @param params The parameters for the experiment.
     * @return An ExperimentOptions object.
     * @throws IllegalArgumentException If the provided experiment name is not supported.
     */
    public static ExperimentOptions createExperimentOptions(final String experimentName, final Map<String, Object> params) {
        return Optional.ofNullable(OPTIONS_BY_EXPERIMENT_NAME.get(experimentName))
            .orElseThrow(() -> new IllegalArgumentException("provided experiment name is not supported"))
            .apply(params);
    }
}
