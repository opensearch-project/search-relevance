/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */
package org.opensearch.searchrelevance.model;

import java.io.IOException;

import org.opensearch.core.xcontent.ToXContentObject;
import org.opensearch.core.xcontent.XContentBuilder;

import lombok.AllArgsConstructor;

/**
 * SearchConfiguration is a system index object that represents all search related params.
 */
@AllArgsConstructor
public class SearchConfiguration implements ToXContentObject {
    public static final String ID = "id";
    public static final String NAME = "name";
    public static final String DESCRIPTION = "description";
    public static final String TIME_STAMP = "timestamp";
    public static final String INDEX = "index";

    public static final String QUERY = "query";
    public static final String SEARCH_PIPELINE = "searchPipeline";

    /**
     * Identifier of the system index
     */
    private final String id;
    private final String name;
    private final String timestamp;
    private final String index;
    private final String query;
    private final String searchPipeline;
    private final String description;

    @Override
    public XContentBuilder toXContent(XContentBuilder builder, Params params) throws IOException {
        XContentBuilder xContentBuilder = builder.startObject();
        xContentBuilder.field(ID, this.id);
        xContentBuilder.field(NAME, this.name.trim());
        xContentBuilder.field(TIME_STAMP, this.timestamp.trim());
        xContentBuilder.field(INDEX, this.index.trim());
        xContentBuilder.field(QUERY, this.query.trim());
        xContentBuilder.field(SEARCH_PIPELINE, this.searchPipeline == null ? "" : this.searchPipeline.trim());
        xContentBuilder.field(DESCRIPTION, this.description == null ? "" : this.description.trim());
        return xContentBuilder.endObject();
    }

    public String id() {
        return id;
    }

    public String name() {
        return name;
    }

    public String index() {
        return index;
    }

    public String timestamp() {
        return timestamp;
    }

    public String query() {
        return query;
    }

    public String searchPipeline() {
        return searchPipeline;
    }

}
