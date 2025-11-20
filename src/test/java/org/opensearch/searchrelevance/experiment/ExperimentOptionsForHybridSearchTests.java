/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */
package org.opensearch.searchrelevance.experiment;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.opensearch.test.OpenSearchTestCase;

public class ExperimentOptionsForHybridSearchTests extends OpenSearchTestCase {

    private static final float DELTA_FOR_FLOAT_ASSERTION = 0.0001f;

    public void testGetParameterCombinations_whenIncludeWeightsTrue_thenReturnAllCombinations() {
        // Given
        Set<String> normalizationTechniques = Set.of("min_max", "l2");
        Set<String> combinationTechniques = Set.of("arithmetic_mean", "harmonic_mean");
        ExperimentOptionsForHybridSearch.WeightsRange weightsRange = ExperimentOptionsForHybridSearch.WeightsRange.builder()
            .rangeMin(0.0f)
            .rangeMax(1.0f)
            .increment(0.5f)
            .build();

        ExperimentOptionsForHybridSearch options = ExperimentOptionsForHybridSearch.builder()
            .normalizationTechniques(normalizationTechniques)
            .combinationTechniques(combinationTechniques)
            .weightsRange(weightsRange)
            .build();

        // When
        List<ExperimentVariantHybridSearchDTO> result = options.getParameterCombinations(true);

        // Then
        assertNotNull(result);
        assertEquals(12, result.size()); // 2 normalization * 2 combination * 3 weight values (0.0, 0.5, 1.0)

        // Instead of relying on specific order, check that all expected combinations are present
        Set<String> expectedNormTechniques = Set.of("min_max", "l2");
        Set<String> expectedCombTechniques = Set.of("arithmetic_mean", "harmonic_mean");
        Set<Float> expectedWeights = Set.of(0.0f, 0.5f, 1.0f);

        // Track which combinations we've found
        Set<String> foundNormTechniques = new HashSet<>();
        Set<String> foundCombTechniques = new HashSet<>();
        Set<Float> foundWeights = new HashSet<>();

        // Verify all combinations
        for (ExperimentVariantHybridSearchDTO combo : result) {
            foundNormTechniques.add(combo.getNormalizationTechnique());
            foundCombTechniques.add(combo.getCombinationTechnique());
            foundWeights.add(combo.getQueryWeightsForCombination()[0]);

            // Verify weights sum to 1.0
            assertEquals(
                1.0f,
                combo.getQueryWeightsForCombination()[0] + combo.getQueryWeightsForCombination()[1],
                DELTA_FOR_FLOAT_ASSERTION
            );
        }

        // Verify we found all expected values
        assertEquals(expectedNormTechniques, foundNormTechniques);
        assertEquals(expectedCombTechniques, foundCombTechniques);
        assertEquals(expectedWeights, foundWeights);

        // Check for specific weight combinations
        boolean foundWeight00 = false;
        boolean foundWeight10 = false;

        for (ExperimentVariantHybridSearchDTO combo : result) {
            float weight0 = combo.getQueryWeightsForCombination()[0];
            float weight1 = combo.getQueryWeightsForCombination()[1];

            if (Math.abs(weight0) < DELTA_FOR_FLOAT_ASSERTION && Math.abs(weight1 - 1.0f) < DELTA_FOR_FLOAT_ASSERTION) {
                foundWeight00 = true;
            }
            if (Math.abs(weight0 - 1.0f) < DELTA_FOR_FLOAT_ASSERTION && Math.abs(weight1) < DELTA_FOR_FLOAT_ASSERTION) {
                foundWeight10 = true;
            }
        }

        assertTrue("Missing weight combination [0.0, 1.0]", foundWeight00);
        assertTrue("Missing weight combination [1.0, 0.0]", foundWeight10);
    }

    public void testGetParameterCombinations_whenIncludeWeightsFalse_thenReturnDefaultWeights() {
        // Given
        Set<String> normalizationTechniques = Set.of("min_max", "l2");
        Set<String> combinationTechniques = Set.of("arithmetic_mean", "harmonic_mean");
        ExperimentOptionsForHybridSearch.WeightsRange weightsRange = ExperimentOptionsForHybridSearch.WeightsRange.builder()
            .rangeMin(0.0f)
            .rangeMax(1.0f)
            .increment(0.5f)
            .build();

        ExperimentOptionsForHybridSearch options = ExperimentOptionsForHybridSearch.builder()
            .normalizationTechniques(normalizationTechniques)
            .combinationTechniques(combinationTechniques)
            .weightsRange(weightsRange)
            .build();

        // When
        List<ExperimentVariantHybridSearchDTO> result = options.getParameterCombinations(false);

        // Then
        assertNotNull(result);
        assertEquals(4, result.size()); // 2 normalization * 2 combination techniques

        // Verify all combinations have default weights
        for (ExperimentVariantHybridSearchDTO combination : result) {
            assertEquals(0.5f, combination.getQueryWeightsForCombination()[0], DELTA_FOR_FLOAT_ASSERTION);
            assertEquals(0.5f, combination.getQueryWeightsForCombination()[1], DELTA_FOR_FLOAT_ASSERTION);
        }
    }

    public void testGetParameterCombinations_whenEmptyTechniques_thenReturnEmptyList() {
        // Given
        Set<String> emptySet = new HashSet<>();
        ExperimentOptionsForHybridSearch.WeightsRange weightsRange = ExperimentOptionsForHybridSearch.WeightsRange.builder()
            .rangeMin(0.0f)
            .rangeMax(1.0f)
            .increment(0.5f)
            .build();

        ExperimentOptionsForHybridSearch options = ExperimentOptionsForHybridSearch.builder()
            .normalizationTechniques(emptySet)
            .combinationTechniques(Set.of("arithmetic_mean"))
            .weightsRange(weightsRange)
            .build();

        // When
        List<ExperimentVariantHybridSearchDTO> result = options.getParameterCombinations(true);

        // Then
        assertNotNull(result);
        assertEquals(0, result.size());
    }

    public void testWeightsRange_gettersAndSetters() {
        // Given
        ExperimentOptionsForHybridSearch.WeightsRange weightsRange = ExperimentOptionsForHybridSearch.WeightsRange.builder()
            .rangeMin(0.1f)
            .rangeMax(0.9f)
            .increment(0.2f)
            .build();

        // Then
        assertEquals(0.1f, weightsRange.getRangeMin(), DELTA_FOR_FLOAT_ASSERTION);
        assertEquals(0.9f, weightsRange.getRangeMax(), DELTA_FOR_FLOAT_ASSERTION);
        assertEquals(0.2f, weightsRange.getIncrement(), DELTA_FOR_FLOAT_ASSERTION);
    }

    public void testExperimentOptionsForHybridSearch_gettersAndSetters() {
        // Given
        Set<String> normalizationTechniques = Set.of("min_max");
        Set<String> combinationTechniques = Set.of("arithmetic_mean");
        ExperimentOptionsForHybridSearch.WeightsRange weightsRange = ExperimentOptionsForHybridSearch.WeightsRange.builder()
            .rangeMin(0.0f)
            .rangeMax(1.0f)
            .increment(0.5f)
            .build();

        // When
        ExperimentOptionsForHybridSearch options = ExperimentOptionsForHybridSearch.builder()
            .normalizationTechniques(normalizationTechniques)
            .combinationTechniques(combinationTechniques)
            .weightsRange(weightsRange)
            .build();

        // Then
        assertEquals(normalizationTechniques, options.getNormalizationTechniques());
        assertEquals(combinationTechniques, options.getCombinationTechniques());
        assertEquals(weightsRange, options.getWeightsRange());
    }

    public void testGetParameterCombinations_withPreciseIncrements_shouldIncludeAllWeights() {
        // Given
        Set<String> normalizationTechniques = Set.of("min_max", "l2");
        Set<String> combinationTechniques = Set.of("arithmetic_mean", "harmonic_mean", "geometric_mean");
        ExperimentOptionsForHybridSearch.WeightsRange weightsRange = ExperimentOptionsForHybridSearch.WeightsRange.builder()
            .rangeMin(0.0f)
            .rangeMax(1.0f)
            .increment(0.1f)
            .build();

        ExperimentOptionsForHybridSearch options = ExperimentOptionsForHybridSearch.builder()
            .normalizationTechniques(normalizationTechniques)
            .combinationTechniques(combinationTechniques)
            .weightsRange(weightsRange)
            .build();

        // When
        List<ExperimentVariantHybridSearchDTO> combinations = options.getParameterCombinations(true);

        // Then
        // 2 normalization techniques * 3 combination techniques * 11 weights (0.0 to 1.0 in 0.1 increments) = 66
        assertEquals(66, combinations.size());

        // Check that edge cases exist (both [0.0, 1.0] and [1.0, 0.0] weight combinations)
        boolean foundWeight10 = false;
        boolean foundWeight01 = false;

        for (ExperimentVariantHybridSearchDTO combo : combinations) {
            float weight0 = combo.getQueryWeightsForCombination()[0];
            float weight1 = combo.getQueryWeightsForCombination()[1];

            if (Math.abs(weight0 - 1.0f) < DELTA_FOR_FLOAT_ASSERTION && Math.abs(weight1 - 0.0f) < DELTA_FOR_FLOAT_ASSERTION) {
                foundWeight10 = true;
            }
            if (Math.abs(weight0 - 0.0f) < DELTA_FOR_FLOAT_ASSERTION && Math.abs(weight1 - 1.0f) < DELTA_FOR_FLOAT_ASSERTION) {
                foundWeight01 = true;
            }
        }

        assertTrue("Missing weight combination [1.0, 0.0]", foundWeight10);
        assertTrue("Missing weight combination [0.0, 1.0]", foundWeight01);

        // Verify that we have all expected weights (0.0, 0.1, 0.2, ..., 0.9, 1.0)
        Set<Float> uniqueWeights = new HashSet<>();
        for (ExperimentVariantHybridSearchDTO combo : combinations) {
            uniqueWeights.add(combo.getQueryWeightsForCombination()[0]);
        }

        assertEquals("Should have exactly 11 unique weights", 11, uniqueWeights.size());

        // Check if all expected weights exist
        for (float expected = 0.0f; expected <= 1.0f; expected += 0.1f) {
            boolean foundWeight = false;
            for (float actual : uniqueWeights) {
                if (Math.abs(actual - expected) < DELTA_FOR_FLOAT_ASSERTION) {
                    foundWeight = true;
                    break;
                }
            }
            assertTrue("Missing expected weight: " + expected, foundWeight);
        }
    }

    public void testGetParameterCombinations_weightsAreRoundedToOneDecimal() {
        // Given
        Set<String> normalizationTechniques = Set.of("min_max");
        Set<String> combinationTechniques = Set.of("arithmetic_mean");

        // Use increment that is MOST LIKELY TO EXPOSE FLOAT DRIFT
        ExperimentOptionsForHybridSearch.WeightsRange weightsRange = ExperimentOptionsForHybridSearch.WeightsRange.builder()
            .rangeMin(0.0f)
            .rangeMax(1.0f)
            .increment(0.1f)
            .build();

        ExperimentOptionsForHybridSearch options = ExperimentOptionsForHybridSearch.builder()
            .normalizationTechniques(normalizationTechniques)
            .combinationTechniques(combinationTechniques)
            .weightsRange(weightsRange)
            .build();

        // When
        List<ExperimentVariantHybridSearchDTO> combinations = options.getParameterCombinations(true);

        // Then
        for (ExperimentVariantHybridSearchDTO combo : combinations) {
            float w1 = combo.getQueryWeightsForCombination()[0];
            float w2 = combo.getQueryWeightsForCombination()[1];

            assertEquals(1.0f, w1 + w2, 0.0f);

            String w1Str = Float.toString(w1);
            String w2Str = Float.toString(w2);

            assertFalse("w1 has float drift: " + w1Str, w1Str.matches(".*\\d{2,}E-.*"));
            assertFalse("w2 has float drift: " + w2Str, w2Str.matches(".*\\d{2,}E-.*"));
            assertTrue("w1 is not 1-decimal rounded: " + w1Str, w1Str.matches("^-?\\d(\\.\\d)?$"));
            assertTrue("w2 is not 1-decimal rounded: " + w2Str, w2Str.matches("^-?\\d(\\.\\d)?$"));
        }
    }

    public void testGetParameterCombinations_withConstantListOfCorrectValues_weightsAreRoundedToOneDecimal() {
        // Given
        Set<String> normalizationTechniques = Set.of("min_max");
        Set<String> combinationTechniques = Set.of("arithmetic_mean");

        // Use increment that is MOST LIKELY TO EXPOSE FLOAT DRIFT
        ExperimentOptionsForHybridSearch.WeightsRange weightsRange = ExperimentOptionsForHybridSearch.WeightsRange.builder()
            .rangeMin(0.0f)
            .rangeMax(1.0f)
            .increment(0.1f)
            .build();

        ExperimentOptionsForHybridSearch options = ExperimentOptionsForHybridSearch.builder()
            .normalizationTechniques(normalizationTechniques)
            .combinationTechniques(combinationTechniques)
            .weightsRange(weightsRange)
            .build();

        // When
        List<ExperimentVariantHybridSearchDTO> combinations = options.getParameterCombinations(true);

        // Then
        float[][] expected = {
            { 0.0f, 1.0f },
            { 0.1f, 0.9f },
            { 0.2f, 0.8f },
            { 0.3f, 0.7f },
            { 0.4f, 0.6f },
            { 0.5f, 0.5f },
            { 0.6f, 0.4f },
            { 0.7f, 0.3f },
            { 0.8f, 0.2f },
            { 0.9f, 0.1f },
            { 1.0f, 0.0f } };

        assertEquals(11, combinations.size());

        for (int i = 0; i < 11; i++) {
            float w1 = combinations.get(i).getQueryWeightsForCombination()[0];
            float w2 = combinations.get(i).getQueryWeightsForCombination()[1];

            assertEquals(expected[i][0], w1, 0.0f);
            assertEquals(expected[i][1], w2, 0.0f);
            assertEquals(1.0f, w1 + w2, 0.0f);

            String w1Str = Float.toString(w1);
            String w2Str = Float.toString(w2);

            assertFalse("w1 has float drift: " + w1Str, w1Str.matches(".*\\d{2,}E-.*"));
            assertFalse("w2 has float drift: " + w2Str, w2Str.matches(".*\\d{2,}E-.*"));
            assertTrue("w1 is not 1-decimal rounded: " + w1Str, w1Str.matches("^-?\\d(\\.\\d)?$"));
            assertTrue("w2 is not 1-decimal rounded: " + w2Str, w2Str.matches("^-?\\d(\\.\\d)?$"));
        }
    }
}
