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

public class Judgment implements ToXContentObject {
    public static final String ID = "id";
    public static final String TIME_STAMP = "timestamp";
    public static final String RESPONSE = "response";
    private String id;
    private String timestamp;
    private String response;

    public Judgment(String id, String timestamp, String response) {
        this.id = id;
        this.timestamp = timestamp;
        this.response = response;
    }

    @Override
    public XContentBuilder toXContent(XContentBuilder builder, Params params) throws IOException {
        XContentBuilder xContentBuilder = builder.startObject();
        xContentBuilder.field(ID, this.id.trim());
        xContentBuilder.field(TIME_STAMP, this.timestamp.trim());
        xContentBuilder.field(RESPONSE, this.response.trim());
        return xContentBuilder.endObject();
    }

    public String id() {
        return id;
    }

    public String timestamp() {
        return timestamp;
    }

    public String response() {
        return response;
    }

}
