/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */
package org.opensearch.searchrelevance.stats.common;

import java.io.IOException;

import org.opensearch.core.xcontent.ToXContentFragment;
import org.opensearch.core.xcontent.XContentBuilder;

/**
 * A serializable snapshot of a stat at a given point in time.
 * Holds stat values, type, and metadata for processing and returning across rest layer.
 * These are not meant to be persisted.
 * @param <T> The type of the value of the stat
 */
public interface StatSnapshot<T> extends ToXContentFragment {
    /**
     * Field name of the stat_type in XContent
     */
    String STAT_TYPE_FIELD = "stat_type";

    /**
     * Field name of the value in XContent
     */
    String VALUE_FIELD = "value";

    /**
     * Gets the raw value of the stat, excluding any metadata
     * @return the raw stat value
     */
    T getValue();

    /**
     * Converts to fields xContent, including stat metadata
     *
     * @param builder XContentBuilder
     * @param params Params
     * @return XContentBuilder
     * @throws IOException thrown by builder for invalid field
     */
    XContentBuilder toXContent(XContentBuilder builder, Params params) throws IOException;
}
