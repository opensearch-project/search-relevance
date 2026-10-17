/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */
package org.opensearch.searchrelevance.experiment;

import static org.opensearch.searchrelevance.experiment.ExperimentOptionsForHybridSearch.COMBINATION_RRF;
import static org.opensearch.searchrelevance.experiment.ExperimentOptionsForHybridSearch.EXPERIMENT_OPTION_COMBINATION_TECHNIQUE;

import java.util.Map;

/**
 * Factory for reconstructing typed {@link ExperimentVariantHybridSearchDTO} instances
 * from the untyped {@code parameters} map persisted on
 * {@link org.opensearch.searchrelevance.model.ExperimentVariant}. Dispatches on the
 * {@code combination} value: {@code "rrf"} routes to RRF, anything else routes to
 * score-based.
 */
public final class ExperimentVariantHybridSearchDTOFactory {

    private ExperimentVariantHybridSearchDTOFactory() {}

    public static ExperimentVariantHybridSearchDTO fromParameters(Map<String, Object> parameters) {
        if (COMBINATION_RRF.equals(parameters.get(EXPERIMENT_OPTION_COMBINATION_TECHNIQUE))) {
            return RRFExperimentVariantHybridSearchDTO.fromParameters(parameters);
        }
        return ScoreBasedExperimentVariantHybridSearchDTO.fromParameters(parameters);
    }
}
