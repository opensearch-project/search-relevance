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

    public QuerySet(String id, String name, String description) {
        this.id = id;
        this.description = description;
        this.name = name;
    }

    @Override
    public XContentBuilder toXContent(XContentBuilder builder, Params params) throws IOException {
        XContentBuilder xContentBuilder = builder.startObject();
        xContentBuilder.field("id", this.id.trim());
        xContentBuilder.field("name", this.name == null ? "" : this.name.trim());
        xContentBuilder.field("description", this.description == null ? "" : this.description.trim());
        return xContentBuilder.endObject();
    }

    public static class Builder {
        private String id;
        private String name = "";
        private String description = "";

        private Builder() {}

        private Builder(QuerySet t) {
            this.name = t.name;
            this.id = t.id;
            this.description = t.description;
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

        public QuerySet build() {
            return new QuerySet(this.id, this.name, this.description);
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

    public String description() {
        return description;
    }
}
