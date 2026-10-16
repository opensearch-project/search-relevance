/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */
package org.opensearch.searchrelevance.experiment;

import java.io.IOException;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.opensearch.searchrelevance.model.ExperimentVariant;

/**
 * Utility class for a query source
 */
public class QuerySourceUtil {

    public static final int NUMBER_OF_SUBQUERIES_IN_HYBRID_QUERY = 2;

    static final String PHASE_RESULTS_PROCESSORS_KEY = "phase_results_processors";
    static final String NORMALIZATION_PROCESSOR_KEY = "normalization-processor";
    static final String SCORE_RANKER_PROCESSOR_KEY = "score-ranker-processor";
    static final String NORMALIZATION_KEY = "normalization";
    static final String COMBINATION_KEY = "combination";
    static final String TECHNIQUE_KEY = "technique";
    static final String PARAMETERS_KEY = "parameters";
    static final String WEIGHTS_KEY = "weights";
    static final String RANK_CONSTANT_KEY = "rank_constant";

    /**
     * Creates a definition of a temporary search pipeline for hybrid search by reconstructing
     * the appropriate variant DTO from the persisted parameters and delegating pipeline
     * generation to it.
     *
     * @param experimentVariant sub-experiment to create the pipeline for
     * @return definition of a temporary search pipeline
     */
    public static Map<String, Object> createDefinitionOfTemporarySearchPipeline(final ExperimentVariant experimentVariant) {
        return ExperimentVariantHybridSearchDTOFactory.fromParameters(experimentVariant.getParameters()).toSearchPipeline();
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
