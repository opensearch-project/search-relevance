/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */
package org.opensearch.searchrelevance.experiment;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
/**
 * Experiment options for hybrid search
 */
public class ExperimentOptionsForHybridSearch implements ExperimentOptions {
    private Set<String> normalizationTechniques;
    private Set<String> combinationTechniques;
    private WeightsRange weightsRange;

    public static final String EXPERIMENT_OPTION_NORMALIZATION_TECHNIQUE = "normalization";
    public static final String EXPERIMENT_OPTION_COMBINATION_TECHNIQUE = "combination";
    public static final String EXPERIMENT_OPTION_WEIGHTS_FOR_COMBINATION = "weights";

    @Data
    @Builder
    static class WeightsRange {
        private float rangeMin;
        private float rangeMax;
        private float increment;
    }

    public List<ExperimentVariantHybridSearchDTO> getParameterCombinations(boolean includeWeights) {
        List<ExperimentVariantHybridSearchDTO> allPossibleParameterCombinations = new ArrayList<>();
        for (String normalizationTechnique : normalizationTechniques) {
            for (String combinationTechnique : combinationTechniques) {
                if (includeWeights) {
                    for (float queryWeightForCombination = weightsRange.getRangeMin(); queryWeightForCombination <= weightsRange
                        .getRangeMax(); queryWeightForCombination += weightsRange.getIncrement()) {
                        allPossibleParameterCombinations.add(
                            ExperimentVariantHybridSearchDTO.builder()
                                .normalizationTechnique(normalizationTechnique)
                                .combinationTechnique(combinationTechnique)
                                .queryWeightsForCombination(new float[] { queryWeightForCombination, 1.0f - queryWeightForCombination })
                                .build()
                        );
                    }
                } else {
                    allPossibleParameterCombinations.add(
                        ExperimentVariantHybridSearchDTO.builder()
                            .normalizationTechnique(normalizationTechnique)
                            .combinationTechnique(combinationTechnique)
                            .queryWeightsForCombination(new float[] { 0.5f, 0.5f })
                            .build()
                    );
                }
            }
        }
        return allPossibleParameterCombinations;
    }
}
