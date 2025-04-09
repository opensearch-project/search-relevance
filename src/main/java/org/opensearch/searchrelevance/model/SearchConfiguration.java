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

public class SearchConfiguration implements ToXContentObject {
    public static final String ID = "id";
    public static final String TIME_STAMP = "timestamp";
    public static final String NAME = "name";
    public static final String QUERY_BODY = "queryBody";
    public static final String SEARCH_PIPELINE = "searchPipeline";
    private String id;
    private String timestamp;
    private String name;
    private String queryBody;
    private String searchPipeline;

    public SearchConfiguration(String id, String timestamp, String name, String queryBody, String searchPipeline) {
        this.id = id;
        this.timestamp = timestamp;
        this.name = name;
        this.queryBody = queryBody;
        this.searchPipeline = searchPipeline;
    }

    @Override
    public XContentBuilder toXContent(XContentBuilder builder, Params params) throws IOException {
        XContentBuilder xContentBuilder = builder.startObject();
        xContentBuilder.field(ID, this.id.trim());
        xContentBuilder.field(TIME_STAMP, this.timestamp.trim());
        xContentBuilder.field(NAME, this.name.trim());
        xContentBuilder.field(QUERY_BODY, this.queryBody.trim());
        xContentBuilder.field(SEARCH_PIPELINE, this.searchPipeline == null ? "" : this.searchPipeline.trim());
        return xContentBuilder.endObject();
    }

    public String id() {
        return id;
    }

    public String timestamp() {
        return timestamp;
    }

    public String name() {
        return name;
    }

    public String queryBody() {
        return queryBody;
    }

    public String searchPipeline() {
        return searchPipeline;
    }

}
