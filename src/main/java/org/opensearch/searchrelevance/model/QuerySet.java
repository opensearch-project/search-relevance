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

import lombok.Builder;
import lombok.Getter;

/**
 * QuerySet is a system index object that represents all query set sampling/inserting params.
 */
@Getter
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
    public static final String MODEL_ID = "modelId";
    public static final String SOURCE_INDEX = "sourceIndex";
    public static final String CONTEXT_FIELDS = "contextFields";
    public static final String CATEGORIES = "categories";

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
    private final String modelId;
    private final String sourceIndex;
    private final List<String> contextFields;
    private final List<String> categories;
    private final List<QuerySetEntry> querySetQueries;

    @Override
    public XContentBuilder toXContent(XContentBuilder builder, Params params) throws IOException {
        XContentBuilder xContentBuilder = builder.startObject();
        xContentBuilder.field(ID, this.id);
        xContentBuilder.field(NAME, this.name == null ? "" : this.name.trim());
        xContentBuilder.field(DESCRIPTION, this.description == null ? "" : this.description.trim());
        xContentBuilder.field(SAMPLING, this.sampling == null ? "" : this.sampling.trim());
        xContentBuilder.field(TIME_STAMP, this.timestamp.trim());
        xContentBuilder.field(STATUS, this.status != null ? this.status.name() : null);
        xContentBuilder.field(TYPE, this.type != null ? this.type.getValue() : null);
        xContentBuilder.field(NUMBER_OF_QUERY_TERMS, this.numberOfQueryTerms);
        if (this.modelId != null) {
            xContentBuilder.field(MODEL_ID, this.modelId);
        }
        if (this.sourceIndex != null) {
            xContentBuilder.field(SOURCE_INDEX, this.sourceIndex);
        }
        if (this.contextFields != null && !this.contextFields.isEmpty()) {
            xContentBuilder.field(CONTEXT_FIELDS, this.contextFields);
        }
        if (this.categories != null && !this.categories.isEmpty()) {
            xContentBuilder.field(CATEGORIES, this.categories);
        }
        // Add the query_set_queries field
        xContentBuilder.startArray(QUERY_SET_QUERIES);
        for (QuerySetEntry entry : querySetQueries) {
            entry.toXContent(xContentBuilder, params);
        }
        xContentBuilder.endArray();
        return xContentBuilder.endObject();
    }
}
