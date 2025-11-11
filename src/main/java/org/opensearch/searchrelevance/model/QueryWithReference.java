/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */
package org.opensearch.searchrelevance.model;

import java.io.IOException;
import java.util.Collections;
import java.util.Map;
import java.util.Objects;

import org.opensearch.core.common.io.stream.StreamInput;
import org.opensearch.core.common.io.stream.StreamOutput;
import org.opensearch.core.common.io.stream.Writeable;

public class QueryWithReference implements Writeable {
    private final String queryText;
    private final Map<String, String> customizedKeyValueMap;

    public final static String DELIMITER = "#";

    public QueryWithReference(String queryText, Map<String, String> customizedKeyValueMap) {
        this.queryText = queryText;
        this.customizedKeyValueMap = customizedKeyValueMap != null ? customizedKeyValueMap : Collections.emptyMap();
    }

    public QueryWithReference(StreamInput in) throws IOException {
        this.queryText = in.readString();
        boolean hasCustomizedKeyValueMap = in.readBoolean();
        if (hasCustomizedKeyValueMap) {
            this.customizedKeyValueMap = in.readMap(StreamInput::readString, StreamInput::readString);
        } else {
            this.customizedKeyValueMap = Collections.emptyMap();
        }
    }

    @Override
    public void writeTo(StreamOutput out) throws IOException {
        out.writeString(queryText);
        if (customizedKeyValueMap != null && !customizedKeyValueMap.isEmpty()) {
            out.writeBoolean(true);
            out.writeMap(customizedKeyValueMap, StreamOutput::writeString, StreamOutput::writeString);
        } else {
            out.writeBoolean(false);
        }
    }

    public String getQueryText() {
        return queryText;
    }

    public Map<String, String> getCustomizedKeyValueMap() {
        return customizedKeyValueMap;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        QueryWithReference that = (QueryWithReference) o;
        return Objects.equals(queryText, that.queryText) && Objects.equals(customizedKeyValueMap, that.customizedKeyValueMap);
    }

    @Override
    public int hashCode() {
        return Objects.hash(queryText, customizedKeyValueMap);
    }

    @Override
    public String toString() {
        return "QueryWithReference{" + "queryText='" + queryText + '\'' + ", customizedKeyValueMap=" + customizedKeyValueMap + '}';
    }
}
