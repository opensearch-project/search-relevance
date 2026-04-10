/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */
package org.opensearch.searchrelevance.model;

import java.io.IOException;
import java.util.List;

import org.opensearch.core.xcontent.ToXContent.Params;
import org.opensearch.core.xcontent.ToXContentObject;
import org.opensearch.core.xcontent.XContentBuilder;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

/**
 * QuerySet is a system index object that represents all query set sampling/inserting params.
 */
@Getter
@AllArgsConstructor
@Builder
public class QuerySet implements ToXContentObject {

    public static final String ID = "id";
    public static final String NAME = "name";
    public static final String DESCRIPTION = "description";
    public static final String TIME_STAMP = "timestamp";
    public static final String SAMPLING = "sampling";
    public static final String STATUS = "status";
    public static final String TYPE = "type";
    public static final String NUMBER_OF_QUERY_TERMS = "numberOfQueryTerms";
    public static final String QUERY_SET_QUERIES = "querySetQueries";

    /**
     * Identifier of the system index
     */
    private final String id;
    private final String name;
    private final String description;
    private final String timestamp;
    private final String sampling;
    private final AsyncStatus status;
    private final QuerySetType type;
    private final int numberOfQueryTerms;
    private final List<QuerySetEntry> querySetQueries;

    @Override
    public XContentBuilder toXContent(XContentBuilder builder, Params params) throws IOException {
        XContentBuilder xContentBuilder = builder.startObject();
        xContentBuilder.field(ID, this.id);
        xContentBuilder.field(NAME, this.name == null ? "" : this.name.trim());
        xContentBuilder.field(DESCRIPTION, this.description == null ? "" : this.description.trim());
        xContentBuilder.field(SAMPLING, this.sampling == null ? "" : this.sampling.trim());
        xContentBuilder.field(TIME_STAMP, this.timestamp.trim());
        xContentBuilder.field(STATUS, this.status.name().trim());
        xContentBuilder.field(TYPE, this.type.name().trim());
        xContentBuilder.field(NUMBER_OF_QUERY_TERMS, this.numberOfQueryTerms);
        // Add the query_set_queries field
        xContentBuilder.startArray(QUERY_SET_QUERIES);
        for (QuerySetEntry entry : querySetQueries) {
            entry.toXContent(xContentBuilder, params);
        }
        xContentBuilder.endArray();
        return xContentBuilder.endObject();
    }
}
