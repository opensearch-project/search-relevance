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

import lombok.Builder;
import lombok.Data;

/**
 * A score-based hybrid-search configuration: a single (normalization, combination)
 * pair swept across a weights range. Backed by the neural-search
 * normalization-processor.
 */
@Data
@Builder
public class ScoreBasedHybridSearchConfig implements HybridSearchConfig {

    private final String normalizationTechnique;
    private final String combinationTechnique;
    private final ExperimentOptionsForHybridSearch.WeightsRange weightsRange;

    @Override
    public List<ExperimentVariantHybridSearchDTO> getAllVariants() {
        List<ExperimentVariantHybridSearchDTO> variants = new ArrayList<>();
        // use integer-based stepping to avoid floating-point precision issues
        float min = weightsRange.getRangeMin();
        float max = weightsRange.getRangeMax();
        float increment = weightsRange.getIncrement();

        int steps = Math.round((max - min) / increment) + 1;

        for (int i = 0; i < steps; i++) {
            float queryWeightForCombination;
            if (i == steps - 1) {
                queryWeightForCombination = max;
            } else {
                queryWeightForCombination = min + (i * increment);
            }

            float w1 = Math.round(queryWeightForCombination * 10) / 10.0f;
            float w2 = Math.round((1.0f - w1) * 10) / 10.0f;

            variants.add(
                new ScoreBasedExperimentVariantHybridSearchDTO(normalizationTechnique, combinationTechnique, new float[] { w1, w2 })
            );
        }
        return variants;
    }
}
