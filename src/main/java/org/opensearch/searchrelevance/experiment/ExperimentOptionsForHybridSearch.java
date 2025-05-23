/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */
package org.opensearch.searchrelevance.experiment;

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

    @Data
    @Builder
    static class WeightsRange {
        private float rangeMin;
        private float rangeMax;
        private float increment;
    }
}
