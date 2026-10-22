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
 * Experiment options for hybrid search.
 * <p>
 * Holds the technique-name constants and assembles the list of
 * {@link HybridSearchConfig} instances representing the curated default sweep.
 * Valid combinations are spelled out explicitly here so the relationships
 * between parameters are visible at the definition site.
 */
public class ExperimentOptionsForHybridSearch implements ExperimentOptions {

    public static final String EXPERIMENT_OPTION_NORMALIZATION_TECHNIQUE = "normalization";
    public static final String EXPERIMENT_OPTION_COMBINATION_TECHNIQUE = "combination";
    public static final String EXPERIMENT_OPTION_WEIGHTS_FOR_COMBINATION = "weights";
    public static final String EXPERIMENT_OPTION_RANK_CONSTANT = "rank_constant";

    public static final String NORMALIZATION_MIN_MAX = "min_max";
    public static final String NORMALIZATION_L2 = "l2";
    public static final String NORMALIZATION_Z_SCORE = "z_score";

    public static final String COMBINATION_ARITHMETIC_MEAN = "arithmetic_mean";
    public static final String COMBINATION_GEOMETRIC_MEAN = "geometric_mean";
    public static final String COMBINATION_HARMONIC_MEAN = "harmonic_mean";
    public static final String COMBINATION_RRF = "rrf";

    @Data
    @Builder
    public static class WeightsRange {
        private float rangeMin;
        private float rangeMax;
        private float increment;
    }

    /**
     * Expand the curated default sweep into concrete variant DTOs.
     * <p>
     * The structure makes the strategy-to-parameter relationships explicit:
     * RRF contributes one variant per rank_constant (normalization-independent);
     * the legacy normalization-processor contributes one variant per
     * (normalization, combination, weight) for {@code min_max} / {@code l2}
     * paired with the three mean combinations; {@code z_score} is paired only
     * with arithmetic mean since it produces negative values that break the
     * geometric and harmonic means.
     */
    public List<ExperimentVariantHybridSearchDTO> getParameterCombinations() {
        WeightsRange weightsRange = WeightsRange.builder().rangeMin(0.0f).rangeMax(1.0f).increment(0.1f).build();

        List<HybridSearchConfig> configs = new ArrayList<>();
        configs.add(RRFHybridSearchConfig.builder().rankConstants(List.of(1, 5, 10, 20, 60)).build());
        for (String normalization : List.of(NORMALIZATION_MIN_MAX, NORMALIZATION_L2)) {
            for (String combination : List.of(COMBINATION_ARITHMETIC_MEAN, COMBINATION_GEOMETRIC_MEAN, COMBINATION_HARMONIC_MEAN)) {
                configs.add(
                    ScoreBasedHybridSearchConfig.builder()
                        .normalizationTechnique(normalization)
                        .combinationTechnique(combination)
                        .weightsRange(weightsRange)
                        .build()
                );
            }
        }
        configs.add(
            ScoreBasedHybridSearchConfig.builder()
                .normalizationTechnique(NORMALIZATION_Z_SCORE)
                .combinationTechnique(COMBINATION_ARITHMETIC_MEAN)
                .weightsRange(weightsRange)
                .build()
        );

        List<ExperimentVariantHybridSearchDTO> allVariants = new ArrayList<>();
        for (HybridSearchConfig config : configs) {
            allVariants.addAll(config.getAllVariants());
        }
        return allVariants;
    }
}
