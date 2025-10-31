/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */
package org.opensearch.searchrelevance.model;

import lombok.Builder;
import lombok.Data;
import lombok.NonNull;

/**
 * Immutable class representing search configuration details.
 */
@Data
@Builder
public class SearchConfigurationDetails {
    @NonNull
    private final String index;

    @NonNull
    private final String query;

    private final String pipeline; // can be null or empty
}
