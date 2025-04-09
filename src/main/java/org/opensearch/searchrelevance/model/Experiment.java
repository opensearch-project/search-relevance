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

import org.opensearch.core.xcontent.ToXContent.Params;
import org.opensearch.core.xcontent.ToXContentObject;
import org.opensearch.core.xcontent.XContentBuilder;

/**
 * Experiment is a system index object that store experiment results.
 */
public class Experiment implements ToXContentObject {
    public static final String ID = "id";
    public static final String TIME_STAMP = "timestamp";
    public static final String INDEX = "index";
    public static final String QUERY_SET_ID = "querySetId";
    public static final String SEARCH_CONFIGURATION_LIST = "searchConfigurationList";
    public static final String K = "k";
    public static final String RESULTS = "results";
    private static final int DEFAULTED_K_VALUE = 10;

    /**
     * Identifier of the system index
     */
    private final String id;
    private final String timestamp;
    private final String index;
    private final String querySetId;
    private final List<String> searchConfigurationList;
    private final int k;
    private final Map<String, Object> results;

    public Experiment(
        String id,
        String timestamp,
        String index,
        String querySetId,
        List<String> searchConfigurationList,
        int k,
        Map<String, Object> results
    ) {
        this.id = id;
        this.timestamp = timestamp;
        this.index = index;
        this.querySetId = querySetId;
        this.searchConfigurationList = searchConfigurationList;
        this.k = k;
        this.results = results;
    }

    @Override
    public XContentBuilder toXContent(XContentBuilder builder, Params params) throws IOException {
        XContentBuilder xContentBuilder = builder.startObject();
        xContentBuilder.field(ID, this.id.trim());
        xContentBuilder.field(TIME_STAMP, this.timestamp.trim());
        xContentBuilder.field(INDEX, this.index.trim());
        xContentBuilder.field(QUERY_SET_ID, this.querySetId.trim());
        xContentBuilder.field(
            SEARCH_CONFIGURATION_LIST,
            this.searchConfigurationList == null ? new ArrayList<>() : this.searchConfigurationList
        );
        xContentBuilder.field(K, Optional.of(this.k).orElse(DEFAULTED_K_VALUE));
        xContentBuilder.field(RESULTS, this.results);
        return xContentBuilder.endObject();
    }

    public String id() {
        return id;
    }

    public String timestamp() {
        return timestamp;
    }

    public String index() {
        return index;
    }

    public String querySetId() {
        return querySetId;
    }

    public List<String> searchConfigurationList() {
        return searchConfigurationList;
    }

    public int k() {
        return k;
    }

    public Map<String, Object> results() {
        return results;
    }

}
