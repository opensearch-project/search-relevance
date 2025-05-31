/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */
package org.opensearch.searchrelevance.experiment;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Utility class for a query source
 */
public class QuerySourceUtil {

    /**
     * Creates a definition of a temporary search pipeline for hybrid search.
     * @param experimentHybridSearchDao
     * @return
     */
    public static Map<String, Object> createDefinitionOfTemporarySearchPipeline(
        final SubExperimentHybridSearchDao experimentHybridSearchDao
    ) {
        Map<String, Object> normalizationTechniqueConfig = new HashMap<>(
            Map.of("technique", experimentHybridSearchDao.getNormalizationTechnique())
        );
        Map<String, Object> combinationTechniqueConfig = new HashMap<>(
            Map.of("technique", experimentHybridSearchDao.getCombinationTechnique())
        );
        Map<String, Object> normalizationProcessorConfig = new HashMap<>(
            Map.of("normalization", normalizationTechniqueConfig, "combination", combinationTechniqueConfig)
        );
        Map<String, Object> phaseProcessorObject = new HashMap<>(Map.of("normalization-processor", normalizationProcessorConfig));
        Map<String, Object> temporarySearchPipeline = new HashMap<>();
        temporarySearchPipeline.put("phase_results_processors", List.of(phaseProcessorObject));
        return temporarySearchPipeline;
    }
}
