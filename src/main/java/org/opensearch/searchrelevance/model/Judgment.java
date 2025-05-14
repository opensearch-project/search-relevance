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

public class Judgment implements ToXContentObject {
    public static final String ID = "id";
    public static final String TIME_STAMP = "timestamp";
    public static final String NAME = "name";
    public static final String STATUS = "status";
    public static final String TYPE = "type";
    public static final String METADATA = "metadata";
    public static final String JUDGMENT_SCORES = "judgmentScores";

    /**
     * Identifier of the system index
     */
    private String id;
    private String timestamp;
    private String name;
    private final AsyncStatus status;
    private JudgmentType type;
    private Map<String, Object> metadata;
    private Map<String, Map<String, String>> judgmentScores;

    public Judgment(
        String id,
        String timestamp,
        String name,
        AsyncStatus status,
        JudgmentType type,
        Map<String, Object> metadata,
        Map<String, Map<String, String>> judgmentScores
    ) {
        this.id = id;
        this.timestamp = timestamp;
        this.name = name;
        this.status = status;
        this.type = type;
        this.metadata = metadata;
        this.judgmentScores = judgmentScores;
    }

    @Override
    public XContentBuilder toXContent(XContentBuilder builder, Params params) throws IOException {
        XContentBuilder xContentBuilder = builder.startObject();
        xContentBuilder.field(ID, this.id.trim());
        xContentBuilder.field(TIME_STAMP, this.timestamp.trim());
        xContentBuilder.field(NAME, this.name.trim());
        xContentBuilder.field(STATUS, this.status.name().trim());
        xContentBuilder.field(TYPE, this.type.name().trim());
        xContentBuilder.field(METADATA, this.metadata);
        // Start judgmentScores object
        xContentBuilder.startObject(JUDGMENT_SCORES);
        for (Map.Entry<String, Map<String, String>> queryEntry : this.judgmentScores.entrySet()) {
            xContentBuilder.startArray(queryEntry.getKey());
            for (Map.Entry<String, String> docEntry : queryEntry.getValue().entrySet()) {
                xContentBuilder.startObject().field("docId", docEntry.getKey()).field("score", docEntry.getValue()).endObject();
            }
            xContentBuilder.endArray();
        }
        xContentBuilder.endObject();
        // End judgmentScores object
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

    public AsyncStatus status() {
        return status;
    }

    public JudgmentType type() {
        return type;
    }

    public Map<String, Object> metadata() {
        return metadata;
    }

    public Map<String, Map<String, String>> judgmentScores() {
        return judgmentScores;
    }

}
