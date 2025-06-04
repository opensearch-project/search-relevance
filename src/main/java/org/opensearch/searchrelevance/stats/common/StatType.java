/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */
package org.opensearch.searchrelevance.stats.common;

/**
 * Interface for the type of stat. Used for stat type metadata
 */
public interface StatType {

    /**
     * Get the name of the stat type containing info about the type and how to process it
     * @return name of the stat type
     */
    String getTypeString();
}
