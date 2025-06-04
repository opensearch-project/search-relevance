/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */
package org.opensearch.searchrelevance.stats.info;

import java.io.IOException;

import org.opensearch.core.xcontent.XContentBuilder;
import org.opensearch.searchrelevance.stats.common.StatSnapshot;

import lombok.Getter;
import lombok.Setter;

/**
 * A settable info snapshot used to track Strings, booleans, or other simple serializable objects
 * This are meant to be constructed, set, and serialized, not for long storage in memory
 * @param <T> the type of the value to set
 */
public class SettableInfoStatSnapshot<T> implements StatSnapshot<T> {
    @Getter
    @Setter
    private T value;

    private InfoStatName statName;

    /**
     * Creates a new stat snapshot with default null value
     * @param statName the associated stat name
     */
    public SettableInfoStatSnapshot(InfoStatName statName) {
        this.statName = statName;
        this.value = null;
    }

    /**
     * Creates a new stat snapshot for a given value
     * @param statName the associated stat name
     * @param value the initial value to set
     */
    public SettableInfoStatSnapshot(InfoStatName statName, T value) {
        this.statName = statName;
        this.value = value;
    }

    /**
     * Converts to fields xContent, including stat metadata
     *
     * @param builder XContentBuilder
     * @param params Params
     * @return XContentBuilder
     * @throws IOException thrown by builder for invalid field
     */
    @Override
    public XContentBuilder toXContent(XContentBuilder builder, Params params) throws IOException {
        builder.startObject();
        builder.field(StatSnapshot.VALUE_FIELD, getValue());
        builder.field(StatSnapshot.STAT_TYPE_FIELD, statName.getStatType().getTypeString());
        builder.endObject();
        return builder;
    }
}
