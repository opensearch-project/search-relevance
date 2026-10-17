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

    public void testGetParameterCombinations_yieldsAllCuratedDefaultVariants() {
        // Given the production curated default sweep.
        ExperimentOptionsForHybridSearch options = new ExperimentOptionsForHybridSearch();

        // When
        List<ExperimentVariantHybridSearchDTO> combinations = options.getParameterCombinations();

        // Then: 66 legacy NP + 11 z_score NP + 5 RRF = 82 total
        assertEquals("default parameters should produce 82 variants per query", 82, combinations.size());

        int rrfCount = 0;
        int zScoreCount = 0;
        int legacyNpCount = 0;
        Set<Integer> rrfRankConstants = new HashSet<>();
        Set<String> zScoreCombinations = new HashSet<>();
        Set<String> legacyNormTechniques = new HashSet<>();
        Set<String> legacyCombTechniques = new HashSet<>();

        for (ExperimentVariantHybridSearchDTO combo : combinations) {
            if (combo instanceof RRFExperimentVariantHybridSearchDTO rrf) {
                rrfCount++;
                rrfRankConstants.add(rrf.getRankConstant());
            } else if (combo instanceof ScoreBasedExperimentVariantHybridSearchDTO sb) {
                if ("z_score".equals(sb.getNormalizationTechnique())) {
                    zScoreCount++;
                    zScoreCombinations.add(sb.getCombinationTechnique());
                } else {
                    legacyNpCount++;
                    legacyNormTechniques.add(sb.getNormalizationTechnique());
                    legacyCombTechniques.add(sb.getCombinationTechnique());
                }
                assertNotNull(sb.getQueryWeightsForCombination());
                assertEquals(2, sb.getQueryWeightsForCombination().length);
                assertEquals(
                    1.0f,
                    sb.getQueryWeightsForCombination()[0] + sb.getQueryWeightsForCombination()[1],
                    DELTA_FOR_FLOAT_ASSERTION
                );
            } else {
                fail("unexpected variant type: " + combo.getClass());
            }
        }

        assertEquals("legacy NP variant count", 66, legacyNpCount);
        assertEquals(Set.of("min_max", "l2"), legacyNormTechniques);
        assertEquals(Set.of("arithmetic_mean", "geometric_mean", "harmonic_mean"), legacyCombTechniques);
        assertEquals("z_score variant count", 11, zScoreCount);
        assertEquals("z_score must only pair with arithmetic_mean", Set.of("arithmetic_mean"), zScoreCombinations);
        assertEquals("rrf variant count", 5, rrfCount);
        assertEquals("rrf rank_constants must match the curated default list", Set.of(1, 5, 10, 20, 60), rrfRankConstants);
    }

    public void testGetParameterCombinations_legacyNpHasAllElevenWeightsPerNormCombPair() {
        // Given
        ExperimentOptionsForHybridSearch options = new ExperimentOptionsForHybridSearch();

        // When
        List<ExperimentVariantHybridSearchDTO> combinations = options.getParameterCombinations();

        // Then: every (norm, comb) pair (min_max/l2 x 3 means) appears 11 times with weights 0.0..1.0
        Set<Float> uniqueWeights = new HashSet<>();
        for (ExperimentVariantHybridSearchDTO combo : combinations) {
            if (combo instanceof ScoreBasedExperimentVariantHybridSearchDTO sb && !"z_score".equals(sb.getNormalizationTechnique())) {
                uniqueWeights.add(sb.getQueryWeightsForCombination()[0]);
            }
        }
        assertEquals("expected exactly 11 distinct legacy weights", 11, uniqueWeights.size());
        for (float expected = 0.0f; expected <= 1.0f; expected += 0.1f) {
            boolean found = false;
            for (float actual : uniqueWeights) {
                if (Math.abs(actual - expected) < DELTA_FOR_FLOAT_ASSERTION) {
                    found = true;
                    break;
                }
            }
            assertTrue("missing expected weight: " + expected, found);
        }
    }

    public void testGetParameterCombinations_weightsAreRoundedToOneDecimal() {
        // Given
        ExperimentOptionsForHybridSearch options = new ExperimentOptionsForHybridSearch();

        // When
        List<ExperimentVariantHybridSearchDTO> combinations = options.getParameterCombinations();

        // Then: every score-based variant has weights with no float drift beyond 1 decimal.
        for (ExperimentVariantHybridSearchDTO combo : combinations) {
            if (combo instanceof ScoreBasedExperimentVariantHybridSearchDTO sb) {
                float w1 = sb.getQueryWeightsForCombination()[0];
                float w2 = sb.getQueryWeightsForCombination()[1];

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
}
