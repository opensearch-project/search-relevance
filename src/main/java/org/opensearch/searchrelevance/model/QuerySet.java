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

import org.opensearch.core.xcontent.ToXContent.Params;
import org.opensearch.core.xcontent.ToXContentObject;
import org.opensearch.core.xcontent.XContentBuilder;

public class QuerySet implements ToXContentObject {

    public static final String ID = "id";
    public static final String TIME_STAMP = "timestamp";
    public static final String DESCRIPTION = "description";
    public static final String NAME = "name";
    public static final String SAMPLING = "sampling";
    public static final String QUERY_SET_QUERIES = "querySetQueries";
    private String id;
    private String description;
    private String name;
    private String sampling;
    private String timestamp;
    private Map<String, Long> querySetQueries;

    public QuerySet(String id, String name, String description, String sampling, String timestamp, Map<String, Long> querySetQueries) {
        this.id = id;
        this.description = description;
        this.name = name;
        this.sampling = sampling;
        this.timestamp = timestamp;
        this.querySetQueries = querySetQueries;
    }

    @Override
    public XContentBuilder toXContent(XContentBuilder builder, Params params) throws IOException {
        XContentBuilder xContentBuilder = builder.startObject();
        xContentBuilder.field(ID, this.id.trim());
        xContentBuilder.field(NAME, this.name == null ? "" : this.name.trim());
        xContentBuilder.field(DESCRIPTION, this.description == null ? "" : this.description.trim());
        xContentBuilder.field(SAMPLING, this.sampling == null ? "" : this.sampling.trim());
        xContentBuilder.field(TIME_STAMP, this.timestamp.trim());
        // Add the query_set_queries field
        xContentBuilder.startObject(QUERY_SET_QUERIES);
        for (Map.Entry<String, Long> entry : querySetQueries.entrySet()) {
            builder.field(entry.getKey(), entry.getValue());
        }
        xContentBuilder.endObject();
        return xContentBuilder.endObject();
    }

    public static class Builder {
        private String id;
        private String name = "";
        private String description = "";
        private String sampling = "";
        private String timestamp = "";
        private Map<String, Long> querySetQueries;

        private Builder() {}

        private Builder(QuerySet t) {
            this.name = t.name;
            this.id = t.id;
            this.description = t.description;
            this.sampling = t.sampling;
            this.timestamp = t.timestamp;
            this.querySetQueries = t.querySetQueries;
        }

        public Builder name(String name) {
            this.name = name;
            return this;
        }

        public Builder id(String id) {
            this.id = id;
            return this;
        }

        public Builder description(String description) {
            this.description = description;
            return this;
        }

        public Builder sampling(String sampling) {
            this.sampling = sampling;
            return this;
        }

        public Builder timestamp(String timestamp) {
            this.timestamp = timestamp;
            return this;
        }

        public Builder querySetQueries(Map<String, Long> querySetQueries) {
            this.querySetQueries = querySetQueries;
            return this;
        }

        public QuerySet build() {
            return new QuerySet(this.id, this.name, this.description, this.sampling, this.timestamp, this.querySetQueries);
        }

        public static Builder builder() {
            return new Builder();
        }

        public static Builder builder(QuerySet t) {
            return new Builder(t);
        }
    }

    public String id() {
        return id;
    }

    public String name() {
        return name;
    }

    public String sampling() {
        return sampling;
    }

    public String timestamp() {
        return timestamp;
    }

}
