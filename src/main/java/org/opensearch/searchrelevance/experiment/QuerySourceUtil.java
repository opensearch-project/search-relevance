/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */
package org.opensearch.searchrelevance.experiment;

import static org.opensearch.searchrelevance.experiment.ExperimentOptionsForHybridSearch.EXPERIMENT_OPTION_COMBINATION_TECHNIQUE;
import static org.opensearch.searchrelevance.experiment.ExperimentOptionsForHybridSearch.EXPERIMENT_OPTION_NORMALIZATION_TECHNIQUE;
import static org.opensearch.searchrelevance.experiment.ExperimentOptionsForHybridSearch.EXPERIMENT_OPTION_WEIGHTS_FOR_COMBINATION;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

import org.opensearch.searchrelevance.model.ExperimentVariant;

/**
 * Utility class for a query source
 */
public class QuerySourceUtil {

    public static final int NUMBER_OF_SUBQUERIES_IN_HYBRID_QUERY = 2;

    /**
     * Creates a definition of a temporary search pipeline for hybrid search.
     * @param experimentVariant sub-experiment to create the pipeline for
     * @return definition of a temporary search pipeline
     */
    public static Map<String, Object> createDefinitionOfTemporarySearchPipeline(final ExperimentVariant experimentVariant) {
        Map<String, Object> experimentVariantParameters = experimentVariant.getParameters();
        Map<String, Object> normalizationTechniqueConfig = new HashMap<>(
            Map.of("technique", experimentVariantParameters.get(EXPERIMENT_OPTION_NORMALIZATION_TECHNIQUE))
        );

        Map<String, Object> combinationTechniqueConfig = new HashMap<>(
            Map.of("technique", experimentVariantParameters.get(EXPERIMENT_OPTION_COMBINATION_TECHNIQUE))
        );
        if (Objects.nonNull(experimentVariantParameters.get(EXPERIMENT_OPTION_WEIGHTS_FOR_COMBINATION))) {
            float[] weights = (float[]) experimentVariantParameters.get(EXPERIMENT_OPTION_WEIGHTS_FOR_COMBINATION);
            List<Double> weightsList = new ArrayList<>(weights.length);
            for (float weight : weights) {
                weightsList.add((double) weight);
            }
            combinationTechniqueConfig.put("parameters", new HashMap<>(Map.of("weights", weightsList)));
        }

        Map<String, Object> normalizationProcessorConfig = new HashMap<>(
            Map.of("normalization", normalizationTechniqueConfig, "combination", combinationTechniqueConfig)
        );
        Map<String, Object> phaseProcessorObject = new HashMap<>(Map.of("normalization-processor", normalizationProcessorConfig));
        Map<String, Object> temporarySearchPipeline = new HashMap<>();
        temporarySearchPipeline.put("phase_results_processors", List.of(phaseProcessorObject));
        return temporarySearchPipeline;
    }

    /**
     * Validate that the query in the search configuration is a hybrid query with two sub-queries.
     * @param fullQueryMap
     * @throws IOException
     */
    public static void validateHybridQuery(Map<String, Object> fullQueryMap) throws IOException {
        if (fullQueryMap.containsKey("query") == false || fullQueryMap.get("query") instanceof Map == false) {
            throw new IllegalArgumentException("search configuration must have at least one query");
        }
        Map<String, Object> queryMap = (Map<String, Object>) fullQueryMap.get("query");
        if (queryMap.containsKey("hybrid") == false || queryMap.get("hybrid") instanceof Map<?, ?> == false) {
            throw new IllegalArgumentException("query in search configuration must be of type hybrid");
        }
        Map<String, Object> hybridMap = (Map<String, Object>) queryMap.get("hybrid");
        if (hybridMap.containsKey("queries") == false || hybridMap.get("queries") instanceof List<?> == false) {
            throw new IllegalArgumentException("hybrid query in search configuration does not have sub-queries");
        }
        List<?> queriesMap = (List<?>) hybridMap.get("queries");
        if (queriesMap.size() != NUMBER_OF_SUBQUERIES_IN_HYBRID_QUERY) {
            throw new IllegalArgumentException(
                String.format(
                    Locale.ROOT,
                    "invalid hybrid query: expected exactly [%d] sub-queries but found [%d]",
                    NUMBER_OF_SUBQUERIES_IN_HYBRID_QUERY,
                    queriesMap.size()
                )
            );
        }
    }
}
