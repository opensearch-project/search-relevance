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
import static org.opensearch.searchrelevance.experiment.QuerySourceUtil.COMBINATION_KEY;
import static org.opensearch.searchrelevance.experiment.QuerySourceUtil.NORMALIZATION_KEY;
import static org.opensearch.searchrelevance.experiment.QuerySourceUtil.NORMALIZATION_PROCESSOR_KEY;
import static org.opensearch.searchrelevance.experiment.QuerySourceUtil.PARAMETERS_KEY;
import static org.opensearch.searchrelevance.experiment.QuerySourceUtil.PHASE_RESULTS_PROCESSORS_KEY;
import static org.opensearch.searchrelevance.experiment.QuerySourceUtil.TECHNIQUE_KEY;
import static org.opensearch.searchrelevance.experiment.QuerySourceUtil.WEIGHTS_KEY;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * Variant DTO for score-based hybrid search through the neural-search
 * normalization-processor. Carries a (normalization, combination) pair
 * and the per-subquery weights.
 */
@Data
@AllArgsConstructor
public class ScoreBasedExperimentVariantHybridSearchDTO implements ExperimentVariantHybridSearchDTO {

    private final String normalizationTechnique;
    private final String combinationTechnique;
    private final float[] queryWeightsForCombination;

    @Override
    public Map<String, Object> toParameters() {
        Map<String, Object> parameters = new HashMap<>();
        parameters.put(EXPERIMENT_OPTION_NORMALIZATION_TECHNIQUE, normalizationTechnique);
        parameters.put(EXPERIMENT_OPTION_COMBINATION_TECHNIQUE, combinationTechnique);
        parameters.put(EXPERIMENT_OPTION_WEIGHTS_FOR_COMBINATION, queryWeightsForCombination);
        return parameters;
    }

    @Override
    public Map<String, Object> toSearchPipeline() {
        if (queryWeightsForCombination == null) {
            throw new IllegalArgumentException("queryWeightsForCombination must not be null for a score-based variant");
        }
        Map<String, Object> normalizationTechniqueConfig = new HashMap<>(Map.of(TECHNIQUE_KEY, normalizationTechnique));
        Map<String, Object> combinationTechniqueConfig = new HashMap<>(Map.of(TECHNIQUE_KEY, combinationTechnique));
        List<Double> weightsList = new ArrayList<>(queryWeightsForCombination.length);
        for (float weight : queryWeightsForCombination) {
            weightsList.add((double) weight);
        }
        combinationTechniqueConfig.put(PARAMETERS_KEY, new HashMap<>(Map.of(WEIGHTS_KEY, weightsList)));
        Map<String, Object> normalizationProcessorConfig = new HashMap<>(
            Map.of(NORMALIZATION_KEY, normalizationTechniqueConfig, COMBINATION_KEY, combinationTechniqueConfig)
        );
        Map<String, Object> phaseProcessorObject = new HashMap<>(Map.of(NORMALIZATION_PROCESSOR_KEY, normalizationProcessorConfig));
        Map<String, Object> pipeline = new HashMap<>();
        pipeline.put(PHASE_RESULTS_PROCESSORS_KEY, List.of(phaseProcessorObject));
        return pipeline;
    }

    /**
     * Reconstruct a score-based variant DTO from a persisted {@code parameters} map.
     */
    static ScoreBasedExperimentVariantHybridSearchDTO fromParameters(Map<String, Object> parameters) {
        String normalization = (String) parameters.get(EXPERIMENT_OPTION_NORMALIZATION_TECHNIQUE);
        String combination = (String) parameters.get(EXPERIMENT_OPTION_COMBINATION_TECHNIQUE);
        float[] weights = (float[]) parameters.get(EXPERIMENT_OPTION_WEIGHTS_FOR_COMBINATION);
        return new ScoreBasedExperimentVariantHybridSearchDTO(normalization, combination, weights);
    }
}
