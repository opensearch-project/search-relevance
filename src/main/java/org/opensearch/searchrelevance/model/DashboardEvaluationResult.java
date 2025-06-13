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

import org.opensearch.core.xcontent.ToXContentObject;
import org.opensearch.core.xcontent.XContentBuilder;

/**
 * DashboardEvaluationResult is a system index object that stores evaluation results for dashboard display.
 */
public class DashboardEvaluationResult implements ToXContentObject {
    public static final String ID = "id";
    public static final String TIMESTAMP = "timestamp";
    public static final String SEARCH_CONFIG = "search_config";
    public static final String QUERY_SET_ID = "query_set_id";
    public static final String QUERY = "query";
    public static final String METRIC = "metric";
    public static final String VALUE = "value";
    public static final String APPLICATION = "application";
    public static final String EVALUATION_ID = "evaluation_id";

    private final String id;
    private final String timestamp;
    private final String searchConfig;
    private final String querySetId;
    private final String query;
    private final String metric;
    private final String value;
    private final String application;
    private final String evaluationId;

    public DashboardEvaluationResult(
        String id,
        String timestamp,
        String searchConfig,
        String querySetId,
        String query,
        String metric,
        String value,
        String application,
        String evaluationId
    ) {
        this.id = id;
        this.timestamp = timestamp;
        this.searchConfig = searchConfig;
        this.querySetId = querySetId;
        this.query = query;
        this.metric = metric;
        this.value = value;
        this.application = application;
        this.evaluationId = evaluationId;
    }

    @Override
    public XContentBuilder toXContent(XContentBuilder builder, Params params) throws IOException {
        XContentBuilder xContentBuilder = builder.startObject();
        xContentBuilder.field(ID, this.id.trim());
        xContentBuilder.field(TIMESTAMP, this.timestamp.trim());
        xContentBuilder.field(SEARCH_CONFIG, this.searchConfig.trim());
        xContentBuilder.field(QUERY_SET_ID, this.querySetId.trim());
        xContentBuilder.field(QUERY, this.query.trim());
        xContentBuilder.field(METRIC, this.metric.trim());
        xContentBuilder.field(VALUE, this.value.trim());
        xContentBuilder.field(APPLICATION, this.application.trim());
        xContentBuilder.field(EVALUATION_ID, this.evaluationId.trim());
        return xContentBuilder.endObject();
    }

    public String id() {
        return id;
    }

    public String timestamp() {
        return timestamp;
    }

    public String searchConfig() {
        return searchConfig;
    }

    public String querySetId() {
        return querySetId;
    }

    public String query() {
        return query;
    }

    public String metric() {
        return metric;
    }

    public String value() {
        return value;
    }

    public String application() {
        return application;
    }

    public String evaluationId() {
        return evaluationId;
    }
} 