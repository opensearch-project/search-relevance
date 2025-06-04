/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */
package org.opensearch.searchrelevance.stats.events;

import java.util.Locale;

import org.opensearch.searchrelevance.stats.common.StatType;

/**
 * Enum for different kinds of event stat types to track
 */
public enum EventStatType implements StatType {
    TIMESTAMPED_EVENT_COUNTER;

    /**
     * Gets the name of the stat type, the enum name in lowercase
     * @return the name of the stat type
     */
    public String getTypeString() {
        return name().toLowerCase(Locale.ROOT);
    }
}
