/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */
package org.opensearch.searchrelevance.transport.abTest;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.opensearch.core.action.ActionResponse;
import org.opensearch.core.common.io.stream.StreamInput;
import org.opensearch.core.common.io.stream.StreamOutput;
import org.opensearch.core.xcontent.ToXContentObject;
import org.opensearch.core.xcontent.XContentBuilder;

import lombok.Getter;

/**
 * Response for AB test search containing test_id and interleaved hits
 * with _search_configuration_id per hit for click attribution.
 */
@Getter
public class ABTestSearchResponse extends ActionResponse implements ToXContentObject {
    private final String testId;
    private final List<Map<String, Object>> hits;

    public ABTestSearchResponse(String testId, List<Map<String, Object>> hits) {
        this.testId = testId;
        this.hits = hits;
    }

    @SuppressWarnings("unchecked")
    public ABTestSearchResponse(StreamInput in) throws IOException {
        super(in);
        this.testId = in.readString();
        int size = in.readVInt();
        this.hits = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            this.hits.add(in.readMap());
        }
    }

    @Override
    public void writeTo(StreamOutput out) throws IOException {
        out.writeString(testId);
        out.writeVInt(hits.size());
        for (Map<String, Object> hit : hits) {
            out.writeMap(hit);
        }
    }

    @Override
    public XContentBuilder toXContent(XContentBuilder builder, Params params) throws IOException {
        builder.startObject();
        builder.field("test_id", testId);
        builder.startArray("hits");
        for (Map<String, Object> hit : hits) {
            builder.map(hit);
        }
        builder.endArray();
        builder.endObject();
        return builder;
    }
}
