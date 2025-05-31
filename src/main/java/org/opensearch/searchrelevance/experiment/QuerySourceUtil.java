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
import static org.opensearch.searchrelevance.experiment.ExperimentOptionsForHybridSearch.EXPERIMENT_OPTION_WEIGHTS_FOR_COMBINATION;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.opensearch.searchrelevance.model.ExperimentVariant;

/**
 * Utility class for a query source
 */
public class QuerySourceUtil {

    /**
     * Creates a definition of a temporary search pipeline for hybrid search.
     * @param experimentVariant sub-experiment to create the pipeline for
     * @return definition of a temporary search pipeline
     */
    public static Map<String, Object> createDefinitionOfTemporarySearchPipeline(final ExperimentVariant experimentVariant) {
        Map<String, Object> experimentVariantParameters = experimentVariant.getParameters();
        Map<String, Object> normalizationTechniqueConfig = new HashMap<>(
            Map.of("technique", experimentVariantParameters.get(EXPERIMENT_OPTION_NORMALIZATION_TECHNIQUE))
        );

        Map<String, Object> combinationTechniqueConfig = new HashMap<>(
            Map.of("technique", experimentVariantParameters.get(EXPERIMENT_OPTION_COMBINATION_TECHNIQUE))
        );
        if (Objects.nonNull(experimentVariantParameters.get(EXPERIMENT_OPTION_WEIGHTS_FOR_COMBINATION))) {
            float[] weights = (float[]) experimentVariantParameters.get(EXPERIMENT_OPTION_WEIGHTS_FOR_COMBINATION);
            List<Double> weightsList = new ArrayList<>(weights.length);
            for (float weight : weights) {
                weightsList.add((double) weight);
            }
            combinationTechniqueConfig.put("parameters", new HashMap<>(Map.of("weights", weightsList)));
        }

        Map<String, Object> normalizationProcessorConfig = new HashMap<>(
            Map.of("normalization", normalizationTechniqueConfig, "combination", combinationTechniqueConfig)
        );
        Map<String, Object> phaseProcessorObject = new HashMap<>(Map.of("normalization-processor", normalizationProcessorConfig));
        Map<String, Object> temporarySearchPipeline = new HashMap<>();
        temporarySearchPipeline.put("phase_results_processors", List.of(phaseProcessorObject));
        return temporarySearchPipeline;
    }
}
