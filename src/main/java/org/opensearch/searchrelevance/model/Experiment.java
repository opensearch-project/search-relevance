/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */
package org.opensearch.searchrelevance.model;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.opensearch.core.xcontent.ToXContent.Params;
import org.opensearch.core.xcontent.ToXContentObject;
import org.opensearch.core.xcontent.XContentBuilder;

public class Experiment implements ToXContentObject {
    public static final String ID = "id";
    public static final String NAME = "name";
    public static final String DESCRIPTION = "description";
    public static final String TIME_STAMP = "timestamp";
    public static final String INDEX = "index";
    public static final String JUDGMENT_LIST = "judgmentList";
    public static final String QUERY_SET_LIST = "querySetList";
    private String id;
    private String name;
    private String description;
    private String timestamp;
    private String index;
    private List<String> judgmentList;
    private List<String> querySetList;

    public Experiment(
        String id,
        String name,
        String description,
        String timestamp,
        String index,
        List<String> judgmentList,
        List<String> querySetList
    ) {
        this.id = id;
        this.name = name;
        this.description = description;
        this.timestamp = timestamp;
        this.index = index;
        this.judgmentList = judgmentList;
        this.querySetList = querySetList;
    }

    @Override
    public XContentBuilder toXContent(XContentBuilder builder, Params params) throws IOException {
        XContentBuilder xContentBuilder = builder.startObject();
        xContentBuilder.field(ID, this.id.trim());
        xContentBuilder.field(NAME, this.name == null ? "" : this.name.trim());
        xContentBuilder.field(DESCRIPTION, this.description == null ? "" : this.description.trim());
        xContentBuilder.field(TIME_STAMP, this.timestamp.trim());
        xContentBuilder.field(INDEX, this.index.trim());
        xContentBuilder.field(JUDGMENT_LIST, this.judgmentList == null ? new ArrayList<>() : this.judgmentList);
        xContentBuilder.field(QUERY_SET_LIST, this.querySetList == null ? new ArrayList<>() : this.querySetList);
        return xContentBuilder.endObject();
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

    public String timestamp() {
        return timestamp;
    }

    public String index() {
        return index;
    }

    public List<String> judgmentList() {
        return judgmentList;
    }

    public List<String> querySetList() {
        return querySetList;
    }

}
