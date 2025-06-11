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
import lombok.Builder;
import lombok.Getter;

/**
 * Experiment is a system index object that store experiment variant results.
 */
@AllArgsConstructor
@Builder
@Getter
public class ExperimentVariant implements ToXContentObject {
    public static final String ID = "id";
    public static final String TIME_STAMP = "timestamp";
    public static final String TYPE = "type";
    public static final String STATUS = "status";
    public static final String EXPERIMENT_ID = "experimentId";
    public static final String PARAMETERS = "parameters";
    public static final String RESULTS = "results";

    /**
     * Identifier of the system index
     */
    private final String id;
    private final String timestamp;
    private final ExperimentType type;
    private final AsyncStatus status;
    private final String experimentId;
    private final List<Map<String, Object>> parameters;
    private final Map<String, Object> results;

    @Override
    public XContentBuilder toXContent(XContentBuilder builder, Params params) throws IOException {
        XContentBuilder xContentBuilder = builder.startObject();
        xContentBuilder.field(ID, this.id.trim());
        xContentBuilder.field(TIME_STAMP, this.timestamp.trim());
        xContentBuilder.field(TYPE, this.type.name().trim());
        xContentBuilder.field(STATUS, this.status.name().trim());
        xContentBuilder.field(EXPERIMENT_ID, this.experimentId.trim());
        xContentBuilder.field(PARAMETERS, this.parameters);
        xContentBuilder.field(RESULTS, this.results);
        return xContentBuilder.endObject();
    }

    /**
     * Get parameter value by name from the nested parameters structure
     * @param parameterName the name of the parameter to retrieve
     * @return the parameter value or null if not found
     */
    public Object getParameterValue(String parameterName) {
        if (parameters == null) {
            return null;
        }
        return parameters.stream()
            .filter(param -> parameterName.equals(param.get("name")))
            .map(param -> param.get("value"))
            .findFirst()
            .orElse(null);
    }
}
