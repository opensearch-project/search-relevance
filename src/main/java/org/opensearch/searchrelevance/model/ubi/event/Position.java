/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */
package org.opensearch.searchrelevance.model.ubi.event;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * A position represents the location of a search result in an event.
 */
public class Position {

    @JsonProperty("ordinal")
    private int ordinal;

    @Override
    public String toString() {
        return String.valueOf(ordinal);
    }

    /**
     * Gets the ordinal of the position.
     * @return The ordinal of the position.
     */
    public int getOrdinal() {
        return ordinal;
    }

    /**
     * Sets the ordinal of the position.
     * @param ordinal The ordinal of the position.
     */
    public void setOrdinal(int ordinal) {
        this.ordinal = ordinal;
    }

}
