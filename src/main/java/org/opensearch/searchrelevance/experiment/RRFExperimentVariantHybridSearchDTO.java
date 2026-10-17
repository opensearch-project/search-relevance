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
import static org.opensearch.searchrelevance.experiment.ExperimentOptionsForHybridSearch.EXPERIMENT_OPTION_RANK_CONSTANT;
import static org.opensearch.searchrelevance.experiment.QuerySourceUtil.COMBINATION_KEY;
import static org.opensearch.searchrelevance.experiment.QuerySourceUtil.PHASE_RESULTS_PROCESSORS_KEY;
import static org.opensearch.searchrelevance.experiment.QuerySourceUtil.RANK_CONSTANT_KEY;
import static org.opensearch.searchrelevance.experiment.QuerySourceUtil.SCORE_RANKER_PROCESSOR_KEY;
import static org.opensearch.searchrelevance.experiment.QuerySourceUtil.TECHNIQUE_KEY;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * Variant DTO for rank-based hybrid search through the neural-search
 * score-ranker-processor. Carries the single tunable, {@code rank_constant}.
 */
@Data
@AllArgsConstructor
public class RRFExperimentVariantHybridSearchDTO implements ExperimentVariantHybridSearchDTO {

    private final int rankConstant;

    @Override
    public Map<String, Object> toParameters() {
        Map<String, Object> parameters = new HashMap<>();
        parameters.put(EXPERIMENT_OPTION_COMBINATION_TECHNIQUE, COMBINATION_RRF);
        parameters.put(EXPERIMENT_OPTION_RANK_CONSTANT, rankConstant);
        return parameters;
    }

    @Override
    public Map<String, Object> toSearchPipeline() {
        Map<String, Object> rrfCombinationConfig = new HashMap<>(Map.of(TECHNIQUE_KEY, COMBINATION_RRF, RANK_CONSTANT_KEY, rankConstant));
        Map<String, Object> scoreRankerConfig = new HashMap<>(Map.of(COMBINATION_KEY, rrfCombinationConfig));
        Map<String, Object> phaseProcessorObject = new HashMap<>(Map.of(SCORE_RANKER_PROCESSOR_KEY, scoreRankerConfig));
        Map<String, Object> pipeline = new HashMap<>();
        pipeline.put(PHASE_RESULTS_PROCESSORS_KEY, List.of(phaseProcessorObject));
        return pipeline;
    }

    /**
     * Reconstruct an RRF variant DTO from a persisted {@code parameters} map. The map must
     * carry a numeric {@link ExperimentOptionsForHybridSearch#EXPERIMENT_OPTION_RANK_CONSTANT}.
     */
    static RRFExperimentVariantHybridSearchDTO fromParameters(Map<String, Object> parameters) {
        Object rankConstantObj = parameters.get(EXPERIMENT_OPTION_RANK_CONSTANT);
        if (rankConstantObj == null) {
            throw new IllegalArgumentException("RRF variant is missing required parameter '" + EXPERIMENT_OPTION_RANK_CONSTANT + "'");
        }
        if (!(rankConstantObj instanceof Number)) {
            throw new IllegalArgumentException(
                "RRF parameter '" + EXPERIMENT_OPTION_RANK_CONSTANT + "' must be a number, got: " + rankConstantObj.getClass()
            );
        }
        return new RRFExperimentVariantHybridSearchDTO(((Number) rankConstantObj).intValue());
    }
}
