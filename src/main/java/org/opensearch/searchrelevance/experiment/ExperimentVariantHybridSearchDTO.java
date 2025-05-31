/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */
package org.opensearch.searchrelevance.experiment;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ExperimentVariantHybridSearchDTO {
    private final String normalizationTechnique;
    private final String combinationTechnique;
    private final float[] queryWeightsForCombination;
}
