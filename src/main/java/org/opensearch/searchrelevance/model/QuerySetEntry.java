/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */
package org.opensearch.searchrelevance.model;

import java.io.IOException;

import org.opensearch.core.xcontent.ToXContent.Params;
import org.opensearch.core.xcontent.ToXContentObject;
import org.opensearch.core.xcontent.XContentBuilder;

/**
 * QuerySetEntry represents a single query entry in a query set with its text.
 */
public class QuerySetEntry implements ToXContentObject {

    public static final String QUERY_TEXT = "queryText";

    private final String queryText;

    public QuerySetEntry(String queryText) {
        this.queryText = queryText;
    }

    @Override
    public XContentBuilder toXContent(XContentBuilder builder, Params params) throws IOException {
        XContentBuilder xContentBuilder = builder.startObject();
        xContentBuilder.field(QUERY_TEXT, this.queryText);
        return xContentBuilder.endObject();
    }

    public String queryText() {
        return queryText;
    }

    public static class Builder {
        private String queryText;

        private Builder() {}

        private Builder(QuerySetEntry entry) {
            this.queryText = entry.queryText;
        }

        public Builder queryText(String queryText) {
            this.queryText = queryText;
            return this;
        }

        public QuerySetEntry build() {
            return new QuerySetEntry(this.queryText);
        }

        public static Builder builder() {
            return new Builder();
        }

        public static Builder builder(QuerySetEntry entry) {
            return new Builder(entry);
        }
    }
}
