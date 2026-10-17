/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */
package org.opensearch.searchrelevance.model;

import java.io.IOException;
import java.util.Map;
import java.util.Objects;

import org.opensearch.core.xcontent.ToXContent;
import org.opensearch.core.xcontent.ToXContentObject;
import org.opensearch.core.xcontent.XContentBuilder;

/**
 * Immutable fingerprints of experiment inputs captured at execution time for drift detection.
 */
public final class ExperimentInputSignature implements ToXContentObject {
    public static final String FIELD = "inputSignature";
    public static final String QUERY_SET = "query_set";
    public static final String JUDGMENT_LIST = "judgment_list";
    public static final String SEARCH_CONFIGURATIONS = "search_configurations";

    private final String querySetSha256;
    private final String judgmentListSha256;
    private final String searchConfigurationsSha256;

    public ExperimentInputSignature(String querySetSha256, String judgmentListSha256, String searchConfigurationsSha256) {
        this.querySetSha256 = Objects.requireNonNull(querySetSha256, "querySetSha256");
        this.judgmentListSha256 = Objects.requireNonNull(judgmentListSha256, "judgmentListSha256");
        this.searchConfigurationsSha256 = Objects.requireNonNull(searchConfigurationsSha256, "searchConfigurationsSha256");
    }

    public static ExperimentInputSignature fromStoredMap(Map<String, ?> map) {
        if (map == null) {
            return null;
        }
        Object qs = map.get(QUERY_SET);
        Object jl = map.get(JUDGMENT_LIST);
        Object sc = map.get(SEARCH_CONFIGURATIONS);
        if (!(qs instanceof String) || !(jl instanceof String) || !(sc instanceof String)) {
            return null;
        }
        return new ExperimentInputSignature((String) qs, (String) jl, (String) sc);
    }

    public String querySetSha256() {
        return querySetSha256;
    }

    public String judgmentListSha256() {
        return judgmentListSha256;
    }

    public String searchConfigurationsSha256() {
        return searchConfigurationsSha256;
    }

    @Override
    public XContentBuilder toXContent(XContentBuilder builder, ToXContent.Params params) throws IOException {
        XContentBuilder b = builder.startObject();
        b.field(QUERY_SET, querySetSha256);
        b.field(JUDGMENT_LIST, judgmentListSha256);
        b.field(SEARCH_CONFIGURATIONS, searchConfigurationsSha256);
        return b.endObject();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ExperimentInputSignature that = (ExperimentInputSignature) o;
        return querySetSha256.equals(that.querySetSha256)
            && judgmentListSha256.equals(that.judgmentListSha256)
            && searchConfigurationsSha256.equals(that.searchConfigurationsSha256);
    }

    @Override
    public int hashCode() {
        return Objects.hash(querySetSha256, judgmentListSha256, searchConfigurationsSha256);
    }
}
