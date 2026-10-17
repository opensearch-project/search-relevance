/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */
package org.opensearch.searchrelevance.experiment;

import java.util.List;

/**
 * A hybrid-search configuration that knows how to expand itself into one or more
 * concrete variant DTOs. Each implementation owns the parameters and the expansion
 * logic for a single technique family (e.g. score-based mean combinations, RRF).
 */
public interface HybridSearchConfig {

    /**
     * Expand this configuration into the list of variant DTOs it represents.
     */
    List<ExperimentVariantHybridSearchDTO> getAllVariants();
}
