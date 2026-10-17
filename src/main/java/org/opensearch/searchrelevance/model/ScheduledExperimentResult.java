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
public class ScheduledExperimentResult implements ToXContentObject {
    public static final String ID_FIELD = "id";
    public static final String EXPERIMENT_ID_FIELD = "experimentId";
    public static final String TIMESTAMP_FIELD = "timestamp";
    public static final String STATUS = "status";
    public static final String RESULTS_FIELD = "results";

    private final String id;
    private final String experimentId;
    private final String timestamp;
    private final AsyncStatus status;
    private final List<Map<String, Object>> results;

    @Override
    public XContentBuilder toXContent(XContentBuilder builder, Params params) throws IOException {
        builder.startObject();
        builder.field(ID_FIELD, this.id)
            .field(EXPERIMENT_ID_FIELD, this.experimentId)
            .field(TIMESTAMP_FIELD, this.timestamp)
            .field(STATUS, this.status.name())
            .field(RESULTS_FIELD, this.results);
        builder.endObject();
        return builder;
    }
}
