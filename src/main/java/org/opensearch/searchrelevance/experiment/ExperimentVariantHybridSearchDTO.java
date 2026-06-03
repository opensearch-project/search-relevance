/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */
package org.opensearch.searchrelevance.experiment;

import java.util.Map;

/**
 * Carrier for a single hybrid-search experiment variant. Each implementation knows how to
 * (a) materialize itself into a {@code parameters} map for persistence, and
 * (b) materialize itself into a temporary search-pipeline definition for execution.
 * <p>
 * Two implementations exist: {@link RRFExperimentVariantHybridSearchDTO} for rank-based
 * RRF variants, and {@link ScoreBasedExperimentVariantHybridSearchDTO} for score-based
 * variants going through the normalization-processor.
 */
public interface ExperimentVariantHybridSearchDTO {

    /**
     * @return parameters map suitable for persistence as the {@code parameters} field of an
     *         {@link org.opensearch.searchrelevance.model.ExperimentVariant} document.
     */
    Map<String, Object> toParameters();

    /**
     * @return a temporary search-pipeline definition (a nested map mirroring the JSON
     *         structure neural-search expects) for executing this variant.
     */
    Map<String, Object> toSearchPipeline();
}
