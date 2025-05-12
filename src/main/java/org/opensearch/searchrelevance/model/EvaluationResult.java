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

import org.opensearch.core.xcontent.ToXContentObject;
import org.opensearch.core.xcontent.XContentBuilder;

/**
 * EvaluationResult is a system index object that store single searchConfiguration and queryText results.
 */
public class EvaluationResult implements ToXContentObject {
    public static final String ID = "id";
    public static final String TIMESTAMP = "timestamp";
    public static final String SEARCH_CONFIGURATION_ID = "searchConfigurationId";
    public static final String SEARCH_TEXT = "searchText";
    public static final String JUDGMENT_IDS = "judgmentIds";
    public static final String DOCUMENT_IDS = "documentIds";
    public static final String METRICS = "metrics";

    /**
     * Identifier of the system index
     */
    private final String id;
    private final String timestamp;
    private final String searchConfigurationId;
    private final String searchText;
    private final List<String> judgmentIds;
    private final List<String> documentIds;
    private final Map<String, String> metrics;

    public EvaluationResult(
        String id,
        String timestamp,
        String searchConfigurationId,
        String searchText,
        List<String> judgmentIds,
        List<String> documentIds,
        Map<String, String> metrics
    ) {
        this.id = id;
        this.timestamp = timestamp;
        this.searchConfigurationId = searchConfigurationId;
        this.searchText = searchText;
        this.judgmentIds = judgmentIds;
        this.documentIds = documentIds;
        this.metrics = metrics;
    }

    @Override
    public XContentBuilder toXContent(XContentBuilder builder, Params params) throws IOException {
        XContentBuilder xContentBuilder = builder.startObject();
        xContentBuilder.field(ID, this.id.trim());
        xContentBuilder.field(TIMESTAMP, this.timestamp.trim());
        xContentBuilder.field(SEARCH_CONFIGURATION_ID, this.searchConfigurationId.trim());
        xContentBuilder.field(SEARCH_TEXT, this.searchText.trim());
        xContentBuilder.field(JUDGMENT_IDS, this.judgmentIds == null ? new ArrayList<>() : this.judgmentIds);
        xContentBuilder.field(DOCUMENT_IDS, this.documentIds == null ? new ArrayList<>() : this.documentIds);
        xContentBuilder.field(METRICS, this.metrics);
        return xContentBuilder.endObject();
    }

    public String id() {
        return id;
    }

    public String timestamp() {
        return timestamp;
    }

    public String searchConfigurationId() {
        return searchConfigurationId;
    }

    public String searchText() {
        return searchText;
    }

    public List<String> judgmentIds() {
        return judgmentIds;
    }

    public List<String> documentIds() {
        return documentIds;
    }

    public Map<String, String> metrics() {
        return metrics;
    }
}
