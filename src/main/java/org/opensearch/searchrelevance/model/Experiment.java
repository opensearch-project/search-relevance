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
import java.util.Map;
import java.util.Optional;

import org.opensearch.core.xcontent.ToXContentObject;
import org.opensearch.core.xcontent.XContentBuilder;

/**
 * Experiment is a system index object that store experiment results.
 */
public class Experiment implements ToXContentObject {
    public static final String ID = "id";
    public static final String TIME_STAMP = "timestamp";
    public static final String TYPE = "type";
    public static final String STATUS = "status";
    public static final String QUERY_SET_ID = "querySetId";
    public static final String SEARCH_CONFIGURATION_LIST = "searchConfigurationList";
    public static final String JUDGMENT_LIST = "judgmentList";
    public static final String SIZE = "size";
    public static final String RESULTS = "results";
    private static final int DEFAULTED_SIZE = 10;

    /**
     * Identifier of the system index
     */
    private final String id;
    private final String timestamp;
    private final ExperimentType type;
    private final ExperimentStatus status;
    private final String querySetId;
    private final List<String> searchConfigurationList;
    private final List<String> judgmentList;
    private final int size;
    private final Map<String, Object> results;

    public Experiment(
        String id,
        String timestamp,
        ExperimentType type,
        ExperimentStatus status,
        String querySetId,
        List<String> searchConfigurationList,
        List<String> judgmentList,
        int size,
        Map<String, Object> results
    ) {
        this.id = id;
        this.timestamp = timestamp;
        this.type = type;
        this.status = status;
        this.querySetId = querySetId;
        this.searchConfigurationList = searchConfigurationList;
        this.judgmentList = judgmentList;
        this.size = size;
        this.results = results;
    }

    @Override
    public XContentBuilder toXContent(XContentBuilder builder, Params params) throws IOException {
        XContentBuilder xContentBuilder = builder.startObject();
        xContentBuilder.field(ID, this.id.trim());
        xContentBuilder.field(TIME_STAMP, this.timestamp.trim());
        xContentBuilder.field(TYPE, this.type.name().trim());
        xContentBuilder.field(STATUS, this.status.name().trim());
        xContentBuilder.field(QUERY_SET_ID, this.querySetId.trim());
        xContentBuilder.field(
            SEARCH_CONFIGURATION_LIST,
            this.searchConfigurationList == null ? new ArrayList<>() : this.searchConfigurationList
        );
        xContentBuilder.field(JUDGMENT_LIST, this.judgmentList == null ? new ArrayList<>() : this.judgmentList);
        xContentBuilder.field(SIZE, Optional.of(this.size).orElse(DEFAULTED_SIZE));
        xContentBuilder.field(RESULTS, this.results);
        return xContentBuilder.endObject();
    }

    public String id() {
        return id;
    }

    public String timestamp() {
        return timestamp;
    }

    public ExperimentType type() {
        return type;
    }

    public ExperimentStatus status() {
        return status;
    }

    public String querySetId() {
        return querySetId;
    }

    public List<String> searchConfigurationList() {
        return searchConfigurationList;
    }

    public List<String> judgmentList() {
        return judgmentList;
    }

    public int size() {
        return size;
    }

    public Map<String, Object> results() {
        return results;
    }

}
