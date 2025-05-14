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

public class JudgmentCache implements ToXContentObject {
    public static final String ID = "id";
    public static final String QUERY_TEXT = "queryText";
    public static final String DOCUMENT_ID = "documentId";
    public static final String TIME_STAMP = "timestamp";
    public static final String SCORE = "score";
    public static final String MODEL_ID = "modelId";

    /**
     * Identifier of the system index
     */
    private String id;
    private String timestamp;
    private String queryText;
    private String documentId;
    private String score;
    private String modelId;

    public JudgmentCache(String id, String timestamp, String queryText, String documentId, String score, String modelId) {
        this.id = id;
        this.timestamp = timestamp;
        this.queryText = queryText;
        this.documentId = documentId;
        this.score = score;
        this.modelId = modelId;
    }

    @Override
    public XContentBuilder toXContent(XContentBuilder builder, Params params) throws IOException {
        XContentBuilder xContentBuilder = builder.startObject();
        xContentBuilder.field(ID, this.id.trim());
        xContentBuilder.field(TIME_STAMP, this.timestamp.trim());
        xContentBuilder.field(QUERY_TEXT, this.queryText.trim());
        xContentBuilder.field(DOCUMENT_ID, this.documentId.trim());
        xContentBuilder.field(SCORE, this.score.trim());
        xContentBuilder.field(MODEL_ID, this.modelId.trim());
        return xContentBuilder.endObject();
    }

    public String id() {
        return id;
    }

    public String timestamp() {
        return timestamp;
    }

    public String queryText() {
        return queryText;
    }

    public String documentId() {
        return documentId;
    }

    public String score() {
        return score;
    }
}
