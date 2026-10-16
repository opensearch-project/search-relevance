/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */
package org.opensearch.searchrelevance.model;

import static org.opensearch.searchrelevance.experiment.ExperimentOptionsForHybridSearch.EXPERIMENT_OPTION_COMBINATION_TECHNIQUE;
import static org.opensearch.searchrelevance.experiment.ExperimentOptionsForHybridSearch.EXPERIMENT_OPTION_NORMALIZATION_TECHNIQUE;
import static org.opensearch.searchrelevance.experiment.ExperimentOptionsForHybridSearch.EXPERIMENT_OPTION_RANK_CONSTANT;
import static org.opensearch.searchrelevance.experiment.ExperimentOptionsForHybridSearch.EXPERIMENT_OPTION_WEIGHTS_FOR_COMBINATION;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.opensearch.test.OpenSearchTestCase;

public class ExperimentVariantTests extends OpenSearchTestCase {

    public void testGetTextualParameters_scoreBasedVariant_emitsCombinationNormalizationAndWeights() {
        Map<String, Object> parameters = new HashMap<>();
        parameters.put(EXPERIMENT_OPTION_COMBINATION_TECHNIQUE, "arithmetic_mean");
        parameters.put(EXPERIMENT_OPTION_NORMALIZATION_TECHNIQUE, "min_max");
        parameters.put(EXPERIMENT_OPTION_WEIGHTS_FOR_COMBINATION, new float[] { 0.3f, 0.7f });
        ExperimentVariant variant = newVariant(parameters);

        assertEquals("combination=arithmetic_mean, normalization=min_max, weights=0.3;0.7", variant.getTextualParameters());
    }

    public void testGetTextualParameters_rrfVariant_emitsCombinationAndRankConstant() {
        Map<String, Object> parameters = new HashMap<>();
        parameters.put(EXPERIMENT_OPTION_COMBINATION_TECHNIQUE, "rrf");
        parameters.put(EXPERIMENT_OPTION_RANK_CONSTANT, 60);
        ExperimentVariant variant = newVariant(parameters);

        assertEquals("combination=rrf, rank_constant=60", variant.getTextualParameters());
    }

    public void testGetTextualParameters_emptyParameters_returnsEmptyString() {
        ExperimentVariant variant = newVariant(new HashMap<>());

        assertEquals("", variant.getTextualParameters());
    }

    public void testGetTextualParameters_weightsAsFirstField_omitsLeadingComma() {
        // exercises the "no leading comma when sb is empty" branch with a non-combination first field
        Map<String, Object> parameters = new HashMap<>();
        parameters.put(EXPERIMENT_OPTION_WEIGHTS_FOR_COMBINATION, new float[] { 0.5f, 0.5f });
        ExperimentVariant variant = newVariant(parameters);

        assertEquals("weights=0.5;0.5", variant.getTextualParameters());
    }

    public void testGetTextualParameters_rankConstantOnly_omitsLeadingComma() {
        Map<String, Object> parameters = new HashMap<>();
        parameters.put(EXPERIMENT_OPTION_RANK_CONSTANT, 60);
        ExperimentVariant variant = newVariant(parameters);

        assertEquals("rank_constant=60", variant.getTextualParameters());
    }

    public void testGetTextualParameters_emptyWeightsArray_skipsWeightsField() {
        Map<String, Object> parameters = new HashMap<>();
        parameters.put(EXPERIMENT_OPTION_COMBINATION_TECHNIQUE, "arithmetic_mean");
        parameters.put(EXPERIMENT_OPTION_WEIGHTS_FOR_COMBINATION, new float[0]);
        ExperimentVariant variant = newVariant(parameters);

        assertEquals("combination=arithmetic_mean", variant.getTextualParameters());
    }

    public void testGetTextualParameters_weightsNotFloatArray_skipsWeightsField() {
        // weights only renders when the value is actually a float[]; other types are ignored
        Map<String, Object> parameters = new HashMap<>();
        parameters.put(EXPERIMENT_OPTION_COMBINATION_TECHNIQUE, "arithmetic_mean");
        parameters.put(EXPERIMENT_OPTION_WEIGHTS_FOR_COMBINATION, List.of(0.3, 0.7));
        ExperimentVariant variant = newVariant(parameters);

        assertEquals("combination=arithmetic_mean", variant.getTextualParameters());
    }

    public void testGetTextualParameters_singleWeightValue_omitsSeparator() {
        Map<String, Object> parameters = new HashMap<>();
        parameters.put(EXPERIMENT_OPTION_COMBINATION_TECHNIQUE, "arithmetic_mean");
        parameters.put(EXPERIMENT_OPTION_WEIGHTS_FOR_COMBINATION, new float[] { 0.5f });
        ExperimentVariant variant = newVariant(parameters);

        assertEquals("combination=arithmetic_mean, weights=0.5", variant.getTextualParameters());
    }

    public void testGetTextualParameters_weightsRoundedToTwoDecimalPlaces() {
        Map<String, Object> parameters = new HashMap<>();
        parameters.put(EXPERIMENT_OPTION_COMBINATION_TECHNIQUE, "arithmetic_mean");
        parameters.put(EXPERIMENT_OPTION_WEIGHTS_FOR_COMBINATION, new float[] { 0.123456f, 0.876543f });
        ExperimentVariant variant = newVariant(parameters);

        assertEquals("combination=arithmetic_mean, weights=0.12;0.88", variant.getTextualParameters());
    }

    public void testGetTextualParameters_integerWeights_dropTrailingZeros() {
        // setMinimumFractionDigits(0) means a whole-number weight prints as "1" (no ".0")
        Map<String, Object> parameters = new HashMap<>();
        parameters.put(EXPERIMENT_OPTION_COMBINATION_TECHNIQUE, "arithmetic_mean");
        parameters.put(EXPERIMENT_OPTION_WEIGHTS_FOR_COMBINATION, new float[] { 1.0f, 0.0f });
        ExperimentVariant variant = newVariant(parameters);

        assertEquals("combination=arithmetic_mean, weights=1;0", variant.getTextualParameters());
    }

    private static ExperimentVariant newVariant(Map<String, Object> parameters) {
        return ExperimentVariant.builder()
            .id("test-id")
            .timestamp("2024-01-01T00:00:00Z")
            .type(ExperimentType.HYBRID_OPTIMIZER)
            .status(AsyncStatus.PROCESSING)
            .experimentId("test-experiment-id")
            .parameters(parameters)
            .results(Map.of())
            .build();
    }
}
