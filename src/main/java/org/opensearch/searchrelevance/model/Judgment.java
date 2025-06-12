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
import java.util.Map;

import org.opensearch.core.xcontent.ToXContentObject;
import org.opensearch.core.xcontent.XContentBuilder;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class Judgment implements ToXContentObject {
    public static final String ID = "id";
    public static final String TIME_STAMP = "timestamp";
    public static final String NAME = "name";
    public static final String STATUS = "status";
    public static final String TYPE = "type";
    public static final String METADATA = "metadata";
    public static final String JUDGMENT_RATINGS = "judgmentRatings";

    /**
     * Identifier of the system index
     */
    private final String id;
    private final String timestamp;
    private final String name;
    private final AsyncStatus status;
    private final JudgmentType type;
    private final Map<String, Object> metadata;
    private final List<Map<String, Object>> judgmentRatings;

    @Override
    public XContentBuilder toXContent(XContentBuilder builder, Params params) throws IOException {
        XContentBuilder xContentBuilder = builder.startObject();
        xContentBuilder.field(ID, this.id.trim());
        xContentBuilder.field(TIME_STAMP, this.timestamp.trim());
        xContentBuilder.field(NAME, this.name.trim());
        xContentBuilder.field(STATUS, this.status.name().trim());
        xContentBuilder.field(TYPE, this.type.name().trim());
        xContentBuilder.field(METADATA, this.metadata);
        // Start judgmentRatings object
        xContentBuilder.startArray(JUDGMENT_RATINGS);
        for (Map<String, Object> judgment : this.judgmentRatings) {
            xContentBuilder.startObject();
            xContentBuilder.field("query", judgment.get("query"));
            xContentBuilder.startArray("ratings");
            for (Map<String, Object> rating : (List<Map<String, Object>>) judgment.get("ratings")) {
                xContentBuilder.startObject();
                for (Map.Entry<String, Object> entry : rating.entrySet()) {
                    xContentBuilder.field(entry.getKey(), entry.getValue());
                }
                xContentBuilder.endObject();
            }
            xContentBuilder.endArray();
            xContentBuilder.endObject();
        }
        xContentBuilder.endArray();
        // End judgmentRatings object
        return xContentBuilder.endObject();
    }
}
